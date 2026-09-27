# Revyu

An AI-powered study companion for LLCC students. Upload your Study Load and your class
materials, and Revyu turns them into a Reviewer (PDF), Flashcards, and Practice
Questions — with Exam Mode, Results, and a Smart Calendar that reminds you when an
upcoming subject still has an unfinished Exam Mode.

Built native (Kotlin + Jetpack Compose) in Android Studio, using **NVIDIA Nemotron 3
Ultra (free)** via OpenRouter.

---

## Opening the project

1. **Android Studio** — use a recent stable release (Koala/2024.1 or newer). File →
   Open → select the `Revyu` folder.
2. Let Gradle sync. First sync will download dependencies — give it a few minutes.
3. The Gradle wrapper is included, pinned to **Gradle 8.10.2**, so `./gradlew` works
   from a fresh clone (`gradlew.bat` on Windows) — no extra setup needed.
4. Run on a device or emulator with **API 26+** (Android 8.0 or newer).

## Before you can generate anything

You'll need a free OpenRouter API key (starts with `sk-or-`):
[openrouter.ai/keys](https://openrouter.ai/keys). The app asks for it on first launch
and stores it encrypted, on-device, via `EncryptedSharedPreferences`. You won't need to
re-enter it.

## First run flow

1. Enter your OpenRouter API key.
2. Upload your Study Load (PDF/DOCX/TXT/MD). Revyu parses it into subjects + a weekly
   schedule — you'll always see an **editable table** before anything is saved, since
   the parser is heuristic and school Study Load formats vary a lot. You can also skip
   the file entirely and add subjects manually.
3. From Home, tap a subject → the **+** button → upload material → customize the
   Reviewer (layout/font/margins) → customize practice questions (count/types) →
   Generate.
4. Once generated: Reviewer (PDF preview + open/share), Flashcards (tap to flip),
   Questions (tap to reveal answer), and a **Start Exam Mode** button.
5. Smart Calendar surfaces on Home automatically, and also runs a background check once
   a day via WorkManager, notifying you if a subject meets soon and its Exam Mode isn't
   done yet.

## What's in v1 vs. later

**Built:** Study Load intake, subject folders, material upload + text extraction,
Reviewer/Flashcard/Question generation, Exam Mode, Results, Study Set history, Smart
Calendar (on-open + daily background check + notification).

**Deliberately deferred** (per product scope decision): Semester Vault, Temporal Vault,
Taglish language support, dark mode.

## Architecture, in short

- **UI**: Jetpack Compose + Material3, MVVM. Manual dependency injection
  (`di/AppContainer.kt`) instead of Hilt — no annotation processor to keep in lockstep
  with Kotlin/AGP versions, which is the most common cause of a first Android Studio
  sync failing.
- **Data**: Room (local-only, no backend). Uploaded files are reduced to extracted plain
  text at upload time and kept in the database — original file bytes are discarded —
  so a Study Set can be regenerated later without re-uploading.
- **Networking**: Retrofit + OkHttp + kotlinx.serialization, targeting
  `nvidia/nemotron-3-ultra-550b-a55b:free` on OpenRouter. Includes a retry-with-backoff
  interceptor for 429s (the free tier does rate-limit under load) and a friendly
  "still busy, try again" error state in the UI rather than a raw failure.
- **File parsing**: PDF via PdfBox-Android; DOCX via the platform's built-in
  `ZipInputStream` + `XmlPullParser` (no extra dependency); TXT/MD read directly.
- **Reviewer PDF**: rendered on-device with Android's built-in `PdfDocument` — no PDF
  library needed for output, only for reading uploads.
- **Design system**: `core/theme/` — Paper/Ink Navy/Highlighter Yellow/Folder
  Coral/Pass Green palette, serif/sans/mono type trio, and the "Margin Rule" signature
  element (`ui/components/MarginRuleCard.kt`) used across subject cards, generated
  content, and Smart Calendar reminders.

## If the first build doesn't go clean

This was written carefully against known-compatible versions (AGP 8.5.2 / Kotlin 1.9.24
/ Compose BOM 2024.06.00 / Gradle 8.10.2), but a fresh Android Studio project opened for
the first time can still hit friction. Common fixes, in order:

1. **File → Invalidate Caches / Restart** if sync hangs or reports phantom errors.
2. Confirm Android Studio's bundled JDK is 17 (Settings → Build Tools → Gradle → Gradle
   JDK).
3. If a specific dependency fails to resolve, it's almost always a transient Maven
   Central hiccup — re-sync.
4. If Room's annotation processing (KSP) errors on a totally fresh checkout, do one full
   **Build → Clean Project** then **Build → Rebuild Project**.

## Known trade-offs, going in with eyes open

- The Study Load parser is heuristic (regex-based day/time detection). It's always
  paired with an editable review screen so a bad parse never blocks you — but don't
  expect perfect first-pass accuracy on an unusual layout.
- The Reviewer PDF renderer is a from-scratch multi-column text layout engine built on
  `PdfDocument`/`Canvas` — functional and on-brand, but it's not a typesetting engine;
  very long or unusually structured reviewer content may paginate a little roughly.
- Nemotron's free tier can be slow or rate-limited under load — that's expected, not a
  bug in the app; the retry/backoff and error states are built around that reality.
