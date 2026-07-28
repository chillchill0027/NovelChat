package com.novelchat.data.remote.api

import com.novelchat.data.remote.model.ChatRequest
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Streaming

interface OpenAiApi {
    @POST("v1/chat/completions")
    @Streaming
    suspend fun chatCompletions(
        @Header("Authorization") authorization: String?,
        @Body request: ChatRequest
    ): Response<ResponseBody>
}
