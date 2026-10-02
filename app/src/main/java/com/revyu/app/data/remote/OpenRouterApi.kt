package com.revyu.app.data.remote

import retrofit2.http.Body
import retrofit2.http.POST

interface OpenRouterApi {
    @POST("chat/completions")
    suspend fun createChatCompletion(@Body request: ChatCompletionRequest): ChatCompletionResponse

    companion object {
        const val BASE_URL = "https://openrouter.ai/api/v1/"
        const val MODEL_ID = "nvidia/nemotron-3-ultra-550b-a55b:free"
    }
}
