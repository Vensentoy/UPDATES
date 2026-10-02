package com.revyu.app.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ChatMessage(
    val role: String,
    val content: String
)

@Serializable
data class ReasoningConfig(
    val enabled: Boolean = true,
        val effort: String = "low",
    /** We don't surface reasoning text in the UI, so ask OpenRouter to omit it from the payload. */
    val exclude: Boolean = true
)

@Serializable
data class ResponseFormat(
    val type: String = "json_object"
)

@Serializable
data class ChatCompletionRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val temperature: Double = 0.4,
    @SerialName("max_tokens") val maxTokens: Int = 12000,
    @SerialName("top_p") val topP: Double = 0.95,
    val reasoning: ReasoningConfig = ReasoningConfig(),
    @SerialName("response_format") val responseFormat: ResponseFormat = ResponseFormat()
)

@Serializable
data class ChatChoice(
    val message: ChatMessage,
    @SerialName("finish_reason") val finishReason: String? = null
)

@Serializable
data class ChatCompletionResponse(
    val id: String? = null,
    val choices: List<ChatChoice> = emptyList(),
    val error: ApiError? = null
)

@Serializable
data class ApiError(
    val message: String,
    val code: Int? = null
)
