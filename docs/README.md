# Revyu — Documentation

This folder documents the **Revyu** Android app: what it does, every feature and how it
works, and the architecture behind it.

Revyu is an AI-powered study companion for **LLCC (Lapu-Lapu City College)** students.
You upload your Study Load (class schedule) and your course materials, and Revyu turns
them into a **Reviewer (PDF)**, **Flashcards**, and **Practice Questions** — plus a
**Smart Calendar** that reminds you when an upcoming subject still has an unfinished
**Exam Mode**.

## Docs index

| File | What it covers |
|---|---|
| [FEATURES.md](FEATURES.md) | The full feature list — what each feature does for the user and how it works under the hood. |
| [ARCHITECTURE.md](ARCHITECTURE.md) | Developer internals: layers, data model, parsers, AI pipeline, calendar engine, widgets, DI. |

## Building & running

**Requirements**

- Android Studio (recent stable), Gradle JDK set to **JDK 17**.
- A device or emulator on **API 26+** (Android 8.0 or newer).

**From Android Studio**

Open the `Revyu` folder, let Gradle sync, then Run. The project uses the standard
`app-debug` configuration.

**From the command line**

```bash
export JAVA_HOME=/path/to/jdk17        # Windows: $env:JAVA_HOME=...
gradle :app:testDebugUnitTest          # run the unit tests (25 tests)
gradle :app:assembleDebug              # build the debug APK
```

The debug APK lands in `app/build/outputs/apk/debug/app-debug.apk`.

## Tech stack

| Piece | Choice |
|---|---|
| Language / UI | Kotlin, Jetpack Compose + Material 3 (single-activity) |
| DI | Hand-rolled service locator (`di/AppContainer.kt`) — deliberately no Hilt |
| Persistence | Room (SQLite), locally only; no backend |
| Preferences | DataStore (settings) + EncryptedSharedPreferences (API key) |
| Networking | Retrofit + OkHttp + kotlinx.serialization → OpenRouter |
| AI model | NVIDIA Nemotron 3 Ultra (free) via OpenRouter |
| File reading | PDFBox-Android (PDF), built-in Zip+XmlPullParser (DOCX), plain (TXT/MD) |
| PDF output | Android's built-in `PdfDocument` (no PDF library for rendering) |
| Background | WorkManager (daily Smart Calendar check + widget refresh) |
| Key versions | AGP 8.5.2 / Kotlin 1.9.24 / Compose BOM 2024.06.00 / Room 2.6.1 / Gradle 8.7 |

## Project layout (top level)

```
app/src/main/java/com/revyu/app/
├── MainActivity.kt          single activity + deep-link extras
├── RevyuApplication.kt      PDFBox init, DI container, notifications, workers
├── core/                    calendar engine, util (parsers, PDF renderer), theme, prefs
├── data/                    Room entities/DAOs, repositories, remote (OpenRouter), prompts
├── di/                      AppContainer (manual DI) + ComposeDI (LocalAppContainer)
├── ui/                      every screen (home, reviewer, create, import, vault, ...)
├── widget/                  native home-screen widget (AppWidgetProvider + renderer)
└── work/                    WorkManager workers (calendar check, widget refresh)
```

## Navigation map

Bottom bar shows five tab roots:

| Route | Tab |
|---|---|
| `smart_calendar` | Home (Smart Calendar) |
| `reviewer_tab` | Reviewer |
| `widgets` | Widgets (in-app panel) |
| `history` | History |
| `settings` | Settings |

Key non-tab routes: `api_key_setup` (first-run gate), `subject/{subjectId}` (subject
detail), `study_set/{studySetId}` (overview), `.../exam` and `.../results/{attemptId}`
(Exam Mode), `study_load_picker` → `study_load_review` (Study Load import), the Create
wizard graph `create/{subjectId}` (upload → reviewer → practice → generating), and the
Semester Vault graph (`vault_subjects` → `vault/{subjectId}` → `vault_configure` →
`vault_generating`). See `core/navigation/RevyuDestinations.kt`.