# REVYU — Study Set Improvement Spec

Drop this file into the repo as `docs/STUDY_SET_SPEC.md`. Every Copilot prompt in
`COPILOT_PROMPTS.md` tells Copilot to read it first. Package root: `com.revyu.app`
(`app/src/main/java/com/revyu/app/`).

## 0. What is wrong today (found in the code)

| # | Problem | Where |
|---|---------|-------|
| 1 | Only the first 40,000 characters of a file are sent to the AI. The rest of a long file is silently ignored. | `PromptBuilder.MAX_SOURCE_CHARS` |
| 2 | One AI call must produce reviewer + flashcards + questions inside 12,000 tokens, so every part stays shallow and the reply can be cut off. | `GenerationRepository`, `ChatCompletionRequest.maxTokens` |
| 3 | Reviewer is plain text, 600–1000 words, "no markdown". The PDF has no bullets, bold terms, callouts, tables, page numbers, or running header. Headings are guessed (`< 70 chars, no period`). | `PromptBuilder`, `PdfReviewerRenderer` |
| 4 | Flashcards are just `front/back`, always in the same order, no progress tracking, count is not user-controlled (minimum 8). | `FlashcardEntity`, `FlashcardsTab` |
| 5 | Questions have no explanation, topic, or difficulty. Nothing checks that the correct answer is actually inside `options`. | `QuestionEntity`, `GeneratedQuestion` |
| 6 | Practice tab is read-only (tap to reveal). Exam Mode never shuffles, has no timer, no question navigator, no flagging. | `QuestionsTab`, `ExamModeScreen` |
| 7 | IDENTIFICATION / SHORT_ANSWER are graded by exact match after trim+lowercase, so "Mitochondria" vs "the mitochondria" is wrong. | `StudySetRepository.submitExamAttempt` |
| 8 | Uploading the same file again opens the old Study Set instead of making a new one. Same prompt + temperature 0.4 would also give near-identical output. | `CreateStudySetViewModel.uploadMaterial`, `getReadyByMaterialId` |
| 9 | `RevyuDatabase` is v3 with `fallbackToDestructiveMigration()`. Adding columns without a migration **wipes the user's data**. | `RevyuDatabase.kt` |

## 1. Target generation pipeline

Replace the single call with a short sequential pipeline. Each step saves its result
immediately, so a failure at step N can be retried without redoing steps before it.

```
Source text
  └─ SourceChunker  (split on Markdown headings / slide separators, chunks <= 12k chars)
       ├─ Step A  OUTLINE      (skipped if source <= 12k chars; otherwise one call per ~24k chars, merged)
       ├─ Step B  REVIEWER     (structured blocks JSON, see 2)
       ├─ Step C  FLASHCARDS   (see 3)
       └─ Step D  QUESTIONS    (see 4)
Post-processing: Validator -> Deduper -> (top-up call if < 70% of target survive)
```

- Calls run one at a time with a small delay. Keep the existing `RetryInterceptor` for 429s.
- `GeneratingScreen` shows the current step ("Reading your file", "Writing reviewer",
  "Making flashcards", "Writing questions").
- A step that fails after its retry marks the Study Set `FAILED` with a message saying which
  step failed; steps already done are kept.
- Semester Vault (`vaultExamLabel != null`) must keep working. It may use a single combined
  source, but must go through the same DTOs, validator and renderer.

### Outline JSON (Step A)
```json
{ "topics": [ { "id": "t1", "title": "string", "importance": 1,
                "keyPoints": ["string"], "terms": ["string"] } ] }
```
`importance`: 1 = supporting, 2 = important, 3 = core. `id` is reused as `topicId` everywhere below.

## 2. Reviewer PDF format

