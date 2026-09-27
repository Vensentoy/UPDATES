# Revyu — Architecture

Developer-facing internals: layers, data model, parser pipeline, AI pipeline, calendar
engine, widgets, and DI. Companion to [FEATURES.md](FEATURES.md).

## Layer map

```
ui/        Compose screens + ViewModels (MVVM), one activity
data/      Room entities/DAOs, repositories, remote (OpenRouter), prompt builder
core/      framework-free logic: calendar engine, parsers, PDF renderer, theme, prefs
di/        AppContainer (manual service locator) + ComposeDI (LocalAppContainer)
work/      WorkManager workers (Smart Calendar check, widget refresh)
widget/    Native home-screen widget (provider + renderer + pin receiver)
```

Dependency direction: `ui → data → core`, everything constructed in `di/AppContainer`.
There is no framework DI (Hilt/Dagger) — a deliberate choice to avoid annotation-processor
version coupling with Kotlin/AGP.

## Dependency injection (`di/`)

- `di/AppContainer.kt` builds and owns every singleton: AppSettingsStore, ApiKeyManager,
  Room database, repositories, parser registry, renderer, WorkScheduler, and the
  `FileTextExtractor`.
- `di/ComposeDi.kt` exposes a static `LocalAppContainer` CompositionLocal and a
  `viewModelFactory {}` helper. ViewModels pull dependencies from it in Compose.
- **Graph-scoped ViewModels**: the Study Load import flow and the Create/Vault wizards use
  nested navigation graphs; their ViewModels are created against the graph's back-stack
  entry (`rememberWizardViewModel`, `rememberVaultViewModel`) so state survives across steps.

## Data layer (`data/`)

### Room schema (`data/local/`)

| Entity | Purpose | Key DAO |
|---|---|---|
| `SubjectEntity` | A subject (folder), stable color accent | `SubjectDao` |
| `StudySetEntity` | One generated set (kind: REGULAR / MIDTERM_VAULT / FINAL_VAULT; generation status PENDING/GENERATING/READY/FAILED) | `StudySetDao` |
| `StudyMaterialEntity` | Source material (extracted text kept, original bytes discarded) | `StudyMaterialDao` |
| `StudyLoadEntity` | An imported study-load file (History tab) | `StudyLoadDao` |
| `ScheduleEntryEntity` | One class block from the study load | `ScheduleDao` |
| `CalendarEventEntity` | Rendered calendar items (CLASS / SUGGESTED_STUDY / REVIEW / EXAM / REMINDER) | `CalendarEventDao` |
| `FlashcardEntity`, `QuestionEntity`, `ExamAttemptEntity` | Generated content + exam attempts | `ContentDao` (Flashcard/Question/ExamAttempt DAOs) |
| `HomeWidgetPrefEntity` | In-app widget grid visibility/order | `HomeWidgetPrefDao` |

`TypeConverters` map `Instant↔Long`, `List<String>` ↔ JSON, and enums ↔ name strings.

### Repositories (`data/repository/`)

- `SubjectRepository`, `StudySetRepository`, `StudyMaterialRepository` — subject/study-set
  CRUD and generation-status tracking.
- `StudyLoadRepository` — transactionally imports a study load: StudyLoadEntity →
  subjects + schedule entries + CLASS events + suggested events.
- `SmartCalendarRepository` — the "smart" layer: 3-day window of `PendingReviewTask`s
  derived from schedule vs exam status (offline, deterministic).
- `CalendarEventRepository` + `CalendarEventMappers` — observes events, regenerates
  suggestions, group deletes.
- `GenerationRepository`, `WidgetDataRepository`, `SettingsRepository` — see below.

## Study Load parsing (`core/util/studyload/`)

- `StudyLoadParser.kt` — the **interface** + `StudyLoadParserRegistry` that probes parsers
  in order on raw extracted text. Only `LLCCStudyLoadParser` is registered today; the
  registry is the seam for future colleges.
