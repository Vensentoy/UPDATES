package com.revyu.app.data.remote

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

/**
 * Must extend [IOException]: OkHttp only delivers [IOException] to the async callback's
 * onFailure. Any other Throwable thrown from an interceptor is re-thrown on the OkHttp
 * Dispatcher thread and kills the process (FATAL EXCEPTION: OkHttp Dispatcher).
 */
class MissingApiKeyException : IOException("No OpenRouter API key is set.")

class AuthInterceptor(private val apiKeyProvider: ApiKeyProvider) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val key = apiKeyProvider.apiKey ?: throw MissingApiKeyException()
        val request = chain.request().newBuilder()
            .addHeader("Authorization", "Bearer $key")
            .addHeader("HTTP-Referer", "https://revyu.app")
            .addHeader("X-Title", "Revyu")
            .build()
        return chain.proceed(request)
    }
}