### 2.1 Structured reviewer JSON (Step B)
```json
{
  "title": "string",
  "overview": "3-5 sentence summary",
  "sections": [ {
    "topicId": "t1",
    "heading": "string",
    "blocks": [
      { "type": "PARAGRAPH", "text": "string" },
      { "type": "BULLETS",   "items": ["string"] },
      { "type": "STEPS",     "items": ["string"] },
      { "type": "DEFINITION","term": "string", "text": "string" },
      { "type": "CALLOUT",   "kind": "REMEMBER|TIP|WARNING|EXAMPLE", "text": "string" },
      { "type": "TABLE",     "headers": ["string"], "rows": [["string"]] },
      { "type": "FORMULA",   "text": "string" }
    ]
  } ],
  "glossary":   [ { "term": "string", "meaning": "string" } ],
  "cheatSheet": ["one-line fact", "... 8-12 items"]
}
```
Content rules for the prompt:
- Every section opens with a one-sentence PARAGRAPH, then bullets/definitions, and ends with one
  CALLOUT `REMEMBER` (the single takeaway). Use TABLE only for real comparisons, STEPS only for real sequences.
- Bold terms are expressed through DEFINITION blocks, never Markdown symbols.
- Length scales with the new **Detail level** setting (word budget of the whole reviewer):
  CONCISE 500–900, STANDARD 900–1800 (default), DETAILED 1800–3500. Hard cap 3500.
- Only facts supported by the source. No invented examples presented as source content.

Store the JSON in `StudySetEntity.reviewerBlocksJson`. Keep `reviewerBodyText` as a flattened
plain-text version (Semester Vault aggregation and widgets still read it). If the JSON cannot be
parsed, fall back to the old plain-text renderer so the user always gets a PDF.

### 2.2 Page layout (A4, 1240x1754 px, existing columns / font / size / margin settings still apply)
1. **Page 1 header** (keep the current accent bar, eyebrow, title) + a meta line
   (`source file · date · N sections`) + an **Overview box** (Highlighter Yellow tint) +
   a **Contents** list when there are 4+ sections.
2. **Running header on pages 2+**: `SUBJECT · Title` in small coral text with a hairline rule.
3. **Footer on every page**: `Page X of Y` centered, `Revyu` right-aligned. Total pages are only
   known after layout, so layout first into an in-memory page model, then draw it.
4. **Section heading**: coral number + bold navy title + thin rule. *Keep-with-next*: a heading never
   sits alone at the bottom of a column (needs at least 2 lines of the next block under it).
5. **Bullets / steps**: hanging indent (`•` or `1.`), wrapped lines align with the text, not the marker.
6. **DEFINITION**: bold term followed by the meaning, in one paragraph (use `StaticLayout` + bold spans).
7. **CALLOUT**: rounded box with a left color bar and a small label.
   REMEMBER = yellow tint, WARNING = coral tint, TIP = green tint, EXAMPLE = light gray.
   Never split a callout between columns unless it is taller than a column.
8. **TABLE**: drawn as a grid with a shaded header row when column width >= 420 px and <= 3 table
   columns. Otherwise convert each row to `Header: value` lines. Never overflow the column.
9. **FORMULA**: monospace on a light gray band.
10. **Last pages**: boxed **Cheat Sheet** (the 8–12 one-liners), then a compact **Glossary**.
11. Do not break a TABLE or DEFINITION across pages when it fits on one column.

PDF colors are fixed print colors (Ink Navy, Folder Coral, Highlighter Yellow, Pass Green) because a
PDF is not themed; this is the only place hardcoded colors are acceptable.

Bonus (cheap because JSON is stored): a "Re-layout PDF" action that re-renders the saved blocks with
new columns/font/margins without calling the AI.

## 3. Flashcards

### 3.1 Generated schema (Step C)
```json
{ "flashcards": [ {
    "front": "string", "back": "string", "hint": "string or empty",
    "topicId": "t1", "difficulty": 1,
    "kind": "TERM|CONCEPT|COMPARE|PROCESS|FORMULA|CLOZE"
} ] }
```
Quality rules for the prompt:
- One idea per card. Back is at most ~25 words (an optional short example may follow).
- Front is a real question or a clear term, never "Explain X in detail".
- Mix kinds: definitions, "why/how" concepts, compare/contrast, process steps, formulas, cloze
  (`The ___ stores genetic information`).