- `LLCCStudyLoadParser.kt` — deterministic, regex/heuristic parser. Design points:
  - **Scored recognition**: college wording (LAPU + CITY COLLEGE anywhere), a "STUDY LOAD"
    heading + semester/A.Y., or ≥1 subject code `[A-Z]{2,6}\d{3,5}-\d{2,4}`. One strong
    signal is enough — partial matches parse with a warning attached to the report.
  - **Block splitting by subject code**: repeated codes stay in one block so every weekly
    session belongs to its subject; lines before the first code are preamble.
  - **Orphan carry-forward**: in PdfBox's position-sorted output, a 2-meeting subject's
    second session row can be drawn below the *previous* subject's row. Trailing
    schedule-only rows transfer to the next subject only when the current code line
    already carried an inline session. Preamble session rows prepend to the first subject.
  - **Room fragments**: a wrapped room cell (`CAS-RM5A/COT-` + `RM2`) is re-joined when a
    session's room ends in `-` and the next starts with `RM`.
  - **Units**: accepts `N units` or a bare trailing digit (guarded ≤ 8 so "…101" in a title
    isn't misread). Catalog codes (`TPCC 311`) are stripped from names.
  - **Metadata**: semester, academic year (`A.Y. 2026 - 2027`), course/year/section
    (`BIndTech 3 - D Computer Technology`), and student name (`TAPAO, JAPHET S.`).
  - **Noise**: repeated table headers, page numbers, registrar footers/signatures, and
    date lines are filtered.
  - Models (`StudyLoadModels.kt`): `ParsedStudyLoad`, `ParsedStudyLoadSubject`,
    `ParsedClassSession`, `StudyLoadParseReport` (studyLoad + warnings).
- **Legacy parser** `core/util/StudyLoadParser.kt` (block → editable text round-trip) is
  only used by the old onboarding flow; keep as reference for carry-forward logic.

## Text extraction (`core/util/FileTextExtractor.kt`)

Returns `ExtractionResult(text, sourceType, fileName)`.

- PDF: PdfBox-Android with `sortByPosition = true` (visual reading order). If the result
  scores lower than raw content-stream order on a table heuristic (line count, subject
  codes, clock times), the raw order wins — a safety net for odd/scan-ish PDFs.
- DOCX: unzips `word/document.xml` and walks `<w:t>` runs with the platform
  `XmlPullParser`, inserting newlines at paragraph boundaries (`<w:p>`).
- TXT/MD: read as UTF-8 text. Empty extraction throws `ExtractionException`.

## Calendar engine (`core/calendar/`)

- `StudyScheduler.kt` — pure scheduling: `SmartStudyScheduler` derives
  `SuggestedSession`s from `ClassBlock`s using `SchedulerConfig` (block minutes, day
  window, weekend flag, daily max). No wall-clock or DB dependency → unit-testable.
- `CalendarEventGenerator.kt` — `classEvents()` and `studyEvents()` produce
  `CalendarEvent`s from schedule entries and sessions.
- `CalendarEvent.kt` — event model + `displayLabel`.

## AI generation (`data/remote/`, `data/prompt/`, `data/repository/`)

- `OpenRouterClient.kt` — Retrofit + OkHttp. Headers via `AuthInterceptor` (Bearer key,
  `HTTP-Referer: https://revyu.app`, `X-Title: Revyu`). `RetryInterceptor` handles 429 with
  3 retries and exponential backoff (1.5s/3s/6s, honoring `Retry-After`). Timeouts:
  connect 30s / read 90s / write 60s.
- `ApiKeyManager` (EncryptedSharedPreferences) → `ApiKeyProvider` (in-memory) →
  interceptor. `AiProvider` enum (OPENROUTER) is the seam for future providers.
- `ChatDtos` — `ChatCompletionRequest(model = nvidia/nemotron-3-ultra-550b-a55b:free,
  temperature 0.4, maxTokens 4096, topP 0.95, reasoning, response_format = json_object)`.
- `PromptBuilder.kt` — `MAX_SOURCE_CHARS = 40_000`, `MAX_VAULT_SOURCE_CHARS = 60_000`;
  min flashcard counts 8 (normal) / 15 (vault); reflects customizer choices and returns a
  strict-JSON schema request.
- `GenerationRepository.kt` — sets status GENERATING → calls API → parses
  `GeneratedStudySetPayload(reviewer, flashcards, questions)` → persists. Wraps outcomes
  in `ReyyuResult.Success/Error(isRateLimit)`.

## PDF rendering (`core/util/PdfReviewerRenderer.kt`)

A from-scratch multi-column text layout engine on Android's `PdfDocument` (A4 1240×1754
points). Honors `ReviewerMargins` (compact/normal/spacious), `ReviewerFontStyle`
(serif/sans/mono), and `ReviewerLayoutColumns` (1–4). Not a typesetting engine — very
long or unusual content can paginate roughly (acknowledged trade-off).

## Widget system (`widget/`, `ui/widgets/`, `data/repository/WidgetDataRepository.kt`)

- `RevyuWidgetProvider` (AppWidgetProvider) on update triggers `WorkScheduler.refreshWidgetsNow`.
- `RevyuWidgetRenderer` builds `RemoteViews` by size class (small ≤2 rows / medium ≤4 /
  large 8); event rows come from `core/widget/WidgetModels.kt`.
- In-app panel (`WidgetsTabScreen` + `HomeWidgetGrid`) renders the same data as cards,
  reorderable/toggleable via `home_widget_prefs`.
- `WidgetRefreshWorker` runs daily and re-renders pinned widgets from Room.
- Deep links from the widget are handled in `MainActivity` extras
  (`EXTRA_OPEN_SUBJECT_ID`, `EXTRA_OPEN_STUDY_SET_ID`, `EXTRA_OPEN_TAB`).

## Background work (`work/`)

- `WorkScheduler` — `scheduleDailySmartCalendarCheck` (24h periodic), `scheduleDailyWidgetRefresh`
  (24h), and one-shot coalescing `refreshWidgetsNow`.
- `SmartCalendarWorker` — pending-task check + notification (requires API key +
  `POST_NOTIFICATIONS`); channel `smart_calendar`.
- `WidgetRefreshWorker` — re-renders widgets.

## Notifications & permissions

Manifest grants `INTERNET` and `POST_NOTIFICATIONS`. A notification channel is created in
`RevyuApplication`. Network calls use a `network_security_config`. File export/open uses a
`FileProvider` (`com.revyu.app.fileprovider`).

## Testing

- Unit tests (JUnit 4 + kotlinx flows) live under `app/src/test`. The study-load parser
  suite includes both fixture documents and a copy of a **real LLCC Study Load PDF**
  text (position-sorted and raw) asserting exact subjects/sessions/units/metadata.
- `gradle :app:testDebugUnitTest` runs the full suite (currently 25 tests).
- Instrumented tests (`androidTest`) use the Compose test rules + Espresso for UI.