package com.plantlens.ai.network

import com.google.gson.annotations.SerializedName
import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

data class DiseasePredictionResponse(
    @SerializedName("disease")
    val disease: String,

    @SerializedName("confidence")
    val confidence: String,

    @SerializedName("is_plant")
    val isPlant: Boolean = true,

    @SerializedName("message")
    val message: String? = null
)

interface PlantDiseaseApiService {
    @Multipart
    @POST("classify")
    suspend fun classifyPlant(
        @Part image: MultipartBody.Part,
        @Part("language") language: okhttp3.RequestBody? = null
    ): Response<ClassificationResponse>

    @Multipart
    @POST("predict")
    suspend fun predictDisease(
        @Part file: MultipartBody.Part
    ): Response<DiseasePredictionResponse>
}

