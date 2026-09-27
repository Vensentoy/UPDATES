package com.revyu.app.core.util.schoolcalendar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SchoolCalendarOcrAssemblerTest {

    private fun el(
        text: String,
        x: Float,
        y: Float,
        width: Float = 10f,
        height: Float = 6f
    ) = OcrElement(text, x, y, width, height)

    @Test
    fun `same baseline becomes one line left to right`() {
        val lines = OcrLineAssembler.assemble(
            listOf(el("Sports", x = 58f, y = 40f), el("Intramurals", x = 40f, y = 40f))
        )
        assertEquals(1, lines.size)
        assertEquals("Intramurals Sports", lines[0].text)
    }

    @Test
    fun `wide horizontal gap is a scanner column split on pipe`() {
        val lines = OcrLineAssembler.assemble(
            listOf(
                el("AUG 24", x = 60f, y = 80f),
                el("Intramurals", x = 900f, y = 80f),
                el("AUG 26", x = 60f, y = 110f),
                el("Freshman Walk", x = 900f, y = 110f)
            )
        )
        assertEquals(2, lines.size)
        assertEquals("AUG 24 | Intramurals", lines[0].text)
        assertEquals("AUG 26 | Freshman Walk", lines[1].text)
        assertTrue(lines[0].y <= lines[1].y)
    }

    @Test
    fun `rows separated vertically do not merge`() {
        val lines = OcrLineAssembler.assemble(
            listOf(el("SEPT. 2026", x = 20f, y = 10f), el("Aug 30", x = 20f, y = 120f))
        )
        assertEquals(2, lines.size)
    }

    @Test
    fun `blank elements dropped`() {
        val lines = OcrLineAssembler.assemble(
            listOf(el("   ", x = 10f, y = 0f), el("Aug 15", x = 10f, y = 0f))
        )
        assertEquals(1, lines.size)
        assertEquals("Aug 15", lines[0].text)
    }

    @Test
    fun `unsorted input is ordered by band then x`() {
        val lines = OcrLineAssembler.assemble(
            listOf(el("Exam Week", x = 52f, y = 205f), el("AUG 14", x = 30f, y = 200f))
        )
        assertEquals(1, lines.size)
        assertEquals("AUG 14 Exam Week", lines[0].text)
    }
}