# Revyu — Feature Guide

Every feature in the app, what it does for you, and how it works under the hood.

---

## 1. Smart Calendar (Home tab)

**What it does.** Shows a weekly calendar of your real classes plus *suggested* study
blocks, day by day. Tapping a day reveals its schedule. Smart Calendar also runs a
background check once a day and sends a notification when a subject has a class soon but
its **Exam Mode isn't finished yet**.

**How it works.**

- **Deterministic and offline** — no AI runs on the calendar side. `core/calendar/StudyScheduler.kt`
  computes suggested session blocks from your study-load schedule using
  `SchedulerConfig` (defaults: 45-minute blocks, 16:00–21:00 window, weekends allowed,
  max 90 minutes/day per subject). `core/calendar/CalendarEventGenerator.kt` turns classes
  and suggestions into `CalendarEvent`s (`CLASS`, `SUGGESTED_STUDY`,
  `REVIEW`, `EXAM`, `REMINDER`).
- **The "smart" layer** is `data/repository/SmartCalendarRepository.kt`: it cross-references
  each subject's schedule against its `ExamStatus` (`NOT_TAKEN` / `IN_PROGRESS` / `COMPLETED`)
  over a rolling 3-day window and emits `PendingReviewTask`s.
- **Background**: `work/SmartCalendarWorker.kt` (scheduled daily by `work/WorkScheduler.kt`)
  checks pending tasks and posts a notification (channel `smart_calendar`); refresh happens
  on app open and via daily WorkManager.
- **Settings that affect it** (see feature 10): auto-study-suggestion toggle, block
  minutes, preferred start/end hours, weekend study toggle.

Key files: `ui/home/SmartCalendarScreen.kt`, `core/calendar/StudyScheduler.kt`,
`data/repository/SmartCalendarRepository.kt`, `work/SmartCalendarWorker.kt`.

---

## 2. Study Load importer

**What it does.** Takes your official LLCC "STUDY LOAD" PDF (the class schedule you get
from the registrar) and turns it into recognized subjects, your course/year/section
metadata, class sessions, and calendar events. Before anything is saved you get an
**editable review table** — rename subjects, fix units, toggle them on/off — then "Save".

**How it works.**

1. **Pick & validate** (`ui/import/StudyLoadImportScreen.kt`,
   `StudyLoadFileValidation`): PDF only, max 5 MB, friendly Cebuano error messages.
2. **Extract text** (`core/util/FileTextExtractor.kt`): PDFBox extracts with
   `sortByPosition = true`; if that comes back weak it falls back to the raw content-stream
   order (score-based decision). DOCX, TXT, and MD are also supported for the general
   create flow.
3. **Parse** (`core/util/studyload/`): a registry probes parsers in order
   (`StudyLoadParserRegistry`), currently just `LLCCStudyLoadParser`. Recognition is
   score-based (college wording, "STUDY LOAD" heading, or any subject code), so a PDF
   whose header words sort apart still imports. The parser handles row-major and block
   layouts, multi-session subjects (orphan session rows are carried to the subject that
   owns them), wrapped room cells ("CAS-RM5A/COT-" + "RM2"), bare unit digits, scanners,
   footers, and page breaks. It also reads header metadata: semester, academic year,
   course, year & section, and student name.
4. **Review & save** (`StudyLoadReviewScreen.kt`, `StudyLoadImportViewModel.kt`): you
   confirm the parsed subjects, then `data/repository/StudyLoadRepository.kt` imports
   transactionally — one `StudyLoadEntity`, its subjects, schedule entries, CLASS events,
   and suggested-study events.

If a file isn't recognized at all, the error screen shows a short preview of the
extracted text so you can tell why (e.g., a scanned PDF that has no text layer).

Key files: `core/util/studyload/` (parser, models, validation), `core/util/FileTextExtractor.kt`,
`data/repository/StudyLoadRepository.kt`, `ui/import/`.

---

## 3. Reviewer tab

**What it does.** The hub for your study material. Lists every subject with its study
sets and a status badge: **Ready**, **Generating**, **Failed**, or an exam status like
**Exam pending/completed**. From here you can import a Study Load, open a subject, create
a new Study Set, or jump straight into Exam Mode.

