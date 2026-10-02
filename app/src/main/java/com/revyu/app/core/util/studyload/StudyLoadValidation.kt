package com.revyu.app.core.util.studyload

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns

/** File-level validation for study-load uploads. Pure logic, unit-testable. */
object StudyLoadFileValidation {

    const val MAX_FILE_BYTES: Long = 5L * 1024L * 1024L // 5 MB
    const val MAX_FILE_MB: Int = 5

    sealed class SizeResult {
        data object Ok : SizeResult()
        data class TooLarge(val sizeBytes: Long, val maxBytes: Long) : SizeResult()
    }

    fun checkSize(sizeBytes: Long): SizeResult =
        if (sizeBytes in 0..MAX_FILE_BYTES) SizeResult.Ok
        else SizeResult.TooLarge(sizeBytes, MAX_FILE_BYTES)

    /** "PDF only for now" - the Smart Calendar importer accepts PDFs only. */
    fun looksLikePdf(fileName: String): Boolean =
        fileName.endsWith(".pdf", ignoreCase = true)

    val FILE_TOO_LARGE_TITLE = "File too large"
    val FILE_TOO_LARGE_MESSAGE = "Uy bai, sobra sa 5 MB 😭\nPlease choose a smaller study load."
    val FILE_TOO_LARGE_ACTION = "Choose Another File"

    val NOT_LLCC_MESSAGE = "Murag dili ni LLCC study load, bai."
    val NOT_LLCC_ACTION = "Try Another File"

    /**
     * A short excerpt of the extracted text, so "not recognized" errors show what the
     * parser actually received (useful when a PDF scanner only produced an image layer).
     */
    fun previewOf(text: String, maxLength: Int = 160): String {
        val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }
        val snippet = lines.take(4).joinToString(" | ")
        return if (snippet.length > maxLength) snippet.take(maxLength) + "…" else snippet
    }

    val UNREADABLE_MESSAGE = "Couldn't read this PDF.\nTry uploading a clearer copy."

    val PARTIAL_PARSE_MESSAGE = "We found some schedule details we couldn't confidently read."
    val PARTIAL_PARSE_ACTION = "Review & Fix"

    val NOT_PDF_MESSAGE = "PDF only for now.\nPlease upload a .pdf study load file."
}

/**
 * Queries the size of a content:// document. Returns -1 when the provider doesn't report one.
 */
fun Uri.sizeOf(context: Context): Long {
    var size = -1L
    context.contentResolver.query(this, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            val idx = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (idx >= 0 && !cursor.isNull(idx)) size = cursor.getLong(idx)
        }
    }
    if (size < 0) {
        // Fallback by actually reading the stream (e.g. Google Drive size queries).
        context.contentResolver.openInputStream(this)?.use { input ->
            size = input.available().toLong()
        } ?: run { size = 0L }
    }
    return size
}

fun Uri.displayFileName(context: Context): String {
    context.contentResolver.query(this, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && !cursor.isNull(idx)) return cursor.getString(idx)
        }
    }
    return "study-load"
}