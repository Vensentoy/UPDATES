package com.revyu.app.data.remote

import kotlinx.serialization.Serializable

/** The exact JSON shape we instruct the model to return for one Study Set generation. */
@Serializable
data class GeneratedStudySetPayload(
    val reviewer: String,
    val flashcards: List<GeneratedFlashcard> = emptyList(),
    val questions: List<GeneratedQuestion> = emptyList()
)

@Serializable
data class GeneratedFlashcard(
    val front: String,
    val back: String
)

@Serializable
data class GeneratedQuestion(
    val type: String,
    val prompt: String,
    val options: List<String> = emptyList(),
    val correctAnswers: List<String> = emptyList()
)