**How it works.** `ui/reviewer/ReviewerTabScreen.kt` + `ReviewerTabViewModel.kt` observe
subjects with their study sets via `data/repository/SubjectRepository.kt`
(`observeSubjectsWithStudySets`) and roll statuses up from `GenerationStatus` and
`ExamStatus`. After an import succeeds, navigation clears the back stack back to this tab
(`RevyuNavHost` uses `popUpTo` on the start destination so the tab is always reachable).

---

## 4. Create Study Set wizard

**What it does.** Four steps: pick a subject → upload material → customize the Reviewer →
customize practice questions → generate. The result is a Study Set containing a
Reviewer PDF, Flashcards, and Practice Questions.

**How it works.** A nested navigation graph (`create/{subjectId}`) scopes one
`CreateStudySetViewModel` across all steps:

1. **Upload** (`UploadMaterialScreen`): PDF / DOCX / TXT / MD. Text is extracted at
   upload time via `FileTextExtractor` and stored as plain text — original bytes are
   discarded, so a set can be regenerated later without re-uploading.
2. **Customize reviewer** (`CustomizeReviewerScreen`): margins (compact/normal/spacious),
   font (serif/sans/mono), column count (1–4), and whether it's included in Exam Mode.
3. **Customize practice** (`CustomizePracticeScreen`): question types and counts.
   `QuestionType`: single choice, multiple choice, true/false, identification, short answer.
4. **Generate** (`GeneratingScreen` + `GenerationUiState`): calls the AI pipeline
   (feature 12). Big source materials are truncated (40,000 chars for a normal set;
   60,000 for vault synthesis). Rate-limit responses surface a friendly "still busy"
   state instead of a raw failure.

---

## 5. Study Set viewer

**What it does.** Opens the generated Set with three tabs:

- **Reviewer** — a rendered PDF preview. Includes Open and Share/Download (via
  `FileProvider`).
- **Flashcards** — a deck of flip cards (animated 3D flip).
- **Questions** — practice questions with tap-to-reveal answers, presented in the app's
  signature "Margin Rule" card style.

**How it works.** `ui/studyset/StudySetOverviewScreen.kt` + `StudySetViewModel` load the
set from Room. Reviewer pages are drawn with Android's `PdfRenderer` from the PDF written
by `core/util/PdfReviewerRenderer.kt` (feature 12); flashcards use a `FlipCard` composable
with a `rotationY` animation; questions render from `QuestionEntity`s.

---

## 6. Exam Mode & Results

**What it does.** A timed exam built from a Study Set's questions. Submit to see a score
summary and a per-question review (correct/incorrect with the right answer).

**How it works.**

- `ui/studyset/ExamModeScreen.kt` + `ExamModeViewModel`: a timer runs while you answer;
  answers are collected in a map and submitted, creating an `ExamAttemptEntity` and setting
  the set's `ExamStatus`.
- `ui/studyset/ResultsScreen.kt` + `ResultsViewModel`: shows the score and per-question
  review from the attempt.
- **Why it matters**: `ExamStatus` is exactly what Smart Calendar inspects to decide a
  subject needs a reminder (feature 1).

---

## 7. Semester Vault

**What it does.** Synthesizes one **MIDTERM** or **FINAL** comprehensive reviewer across
all the prior study sets of a subject — a single "whole-semester" review document instead
of per-upload sets.

**How it works.** `ui/vault/` (SemesterVaultSubjectsScreen → VaultConfigure →
VaultGenerating, plus `SemesterVaultViewModel`) uses the same AI pipeline as the Create
wizard but with a vault prompt that aggregates the subject's existing sets. The output is
a Study Set of kind `MIDTERM_VAULT` or `FINAL_VAULT` (`StudySetKind`), which opens in the
normal Study Set viewer. Vault synthesis uses a larger source budget (60,000 chars) and
higher minimum flashcard counts (15 vs 8). _Note: the vault screens and routes are
implemented; a visible entry button in the tab UI is not currently wired up._

---

## 8. History

**What it does.** Lists every imported Study Load (with its file name) and lets you
delete one.

**How it works.** `ui/history/HistoryTabScreen.kt` + ViewModel read `StudyLoadEntity`s from
`StudyLoadDao.observeAll`; delete removes the load and its associated rows.

---

## 9. Widgets

**What it does.** Two layers:

- **Native home-screen widget** — a resizable widget (small / medium / large) showing your
  next class, today's schedule, and study-plan info. Tapping it deep-links into the app
  (subject or study set).
- **In-app Widgets panel** — six snapshot cards that mirror what the native widget
  renders, fully toggleable and reorderable: `today_schedule`, `next_class`, `study_plan`,
  `week_overview`, `study_minutes`, `subjects`.

