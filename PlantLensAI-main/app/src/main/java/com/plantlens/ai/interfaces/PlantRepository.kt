package com.plantlens.ai.interfaces

import android.content.Context
import com.plantlens.ai.models.Plant
import com.plantlens.ai.models.SavedPlant
import com.plantlens.ai.network.ClassificationResponse
import com.plantlens.ai.utils.Resource
import kotlinx.coroutines.flow.Flow
import okhttp3.MultipartBody

import com.plantlens.ai.models.ScanRecord

interface PlantRepository {
    fun getAllPlants(): Flow<List<Plant>>
    fun getPlantById(id: String): Flow<Resource<Plant>>
    fun classifyPlantImage(
        imagePart: MultipartBody.Part,
        apiKey: String
    ): Flow<Resource<ClassificationResponse>>

    // Local Scan Cache history
    fun getLocalScanHistory(): Flow<List<ScanRecord>>
    fun getScanHistoryByPlantId(plantId: String): Flow<List<ScanRecord>>
    suspend fun saveScanRecord(scanRecord: ScanRecord)
    fun searchPlants(query: String): Flow<List<Plant>>
    fun getPlantsByCategory(category: String): Flow<List<Plant>>
    
    // Personal Garden (Saved Plants)
    fun getSavedPlants(): Flow<List<SavedPlant>>
    fun savePlant(savedPlant: SavedPlant): Flow<Resource<Unit>>
    fun removeSavedPlant(savedPlant: SavedPlant): Flow<Resource<Unit>>
    fun updateWateringStatus(savedPlantId: String): Flow<Resource<Unit>>
    fun getSavedPlantsCount(): Flow<Int>
    
    // Database seeding
    suspend fun seedDatabaseIfNeeded(context: Context)

    // Disease History
    fun getDiseaseHistoryForPlant(plantId: String): Flow<List<com.plantlens.ai.models.DiseaseHistory>>
    suspend fun saveDiseaseHistory(history: com.plantlens.ai.models.DiseaseHistory)

    // Growth Timeline
    fun getTimelineForPlant(plantId: String): Flow<List<com.plantlens.ai.models.GrowthTimelineEntry>>
    suspend fun saveGrowthTimelineEntry(entry: com.plantlens.ai.models.GrowthTimelineEntry)
    fun getAllGrowthEntries(): Flow<List<com.plantlens.ai.models.GrowthTimelineEntry>>

    // Cache operations
    suspend fun savePlantToCache(plant: Plant)
    suspend fun getScanRecordByHash(imageHash: String): ScanRecord?
    
    // Weather System
    fun getWeatherData(latitude: Double, longitude: Double): Flow<Resource<com.plantlens.ai.models.WeatherRecord>>
}
