package com.plantlens.ai.network

import retrofit2.http.Body
import retrofit2.http.POST

data class OllamaRequest(
    val question: String,
    val plantName: String
)

data class OllamaResponse(
    val answer: String
)

interface OllamaApiService {

    @POST("api/chat")
    suspend fun askAssistant(
        @Body request: OllamaRequest
    ): OllamaResponse
}