**How it works.**

- `widget/RevyuWidgetProvider.kt` (an `AppWidgetProvider`) + `widget/RevyuWidgetRenderer.kt`
  build `RemoteViews` sized by the widget's class (`small` ≤2 rows, `medium` ≤4 rows,
  `large` 8 rows). `widget/WidgetPinResultReceiver.kt` handles the pin callback.
- Data comes from `data/repository/WidgetDataRepository.kt` (schedule, week, study minutes,
  subjects from Room) shared by both the native widget and the in-app cards
  (`ui/widgets/WidgetsTabScreen.kt`).
- Visibility/order live in `home_widget_prefs` (`HomeWidgetPrefEntity` + `HomeWidgetKeys`).
- A daily `WidgetRefreshWorker` re-renders all pinned widgets; updates also trigger on
  app data changes.
- All rendering is `RemoteViews`/Compose — no bitmap caching is involved.

---

## 10. Settings

**What it does.** Theme mode, auto-study-suggestion toggle, study/review block minutes,
preferred start/end hours, weekend study toggle, widget auto-refresh toggle, and API-key
management.

**How it works.** `ui/settings/SettingsTabScreen.kt` + `SettingsViewModel` persist to two
stores: `core/preferences/AppSettingsStore` (DataStore key `revyu_settings`) maps to
`schedulerConfig()` for the calendar engine, and `data/repository/SettingsRepository.kt`
manages the API key in `EncryptedSharedPreferences`. API-key edits open the onboarding
screen (`api_key_setup`).

---

## 11. Onboarding & API key

**What it does.** The first-run gate: you enter your free OpenRouter API key (must start
with `sk-or-`). If entered from Settings it pops back; otherwise it proceeds to the app.

**How it works.** `ui/onboarding/ApiKeySetupScreen.kt` + ViewModel validate the prefix,
then `SettingsRepository.saveApiKey` stores it encrypted via
`androidx.security:security-crypto`. `data/remote/ApiKeyManager.kt` / `ApiKeyProvider.kt`
feed it to the OpenRouter auth interceptor.

---

## 12. AI generation pipeline

**What it does.** The engine behind Create Study Set and Semester Vault: generate the
Reviewer text, flashcards, and practice questions from your uploaded material.

**How it works.**

1. `data/prompt/PromptBuilder.kt` assembles a structured prompt: source text (truncated
   to 40k chars normally / 60k for vault), customizer choices (margins/font/columns,
   question types/counts), and minimum content counts. Response is requested as JSON
   (`response_format = json_object`).
2. `data/repository/GenerationRepository.kt` marks the set `GENERATING`, calls
   `data/remote/OpenRouterClient.kt`, and parses the JSON into
   `GeneratedStudySetPayload(reviewer, flashcards, questions)`, persisting each part.
3. `OpenRouterClient` targets `nvidia/nemotron-3-ultra-550b-a55b:free` (temperature 0.4,
   max tokens 4096). A `RetryInterceptor` retries 429s up to 3 times with exponential
   backoff (1.5s / 3s / 6s, respecting `Retry-After`). Failures return
   `ReyyuResult.Error(isRateLimit = …)` so the UI can show a calm "still busy" state.
4. The Reviewer PDF is written by `core/util/PdfReviewerRenderer.kt` — an A4
   (1240×1754) multi-column text layout engine on `PdfDocument`/`Canvas`.
5. Generation runs in the DAO/`Repo` layer; status changes stream to the UI via Room
   (the Reviewer tab badges react to `GenerationStatus`).

---

## Legacy / unused paths

Kept in the codebase but not part of the active flow (documented so they don't confuse):

- **Old onboarding Study Load importer** (`ui/onboarding/StudyLoadScreen.kt` +
  `StudyLoadViewModel`) uses the **old heuristic parser** (`core/util/StudyLoadParser.kt`
  — line-based `blocksToEditableText`/`parseEditableText`). The shipping import flow (feature 2)
  uses the registry-based parser under `core/util/studyload/` instead; the old parser is
  now only reference for the carry-forward logic.
- **`HomeScreen` / `HomeViewModel`** — superseded by the Smart Calendar screen; the Home
  tab shows `SmartCalendarScreen`.
- **Dead routes** — `create_landing` (kept as the import flow's `popBackStack` target),
  `study_load_upload`, and `home` are declared but unused.