package com.plantlens.ai.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.io.Serializable

@Entity(tableName = "growth_timeline")
data class GrowthTimelineEntry(
    @PrimaryKey val id: String = "",
    val plantId: String = "",
    val timestamp: Long = 0L,
    val heightCm: Double = 0.0,
    val healthScore: Int = 0,
    val notes: String = "",
    val imagePath: String = "",
    val thumbnailPath: String = "",
    val assessmentMethod: String = "Health Assessment Engine"
) : Serializable
