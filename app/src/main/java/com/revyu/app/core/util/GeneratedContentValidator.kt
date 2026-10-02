package com.revyu.app.core.util

import com.revyu.app.data.local.entities.QuestionType
import com.revyu.app.data.remote.GeneratedFlashcard
import com.revyu.app.data.remote.GeneratedQuestion

object GeneratedContentValidator {
    fun validFlashcards(flashcards: List<GeneratedFlashcard>): List<GeneratedFlashcard> =
        flashcards.filter { card ->
            card.front.isNotBlank() &&
                card.back.isNotBlank() &&
                !card.front.trim().equals(card.back.trim(), ignoreCase = true)
        }

    fun validQuestions(questions: List<GeneratedQuestion>): List<GeneratedQuestion> =
        questions.filter(::isValidQuestion)

    private fun isValidQuestion(question: GeneratedQuestion): Boolean {
        if (question.prompt.trim().length < MIN_PROMPT_LENGTH) return false
        if (question.options.any(String::isBlank)) return false

        val options = question.options.map(String::trim)
        if (options.map(String::lowercase).distinct().size != options.size) return false

        val type = runCatching { QuestionType.valueOf(question.type.uppercase()) }.getOrNull() ?: return false
        return when (type) {
            QuestionType.SINGLE_CHOICE ->
                options.size == 4 && question.correctAnswers.size == 1 &&
                    options.contains(question.correctAnswers.single().trim())

            QuestionType.MULTIPLE_CHOICE ->
                options.size in 4..6 && question.correctAnswers.size >= 2 &&
                    question.correctAnswers.all { correct -> options.contains(correct.trim()) }

            QuestionType.TRUE_FALSE ->
                question.options.isEmpty() && question.correctAnswers.size == 1 &&
                    question.correctAnswers.single() in setOf("True", "False")

            QuestionType.IDENTIFICATION, QuestionType.SHORT_ANSWER ->
                question.options.isEmpty() && question.correctAnswers.any(String::isNotBlank)
        }
    }

    private const val MIN_PROMPT_LENGTH = 5
}