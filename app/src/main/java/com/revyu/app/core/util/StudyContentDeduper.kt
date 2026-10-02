package com.revyu.app.core.util

object StudyContentDeduper {
    private val punctuation = Regex("[^\\p{L}\\p{N}]+")
    private val whitespace = Regex("\\s+")
    private val stopwords = setOf(
        "a", "an", "and", "are", "as", "at", "be", "been", "being", "but", "by",
        "did", "do", "does", "for", "from", "how", "in", "into", "is", "it", "of",
        "on", "or", "that", "the", "their", "these", "this", "those", "to", "was",
        "were", "what", "when", "where", "which", "who", "why", "with"
    )

    fun normalize(text: String): Set<String> = text.lowercase()
        .replace(punctuation, " ")
        .split(whitespace)
        .filter { it.isNotBlank() && it !in stopwords }
        .toSet()

    fun similarity(first: String, second: String): Double {
        val firstTokens = normalize(first)
        val secondTokens = normalize(second)
        if (firstTokens.isEmpty() || secondTokens.isEmpty()) return 0.0

        val intersectionSize = firstTokens.intersect(secondTokens).size
        val unionSize = firstTokens.union(secondTokens).size
        val jaccard = intersectionSize.toDouble() / unionSize
        val containment = intersectionSize.toDouble() / minOf(firstTokens.size, secondTokens.size)
        return maxOf(jaccard, containment)
    }

    fun <T> filterFlashcards(
        new: List<T>,
        existing: List<T>,
        front: (T) -> String
    ): List<T> = filterIncrementally(new, existing, front) { candidate, prior ->
        similarity(candidate, prior) >= FLASHCARD_THRESHOLD
    }

    fun <T> filterQuestions(
        new: List<T>,
        existing: List<T>,
        prompt: (T) -> String,
        correctAnswers: (T) -> Collection<String>
    ): List<T> {
        val accepted = mutableListOf<T>()
        val seen = existing.toMutableList()
        for (candidate in new) {
            val candidatePrompt = prompt(candidate)
            val candidateAnswers = answerKey(correctAnswers(candidate))
            val duplicate = seen.any { prior ->
                val promptSimilarity = similarity(candidatePrompt, prompt(prior))
                promptSimilarity >= QUESTION_THRESHOLD ||
                    (promptSimilarity >= SAME_ANSWER_THRESHOLD && candidateAnswers == answerKey(correctAnswers(prior)))
            }
            if (!duplicate) {
                accepted += candidate
                seen += candidate
            }
        }
        return accepted
    }

    private fun <T> filterIncrementally(
        new: List<T>,
        existing: List<T>,
        text: (T) -> String,
        isDuplicate: (String, String) -> Boolean
    ): List<T> {
        val accepted = mutableListOf<T>()
        val seen = existing.toMutableList()
        for (candidate in new) {
            if (seen.none { isDuplicate(text(candidate), text(it)) }) {
                accepted += candidate
                seen += candidate
            }
        }
        return accepted
    }

    private fun answerKey(answers: Collection<String>): Set<String> =
        answers.map { normalize(it).sorted().joinToString(" ") }.filter(String::isNotBlank).toSet()

    private const val FLASHCARD_THRESHOLD = 0.80
    private const val QUESTION_THRESHOLD = 0.70
    private const val SAME_ANSWER_THRESHOLD = 0.55
}
