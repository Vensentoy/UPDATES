package com.revyu.app.core.util.studyload

/**
 * Parses an extracted study-load document into structured subjects/sessions.
 *
 * BaiNaRevyu is designed so additional schools can be supported later: implement this
 * interface for a new institution's document format and register it in
 * [StudyLoadParserRegistry]. The parser is deliberately deterministic and offline.
 */
interface StudyLoadParser {
    /** Stable id used by the registry, e.g. "llcc". */
    val id: String

    /** Display name shown in u/import flows, e.g. "LLCC Study Load". */
    val displayName: String

    /** Whether this parser is confident the extracted text is a document format it understands. */
    fun recognizes(rawText: String): Boolean

    /**
     * Parses [rawText] into a structured study load.
     *
     * Returns null if the text was not recognized. A [StudyLoadParseReport] is returned
     * even for partial parses (the UI always follows with an editable review step, so a
     * confident-but-incomplete parse is never a dead end).
     */
    fun parse(rawText: String): StudyLoadParseReport?
}

/**
 * Tries every registered parser and returns the first that recognizes the document.
 * Order matters: more strict/institution-specific parsers should come first.
 */
object StudyLoadParserRegistry {
    @Volatile
    var parsers: List<StudyLoadParser> = listOf(LLCCStudyLoadParser)

    fun find(rawText: String): StudyLoadParser? = parsers.firstOrNull { it.recognizes(rawText) }

    fun parse(rawText: String): StudyLoadParseReport? {
        val parser = find(rawText) ?: return null
        return parser.parse(rawText)
    }
}