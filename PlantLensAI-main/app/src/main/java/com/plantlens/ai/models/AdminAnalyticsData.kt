package com.plantlens.ai.models

import java.io.Serializable

data class AdminAnalyticsData(
    val totalUsers: Int = 0,
    val totalScans: Int = 0,
    val cacheHits: Int = 0,
    val plantNetCalls: Int = 0,
    val requestsSaved: Int = 0,
    val cacheEfficiency: Float = 0.0f,
    val todayActiveUsers: Int = 0,
    val topPlants: List<Pair<String, Int>> = emptyList()
) : Serializable
