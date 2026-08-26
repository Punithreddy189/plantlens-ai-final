package com.plantlens.ai.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.plantlens.ai.models.SavedPlant
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedPlantDao {

    @Query("SELECT * FROM saved_plants ORDER BY addedDate DESC")
    fun getAllSavedPlants(): Flow<List<SavedPlant>>

    @Query("SELECT * FROM saved_plants WHERE id = :id")
    suspend fun getSavedPlantById(id: String): SavedPlant?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSavedPlant(savedPlant: SavedPlant)

    @Update
    suspend fun updateSavedPlant(savedPlant: SavedPlant)

    @Delete
    suspend fun deleteSavedPlant(savedPlant: SavedPlant)

    @Query("UPDATE saved_plants SET lastWatered = :lastWatered, nextWaterDate = :nextWaterDate WHERE id = :id")
    suspend fun updateWateringDates(id: String, lastWatered: Long, nextWaterDate: Long)

    @Query("SELECT * FROM saved_plants WHERE nextWaterDate <= :currentTime")
    suspend fun getPlantsNeedingWater(currentTime: Long): List<SavedPlant>

    @Query("SELECT COUNT(*) FROM saved_plants")
    fun getSavedPlantsCountFlow(): Flow<Int>

    @Query("SELECT COUNT(*) FROM saved_plants")
    suspend fun getSavedPlantsCount(): Int

    @Query("DELETE FROM saved_plants")
    suspend fun clearAllSavedPlants()
}
