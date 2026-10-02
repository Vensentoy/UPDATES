package com.revyu.app.core.util

object SourceChunker {
    const val MAX_CHUNK_CHARS = 12_000

    private val sectionBoundary = Regex("(?im)^(?:#{1,2}\\s+.*|(?:[-=_*]){3,}\\s*|(?:[-=_*]{3,}\\s*)?(?:slide|sheet|page)\\b.*)$")
    private val sentenceBoundary = Regex("(?<=[.!?])\\s+")

    fun chunk(sourceText: String, maxChars: Int = MAX_CHUNK_CHARS): List<String> {
        require(maxChars > 0)
        if (sourceText.isBlank()) return emptyList()

        val blocks = splitIntoBlocks(sourceText)
        val chunks = mutableListOf<String>()
        var current = StringBuilder()

        fun flush() {
            if (current.isNotEmpty()) {
                chunks += current.toString().trim()
                current = StringBuilder()
            }
        }

        for (block in blocks) {
            val pieces = if (block.length <= maxChars) listOf(block) else splitOversizedBlock(block, maxChars)
            for (piece in pieces) {
                val separatorLength = if (current.isEmpty()) 0 else 2
                if (current.length + separatorLength + piece.length > maxChars) flush()
                if (current.isNotEmpty()) current.append("\n\n")
                current.append(piece)
            }
        }
        flush()
        return chunks.filter(String::isNotBlank)
    }

    private fun splitIntoBlocks(sourceText: String): List<String> {
        val blocks = mutableListOf<String>()
        val current = mutableListOf<String>()

        fun flush() {
            val text = current.joinToString("\n").trim()
            if (text.isNotEmpty()) blocks += text
            current.clear()
        }

        sourceText.lineSequence().forEach { line ->
            if (line.isBlank()) {
                flush()
            } else if (sectionBoundary.matches(line.trim())) {
                flush()
                blocks += line.trim()
            } else {
                current += line
            }
        }
        flush()
        return blocks
    }

    private fun splitOversizedBlock(block: String, maxChars: Int): List<String> {
        val sentences = block.split(sentenceBoundary).filter(String::isNotBlank)
        val pieces = mutableListOf<String>()
        var current = StringBuilder()

        fun flush() {
            if (current.isNotEmpty()) {
                pieces += current.toString().trim()
                current = StringBuilder()
            }
        }

        for (sentence in sentences) {
            val words = if (sentence.length <= maxChars) listOf(sentence) else splitLongSentence(sentence, maxChars)
            for (wordChunk in words) {
                val separatorLength = if (current.isEmpty()) 0 else 1
                if (current.length + separatorLength + wordChunk.length > maxChars) flush()
                if (current.isNotEmpty()) current.append(' ')
                current.append(wordChunk)
            }
        }
        flush()
        return pieces
    }

    private fun splitLongSentence(sentence: String, maxChars: Int): List<String> {
        val chunks = mutableListOf<String>()
        var current = StringBuilder()
        for (word in sentence.trim().split(Regex("\\s+"))) {
            if (word.length > maxChars) {
                if (current.isNotEmpty()) {
                    chunks += current.toString()
                    current = StringBuilder()
                }
                word.chunked(maxChars).forEach(chunks::add)
                continue
            }
            val separatorLength = if (current.isEmpty()) 0 else 1
            if (current.length + separatorLength + word.length > maxChars) {
                chunks += current.toString()
                current = StringBuilder()
            }
            if (current.isNotEmpty()) current.append(' ')
            current.append(word)
        }
        if (current.isNotEmpty()) chunks += current.toString()
        return chunks
    }
}