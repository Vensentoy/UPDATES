package com.revyu.app.core.util

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import com.revyu.app.data.local.entities.SourceFileType
import com.revyu.app.data.remote.MarkItDownClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/** Progress the UI can show while a file is being read. */
sealed class ExtractionStatus {
    data object Reading : ExtractionStatus()
    data object WakingServer : ExtractionStatus()
    data object Converting : ExtractionStatus()
}

/**
 * Decides how an uploaded study material is turned into text.
 *
 * PDF, DOCX, TXT and MD always stay on-device through [FileTextExtractor]. PPTX goes to
 * the MarkItDown backend, and falls back to the on-device [PptxTextExtractor] (through
 * [FileTextExtractor]) whenever the server is not configured, the phone is offline, the
 * file is too big, the server will not wake up, or the conversion fails.
 *
 * Whichever path was used, the returned [ExtractionResult.warning] carries a plain-language
 * note, which the upload screen already shows in an info banner.
 *
 * Failures that cannot be recovered are thrown as [ExtractionException], like
 * [FileTextExtractor] does.
 */
class MaterialExtractor(
    private val context: Context,
    private val localExtractor: FileTextExtractor,
    private val client: MarkItDownClient,
    private val networkObserver: NetworkObserver
) {

    suspend fun extract(
        uri: Uri,
        fileName: String,
        onStatus: (ExtractionStatus) -> Unit = {}
    ): ExtractionResult {
        onStatus(ExtractionStatus.Reading)
        if (!usesServer(fileName)) {
            return localExtractor.extract(uri, fileName)
        }
        return extractWithServer(uri, fileName, onStatus)
    }

    private fun usesServer(fileName: String): Boolean =
        SERVER_EXTENSIONS.any { fileName.endsWith(".$it", ignoreCase = true) }

    private suspend fun extractWithServer(
        uri: Uri,
        fileName: String,
        onStatus: (ExtractionStatus) -> Unit
    ): ExtractionResult {
        if (!client.isConfigured) {
            return localWithNotice(uri, fileName, NOTICE_NOT_CONFIGURED)
        }
        if (!networkObserver.isOnline.value) {
            return localWithNotice(uri, fileName, NOTICE_OFFLINE)
        }

        val bytes = withContext(Dispatchers.IO) { readBytesCapped(uri, MAX_SERVER_BYTES) }
            ?: return localWithNotice(uri, fileName, NOTICE_TOO_LARGE)

        if (!waitForServer(onStatus)) {
            return localWithNotice(uri, fileName, NOTICE_NO_WAKE)
        }

        onStatus(ExtractionStatus.Converting)
        return when (val result = client.convert(fileName, bytes)) {
            is RevyuResult.Success -> {
                val text = result.data.markdown.trim()
                if (text.isEmpty()) {
                    localWithNotice(uri, fileName, NOTICE_EMPTY)
                } else {
                    ExtractionResult(
                        text = text,
                        sourceType = localExtractor.detectType(fileName),
                        fileName = fileName,
                        warning = NOTICE_SERVER_OK
                    )
                }
            }
            is RevyuResult.Error ->
                localWithNotice(uri, fileName, "${result.message} $NOTICE_FELL_BACK")
        }
    }

    /**
     * One probe first. If it fails, the Render free tier is probably asleep: tell the UI,
     * then keep probing every couple of seconds until the server answers or time runs out.
     * Each probe has its own short timeout, so a sleeping server never blocks for long.
     */
    private suspend fun waitForServer(onStatus: (ExtractionStatus) -> Unit): Boolean {
        if (client.probeHealth()) return true
        onStatus(ExtractionStatus.WakingServer)
        val deadline = SystemClock.elapsedRealtime() + WAKE_TIMEOUT_MS
        while (SystemClock.elapsedRealtime() < deadline) {
            delay(POLL_INTERVAL_MS)
            if (client.probeHealth()) return true
        }
        return false
    }

    /** Runs the on-device extractor and puts [notice] in front of any warning it produced. */
        /** Runs the on-device extractor and puts [notice] in front of any warning it produced. */
    private suspend fun localWithNotice(uri: Uri, fileName: String, notice: String): ExtractionResult {
        // .xlsx has no on-device reader, so explain why the server path failed instead.
        if (localExtractor.detectType(fileName) == SourceFileType.XLSX) {
            val serverReason = notice.removeSuffix(" $NOTICE_FELL_BACK").takeIf { it != notice }
            throw ExtractionException(
                serverReason
                    ?: "Excel files can only be read by the conversion server, which isn't available right now. Check your connection and try again."
            )
        }
        val local = localExtractor.extract(uri, fileName)
        val combined = listOfNotNull(notice, local.warning).joinToString("\n")
        return local.copy(warning = combined)
    }

    /** Reads the file, or returns null as soon as it grows past [maxBytes]. */
    private fun readBytesCapped(uri: Uri, maxBytes: Int): ByteArray? {
        val stream = context.contentResolver.openInputStream(uri)
            ?: throw ExtractionException("Couldn't open the selected file.")
        stream.use { input ->
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var total = 0
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                total += read
                if (total > maxBytes) return null
                out.write(buffer, 0, read)
            }
            return out.toByteArray()
        }
    }

    private companion object {
        /** File extensions sent to the backend. Everything else stays on-device. */
        val SERVER_EXTENSIONS = setOf("pptx", "docx", "xlsx", "pdf")

        const val MAX_SERVER_BYTES = 25 * 1024 * 1024
        const val WAKE_TIMEOUT_MS = 65_000L
        const val POLL_INTERVAL_MS = 2_500L

        const val NOTICE_SERVER_OK = "Converted by the MarkItDown server."
        const val NOTICE_NOT_CONFIGURED =
            "Server conversion isn't set up in this build, so this PowerPoint was read on your device."
        const val NOTICE_OFFLINE =
            "You're offline, so this PowerPoint was read on your device."
        const val NOTICE_TOO_LARGE =
            "This file is too large for the conversion server, so it was read on your device."
        const val NOTICE_NO_WAKE =
            "The conversion server didn't wake up in time, so this PowerPoint was read on your device."
        const val NOTICE_EMPTY =
            "The conversion server found no text, so this PowerPoint was read on your device."
        const val NOTICE_FELL_BACK = "This PowerPoint was read on your device instead."
    }
}