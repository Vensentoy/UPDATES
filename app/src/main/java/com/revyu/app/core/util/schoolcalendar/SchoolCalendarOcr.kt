package com.revyu.app.core.util.schoolcalendar

import android.content.Context
import android.net.Uri
import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.revyu.app.core.util.ExtractionException
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.rendering.PDFRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A recognized text block with its on-page geometry, the seam between ML Kit and
 * [OcrLineAssembler]. Dimension units are pixels as reported by ML Kit / PDFBox.
 */
data class OcrElement(
    val text: String,
    val x: Float,
    val y: Float,
    val width: Float = 0f,
    val height: Float = 0f
)

/**
 * Pure clustering of OCR elements into [OcrLine] rows. Handles the two shapes a scanned
 * calendar PDF presents:
 *
 *  - Elements on the same visual line (small vertical gaps) are gathered into a band,
 *    then ordered left-to-right.
 *  - A wide horizontal gap inside a band means the page is 2-column (scanner spread) — the
 *    left and right halves become separate tokens joined with " | ", exactly the token
 *    SchoolCalendarParser splits on.
 *
 * Kept dependency-free so the geometry logic is unit-testable on the JVM.
 */
object OcrLineAssembler {

    fun assemble(
        elements: List<OcrElement>,
        verticalGap: Float = 16f,
        horizontalColumnGap: Float = 60f
    ): List<OcrLine> {
        val sorted = elements
            .filter { it.text.isNotBlank() }
            .sortedWith(compareBy({ it.y }, { it.x }))

        val bands = mutableListOf<MutableList<OcrElement>>()
        for (el in sorted) {
            var target: MutableList<OcrElement>? = null
            for (band in bands) {
                val bottom = band.maxOf { it.y + it.height }
                if (el.y <= bottom + verticalGap) {
                    target = band
                    break
                }
            }
            if (target == null) {
                target = mutableListOf()
                bands.add(target)
            }
            target.add(el)
        }

        return bands.map { band ->
            val ordered = band.sortedBy { it.x }
            val tokens = mutableListOf<String>()
            val current = StringBuilder()
            var lastRight = Float.NEGATIVE_INFINITY
            for (el in ordered) {
                val gap = el.x - lastRight
                if (gap > horizontalColumnGap && current.isNotEmpty()) {
                    tokens.add(current.toString())
                    current.clear()
                }
                if (current.isNotEmpty()) current.append(' ')
                current.append(el.text.trim())
                lastRight = el.x + el.width
            }
            if (current.isNotEmpty()) tokens.add(current.toString())
            OcrLine(
                text = tokens.joinToString(" | "),
                x = ordered.first().x.toInt(),
                y = band.minOf { it.y }.toInt(),
                elements = band.sortedBy { it.x }
            )
        }
    }
}

/**
 * Extracts [OcrLine] rows from a scanned (image-only) school-calendar PDF using on-device
 * ML Kit text recognition. Each page is rasterized with PdfBox-Android at ~300 DPI, OCR'd,
 * and its text blocks are assembled back into reading-order rows. This is the path for
 * files like the ones LLCC posts as photo scans — PDFBox alone yields no text on those.
 */
class SchoolCalendarOcr(private val context: Context) {

    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    suspend fun recognizePdf(uri: Uri): List<OcrLine> = withContext(Dispatchers.IO) {
        val input = context.contentResolver.openInputStream(uri)
            ?: throw ExtractionException("Couldn't open the selected file.")
        val lines = mutableListOf<OcrLine>()
        input.use { stream ->
            PDDocument.load(stream).use { document ->
                val renderer = PDFRenderer(document)
                for (page in 0 until document.numberOfPages) {
                    val pageBox = document.getPage(page).mediaBox
                    val pageW = pageBox.width
                    val pageH = pageBox.height
                    // A scanned A4 is often recorded in *points* of the scan's own pixels, so a
                    // blanket 300 DPI can explode a 2480x3507 pt page into a ~10333x14612 bitmap
                    // (~600 MB). Cap the longest side instead; ML Kit upsamples internally.
                    val dpi = (MAX_PIXELS * 72f / maxOf(pageW, pageH)).coerceIn(72f, 300f)
                    val bitmap = renderer.renderImageWithDPI(page, dpi)
                    val elements = try {
                        recognize(bitmap)
                    } finally {
                        bitmap.recycle()
                    }
                    lines += OcrLineAssembler.assemble(elements)
                }
            }
        }
        if (lines.isEmpty()) {
            throw ExtractionException("No readable text was found in this PDF.")
        }
        lines
    }

    private companion object {
        /** Longest bitmap side in px to hand to ML Kit (its own docs cap ~4096). */
        const val MAX_PIXELS = 3400f
    }

    private fun recognize(bitmap: Bitmap): List<OcrElement> {
        val text: Text = com.google.android.gms.tasks.Tasks.await(
            recognizer.process(InputImage.fromBitmap(bitmap, 0))
        )
        val elements = mutableListOf<OcrElement>()
        text.textBlocks.forEach { block ->
            block.lines.forEach { line ->
                line.elements.forEach { element ->
                    val box = element.boundingBox
                    if (box != null) {
                        elements += OcrElement(
                            text = element.text,
                            x = box.left.toFloat(),
                            y = box.top.toFloat(),
                            width = box.width().toFloat(),
                            height = box.height().toFloat()
                        )
                    }
                }
            }
        }
        return elements
    }
}