package com.plantlens.ai.network

import okhttp3.MultipartBody
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Query
import java.io.Serializable

data class ClassificationResponse(
    val success: Boolean = false,
    val is_plant: Boolean = true,
    val error_message: String = "",
    val plant_name: String = "",
    val scientific_name: String = "",
    val confidence: Double = 0.0,
    val health_status: String = "",
    val disease: String = "",
    val description: String = "",
    val treatment: String = "",
    val watering: String = "",
    val sunlight: String = "",
    val fertilizer: String = "",
    val prevention: String = ""
) : Serializable

data class PlantNetIdentifyResponse(
    val query: Map<String, Any>? = null,
    val results: List<PlantNetResult>? = null,
    val remainingIdentificationRequests: Int? = null
) : Serializable

data class PlantNetResult(
    val score: Double = 0.0,
    val species: PlantNetSpecies? = null
) : Serializable

data class PlantNetSpecies(
    val scientificNameWithoutAuthor: String? = null,
    val scientificNameAuthorship: String? = null,
    val scientificName: String? = null,
    val genus: PlantNetTaxon? = null,
    val family: PlantNetTaxon? = null,
    val commonNames: List<String>? = null
) : Serializable

data class PlantNetTaxon(
    val scientificNameWithoutAuthor: String? = null,
    val scientificName: String? = null
) : Serializable

interface PlantNetApiService {

    @Multipart
    @POST("v2/identify/all")
    suspend fun identify(
        @Query("api-key") apiKey: String,
        @Part images: MultipartBody.Part,
        @Query("include-related-images") includeRelatedImages: Boolean = false
    ): PlantNetIdentifyResponse
}

