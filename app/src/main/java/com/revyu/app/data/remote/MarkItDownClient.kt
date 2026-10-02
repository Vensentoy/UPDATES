package com.revyu.app.data.remote

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.revyu.app.core.util.RevyuResult
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException

/**
 * Talks to the self-hosted MarkItDown backend (Render free tier, so it sleeps when idle).
 *
 * Two separate OkHttp clients on purpose: a short-timeout one for the /health wake-up
 * probe, and a long-timeout one for /convert. Every failure comes back as a
 * [RevyuResult.Error] with a message that is safe to show to the user.
 *
 * baseUrl and apiKey come from BuildConfig; if either is blank the client reports
 * [isConfigured] = false and the caller should use on-device extraction instead.
 */
class MarkItDownClient(
    baseUrl: String,
    private val apiKey: String,
    debug: Boolean
) {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val hasBaseUrl = baseUrl.isNotBlank()

    val isConfigured: Boolean = hasBaseUrl && apiKey.isNotBlank()

    // Retrofit requires a trailing slash on the base URL.
    private val normalizedBaseUrl = baseUrl.trim().trimEnd('/') + "/"

    private val baseHttpClient: OkHttpClient by lazy {
        val logging = HttpLoggingInterceptor().apply {
            // BASIC logs only the request line and status code, never headers or bodies.
            level = if (debug) HttpLoggingInterceptor.Level.BASIC else HttpLoggingInterceptor.Level.NONE
            redactHeader("x-api-key")
        }
        OkHttpClient.Builder()
            .addInterceptor(logging)
            .build()
    }

    private val healthClient: OkHttpClient by lazy {
        baseHttpClient.newBuilder()
            .connectTimeout(4, TimeUnit.SECONDS)
            .readTimeout(4, TimeUnit.SECONDS)
            .writeTimeout(4, TimeUnit.SECONDS)
            .callTimeout(5, TimeUnit.SECONDS)
            .build()
    }

    private val convertClient: OkHttpClient by lazy {
        baseHttpClient.newBuilder()
            .addInterceptor(ApiKeyInterceptor(apiKey))
            .connectTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .readTimeout(70, TimeUnit.SECONDS)
            .build()
    }

    private fun retrofitFor(client: OkHttpClient): Retrofit =
        Retrofit.Builder()
            .baseUrl(normalizedBaseUrl)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

    private val healthApi: MarkItDownHealthApi? by lazy {
        if (hasBaseUrl) retrofitFor(healthClient).create(MarkItDownHealthApi::class.java) else null
    }

    private val convertApi: MarkItDownApi? by lazy {
        if (isConfigured) retrofitFor(convertClient).create(MarkItDownApi::class.java) else null
    }

    /**
     * One quick GET /health. Returns true only if the server answered with a 2xx within
     * a few seconds. A sleeping Render service will return false here; the caller decides
     * whether to keep polling while showing a "waking up" message.
     */
    suspend fun probeHealth(): Boolean {
        val api = healthApi ?: return false
        return try {
            api.health().isSuccessful
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Uploads the file bytes to POST /convert. Only the file extension is sent as the
     * filename ("upload.pptx"): the server uses the extension to pick a converter, and
     * this avoids sending the student's real file name or odd characters.
     */
    suspend fun convert(fileName: String, bytes: ByteArray): RevyuResult<MarkItDownResponse> {
        val api = convertApi
            ?: return RevyuResult.Error("The conversion server isn't configured in this build.")

        val extension = fileName.substringAfterLast('.', "").lowercase()
        val part = MultipartBody.Part.createFormData(
            "file",
            "upload.$extension",
            bytes.toRequestBody("application/octet-stream".toMediaType())
        )

        return try {
            val response = api.convert(part)
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null) {
                    RevyuResult.Success(body)
                } else {
                    RevyuResult.Error("The server sent an empty reply.")
                }
            } else {
                RevyuResult.Error(describeHttpError(response.code(), response.errorBody()?.string()))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: SocketTimeoutException) {
            RevyuResult.Error("The server took too long to respond.", cause = e)
        } catch (e: IOException) {
            RevyuResult.Error("Couldn't reach the conversion server.", cause = e)
        } catch (e: Exception) {
            RevyuResult.Error("The server's reply couldn't be read.", cause = e)
        }
    }

    private fun describeHttpError(code: Int, rawBody: String?): String {
        val detail = parseDetail(rawBody)
        return when {
            code == 401 -> "The conversion server rejected the app's access key."
            code == 413 -> "The file is too large for the conversion server (25 MB max)."
            code == 400 -> "The conversion server can't convert this file type."
            code == 500 && detail?.contains("not configured", ignoreCase = true) == true ->
                "The conversion server isn't set up with an access key yet."
            code in 500..599 -> "The conversion server had a problem with this file."
            else -> "The conversion server returned an error ($code)."
        }
    }

    /** FastAPI errors look like {"detail": "..."}; tolerate any other shape. */
    private fun parseDetail(rawBody: String?): String? {
        if (rawBody.isNullOrBlank()) return null
        return try {
            (json.parseToJsonElement(rawBody).jsonObject["detail"] as? JsonPrimitive)?.contentOrNull
        } catch (_: Exception) {
            null
        }
    }
}
/** Adds the x-api-key header. Used only on the /convert client, never on /health. */

private class ApiKeyInterceptor(private val apiKey: String) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder()
            .header("x-api-key", apiKey)
            .build()
        return chain.proceed(request)
    }
}