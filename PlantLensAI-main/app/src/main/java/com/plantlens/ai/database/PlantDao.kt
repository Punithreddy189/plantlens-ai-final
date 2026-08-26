package com.plantlens.ai.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.plantlens.ai.models.Plant
import com.plantlens.ai.models.GrowthTimelineEntry
import com.plantlens.ai.models.DiseaseHistory
import kotlinx.coroutines.flow.Flow

import com.plantlens.ai.models.WeatherRecord

@Dao
interface PlantDao {

    @Query("SELECT * FROM plants ORDER BY name ASC")
    fun getAllPlants(): Flow<List<Plant>>

    @Query("SELECT * FROM plants WHERE id = :id")
    suspend fun getPlantById(id: String): Plant?

    @Query("SELECT * FROM plants WHERE name LIKE :query OR scientificName LIKE :query ORDER BY name ASC")
    fun searchPlants(query: String): Flow<List<Plant>>

    @Query("SELECT * FROM plants WHERE category = :category ORDER BY name ASC")
    fun getPlantsByCategory(category: String): Flow<List<Plant>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlants(plants: List<Plant>)

    @Query("SELECT COUNT(*) FROM plants")
    suspend fun getPlantsCount(): Int

    // --- Growth Timeline Operations ---
    @Query("SELECT * FROM growth_timeline WHERE plantId = :plantId ORDER BY timestamp DESC")
    fun getTimelineForPlant(plantId: String): Flow<List<GrowthTimelineEntry>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGrowthTimelineEntry(entry: GrowthTimelineEntry)

    @Query("SELECT * FROM growth_timeline ORDER BY timestamp DESC")
    fun getAllGrowthEntries(): Flow<List<GrowthTimelineEntry>>

    // --- Disease History Operations ---
    @Query("SELECT * FROM disease_history WHERE plantId = :plantId ORDER BY timestamp DESC")
    fun getDiseaseHistoryForPlant(plantId: String): Flow<List<DiseaseHistory>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDiseaseHistory(log: DiseaseHistory)

    @Query("SELECT * FROM disease_history ORDER BY timestamp DESC")
    fun getAllDiseaseHistory(): Flow<List<DiseaseHistory>>

    // --- Weather Cache Operations ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWeatherRecord(record: WeatherRecord)

    @Query("SELECT * FROM weather_records ORDER BY timestamp DESC")
    suspend fun getAllWeatherRecords(): List<WeatherRecord>

    @Query("DELETE FROM weather_records WHERE timestamp < :expiryTime")
    suspend fun deleteExpiredWeatherRecords(expiryTime: Long)
}
