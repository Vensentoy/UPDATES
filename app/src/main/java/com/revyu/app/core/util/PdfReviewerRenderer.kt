package com.revyu.app.core.util

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.revyu.app.data.local.entities.ReviewerFontStyle
import com.revyu.app.data.local.entities.ReviewerMargins
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Renders a generated reviewer body (plain text with short "heading" lines separated by
 * blank lines) into a paginated, multi-column PDF using Android's built-in PdfDocument —
 * no external PDF-writing library needed.
 */
class PdfReviewerRenderer(private val context: Context) {

    // A4 at ~150dpi, in pixels — high enough to read comfortably, small enough to render fast.
    private val pageWidth = 1240
    private val pageHeight = 1754

    suspend fun render(
        studySetId: String,
        subjectName: String,
        studySetTitle: String,
        bodyText: String,
        columns: Int,
        fontStyle: ReviewerFontStyle,
        fontSizeSp: Int,
        margins: ReviewerMargins
    ): String = withContext(Dispatchers.IO) {
        val marginPx = when (margins) {
            ReviewerMargins.COMPACT -> 44f
            ReviewerMargins.NORMAL -> 72f
            ReviewerMargins.SPACIOUS -> 108f
        }
        val gutter = 28f
        val typeface = when (fontStyle) {
            ReviewerFontStyle.SERIF -> Typeface.SERIF
            ReviewerFontStyle.SANS -> Typeface.SANS_SERIF
            ReviewerFontStyle.MONO -> Typeface.MONOSPACE
        }

        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = typeface
            textSize = fontSizeSp.toFloat().coerceIn(10f, 22f) * 1.4f // px approximation of sp at ~150dpi
            color = Color.rgb(0x1B, 0x2A, 0x4A) // Ink Navy
        }
        val headingPaint = Paint(bodyPaint).apply {
            this.typeface = Typeface.create(typeface, Typeface.BOLD)
            textSize = bodyPaint.textSize * 1.35f
        }
        val titlePaint = Paint(bodyPaint).apply {
            this.typeface = Typeface.create(typeface, Typeface.BOLD)
            textSize = bodyPaint.textSize * 1.9f
        }
        val eyebrowPaint = Paint(bodyPaint).apply {
            textSize = bodyPaint.textSize * 0.75f
            color = Color.rgb(0xE8, 0x67, 0x4B) // Folder Coral
            this.typeface = Typeface.create(typeface, Typeface.BOLD)
        }
        val accentPaint = Paint().apply { color = Color.rgb(0xF4, 0xC4, 0x30) } // Highlighter Yellow

        val columnCount = columns.coerceIn(1, 4)
        val contentWidth = pageWidth - 2 * marginPx
        val columnWidth = (contentWidth - gutter * (columnCount - 1)) / columnCount

        val document = PdfDocument()
        var page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, document.pages.size + 1).create())
        var canvas = page.canvas

        var currentColumn = 0
        var currentPageIndex = 0
        var cursorY = marginPx
        val topOfColumns: Float

        // Header block: margin-rule accent bar + eyebrow + title, full width, before columns start.
        canvas.drawRect(marginPx, cursorY, marginPx + 6f, cursorY + 64f, accentPaint)
        eyebrowPaint.let { canvas.drawText(subjectName.uppercase(), marginPx + 20f, cursorY + 22f, it) }
        canvas.drawText(studySetTitle, marginPx + 20f, cursorY + 54f, titlePaint)
        cursorY += 90f
        topOfColumns = cursorY

        fun columnLeft(col: Int) = marginPx + col * (columnWidth + gutter)
        fun newPage() {
            document.finishPage(page)
            page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, document.pages.size + 1).create())
            canvas = page.canvas
            currentColumn = 0
            currentPageIndex++
            cursorY = marginPx
        }
        fun advanceColumnOrPage() {
            currentColumn++
            if (currentColumn >= columnCount) {
                newPage()
            } else {
                // Only page 1 reserves space at the top of a column for the header block —
                // pages 2+ never draw a header, so their columns should start flush at the
                // top margin. Using topOfColumns unconditionally here wasted ~90px at the
                // top of every column after the first on every page past page 1.
                cursorY = if (currentPageIndex == 0) topOfColumns else marginPx
            }
        }

        val blocks = bodyText.split(Regex("\n\\s*\n")).map { it.trim() }.filter { it.isNotBlank() }

        for (block in blocks) {
            val lines = block.lines().map { it.trim() }.filter { it.isNotBlank() }
            if (lines.isEmpty()) continue

            val isHeading = lines.size == 1 && lines[0].length < 70 && !lines[0].endsWith(".")
            val paint = if (isHeading) headingPaint else bodyPaint
            val lineHeight = paint.fontSpacing
            val extraSpaceBefore = if (isHeading) lineHeight * 0.5f else 0f

            val wrapped = if (isHeading) lines else wrapParagraph(block, bodyPaint, columnWidth)
            val blockHeight = extraSpaceBefore + wrapped.size * lineHeight + lineHeight * 0.4f

            if (cursorY + blockHeight > pageHeight - marginPx) {
                advanceColumnOrPage()
            }
            cursorY += extraSpaceBefore

            val x = columnLeft(currentColumn)
            for (line in wrapped) {
                if (cursorY + lineHeight > pageHeight - marginPx) {
                    advanceColumnOrPage()
                }
                canvas.drawText(line, x, cursorY, paint)
                cursorY += lineHeight
            }
            cursorY += lineHeight * 0.4f
        }

        document.finishPage(page)

        val dir = File(context.filesDir, "reviewers").apply { mkdirs() }
        val outFile = File(dir, "$studySetId.pdf")
        FileOutputStream(outFile).use { document.writeTo(it) }
        document.close()

        outFile.absolutePath
    }

    private fun wrapParagraph(text: String, paint: Paint, maxWidth: Float): List<String> {
        val words = text.split(Regex("\\s+")).filter { it.isNotBlank() }
        val lines = mutableListOf<String>()
        var current = StringBuilder()

        for (word in words) {
            val candidate = if (current.isEmpty()) word else "${current} $word"
            if (paint.measureText(candidate) <= maxWidth) {
                current = StringBuilder(candidate)
            } else {
                if (current.isNotEmpty()) lines.add(current.toString())
                current = StringBuilder(word)
            }
        }
        if (current.isNotEmpty()) lines.add(current.toString())
        return lines
    }
}
