package com.revyu.app.data.remote

import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

/** Needs no key. Used only as a "is the server awake?" probe. */
interface MarkItDownHealthApi {
    @GET("health")
    suspend fun health(): Response<Unit>
}

interface MarkItDownApi {
    /** The backend reads the multipart field named "file". */
    @Multipart
    @POST("convert")
    suspend fun convert(@Part file: MultipartBody.Part): Response<MarkItDownResponse>
}