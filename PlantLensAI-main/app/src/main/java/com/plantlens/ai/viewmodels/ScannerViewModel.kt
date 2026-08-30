package com.plantlens.ai.viewmodels

import android.content.Context
import android.graphics.Bitmap
import android.provider.Settings
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.plantlens.ai.analytics.AnalyticsManager
import com.plantlens.ai.firebase.FirebaseManager
import com.plantlens.ai.interfaces.PlantRepository
import com.plantlens.ai.models.Plant
import com.plantlens.ai.models.ScanRecord
import com.plantlens.ai.utils.Resource
import com.plantlens.ai.utils.TFLiteClassifier
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import com.plantlens.ai.BuildConfig
import com.plantlens.ai.utils.TranslationManager

@HiltViewModel
class ScannerViewModel @Inject constructor(
    private val plantRepository: PlantRepository,
    private val classifier: TFLiteClassifier,
    private val analyticsManager: AnalyticsManager,
    private val firebaseManager: FirebaseManager,
    private val diseaseApiService: com.plantlens.ai.network.PlantDiseaseApiService,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val tag = "ScannerViewModel"

    init {
        // Initialize TFLite model components on startup
        classifier.initialize(context)
    }

    data class ScanResult(
        val matchedPlant: Plant,
        val confidence: Float,
        val top3Predictions: List<String> = emptyList(),
        val top3Confidences: List<Float> = emptyList(),
        val healthScore: Int = 100,
        val healthStatus: String = "Healthy",
        val diseaseName: String = "None",
        val diseaseConfidence: Float = 0.0f,
        val diseaseSeverity: String = "N/A",
        val treatmentRecommendation: String = "No treatment required.",
        val temp: Double = 25.0,
        val humidity: Double = 60.0,
        val windSpeed: Double = 10.0,
        val rainProbability: Double = 0.0,
        val uvIndex: Double = 3.0,
        val observations: String = "",
        val recommendations: String = "",
        val assessmentMethod: String = "Health Assessment Engine",
        val soilType: String = "Loamy soil",
        val soilPh: String = "6.0 - 7.0",
        val soilDrainage: String = "Well-drained",
        val soilRecommendation: String = "Mix garden soil with compost and sand.",
        val confidenceReason: String = "Clear leaf morphology and distinct foliar characteristics.",
        
        // Added Developer Benchmark Telemetry
        val cropMode: String = "CENTER_CROP",
        val cropQuality: String = "Good",
        val validationScore: Int = 100,
        val detectionTimeMs: Long = 0,
        val classificationTimeMs: Long = 0,
        val top5CommonNames: List<String> = emptyList(),
        val top5ScientificNames: List<String> = emptyList(),
        val top5Confidences: List<Float> = emptyList(),

        // Persisted Developer Analytics Telemetry
        val analyticsTotalScans: Int = 0,
        val analyticsSuccessScans: Int = 0,
        val analyticsRejectedScans: Int = 0,
        val analyticsAvgConfidence: Float = 0.0f,
        val analyticsAvgTime: Long = 0L,

        val family: String = "",
        val genus: String = "",
        val imageHash: String = ""
    )

    private val _scanState = MutableLiveData<Resource<ScanResult>>()
    val scanState: LiveData<Resource<ScanResult>> = _scanState

    /**
     * Persists scan stats across app restarts using SharedPreferences.
     */
    fun trackScanAttempt(success: Boolean, confidence: Float = 0.0f, totalTimeMs: Long = 0L) {
        val sharedPref = context.getSharedPreferences("plantlens_analytics", Context.MODE_PRIVATE)
        val editor = sharedPref.edit()

        val total = sharedPref.getInt("analytics_total_scans", 0) + 1
        editor.putInt("analytics_total_scans", total)

        if (success) {
            val successCount = sharedPref.getInt("analytics_success_scans", 0) + 1
            editor.putInt("analytics_success_scans", successCount)

            val avgConf = sharedPref.getFloat("analytics_avg_confidence", 0.0f)
            val updatedConf = (avgConf * (successCount - 1) + confidence) / successCount
            editor.putFloat("analytics_avg_confidence", updatedConf)

            val avgTime = sharedPref.getLong("analytics_avg_time", 0L)
            val updatedTime = (avgTime * (successCount - 1) + totalTimeMs) / successCount
            editor.putLong("analytics_avg_time", updatedTime)
        } else {
            val rejectedCount = sharedPref.getInt("analytics_rejected_scans", 0) + 1
            editor.putInt("analytics_rejected_scans", rejectedCount)
        }
        editor.apply()
    }

    fun trackRejectedScan() {
        trackScanAttempt(false)
    }

    private fun calculateSHA256(bitmap: Bitmap): String {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)
        val bytes = stream.toByteArray()
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun getTodayDateString(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        return sdf.format(Date())
    }

    fun processCapturedImage(
        bitmap: Bitmap, 
        latitude: Double = 0.0, 
        longitude: Double = 0.0, 
        confidenceMultiplier: Float = 1.0f,
        cropMode: String = "CENTER_CROP",
        detectionTimeMs: Long = 0,
        validationScore: Int = 100
    ) {
        _scanState.value = Resource.Loading

        viewModelScope.launch(Dispatchers.Default) {
            try {
                val imageHash = calculateSHA256(bitmap)
                Log.d(tag, "Computed SHA-256 hash for scanned image: $imageHash")

                var temp = 25.0
                var humidity = 60.0
                var windSpeed = 10.0
                var rainProb = 0.0
                var uvIndex = 3.0

                if (latitude != 0.0 || longitude != 0.0) {
                    try {
                        plantRepository.getWeatherData(latitude, longitude).collect { resource ->
                            if (resource is Resource.Success) {
                                val weather = resource.data
                                temp = weather.temperature
                                humidity = weather.humidity
                                windSpeed = weather.windSpeed
                                rainProb = weather.rainProbability
                                uvIndex = weather.uvIndex
                            }
                        }
                    } catch (we: Exception) {
                        Log.e(tag, "Failed to load weather: ${we.message}")
                    }
                }

                // 0. Daily API Usage Protection Check
                val currentUser = firebaseManager.getCurrentUser()
                val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
                val uid = if (currentUser != null) currentUser.uid else "guest_$androidId"

                var limit = 20
                if (currentUser != null) {
                    val profile = firebaseManager.fetchUserProfile(currentUser.uid)
                    limit = if (profile?.role == "admin") 100 else 50
                }
                val todayStr = getTodayDateString()

                var usage = firebaseManager.fetchUserUsage(uid)
                if (usage == null) {
                    usage = FirebaseManager.UserUsage(todayScans = 0, cacheHits = 0, plantNetCalls = 0, lastReset = todayStr)
                    firebaseManager.updateUserUsage(uid, usage)
                } else if (usage.lastReset != todayStr) {
                    usage = usage.copy(todayScans = 0, cacheHits = 0, plantNetCalls = 0, lastReset = todayStr)
                    firebaseManager.updateUserUsage(uid, usage)
                }

                if (usage.todayScans >= limit) {
                    withContext(Dispatchers.Main) {
                        _scanState.value = Resource.Error(
                            Exception("You have reached today's scan limit. Please try again tomorrow.")
                        )
                    }
                    return@launch
                }

                // Using BuildConfig.PLANTNET_API_KEY for compatibility, though backend handles the keys securely
                val apiKey = BuildConfig.PLANTNET_API_KEY

                // Cache the image to a temporary file for multipart upload
                val cacheFile = File(context.cacheDir, "scan_upload_${System.currentTimeMillis()}.jpg")
                withContext(Dispatchers.IO) {
                    val fos = FileOutputStream(cacheFile)
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 95, fos)
                    fos.flush()
                    fos.close()
                }

                // Prepare standard multipart payload using PlantLensImageUploader (1920px max, ARGB_8888, 90% JPEG)
                val body = com.plantlens.ai.network.PlantLensImageUploader.prepareImagePayload(cacheFile)

                val startTime = System.currentTimeMillis()

                // Route through secure unified FastAPI backend gateway pipeline
                val currentLanguage = TranslationManager.getCurrentLanguageName()
                plantRepository.classifyPlantImage(body, apiKey, currentLanguage).collect { resource ->
                    when (resource) {
                        is Resource.Loading -> {
                            // Handled by fragment showing loading overlay
                        }
                        is Resource.Success -> {
                            val response = resource.data
                            val latency = System.currentTimeMillis() - startTime
                            
                            // Increment daily usage count in Firestore (PlantNet Calls + scans)
                            val newUsage = usage.copy(todayScans = usage.todayScans + 1, plantNetCalls = usage.plantNetCalls + 1)
                            firebaseManager.updateUserUsage(uid, newUsage)
                            firebaseManager.incrementGlobalCounter("totalScans", 1L)
                            firebaseManager.incrementGlobalCounter("plantNetCalls", 1L)

                            val isSuccess = response.success && response.is_plant
                            if (!isSuccess) {
                                trackScanAttempt(false)
                                withContext(Dispatchers.Main) {
                                    val msg = if (response.error_message.isNotBlank()) {
                                        response.error_message
                                    } else {
                                        "No plant detected. Please scan a clear photo of a plant, flower, or leaf."
                                    }
                                    _scanState.value = Resource.Error(Exception(msg), msg)
                                }
                                return@collect
                            }

                            val sName = if (response.scientific_name.isNotBlank()) response.scientific_name else response.plant_name
                            val cName = if (response.plant_name.isNotBlank()) response.plant_name else "Plant"
                            val confidenceVal = response.confidence.toFloat() * confidenceMultiplier
                            
                            val plantId = sName.lowercase().replace(" ", "_").ifBlank { 
                                cName.lowercase().replace(" ", "_").ifBlank { "plant_${System.currentTimeMillis()}" } 
                            }
                            
                            // Parse watering frequency in days
                            val wateringFreq = parseWateringFrequency(response.watering)
                            val careTipsList = listOf(
                                "Sunlight: ${response.sunlight}",
                                "Watering: ${response.watering}",
                                "Fertilizer: ${response.fertilizer}",
                                "Prevention: ${response.prevention}"
                            )
                            val cat = "Plant"

                            val plant = Plant(
                                id = plantId,
                                name = cName,
                                scientificName = sName,
                                category = cat,
                                wateringFrequency = wateringFreq,
                                wateringInstructions = response.watering,
                                careTips = careTipsList,
                                imageUrl = "",
                                family = "",
                                genus = ""
                            )

                            // Log API response from backend
                            Log.e("FINAL_DEBUG", """
                                PLANT: ${response.plant_name}
                                DISEASE: ${response.disease}
                                SEVERITY: ${response.severity}
                                HEALTH: ${response.health_status}
                            """.trimIndent())
                            Log.d("API_RESPONSE", "ClassificationResponse: plant='${response.plant_name}', disease='${response.disease}', severity='${response.severity}', health_status='${response.health_status}', reason='${response.confidence_reason}'")

                            // Strict Normalization to match Web Output 1:1
                            val rawDisease = response.disease.trim()
                            val isHealthy = rawDisease.isBlank() ||
                                    rawDisease.equals("none", ignoreCase = true) ||
                                    rawDisease.equals("none (healthy plant)", ignoreCase = true) ||
                                    rawDisease.equals("none (healthy foliage)", ignoreCase = true) ||
                                    rawDisease.equals("healthy", ignoreCase = true) ||
                                    rawDisease.equals("no disease", ignoreCase = true) ||
                                    rawDisease.equals("no disease detected", ignoreCase = true) ||
                                    rawDisease.equals("optimal", ignoreCase = true)

                            val finalDiseaseName = rawDisease.ifBlank { if (isHealthy) "None (Healthy Plant)" else "Cercospora Leaf Spot / Early Blight" }
                            val finalHealthStatus = if (isHealthy) "Healthy" else "Diseased"
                            val finalSeverity = if (isHealthy) "None (Optimal)" else response.severity.ifBlank { "Moderate" }
                            val finalHealthScore = response.health_score
                            val finalDiseaseConfidence = if (response.confidence > 0.0) response.confidence.toFloat() else 0.95f
                            val finalTreatment = response.treatment
                            val finalObservations = response.description
                            val finalRecommendations = response.prevention
                            val finalMethod = response.assessment_method.ifBlank { "gemini-2.0-flash" }

                            val top3Names = listOf(cName)
                            val top3Confs = listOf(confidenceVal)

                            val top5Commons = listOf(cName)
                            val top5Scientifics = listOf(sName)
                            val top5Confidences = listOf(confidenceVal)

                            val scanRecord = ScanRecord(
                                id = UUID.randomUUID().toString(),
                                plantId = plantId,
                                plantName = cName,
                                scientificName = sName,
                                confidence = confidenceVal,
                                timestamp = System.currentTimeMillis(),
                                imageHash = imageHash,
                                family = "",
                                genus = "",
                                top3Predictions = top3Names,
                                top3Confidences = top3Confs,
                                healthScore = finalHealthScore,
                                diseaseName = finalDiseaseName,
                                diseaseConfidence = finalDiseaseConfidence,
                                treatmentRecommendation = finalTreatment,
                                latitude = latitude,
                                longitude = longitude
                            )

                            // Save locally in Room
                            plantRepository.savePlantToCache(plant)
                            plantRepository.saveScanRecord(scanRecord)

                            // Cache globally in Firestore
                            firebaseManager.uploadPlantToGlobalCache(plant)
                            firebaseManager.uploadScanRecord(scanRecord)
                            firebaseManager.uploadScanToHashCache(imageHash, scanRecord)
                            
                            // Increment top plants counter in global document
                            firebaseManager.incrementGlobalCounter("topPlants.$cName", 1L)

                            val totalTime = detectionTimeMs + latency
                            trackScanAttempt(isSuccess, confidenceVal, totalTime)

                            val sharedPref = context.getSharedPreferences("plantlens_analytics", Context.MODE_PRIVATE)
                            val totalScans = sharedPref.getInt("analytics_total_scans", 0)
                            val successScans = sharedPref.getInt("analytics_success_scans", 0)
                            val rejectedScans = sharedPref.getInt("analytics_rejected_scans", 0)
                            val avgConfidence = sharedPref.getFloat("analytics_avg_confidence", 0.0f)
                            val avgTime = sharedPref.getLong("analytics_avg_time", 0L)

                            val scanResult = ScanResult(
                                matchedPlant = plant,
                                confidence = confidenceVal,
                                top3Predictions = top3Names,
                                top3Confidences = top3Confs,
                                healthScore = finalHealthScore,
                                healthStatus = finalHealthStatus,
                                diseaseName = finalDiseaseName,
                                diseaseConfidence = finalDiseaseConfidence,
                                diseaseSeverity = finalSeverity,
                                treatmentRecommendation = finalTreatment,
                                temp = temp,
                                humidity = humidity,
                                windSpeed = windSpeed,
                                rainProbability = rainProb,
                                uvIndex = uvIndex,
                                observations = finalObservations,
                                recommendations = finalRecommendations,
                                assessmentMethod = finalMethod,
                                soilType = response.soil_type.ifEmpty { "Loamy soil" },
                                soilPh = response.soil_ph.ifEmpty { "6.0 - 7.0" },
                                soilDrainage = response.soil_drainage.ifEmpty { "Well-drained" },
                                soilRecommendation = response.soil_recommendation.ifEmpty { "Mix garden soil with compost and sand." },
                                confidenceReason = response.confidence_reason.ifEmpty { "Clear leaf venation and morphology." },
                                cropMode = cropMode,
                                cropQuality = if (validationScore > 80) "Excellent" else "Good",
                                validationScore = validationScore,
                                detectionTimeMs = detectionTimeMs,
                                classificationTimeMs = latency,
                                top5CommonNames = top5Commons,
                                top5ScientificNames = top5Scientifics,
                                top5Confidences = top5Confidences,
                                analyticsTotalScans = totalScans,
                                analyticsSuccessScans = successScans,
                                analyticsRejectedScans = rejectedScans,
                                analyticsAvgConfidence = avgConfidence,
                                analyticsAvgTime = avgTime,
                                family = "",
                                genus = "",
                                imageHash = imageHash
                            )

                            withContext(Dispatchers.Main) {
                                _scanState.value = Resource.Success(scanResult)
                            }
                        }
                        is Resource.Error -> {
                            withContext(Dispatchers.Main) {
                                _scanState.value = Resource.Error(resource.exception, resource.message)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(tag, "Scan pipeline error: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    _scanState.value = Resource.Error(e, "Connection failed: ${e.localizedMessage ?: "Server unreachable"}")
                }
            } finally {
                // Ensure state is not left in loading if an unhandled completion or cancellation occurs
                if (_scanState.value is Resource.Loading) {
                    withContext(Dispatchers.Main) {
                        _scanState.value = Resource.Error(Exception("Request timed out or cancelled"), "Server unreachable or request timed out.")
                    }
                }
            }
        }
    }

    private fun parseWateringFrequency(watering: String): Int {
        val numbers = Regex("\\d+").findAll(watering).map { it.value.toInt() }.toList()
        return if (numbers.isNotEmpty()) {
            numbers.first()
        } else {
            7 // default fallback to 7 days
        }
    }

    fun resetScanner() {
        _scanState.value = Resource.Loading
    }
}
