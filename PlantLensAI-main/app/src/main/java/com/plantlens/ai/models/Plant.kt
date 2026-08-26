package com.plantlens.ai.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.io.Serializable

@Entity(tableName = "plants")
data class Plant(
    @PrimaryKey val id: String = "",
    val name: String = "",
    val scientificName: String = "",
    val category: String = "",
    val wateringFrequency: Int = 0, // in days
    val wateringInstructions: String = "",
    val advantages: List<String> = emptyList(),
    val disadvantages: List<String> = emptyList(),
    val careTips: List<String> = emptyList(),
    val imageUrl: String = "",
    val family: String = "",
    val genus: String = "",
    val uses: List<String> = emptyList(),
    val localNames: Map<String, String> = emptyMap()
) : Serializable
