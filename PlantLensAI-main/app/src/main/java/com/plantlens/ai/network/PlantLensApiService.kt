package com.plantlens.ai.network

import com.google.gson.annotations.SerializedName
import okhttp3.MultipartBody
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import java.io.Serializable

data class DiagnosisResponse(
    @SerializedName("plant_name")
    val plant_name: String = "",
    @SerializedName("scientific_name")
    val scientific_name: String = "",
    @SerializedName("is_diseased")
    val is_diseased: Boolean = false,
    @SerializedName("disease_name")
    val disease_name: String = "None (Healthy Plant)",
    @SerializedName("health_score")
    val health_score: Int = 100,
    @SerializedName("confidence")
    val confidence: Float = 0.0f,
    @SerializedName("symptoms")
    val symptoms: List<String> = emptyList(),
    @SerializedName("organic_remedies")
    val organic_remedies: List<String> = emptyList(),
    @SerializedName("chemical_treatments")
    val chemical_treatments: List<String> = emptyList(),
    @SerializedName("model_tier_used")
    val model_tier_used: String = "gemini-2.0-flash",
    @SerializedName("escalation_triggered")
    val escalation_triggered: Boolean = false,
    @SerializedName("initial_fast_confidence")
    val initial_fast_confidence: Float? = null
) : Serializable

interface PlantLensApiService {
    @Multipart
    @POST("api/v1/diagnose")
    suspend fun diagnoseLeaf(
        @Part file: MultipartBody.Part
    ): DiagnosisResponse
}
