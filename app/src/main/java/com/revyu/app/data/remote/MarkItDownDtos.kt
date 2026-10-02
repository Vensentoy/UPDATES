package com.revyu.app.data.remote

import kotlinx.serialization.Serializable

/** Successful reply from POST /convert on the MarkItDown backend. */
@Serializable
data class MarkItDownResponse(
    val markdown: String = "",
    val fileName: String = ""
)