package com.revyu.app.core.util

import android.content.Context
import android.net.Uri
import android.util.Xml
import com.revyu.app.data.local.entities.SourceFileType
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.util.zip.ZipInputStream

class ExtractionException(message: String) : Exception(message)

data class ExtractionResult(
    val text: String,
    val sourceType: SourceFileType,
    val fileName: String,
    val warning: String? = null
)

/**
 * Extracts plain text from an uploaded study material. Nemotron is text-in/text-out, so
 * everything gets reduced to a string before it ever reaches PromptBuilder.
 *
 * PDF uses PdfBox-Android. DOCX and PPTX are unzipped and walked with the platform's
 * built-in XmlPullParser — no extra dependency, same "no native module" spirit as the
 * earlier Expo port's pure-JS extraction.
 */
class FileTextExtractor(private val context: Context) {

    suspend fun extract(uri: Uri, fileName: String): ExtractionResult = withContext(Dispatchers.IO) {
        val type = detectType(fileName)
        var warning: String? = null
        val text = when (type) {
            SourceFileType.PDF -> extractPdf(uri)
            SourceFileType.DOCX -> extractDocx(uri)
            SourceFileType.PPTX -> {
                val res = extractPptx(uri)
                warning = res.warning
                res.text
            }
            SourceFileType.TXT, SourceFileType.MD -> extractPlainText(uri)
            // No on-device spreadsheet reader: .xlsx is only supported via the MarkItDown server.
            SourceFileType.XLSX -> throw ExtractionException(
                "Excel files (.xlsx) can only be read by the conversion server."
            )
        }
        val cleaned = text.trim()
        if (cleaned.isEmpty()) {
            throw ExtractionException("No readable text was found in $fileName.")
        }
        ExtractionResult(text = cleaned, sourceType = type, fileName = fileName, warning = warning)
    }

        fun detectType(fileName: String): SourceFileType = when {
        fileName.endsWith(".pdf", ignoreCase = true) -> SourceFileType.PDF
        fileName.endsWith(".docx", ignoreCase = true) -> SourceFileType.DOCX
        fileName.endsWith(".pptx", ignoreCase = true) -> SourceFileType.PPTX
        fileName.endsWith(".xlsx", ignoreCase = true) -> SourceFileType.XLSX
        fileName.endsWith(".md", ignoreCase = true) -> SourceFileType.MD
        fileName.endsWith(".txt", ignoreCase = true) -> SourceFileType.TXT
        else -> throw ExtractionException("Unsupported file type. Please upload PDF, DOCX, PPTX, XLSX, TXT, or MD.")
    }

    private fun extractPptx(uri: Uri): PptxTextExtractor.PptxExtractionResult {
        return openStream(uri).use { stream ->
            PptxTextExtractor().extract(stream)
        }
    }

    private fun openStream(uri: Uri): InputStream =
        context.contentResolver.openInputStream(uri)
            ?: throw ExtractionException("Couldn't open the selected file.")

    private fun extractPlainText(uri: Uri): String =
        openStream(uri).use { it.readBytes().toString(Charsets.UTF_8) }

    private fun extractPdf(uri: Uri): String {
        openStream(uri).use { stream ->
            PDDocument.load(stream).use { document ->
                // Default PDFBox extraction follows raw content-stream draw order, which
                // for a table can come out column-by-column instead of row-by-row.
                // Sorting by position reconstructs true visual reading order instead,
                // which StudyLoadParser's table-aware parsing depends on.
                val sorted = PDFTextStripper().apply { sortByPosition = true }.getText(document)
                val raw = PDFTextStripper().getText(document)
                val codeRegex = Regex("""\b[A-Z]{2,6}\d{3,5}-\d{2,4}\b""")
                val sortedCodes = codeRegex.findAll(sorted).count()
                val rawCodes = codeRegex.findAll(raw).count()
                // Prefer position-sorted extraction whenever it preserves subject codes,
                // avoiding line-count inflation from raw un-grouped fragments.
                return if (sortedCodes >= rawCodes && sortedCodes > 0) sorted
                else if (tableSignalScore(sorted) >= tableSignalScore(raw)) sorted
                else raw
            }
        }
    }

    /** Rough heuristic for how table-like an extraction is: structured lines, subject
     *  codes, and clock times weigh most. */
    private fun tableSignalScore(text: String): Int {
        val lines = text.lines().count { it.isNotBlank() }
        val codes = Regex("""\b[A-Z]{2,6}\d{3,5}-\d{2,4}\b""").findAll(text).count()
        val times = Regex("""\d{1,2}:\d{2}\s*(?:AM|PM)""", RegexOption.IGNORE_CASE)
            .findAll(text).count()
        return lines + codes * 10 + times * 3
    }

    /**
     * A .docx is a zip archive; the visible text lives in word/document.xml as a
     * sequence of <w:t> runs. We walk every entry's XML, pulling text from any element
     * in the WordprocessingML "w" namespace named "t", inserting a newline whenever we
     * pass a paragraph boundary (<w:p>) so extracted text keeps its paragraph structure.
     */
    private fun extractDocx(uri: Uri): String {
        val builder = StringBuilder()
        openStream(uri).use { stream ->
            ZipInputStream(stream).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (entry.name == "word/document.xml") {
                        val parser = Xml.newPullParser()
                        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
                        parser.setInput(zip, "UTF-8")

                        var eventType = parser.eventType
                        while (eventType != XmlPullParser.END_DOCUMENT) {
                            when (eventType) {
                                XmlPullParser.START_TAG -> {
                                    if (parser.name == "p") {
                                        builder.append("\n")
                                    }
                                }
                                XmlPullParser.TEXT -> {
                                    if (parser.text.isNotBlank()) {
                                        builder.append(parser.text)
                                    }
                                }
                            }
                            eventType = parser.next()
                        }
                        // document.xml found and parsed — no need to scan remaining zip entries.
                        break
                    }
                    entry = zip.nextEntry
                }
            }
        }
        if (builder.isEmpty()) {
            throw ExtractionException("Couldn't find readable text inside this .docx file.")
        }
        return builder.toString()
    }
}