- Cover every topic; cards per topic scale with `importance`.
- No duplicates, no card that only restates another card's answer.
- Count follows the new **Flashcards** slider (10–60, default 20) instead of the current hardcoded minimum of 8.

### 3.2 New fields on `FlashcardEntity`
`hint: String?`, `topicId: String?`, `topicTitle: String?`, `difficulty: Int = 2`, `kind: String = "TERM"`,
`mastery: Int = 0` (0 = new/again, 1–3 = Leitner boxes, 3 = mastered), `reviewCount: Int = 0`,
`lastReviewedAt: Instant? = null`, `starred: Boolean = false`.

### 3.3 Study experience (`FlashcardsTab`)
- Order is shuffled by default (toggle to restore original order).
- After flipping: **Again** / **Got it** buttons (>= 48 dp). Swiping is optional.
  Got it = `mastery + 1` (max 3). Again = `mastery = 0` and the card is re-queued 3–5 cards later in the same session.
- Filters (FlowRow chips): All · Not mastered · Starred · by topic.
- Direction toggle: Term -> Definition / Definition -> Term.
- Hint button (shows `hint` if present). Star button.
- End-of-session summary: mastered / still learning, with a "Review missed" button.
- Progress bar + mastery counts at the top.

## 4. Questions, Practice and Exam Mode

### 4.1 Generated schema (Step D)
```json
{ "questions": [ {
    "type": "SINGLE_CHOICE|MULTIPLE_CHOICE|TRUE_FALSE|IDENTIFICATION|SHORT_ANSWER",
    "prompt": "string", "options": ["string"], "correctAnswers": ["string"],
    "acceptedAnswers": ["alternate wording"], "explanation": "string",
    "topicId": "t1", "difficulty": 1, "level": "RECALL|UNDERSTAND|APPLY|ANALYZE"
} ] }
```
Quality rules for the prompt:
- Difficulty mix by the new **Difficulty** chip: Easier 50/35/15, Balanced 30/50/20 (default), Harder 15/45/40 (easy/medium/hard %).
- At least 30% APPLY/ANALYZE (scenario or "which would happen if…" questions) in Balanced.
- Distractors are plausible, similar in length and grammar to the right answer, drawn from real
  misconceptions or neighboring concepts in the source. Never "all of the above" / "none of the above".
  No absolute giveaway words (always/never) unless the source says so.
- `explanation`: 1–2 sentences saying why the answer is right (and the most tempting wrong option if choice-based).
- IDENTIFICATION/SHORT_ANSWER: `correctAnswers` has the canonical answer, `acceptedAnswers` lists
  2–4 acceptable variants/synonyms/abbreviations.
- Cover every topic proportionally to `importance`. Each question tests a different fact.
- Rotate **question angles** (see 5.3) so one generation is not all definition-recall.

### 4.2 New fields
`QuestionEntity`: `acceptedAnswers: List<String> = emptyList()`, `explanation: String? = null`,
`topicId: String? = null`, `topicTitle: String? = null`, `difficulty: Int = 2`, `level: String = "RECALL"`,
`timesAsked: Int = 0`, `timesCorrect: Int = 0`, `lastAskedAt: Instant? = null`, `lastCorrect: Boolean? = null`.

`ExamAttemptEntity`: `questionOrderJson: String = "[]"` (the ids asked, in order),
`optionOrderJson: String = "{}"` (map questionId -> shuffled options), `timeLimitSeconds: Int? = null`,
`elapsedSeconds: Int = 0`, `flaggedJson: String = "[]"`, `overridesJson: String = "[]"`
(question ids the user manually marked correct), `basedOnAttemptId: String? = null`.

