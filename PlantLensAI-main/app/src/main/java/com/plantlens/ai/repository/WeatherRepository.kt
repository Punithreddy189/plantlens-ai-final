package com.plantlens.ai.repository

import com.plantlens.ai.interfaces.PlantRepository
import com.plantlens.ai.models.WeatherRecord
import com.plantlens.ai.utils.Resource
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WeatherRepository @Inject constructor(
    private val plantRepository: PlantRepository
) {
    fun getWeatherData(latitude: Double, longitude: Double): Flow<Resource<WeatherRecord>> {
        return plantRepository.getWeatherData(latitude, longitude)
    }

    fun getWateringRecommendation(weather: WeatherRecord, standardInterval: Int): WateringRecommendation {
        var wateringCycle = standardInterval
        val rulesApplied = mutableListOf<String>()

        if (weather.temperature > 35.0) {
            wateringCycle = (wateringCycle - 2).coerceAtLeast(1)
            rulesApplied.add("☀ Hot conditions detected\nIncrease watering frequency.\nRecommended interval: Every $wateringCycle days.")
        } else if (weather.temperature < 10.0) {
            rulesApplied.add("❄ Cold conditions\nReduce watering frequency.")
        }

        if (weather.rainProbability > 60.0) {
            rulesApplied.add("🌧 Rain expected\nDelay watering.")
        }

        val message = if (rulesApplied.isEmpty()) {
            "Optimal weather conditions. Water every $wateringCycle days."
        } else {
            rulesApplied.joinToString("\n\n")
        }

        return WateringRecommendation(wateringCycle, message)
    }
}

data class WateringRecommendation(
    val recommendedInterval: Int,
    val message: String
)
