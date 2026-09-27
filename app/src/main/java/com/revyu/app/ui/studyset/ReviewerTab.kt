package com.revyu.app.ui.studyset

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.revyu.app.ui.components.EmptyState
import com.revyu.app.ui.components.LoadingState
import com.revyu.app.ui.components.PrimaryButton
import com.revyu.app.ui.components.SecondaryButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun ReviewerTab(pdfPath: String?, studySetTitle: String) {
    val context = LocalContext.current
    var thumbnail by remember(pdfPath) { mutableStateOf<Bitmap?>(null) }
    var loadFailed by remember(pdfPath) { mutableStateOf(false) }

    LaunchedEffect(pdfPath) {
        if (pdfPath == null) return@LaunchedEffect
        thumbnail = withContext(Dispatchers.IO) {
            runCatching {
                val file = File(pdfPath)
                ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                    PdfRenderer(fd).use { renderer ->
                        renderer.openPage(0).use { page ->
                            val bmp = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                            bmp.eraseColor(android.graphics.Color.WHITE)
                            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            bmp
                        }
                    }
                }
            }.onFailure { loadFailed = true }.getOrNull()
        }
    }

    if (pdfPath == null) {
        EmptyState(title = "Reviewer not ready", body = "This Study Set is still generating.")
        return
    }

    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        when {
            thumbnail != null -> {
                Image(
                    bitmap = thumbnail!!.asImageBitmap(),
                    contentDescription = "Reviewer preview",
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(thumbnail!!.width.toFloat() / thumbnail!!.height.toFloat())
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant)
                )
            }
            loadFailed -> {
                Text("Couldn't render a preview, but the PDF is ready below.", style = MaterialTheme.typography.bodyMedium)
            }
            else -> LoadingState("Loading preview…")
        }

        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PrimaryButton(
                text = "Open",
                onClick = { openPdf(context, pdfPath) },
                modifier = Modifier.weight(1f)
            )
            SecondaryButton(
                text = "Share / Download",
                onClick = { sharePdf(context, pdfPath, studySetTitle) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

private fun pdfUri(context: android.content.Context, pdfPath: String) =
    FileProvider.getUriForFile(context, "com.revyu.app.fileprovider", File(pdfPath))

private fun openPdf(context: android.content.Context, pdfPath: String) {
    val uri = pdfUri(context, pdfPath)
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "application/pdf")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(intent) }
}

private fun sharePdf(context: android.content.Context, pdfPath: String, title: String) {
    val uri = pdfUri(context, pdfPath)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, title)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(Intent.createChooser(intent, "Share Reviewer").apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    })
}