### 4.3 Validation (runs on every generated question, before saving)
Drop the question if any rule fails:
- blank/very short prompt; duplicate options; `options` contains a blank
- SINGLE_CHOICE: exactly 4 distinct options and exactly 1 correct answer that is one of them
- MULTIPLE_CHOICE: 4–6 options, >= 2 correct answers, all of them in `options`
- TRUE_FALSE: correct answer is exactly `True` or `False`; options empty
- IDENTIFICATION/SHORT_ANSWER: non-empty correct answer; options empty
Same idea for flashcards: drop blank sides and cards where front equals back.

### 4.4 Practice tab (`QuestionsTab`)
- Interactive: tap an option -> instant feedback (green/red from the theme, correct answer shown) +
  explanation. Multiple choice has a "Check" button. Text types: type answer, "Check", or "Reveal".
- Filters (FlowRow chips): All · by type · by topic · by difficulty · Not yet correct. Shuffle toggle.
- Practice shows feedback immediately; Exam does not.

### 4.5 Exam Mode
**Setup screen (new, before the exam):** number of questions (10 / 15 / 20 / All — capped by bank size),
Timed toggle (default 1 minute per question, off by default), Source filter: *Mixed (recommended)* ·
*Missed before* · *Not yet seen*.

**Drawing questions each attempt** (this is what makes every exam different):
1. Pool = all questions in the Study Set, filtered by the chosen source.
2. Allocate slots per topic in proportion to topic size (weighted by importance), at least 1 per topic when slots allow.
3. Inside a topic pick without replacement by weight:
   `w = 1 + 2·(lastCorrect == false) + 1.5·(timesAsked == 0) − 0.5·min(timesAsked, 3)`, using a seeded RNG (seed = attempt id).
4. Shuffle the final order. For SINGLE/MULTIPLE shuffle options; TRUE_FALSE stays `True, False`.
5. Save order + option order in the attempt so Results shows exactly what the user saw.
6. After submit, update `timesAsked / timesCorrect / lastCorrect / lastAskedAt` for each asked question.

**During the exam:** question palette (grid of numbers showing answered / flagged / current), Flag for review,
Previous/Next, progress bar, optional countdown that auto-submits at 0 and persists `elapsedSeconds`.
Answers autosave to the attempt so leaving the screen does not lose them; an unfinished attempt offers Resume.

**Grading (`StudySetRepository.submitExamAttempt`)**
- Choice / True-False: exact set equality (as today).
- IDENTIFICATION / SHORT_ANSWER: normalize both sides (lowercase, trim, strip punctuation, drop leading
  articles `a/an/the`, collapse spaces). Correct if equal to the canonical or any `acceptedAnswers`, or if
  edit distance <= 1 for answers of 5–8 chars / <= 2 for longer ones. No network call.
- Results screen lets the user tap **Mark as correct** on a text answer that was graded wrong; this is saved in
  `overridesJson` and the score is recomputed.

**Results screen:** score + label, breakdown by topic and by difficulty, per-question review
(your answer vs correct answer + explanation), **Retake missed only**, and a small score-by-attempt trend.

## 5. "Different every time" (same file uploaded twice)

Three layers, from guaranteed to best-effort.

### 5.1 Layer A — per attempt (guaranteed, no AI)
Exam draw + shuffle (4.5) and flashcard shuffle (3.3). Two attempts on the same Study Set differ in question
choice, order, and option order.

### 5.2 Layer B — per generation (same material, new Study Set)
1. **Content identity.** Add `StudyMaterialEntity.contentHash` (SHA-256 of whitespace-normalized extracted text).
   Sibling sets = READY Study Sets in the same subject whose material has the same hash. This also fixes the
   current behavior where a same-named file with changed content reuses stale text.
2. **Replace auto-open with a choice.** When siblings exist show a dialog: *"You already have N Study Sets from
   this file."* — **Open latest** · **Create a new variation** (primary). Variation title gets ` · v{n}`.
