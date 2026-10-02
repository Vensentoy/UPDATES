package com.revyu.app.data.remote

import okhttp3.Interceptor
import okhttp3.Response

/**
 * The free Nemotron tier can return 429 under load. Retries with exponential backoff,
 * honoring a Retry-After header when the server sends one, before giving up and letting
 * the 429 response fall through to GenerationRepository, which turns it into a friendly
 * "still busy, try again" state instead of a raw error.
 */
class RetryInterceptor(
    private val maxRetries: Int = 3,
    private val baseDelayMillis: Long = 1500L
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        var attempt = 0
        var response = chain.proceed(request)

        while (response.code == 429 && attempt < maxRetries) {
            val retryAfterSeconds = response.header("Retry-After")?.toLongOrNull()
            val delayMillis = retryAfterSeconds?.let { it * 1000 }
                ?: (baseDelayMillis * (1L shl attempt)) // 1.5s, 3s, 6s
            response.close()
            try {
                Thread.sleep(delayMillis)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                return response
            }
            attempt++
            response = chain.proceed(request)
        }
        return response
    }
}
