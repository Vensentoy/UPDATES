package com.revyu.app.core.util

import org.junit.Assert.assertTrue
import org.junit.Test

class SourceChunkerTest {
    @Test
    fun `chunks stay within limit and retain all sentence blocks`() {
        val sentences = (1..18).map { "Sentence number $it explains a distinct point." }
        val source = sentences.joinToString("\n\n")

        val chunks = SourceChunker.chunk(source, maxChars = 90)

        assertTrue(chunks.size > 1)
        assertTrue(chunks.all { it.length <= 90 })
        assertTrue(sentences.all { sentence -> chunks.any { sentence in it } })
    }

    @Test
    fun `markdown headings and slide separators are preserved as chunk boundaries`() {
        val source = "# Unit One\nFirst sentence.\n\n---\n\nSlide 2\nSecond sentence."

        val chunks = SourceChunker.chunk(source, maxChars = 100)

        assertTrue(chunks.joinToString("\n").contains("# Unit One"))
        assertTrue(chunks.joinToString("\n").contains("Slide 2"))
    }
}