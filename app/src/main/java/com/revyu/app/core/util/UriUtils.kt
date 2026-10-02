package com.revyu.app.core.util

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns

object UriUtils {
    fun displayName(context: Context, uri: Uri): String {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0 && cursor.moveToFirst()) {
                cursor.getString(nameIndex)?.let { return it }
            }
        }
        return uri.lastPathSegment ?: "file"
    }
}
