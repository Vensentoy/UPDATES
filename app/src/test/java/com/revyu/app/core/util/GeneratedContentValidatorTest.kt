package com.revyu.app.core.util

import com.revyu.app.data.remote.GeneratedFlashcard
import com.revyu.app.data.remote.GeneratedQuestion
import org.junit.Assert.assertEquals
import org.junit.Test

class GeneratedContentValidatorTest {
    @Test
    fun `drops blank and identical flashcards`() {
        val cards = listOf(
            GeneratedFlashcard(front = "What is ATP?", back = "Energy currency"),
            GeneratedFlashcard(front = "   ", back = "Answer"),
            GeneratedFlashcard(front = "Same", back = " same ")
        )

        assertEquals(1, GeneratedContentValidator.validFlashcards(cards).size)
    }

    @Test
    fun `enforces question options and correct answer constraints`() {
        val questions = listOf(
            GeneratedQuestion("SINGLE_CHOICE", "Which is correct?", listOf("A", "B", "C", "D"), listOf("B")),
            GeneratedQuestion("SINGLE_CHOICE", "Which is invalid?", listOf("A", "A", "C", "D"), listOf("A")),
            GeneratedQuestion("MULTIPLE_CHOICE", "Choose all that apply", listOf("A", "B", "C", "D"), listOf("A")),
            GeneratedQuestion("TRUE_FALSE", "Is this accurate?", emptyList(), listOf("True"))
        )

        assertEquals(listOf(questions[0], questions[3]), GeneratedContentValidator.validQuestions(questions))
    }
}