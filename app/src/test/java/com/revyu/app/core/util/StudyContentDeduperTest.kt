package com.revyu.app.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyContentDeduperTest {
    @Test
    fun `drops identical flashcard fronts`() {
        val existing = listOf("What is ATP?")

        val filtered = StudyContentDeduper.filterFlashcards(
            new = listOf("What is ATP?", "What is NADH?"),
            existing = existing,
            front = { it }
        )

        assertEquals(listOf("What is NADH?"), filtered)
    }

    @Test
    fun `drops lightly reworded flashcard fronts`() {
        assertTrue(StudyContentDeduper.similarity("What is the function of ATP?", "ATP's function, what is it?") >= 0.80)

        val filtered = StudyContentDeduper.filterFlashcards(
            new = listOf("ATP's function, what is it?"),
            existing = listOf("What is the function of ATP?"),
            front = { it }
        )

        assertTrue(filtered.isEmpty())
    }

    @Test
    fun `keeps different flashcard fronts`() {
        val filtered = StudyContentDeduper.filterFlashcards(
            new = listOf("How does DNA store genetic information?"),
            existing = listOf("What role does ATP have in cellular energy transfer?"),
            front = { it }
        )

        assertEquals(listOf("How does DNA store genetic information?"), filtered)
    }

    @Test
    fun `drops similar questions when normalized answers also match`() {
        val existing = Question("ATP role energy transfer cell", listOf("Adenosine triphosphate"))
        val reworded = Question("ATP function energy transfer cells", listOf("adenosine triphosphate"))

        assertEquals(0.60, StudyContentDeduper.similarity(existing.prompt, reworded.prompt), 0.001)
        val filtered = StudyContentDeduper.filterQuestions(
            new = listOf(reworded),
            existing = listOf(existing),
            prompt = Question::prompt,
            correctAnswers = Question::answers
        )

        assertTrue(filtered.isEmpty())
    }

    private data class Question(val prompt: String, val answers: List<String>)
}
