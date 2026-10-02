package com.revyu.app.data.remote

import kotlinx.serialization.Serializable

@Serializable
data class GeneratedOutline(
    val topics: List<GeneratedTopic> = emptyList()
)

@Serializable
data class GeneratedTopic(
    val id: String = "",
    val title: String = "",
    val importance: Int = 2,
    val keyPoints: List<String> = emptyList(),
    val terms: List<String> = emptyList()
)

@Serializable
data class StructuredReviewer(
    val title: String = "",
    val overview: String = "",
    val sections: List<ReviewerSection> = emptyList(),
    val glossary: List<GlossaryEntry> = emptyList(),
    val cheatSheet: List<String> = emptyList()
)

@Serializable
data class ReviewerSection(
    val topicId: String? = null,
    val heading: String = "",
    val blocks: List<ReviewerBlock> = emptyList()
)

@Serializable
data class ReviewerBlock(
    val type: String = "",
    val text: String? = null,
    val items: List<String> = emptyList(),
    val kind: String? = null,
    val term: String? = null,
    val headers: List<String> = emptyList(),
    val rows: List<List<String>> = emptyList()
)

@Serializable
data class GlossaryEntry(
    val term: String = "",
    val meaning: String = ""
)

@Serializable
data class GeneratedFlashcardsPayload(
    val flashcards: List<GeneratedFlashcard> = emptyList()
)

@Serializable
data class GeneratedQuestionsPayload(
    val questions: List<GeneratedQuestion> = emptyList()
)

@Serializable
data class GeneratedFlashcard(
    val front: String = "",
    val back: String = "",
    val hint: String = "",
    val topicId: String? = null,
    val difficulty: Int = 2,
    val kind: String = "TERM"
)

@Serializable
data class GeneratedQuestion(
    val type: String = "",
    val prompt: String = "",
    val options: List<String> = emptyList(),
    val correctAnswers: List<String> = emptyList(),
    val acceptedAnswers: List<String> = emptyList(),
    val explanation: String = "",
    val topicId: String? = null,
    val difficulty: Int = 2,
    val level: String = "RECALL"
)