3. `StudySetEntity.variationIndex` = number of sibling sets (0 for the first).
4. **Avoid-list.** Put the siblings' flashcard fronts and question prompts into Steps C and D:
   max 40 items each, each cut to 90 chars, newest first. Instruction: *"Do NOT repeat or lightly reword any
   of these. Test different facts or the same facts from a different angle."*
5. **Coverage rotation.** Count how many earlier items point to each `topicId` (or topic title). Tell the model to
   prioritize the least-covered topics first. Because items store `topicTitle`, this survives the outline being
   regenerated.
6. **Angle rotation.** Fixed list: `definition recall`, `real-world scenario`, `compare/contrast`, `cause and effect`,
   `order of steps / process`, `which statement is FALSE`, `worked example / calculation`, `common misconception`.
   Variation *k* starts at angle `(k * 3) mod 8` and takes the next 4 angles for Step D (and 3 for Step C).
7. **Temperature**: 0.4 for variation 0, 0.75 for variation >= 1 (`top_p` stays 0.95).
8. **Reviewer reuse (default):** a variation does not call Step A/B again. It copies the sibling's outline,
   reviewer JSON and re-renders the PDF, so only Steps C and D run (2 calls instead of 4–5, which is
   friendlier to the free-tier rate limit). A "Also rewrite the reviewer" switch in the wizard turns this off.

### 5.3 Layer C — post-generation de-duplication (guaranteed)
`StudyContentDeduper`:
- `normalize()`: lowercase, strip punctuation, remove a small English stopword list, tokenize.
- Similarity = max(Jaccard of token sets, containment of the shorter in the longer).
- Flashcards: drop if similarity with any sibling or same-set card >= 0.80 on `front`.
- Questions: drop if prompt similarity >= 0.70, **or** >= 0.55 when the normalized correct answer is also equal.
- If fewer than 70% of the target survive, run **one** top-up call with the extended avoid-list and merge the result.
- Accept whatever survives after the top-up; never loop more than once.

Success target (manual QA): generating twice from the same file gives < 25% overlap by this similarity measure.

## 6. Settings additions (wizard)
- `CustomizeReviewerScreen`: **Detail level** chips (Concise / Standard / Detailed).
- `CustomizePracticeScreen`: **Flashcards** slider (10–60, default 20); the existing slider is relabeled
  **Question bank size** (10–60, default 30); **Difficulty** chips (Easier / Balanced / Harder).
  Keep the existing question-type chips.
- New fields on `StudySetEntity`: `reviewerDetail`, `flashcardCount`, `difficultyMix`, `variationIndex`,
  `reviewerBlocksJson`, `outlineJson`.
- All new UI must follow `.github/copilot-instructions.md` (theme colors only, FlowRow for chip groups,
  `maxLines = 1` on labels, 48 dp targets, light/dark + 4 accents).

## 7. Cost / rate-limit note
Today: 1 call per Study Set. After: 3–5 calls for a first set (outline skipped for short files), 2 for a variation.
The free Nemotron tier rate-limits, so calls are sequential, retried once with backoff, and each step's output is saved
so retry only redoes the failed step.

## 8. Database
`RevyuDatabase` goes from version 3 to 4 with an explicit `MIGRATION_3_4` that `ALTER TABLE ... ADD COLUMN`s every
new column with a safe default. Add it to `.addMigrations(...)`. Do not rely on `fallbackToDestructiveMigration()`.
Room `List<String>` / `Instant` columns already have converters in `Converters.kt`; reuse them.

## 9. Done means
- `./gradlew :app:compileDebugKotlin` and `./gradlew :app:testDebugUnitTest` pass.
- Existing widgets (`widget/interactive/*`) and Semester Vault generation still compile and work.
- Upgrading from the v3 database keeps existing Study Sets, flashcards, questions and attempts.
