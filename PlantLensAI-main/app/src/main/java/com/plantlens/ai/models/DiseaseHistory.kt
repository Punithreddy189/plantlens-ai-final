package com.plantlens.ai.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.io.Serializable

@Entity(tableName = "disease_history")
data class DiseaseHistory(
    @PrimaryKey val id: String = "",
    val plantId: String = "",
    val diseaseName: String = "",
    val severity: String = "", // e.g. "Healthy", "Mild", "Moderate", "Severe"
    val confidence: Float = 0.0f,
    val treatment: String = "",
    val timestamp: Long = 0L,
    val healthScore: Int = 100,
    val healthStatus: String = "🟢 Healthy",
    val observations: String = "",
    val recommendations: String = "",
    val assessmentMethod: String = "Health Assessment Engine",
    val plantName: String = ""
) : Serializable
