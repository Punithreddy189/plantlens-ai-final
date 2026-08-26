package com.plantlens.ai.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.io.Serializable

@Entity(tableName = "scan_history")
data class ScanRecord(
    @PrimaryKey val id: String = "",
    val plantId: String = "",
    val plantName: String = "",
    val scientificName: String = "",
    val confidence: Float = 0.0f,
    val timestamp: Long = 0L,
    val imageUrl: String = "", // Deprecated but kept for backward compatibility
    
    // Advanced AI Enhancements
    val originalImageUrl: String = "",
    val croppedLeafImageUrl: String = "",
    val top3Predictions: List<String> = emptyList(),
    val top3Confidences: List<Float> = emptyList(),
    val healthScore: Int = 0,
    val diseaseName: String = "",
    val diseaseConfidence: Float = 0.0f,
    val treatmentRecommendation: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val weatherSyncTime: Long = 0L,
    val imageHash: String = "",
    val family: String = "",
    val genus: String = ""
) : Serializable
