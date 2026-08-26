package com.plantlens.ai.utils

import com.plantlens.ai.R

object PlantImageMapper {
    private val imageMap = mapOf(
        "Aloe Vera" to R.drawable.aloe_vera,
        "Snake Plant" to R.drawable.snake_plant,
        "Peace Lily" to R.drawable.peace_lily,
        "Monstera" to R.drawable.monstera,
        "Lavender" to R.drawable.lavender,
        "Giant Milkweed" to R.drawable.giant_milkweed,
        "Touch-me-not" to R.drawable.touch_me_not
    )

    fun getDrawableRes(plantName: String): Int {
        val cleanName = when {
            plantName.equals("Aloe Vera", ignoreCase = true) || plantName.equals("Aloe vera", ignoreCase = true) -> "Aloe Vera"
            plantName.equals("Snake Plant", ignoreCase = true) || plantName.equals("Sansevieria trifasciata", ignoreCase = true) -> "Snake Plant"
            plantName.equals("Peace Lily", ignoreCase = true) || plantName.equals("Spathiphyllum wallisii", ignoreCase = true) || plantName.equals("Spathiphyllum", ignoreCase = true) -> "Peace Lily"
            plantName.equals("Monstera", ignoreCase = true) || plantName.equals("Monstera deliciosa", ignoreCase = true) -> "Monstera"
            plantName.equals("Lavender", ignoreCase = true) || plantName.equals("Lavandula angustifolia", ignoreCase = true) || plantName.equals("Lavandula", ignoreCase = true) -> "Lavender"
            plantName.equals("Giant Milkweed", ignoreCase = true) || plantName.equals("Calotropis gigantea", ignoreCase = true) -> "Giant Milkweed"
            plantName.equals("Touch-me-not", ignoreCase = true) || plantName.equals("Mimosa pudica", ignoreCase = true) -> "Touch-me-not"
            else -> plantName
        }
        return imageMap[cleanName] ?: R.drawable.plantlens_logo
    }
}

