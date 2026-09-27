package com.revyu.app.data.local.entities

/** One Study Set's exam lifecycle, tracked so Smart Calendar knows what's outstanding. */
enum class ExamStatus {
    NOT_TAKEN,
    IN_PROGRESS,
    COMPLETED
}

/** Tracks the AI generation lifecycle for a Study Set, independent of exam status. */
enum class GenerationStatus {
    PENDING,
    GENERATING,
    READY,
    FAILED
}

enum class QuestionType {
    SINGLE_CHOICE,
    MULTIPLE_CHOICE,
    TRUE_FALSE,
    IDENTIFICATION,
    SHORT_ANSWER
}

enum class ReviewerLayoutColumns(val columns: Int) {
    ONE(1), TWO(2), THREE(3), FOUR(4)
}

enum class ReviewerMargins {
    COMPACT, NORMAL, SPACIOUS
}

enum class ReviewerFontStyle(val displayName: String) {
    SERIF("Serif"),
    SANS("Sans-serif"),
    MONO("Monospace")
}

enum class SourceFileType {
    PDF, TXT, DOCX, MD, PPTX
}

/** Distinguishes a normal single-upload Study Set from a Semester Vault synthesis. */
enum class StudySetKind {
    REGULAR,
    MIDTERM_VAULT,
    FINAL_VAULT
}
