package com.revyu.app.data.prompt

import com.revyu.app.data.local.entities.QuestionType
import com.revyu.app.data.remote.ChatMessage

object PromptBuilder {

    /**
     * Source text is truncated defensively — free-tier context and token budgets are
     * finite, and a full textbook chapter dumped in raw is a common cause of 429s /
     * truncated output. ~40k characters is a generous margin for a single reviewer's
     * worth of material while leaving room for the response.
     */
    private const val MAX_SOURCE_CHARS = 40_000

    /** Semester Vault legitimately aggregates several prior Study Sets, so it gets a larger budget. */
    private const val MAX_VAULT_SOURCE_CHARS = 60_000

    fun buildGenerationMessages(
        subjectName: String,
        sourceText: String,
        language: String,
        maxQuestions: Int,
        questionTypes: List<QuestionType>,
        /** Non-null for a Semester Vault synthesis, e.g. "Midterm Exam" or "Final Exam". Null for a regular Study Set. */
        vaultExamLabel: String? = null
    ): List<ChatMessage> {
        val charBudget = if (vaultExamLabel != null) MAX_VAULT_SOURCE_CHARS else MAX_SOURCE_CHARS
        val trimmedSource = if (sourceText.length > charBudget) sourceText.take(charBudget) else sourceText

        val typesList = questionTypes.joinToString(", ") { it.name }
        val minFlashcards = if (vaultExamLabel != null) 15 else 8

        val sourceDescription = if (vaultExamLabel != null) {
            "accumulated reviewer, flashcard, and question content from several of the student's prior Study Sets for this subject, covering everything to be tested on the $vaultExamLabel"
        } else {
            "the source material"
        }

        val system = """
            You are the content engine inside Revyu, a study app for college students.
            Given ${if (vaultExamLabel != null) "accumulated study content" else "raw study material"}, you produce ONE JSON object and nothing else —
            no markdown code fences, no commentary before or after it.

            The JSON object must have exactly this shape:
            {
              "reviewer": string,
              "flashcards": [ { "front": string, "back": string } ],
              "questions": [
                {
                  "type": one of [$typesList],
                  "prompt": string,
                  "options": [string],
                  "correctAnswers": [string]
                }
              ]
            }

            Rules:
            - The source material is Markdown converted from the student's file
              (headings, lists, tables, slide or sheet separators). Read the
              structure, but do not copy Markdown syntax into your output.
            - "reviewer" is a well-organized study guide covering $sourceDescription,
              written in $language, using plain text with clear section
              headings (a heading on its own line, then its content) and short
              paragraphs or bullet points. No markdown symbols like # or ** — headings
              are conveyed by being short lines followed by a blank line.
            ${if (vaultExamLabel != null) """
            - This is a comprehensive $vaultExamLabel review: consolidate overlapping
              topics from the different source Study Sets into one coherent guide
              instead of just concatenating them, and make sure every distinct topic
              across all the source content is represented.
            """.trimIndent() else ""}
            - Generate at least $minFlashcards flashcards covering the most important
              concepts, definitions, and facts, front = term/question, back = answer.
            - Generate up to $maxQuestions questions total, only using question types
              from [$typesList].
            - For type SINGLE_CHOICE: "options" has exactly 4 plausible choices,
              "correctAnswers" has exactly 1 of those strings.
            - For type MULTIPLE_CHOICE: "options" has 4-6 choices, "correctAnswers" has
              2 or more of those strings.
            - For type TRUE_FALSE: "options" is empty, "correctAnswers" is exactly
              ["True"] or ["False"].
            - For type IDENTIFICATION or SHORT_ANSWER: "options" is empty,
              "correctAnswers" has exactly 1 concise expected answer.
            - Base everything strictly on the provided source material. Do not invent
              facts not supported by it.
            - Keep "reviewer" focused (roughly 600-1000 words) so the whole JSON
              object fits in the response without being cut off.
            - Output must be valid JSON — escape quotes and newlines properly.
              Your reply must start with { and end with }.
        """.trimIndent()

        val user = """
            Subject: $subjectName

            ${if (vaultExamLabel != null) "Accumulated source content (from multiple prior Study Sets):" else "Source material (Markdown):"}
            ---
            $trimmedSource
            ---

            Generate the ${if (vaultExamLabel != null) "$vaultExamLabel Study Set" else "Study Set"} JSON now.
        """.trimIndent()

        return listOf(
            ChatMessage(role = "system", content = system),
            ChatMessage(role = "user", content = user)
        )
    }
}