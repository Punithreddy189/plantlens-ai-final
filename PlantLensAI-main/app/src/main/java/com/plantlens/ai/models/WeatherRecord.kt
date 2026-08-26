package com.plantlens.ai.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.io.Serializable

@Entity(tableName = "weather_records")
data class WeatherRecord(
    @PrimaryKey val id: String, // e.g., "lat_lng" or unique ID
    val latitude: Double,
    val longitude: Double,
    val temperature: Double,
    val humidity: Double,
    val rainProbability: Double,
    val uvIndex: Double,
    val windSpeed: Double,
    val timestamp: Long
) : Serializable
