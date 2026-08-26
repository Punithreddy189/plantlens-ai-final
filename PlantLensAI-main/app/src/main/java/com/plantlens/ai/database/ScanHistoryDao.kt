package com.plantlens.ai.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.plantlens.ai.models.ScanRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface ScanHistoryDao {

    @Query("SELECT * FROM scan_history ORDER BY timestamp DESC")
    fun getAllScanHistory(): Flow<List<ScanRecord>>

    @Query("SELECT * FROM scan_history WHERE id = :id LIMIT 1")
    suspend fun getScanRecordById(id: String): ScanRecord?

    @Query("SELECT * FROM scan_history WHERE plantId = :plantId ORDER BY timestamp ASC")
    fun getScanHistoryByPlantId(plantId: String): Flow<List<ScanRecord>>

    @Query("SELECT * FROM scan_history WHERE imageHash = :imageHash LIMIT 1")
    suspend fun getScanRecordByHash(imageHash: String): ScanRecord?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScanRecord(scanRecord: ScanRecord)

    @Query("DELETE FROM scan_history")
    suspend fun clearScanHistory()
}
