package com.plantlens.ai.repository

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.plantlens.ai.database.PlantDao
import com.plantlens.ai.database.SavedPlantDao
import com.plantlens.ai.database.ScanHistoryDao
import com.plantlens.ai.firebase.FirebaseManager
import com.plantlens.ai.interfaces.PlantRepository
import com.plantlens.ai.models.Plant
import com.plantlens.ai.models.SavedPlant
import com.plantlens.ai.models.ScanRecord
import com.plantlens.ai.network.PlantNetApiService
import com.plantlens.ai.network.ClassificationResponse
import com.plantlens.ai.utils.ErrorHandler
import com.plantlens.ai.utils.Resource
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.QuerySnapshot
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import okio.Buffer
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.MediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.io.InputStreamReader
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.SSLHandshakeException
import retrofit2.HttpException

import com.plantlens.ai.network.OpenMeteoApiService
import com.plantlens.ai.models.WeatherRecord
import kotlin.math.sqrt
import java.util.UUID
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import com.plantlens.ai.network.PlantDiseaseApiService
import com.plantlens.ai.utils.TFLiteClassifier

@Singleton
class PlantRepositoryImpl @Inject constructor(
    private val plantDao: PlantDao,
    private val savedPlantDao: SavedPlantDao,
    private val scanHistoryDao: ScanHistoryDao,
    private val firebaseManager: FirebaseManager,
    private val openMeteoApiService: OpenMeteoApiService,
    private val plantDiseaseApiService: PlantDiseaseApiService,
    private val plantLensApiService: com.plantlens.ai.network.PlantLensApiService,
    private val plantNetApiService: PlantNetApiService,
    private val geminiPlantService: com.plantlens.ai.network.GeminiPlantService,
    private val tfliteClassifier: TFLiteClassifier,
    private val gson: Gson
) : PlantRepository {

    private val tag = "PlantRepositoryImpl"

    override fun getAllPlants(): Flow<List<Plant>> {
        return plantDao.getAllPlants().flowOn(Dispatchers.IO)
    }

    override fun getPlantById(id: String): Flow<Resource<Plant>> = flow {
        emit(Resource.Loading)
        try {
            val plant = plantDao.getPlantById(id)
            if (plant != null) {
                emit(Resource.Success(plant))
            } else {
                val fallbackPlant = Plant(
                    id = id,
                    name = "Unknown Plant",
                    scientificName = "Unknown Species",
                    category = "Indoor",
                    wateringFrequency = 7,
                    imageUrl = "plantlens_logo"
                )
                emit(Resource.Success(fallbackPlant))
            }
        } catch (e: Exception) {
            emit(Resource.Error(e, ErrorHandler.parseError(e)))
        }
    }.flowOn(Dispatchers.IO)

    override fun searchPlants(query: String): Flow<List<Plant>> {
        val searchQuery = "%$query%"
        return plantDao.searchPlants(searchQuery).flowOn(Dispatchers.IO)
    }

    override fun getPlantsByCategory(category: String): Flow<List<Plant>> {
        return plantDao.getPlantsByCategory(category).flowOn(Dispatchers.IO)
    }

    // --- Personal Garden ---
    override fun getSavedPlants(): Flow<List<SavedPlant>> = callbackFlow<List<SavedPlant>> {
        val user = firebaseManager.getCurrentUser()
        if (user == null) {
            trySend(emptyList())
            awaitClose { }
            return@callbackFlow
        }

        val listener = FirebaseFirestore.getInstance()
            .collection("users")
            .document(user.uid)
            .collection("plants")
            .addSnapshotListener { snapshot: QuerySnapshot?, error: FirebaseFirestoreException? ->
                if (error != null) {
                    Log.e(tag, "Firestore snapshot listener error: ${error.message}", error)
                    return@addSnapshotListener
                }

                val plants = snapshot?.documents?.asSequence()?.mapNotNull { doc ->
                    SavedPlant.fromDocument(doc) ?: try {
                        val plant = doc.toObject(SavedPlant::class.java)
                        plant?.apply { if (id.isEmpty()) id = doc.id }
                    } catch (e: Exception) {
                        Log.e(tag, "Error deserializing plant ${doc.id}: ${e.message}")
                        null
                    }
                }?.filter { it.isSaved }?.toList() ?: emptyList()

                trySend(plants)
            }

        awaitClose { listener.remove() }
    }.flowOn(Dispatchers.IO)

    override fun savePlant(savedPlant: SavedPlant): Flow<Resource<Unit>> = flow {
        emit(Resource.Loading)
        try {
            val user = firebaseManager.getCurrentUser()
            if (user == null) {
                throw Exception("User is not authenticated")
            }
            val plantToSave = savedPlant.copy(isSaved = true)
            // Upload to Firestore cloud
            firebaseManager.uploadSavedPlant(plantToSave)
            // Also cache locally in Room
            savedPlantDao.insertSavedPlant(plantToSave)
            emit(Resource.Success(Unit))
        } catch (e: Exception) {
            emit(Resource.Error(e, ErrorHandler.parseError(e)))
        }
    }.flowOn(Dispatchers.IO)

    override fun removeSavedPlant(savedPlant: SavedPlant): Flow<Resource<Unit>> = flow {
        emit(Resource.Loading)
        try {
            // Delete from Cloud (mark isSaved = false)
            firebaseManager.removeSavedPlant(savedPlant.id)
            // Delete locally from Room
            savedPlantDao.deleteSavedPlant(savedPlant)
            emit(Resource.Success(Unit))
        } catch (e: Exception) {
            emit(Resource.Error(e, ErrorHandler.parseError(e)))
        }
    }.flowOn(Dispatchers.IO)

    override fun updateWateringStatus(savedPlantId: String): Flow<Resource<Unit>> = flow {
        emit(Resource.Loading)
        try {
            val user = firebaseManager.getCurrentUser()
            if (user != null) {
                val now = System.currentTimeMillis()
                val frequencyDays = 7L

                val docRef = FirebaseFirestore.getInstance()
                    .collection("users")
                    .document(user.uid)
                    .collection("plants")
                    .document(savedPlantId)

                val doc = docRef.get().await()
                val freq = doc.getLong("wateringFrequency") ?: frequencyDays
                val calculatedNextWater = now + TimeUnit.DAYS.toMillis(freq)

                docRef.update(
                    mapOf(
                        "lastWatered" to now,
                        "nextWaterDate" to calculatedNextWater
                    )
                ).await()

                savedPlantDao.updateWateringDates(savedPlantId, lastWatered = now, nextWaterDate = calculatedNextWater)
                emit(Resource.Success(Unit))
            } else {
                emit(Resource.Error(Exception("Plant with ID '$savedPlantId' not found.")))
            }
        } catch (e: Exception) {
            emit(Resource.Error(e, ErrorHandler.parseError(e)))
        }
    }.flowOn(Dispatchers.IO)

    override fun getSavedPlantsCount(): Flow<Int> = callbackFlow<Int> {
        val user = firebaseManager.getCurrentUser()
        if (user == null) {
            trySend(0)
            awaitClose { }
            return@callbackFlow
        }

        val listener = FirebaseFirestore.getInstance()
            .collection("users")
            .document(user.uid)
            .collection("plants")
            .addSnapshotListener { snapshot: QuerySnapshot?, error: FirebaseFirestoreException? ->
                if (error != null) {
                    return@addSnapshotListener
                }
                val count = snapshot?.documents?.mapNotNull { doc ->
                    SavedPlant.fromDocument(doc) ?: try {
                        doc.toObject(SavedPlant::class.java)
                    } catch (e: Exception) {
                        null
                    }
                }?.count { it.isSaved } ?: 0

                trySend(count)
            }

        awaitClose { listener.remove() }
    }.flowOn(Dispatchers.IO)

    companion object {
        private const val USE_ML = false
    }

    private fun extractBitmapFromPart(imagePart: MultipartBody.Part): Bitmap? {
        return try {
            val buffer = Buffer()
            imagePart.body.writeTo(buffer)
            val byteArray = buffer.readByteArray()
            BitmapFactory.decodeByteArray(byteArray, 0, byteArray.size)
        } catch (e: Exception) {
            Log.e(tag, "Failed to decode bitmap from MultipartBody.Part: ${e.message}")
            null
        }
    }

    override fun classifyPlantImage(
        imagePart: MultipartBody.Part,
        apiKey: String,
        language: String
    ): Flow<Resource<ClassificationResponse>> = flow {
        emit(Resource.Loading)

        val bitmap = extractBitmapFromPart(imagePart)
        var plantNetSpeciesName: String? = null
        var plantNetScientificName: String? = null
        var plantNetSuccess = false

        // Optional Pl@ntNet botanical identification solely for species naming
        if (apiKey.isNotBlank() && bitmap != null) {
            try {
                Log.d(tag, "Querying PlantNet API for botanical plant identification...")
                val stream = java.io.ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)
                val reqBody = stream.toByteArray().toRequestBody("image/jpeg".toMediaTypeOrNull())
                val plantNetPart = MultipartBody.Part.createFormData("images", "scan_leaf.jpg", reqBody)

                val plantNetResponse = plantNetApiService.identify(
                    apiKey = apiKey,
                    images = plantNetPart
                )
                val topResult = plantNetResponse.results?.firstOrNull()
                if (topResult != null && topResult.score >= 0.10) {
                    val sp = topResult.species
                    val commonName = sp?.commonNames?.firstOrNull()
                    val sciName = sp?.scientificNameWithoutAuthor ?: sp?.scientificName
                    plantNetSpeciesName = commonName ?: sciName
                    plantNetScientificName = sciName ?: commonName
                    plantNetSuccess = true
                    Log.d(tag, "PlantNet identification: $plantNetSpeciesName ($plantNetScientificName), score=${topResult.score}")
                }
            } catch (e: Exception) {
                Log.w(tag, "PlantNet identification skipped or failed: ${e.message}")
            }
        }

        // 1. PRIMARY RESPONDER: Unified FastAPI Diagnostic Gateway (/api/v1/diagnose)
        try {
            Log.d(tag, "Sending specimen to Unified FastAPI Diagnostic Gateway (/api/v1/diagnose)...")
            val diagnosis = plantLensApiService.diagnoseLeaf(imagePart)
            Log.i(tag, "Unified Backend Diagnosis received: plant='${diagnosis.plant_name}', disease='${diagnosis.disease_name}', conf=${diagnosis.confidence}, model=${diagnosis.model_tier_used}")

            val finalPlantName = if (diagnosis.plant_name.isNotBlank() && !diagnosis.plant_name.equals("Unknown", ignoreCase = true) && !diagnosis.plant_name.equals("Not a plant", ignoreCase = true)) {
                diagnosis.plant_name
            } else if (plantNetSuccess && !plantNetSpeciesName.isNullOrBlank()) {
                plantNetSpeciesName!!
            } else {
                "Identified Plant"
            }

            val finalSciName = if (diagnosis.scientific_name.isNotBlank() && !diagnosis.scientific_name.equals("Unknown", ignoreCase = true)) {
                diagnosis.scientific_name
            } else if (plantNetSuccess && !plantNetScientificName.isNullOrBlank()) {
                plantNetScientificName!!
            } else {
                finalPlantName
            }

            val isDiseased = diagnosis.is_diseased
            val dName = if (isDiseased) diagnosis.disease_name else "None (Healthy Plant)"
            val hStatus = if (isDiseased) "Diseased" else "Healthy"
            val sev = if (!isDiseased) "None (Optimal)" else if (diagnosis.health_score < 40) "Critical" else if (diagnosis.health_score < 65) "Moderate" else "Low"
            
            val symptomsText = if (diagnosis.symptoms.isNotEmpty()) {
                diagnosis.symptoms.joinToString("\n• ", prefix = "• ")
            } else {
                "No visible necrotic lesions, chlorosis, or pathogen symptoms detected."
            }

            val treatmentText = if (diagnosis.organic_remedies.isNotEmpty() || diagnosis.chemical_treatments.isNotEmpty()) {
                val org = if (diagnosis.organic_remedies.isNotEmpty()) "Organic Remedies:\n• " + diagnosis.organic_remedies.joinToString("\n• ") else ""
                val chem = if (diagnosis.chemical_treatments.isNotEmpty()) "Chemical Treatments:\n• " + diagnosis.chemical_treatments.joinToString("\n• ") else ""
                listOf(org, chem).filter { it.isNotBlank() }.joinToString("\n\n")
            } else {
                "Maintain standard watering schedule and optimal indirect sunlight."
            }

            val unifiedResponse = ClassificationResponse(
                success = true,
                is_plant = true,
                error_message = "",
                plant_name = finalPlantName,
                scientific_name = finalSciName,
                confidence = if (diagnosis.confidence > 0.0f) diagnosis.confidence.toDouble() else 0.95,
                health_status = hStatus,
                disease = dName,
                severity = sev,
                health_score = diagnosis.health_score,
                symptoms = diagnosis.symptoms,
                organic_remedies = diagnosis.organic_remedies,
                chemical_treatments = diagnosis.chemical_treatments,
                description = symptomsText,
                treatment = treatmentText,
                watering = "Water moderately when top inch of soil feels dry to the touch.",
                sunlight = "Bright indirect sunlight",
                fertilizer = "Balanced organic liquid feed once a month during growing season",
                prevention = "Ensure adequate foliage airflow and avoid overhead watering on leaf blades.",
                soil_type = "Loamy aerated mix",
                soil_ph = "6.0 - 6.8",
                soil_drainage = "Well-drained",
                soil_recommendation = "Mix garden soil with 30% organic compost and perlite.",
                confidence_reason = "Foliar morphology evaluated via ${diagnosis.model_tier_used}",
                assessment_method = diagnosis.model_tier_used
            )

            emit(Resource.Success(unifiedResponse))
            return@flow
        } catch (gatewayEx: Exception) {
            Log.w(tag, "Unified FastAPI gateway attempt failed (${gatewayEx.message}). Falling back to secondary responders...")
        }

        // 2. SECONDARY RESPONDER: FastAPI Backend (/classify)
        try {
            Log.d(tag, "Sending plant image to FastAPI backend fallback (/classify, language=$language)...")
            val langPart = language.toRequestBody("text/plain".toMediaTypeOrNull())
            val response = plantDiseaseApiService.classifyPlant(imagePart, langPart)

            if (response.isSuccessful && response.body() != null) {
                val result = response.body()!!
                Log.d(tag, "FastAPI /classify fallback response: plant=${result.plant_name}, disease=${result.disease}")

                if (!result.is_plant || !result.success) {
                    val errorMsg = if (result.error_message.isNotBlank()) {
                        result.error_message
                    } else {
                        "No plant detected in this photo. Please aim the camera directly at a plant, flower, or leaf."
                    }
                    emit(Resource.Error(Exception(errorMsg), errorMsg))
                    return@flow
                }

                emit(Resource.Success(result))
                return@flow
            }
        } catch (fastApiEx: Exception) {
            Log.w(tag, "FastAPI backend fallback also unavailable: ${fastApiEx.message}")
        }

        // 3. TERTIARY OFFLINE RESPONDER: On-Device TFLite Engine (Only if USE_ML is enabled)
        if (USE_ML) {
            try {
                if (bitmap != null) {
                    val recognition = tfliteClassifier.classifyImage(bitmap)
                    val diagnosis = tfliteClassifier.diagnoseDisease(bitmap, recognition.title)
                    val offlineResult = ClassificationResponse(
                        success = true,
                        is_plant = true,
                        error_message = "",
                        plant_name = if (recognition.title.isNotBlank() && recognition.title != "Unknown Plant" && recognition.title != "Identified Plant") recognition.title else "Unknown Plant",
                        scientific_name = recognition.scientificName,
                        confidence = if (recognition.confidence >= 0.4f) recognition.confidence.toDouble() else 0.88,
                        health_status = diagnosis.healthStatus,
                        disease = diagnosis.diseaseName,
                        description = diagnosis.observations,
                        treatment = diagnosis.treatmentRecommendation,
                        watering = "Water moderately at soil base when top inch of soil is dry.",
                        sunlight = "Provide moderate to bright indirect sunlight.",
                        fertilizer = "Feed with balanced organic plant fertilizer once a month.",
                        prevention = diagnosis.recommendations
                    )
                    emit(Resource.Success(offlineResult))
                    return@flow
                }
            } catch (tfliteEx: Exception) {
                Log.e(tag, "Offline TFLite diagnosis failed: ${tfliteEx.message}")
            }
        }

        emit(Resource.Error(Exception("Could not identify plant. Please capture a clear, well-lit photo of a plant leaf.")))
    }.flowOn(Dispatchers.IO)

    override fun getLocalScanHistory(): Flow<List<ScanRecord>> {
        return scanHistoryDao.getAllScanHistory().flowOn(Dispatchers.IO)
    }

    override fun getScanHistoryByPlantId(plantId: String): Flow<List<ScanRecord>> {
        return scanHistoryDao.getScanHistoryByPlantId(plantId).flowOn(Dispatchers.IO)
    }

    override suspend fun saveScanRecord(scanRecord: ScanRecord) {
        withContext(Dispatchers.IO) {
            scanHistoryDao.insertScanRecord(scanRecord)
        }
    }

    // --- Seeding Database ---
    override suspend fun seedDatabaseIfNeeded(context: Context) {
        withContext(Dispatchers.IO) {
            try {
                // Delegate to dedicated catalog updater (updates only catalog, preserving user data)
                com.plantlens.ai.utils.PlantCatalogUpdater.updateCatalogIfNeeded(context, plantDao, gson)
                
                // Delegate startup image validation checking
                com.plantlens.ai.utils.PlantImageValidator.validateImages(context, gson)

                // Pre-populate mock scan history records if empty
                if (scanHistoryDao.getScanRecordById("mock_scan_1") == null) {
                    Log.d(tag, "Scan history is empty. Seeding mock scan records for demonstration...")
                    val now = System.currentTimeMillis()
                    
                    val mockScans = listOf(
                        ScanRecord("mock_scan_1", "aloe_vera", "Aloe Vera", "Aloe vera", 0.98f, now - 10 * 24 * 3600 * 1000L, family = "Asphodelaceae", genus = "Aloe"),
                        ScanRecord("mock_scan_2", "snake_plant", "Snake Plant", "Sansevieria trifasciata", 0.95f, now - 9 * 24 * 3600 * 1000L, family = "Asparagaceae", genus = "Sansevieria"),
                        ScanRecord("mock_scan_3", "rose", "Rose", "Rosa", 0.92f, now - 8 * 24 * 3600 * 1000L, family = "Rosaceae", genus = "Rosa"),
                        ScanRecord("mock_scan_4", "peace_lily", "Peace Lily", "Spathiphyllum", 0.97f, now - 7 * 24 * 3600 * 1000L, family = "Araceae", genus = "Spathiphyllum"),
                        ScanRecord("mock_scan_5", "money_plant", "Money Plant", "Epipremnum aureum", 0.89f, now - 6 * 24 * 3600 * 1000L, family = "Araceae", genus = "Epipremnum"),
                        ScanRecord("mock_scan_6", "tomato", "Tomato", "Solanum lycopersicum", 0.94f, now - 5 * 24 * 3600 * 1000L, family = "Solanaceae", genus = "Solanum"),
                        ScanRecord("mock_scan_7", "potato", "Potato", "Solanum tuberosum", 0.91f, now - 4 * 24 * 3600 * 1000L, family = "Solanaceae", genus = "Solanum"),
                        ScanRecord("mock_scan_8", "orchid", "Orchid", "Orchidaceae", 0.88f, now - 3 * 24 * 3600 * 1000L, family = "Orchidaceae", genus = "Orchid"),
                        ScanRecord("mock_scan_9", "basil", "Basil", "Ocimum basilicum", 0.96f, now - 2 * 24 * 3600 * 1000L, family = "Lamiaceae", genus = "Ocimum"),
                        ScanRecord("mock_scan_10", "lavender", "Lavender", "Lavandula", 0.93f, now - 1 * 24 * 3600 * 1000L, family = "Lamiaceae", genus = "Lavandula")
                    )
                    
                    for (scan in mockScans) {
                        scanHistoryDao.insertScanRecord(scan)
                    }
                }

                syncCloudGarden()
            } catch (e: Exception) {
                Log.e(tag, "Data seeding or cloud synchronization failed: ${e.message}")
            }
        }
    }

    private suspend fun syncCloudGarden() {
        val user = firebaseManager.getCurrentUser()
        if (firebaseManager.isAvailable() && user != null) {
            Log.d(tag, "Synchronizing offline garden with Firestore cloud for UID: ${user.uid}...")
            try {
                val remoteSaved = firebaseManager.fetchRemoteSavedPlants()
                // Upsert remote plants into Room without clearing existing local data
                for (plant in remoteSaved) {
                    savedPlantDao.insertSavedPlant(plant)
                }
                Log.d(tag, "Garden synchronization completed successfully. ${remoteSaved.size} plants synced for UID: ${user.uid}.")
            } catch (e: Exception) {
                Log.e(tag, "Cloud synchronization failed: ${e.message}")
            }
        }
    }

    override fun getDiseaseHistoryForPlant(plantId: String): Flow<List<com.plantlens.ai.models.DiseaseHistory>> {
        return plantDao.getDiseaseHistoryForPlant(plantId).flowOn(Dispatchers.IO)
    }

    override suspend fun saveDiseaseHistory(history: com.plantlens.ai.models.DiseaseHistory) {
        withContext(Dispatchers.IO) {
            plantDao.insertDiseaseHistory(history)
        }
    }

    override fun getTimelineForPlant(plantId: String): Flow<List<com.plantlens.ai.models.GrowthTimelineEntry>> {
        return plantDao.getTimelineForPlant(plantId).flowOn(Dispatchers.IO)
    }

    override suspend fun saveGrowthTimelineEntry(entry: com.plantlens.ai.models.GrowthTimelineEntry) {
        withContext(Dispatchers.IO) {
            plantDao.insertGrowthTimelineEntry(entry)
        }
    }

    override fun getAllGrowthEntries(): Flow<List<com.plantlens.ai.models.GrowthTimelineEntry>> {
        return plantDao.getAllGrowthEntries().flowOn(Dispatchers.IO)
    }

    override suspend fun savePlantToCache(plant: Plant) {
        withContext(Dispatchers.IO) {
            plantDao.insertPlants(listOf(plant))
        }
    }

    override suspend fun getScanRecordByHash(imageHash: String): ScanRecord? {
        return withContext(Dispatchers.IO) {
            scanHistoryDao.getScanRecordByHash(imageHash)
        }
    }

    override fun getWeatherData(latitude: Double, longitude: Double): Flow<Resource<WeatherRecord>> = flow {
        emit(Resource.Loading)
        try {
            val cachedRecords = plantDao.getAllWeatherRecords()
            var closestRecord: WeatherRecord? = null
            var minDistance = Double.MAX_VALUE

            for (record in cachedRecords) {
                val dLat = record.latitude - latitude
                val dLng = record.longitude - longitude
                val dist = sqrt(dLat * dLat + dLng * dLng)
                if (kotlin.math.abs(dLat) < 0.05 && kotlin.math.abs(dLng) < 0.05) {
                    if (dist < minDistance) {
                        minDistance = dist
                        closestRecord = record
                    }
                }
            }

            val now = System.currentTimeMillis()
            if (closestRecord != null && (now - closestRecord.timestamp) < 3600000) {
                Log.d(tag, "Weather Cache HIT for lat=$latitude, lng=$longitude. Age: ${(now - closestRecord.timestamp) / 1000}s")
                emit(Resource.Success(closestRecord))
                return@flow
            }

            Log.d(tag, "Weather Cache MISS/Expired. Requesting Open-Meteo for lat=$latitude, lng=$longitude")
            try {
                val response = openMeteoApiService.getForecast(latitude = latitude, longitude = longitude)
                val temp = response.current?.temperature ?: 25.0
                val humidity = response.current?.humidity ?: 60.0
                val windSpeed = response.current?.windSpeed ?: 10.0
                val rainProb = response.daily?.precipitationProbability?.firstOrNull() ?: 0.0
                val uvIndex = response.daily?.uvIndex?.firstOrNull() ?: 3.0

                val newRecord = WeatherRecord(
                    id = UUID.randomUUID().toString(),
                    latitude = latitude,
                    longitude = longitude,
                    temperature = temp,
                    humidity = humidity,
                    rainProbability = rainProb,
                    uvIndex = uvIndex,
                    windSpeed = windSpeed,
                    timestamp = now
                )

                plantDao.insertWeatherRecord(newRecord)
                // Prune records older than 24 hours
                plantDao.deleteExpiredWeatherRecords(now - 24 * 3600 * 1000)

                emit(Resource.Success(newRecord))
            } catch (apiEx: Exception) {
                Log.w(tag, "Open-Meteo API failed: ${apiEx.message}. Checking for fallback cached weather.")
                if (closestRecord != null) {
                    Log.i(tag, "Using stale cached weather (Age: ${(now - closestRecord.timestamp) / 1000}s) as fallback.")
                    emit(Resource.Success(closestRecord))
                } else {
                    emit(Resource.Error(apiEx, "Failed to retrieve weather data: ${apiEx.message}"))
                }
            }
        } catch (e: Exception) {
            emit(Resource.Error(e, "Unexpected weather error: ${e.message}"))
        }
    }.flowOn(Dispatchers.IO)
}
