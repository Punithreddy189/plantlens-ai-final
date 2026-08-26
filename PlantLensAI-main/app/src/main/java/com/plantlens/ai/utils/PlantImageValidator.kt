package com.plantlens.ai.utils

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.plantlens.ai.models.Plant
import java.io.InputStreamReader

object PlantImageValidator {
    private const val TAG = "ImageValidation"

    fun validateImages(context: Context, gson: Gson) {
        try {
            Log.d(TAG, "Starting Image Asset Validation...")
            val inputStream = context.assets.open("plants.json")
            val reader = InputStreamReader(inputStream)
            val type = object : TypeToken<List<Plant>>() {}.type
            val plantsList: List<Plant> = gson.fromJson(reader, type)
            reader.close()

            for (plant in plantsList) {
                val drawableName = getDrawableNameFromMapper(plant.name)
                val resourceId = context.resources.getIdentifier(drawableName, "drawable", context.packageName)
                
                if (resourceId != 0 && drawableName != "plantlens_logo") {
                    Log.i(TAG, "✓ ${plant.name} -> $drawableName")
                } else {
                    Log.w(TAG, "✗ ${plant.name} -> drawable missing")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Image validation failed: ${e.message}", e)
        }
    }

    private fun getDrawableNameFromMapper(plantName: String): String {
        return when {
            plantName.equals("Aloe Vera", ignoreCase = true) || plantName.equals("Aloe vera", ignoreCase = true) -> "aloe_vera"
            plantName.equals("Snake Plant", ignoreCase = true) || plantName.equals("Sansevieria trifasciata", ignoreCase = true) -> "snake_plant"
            plantName.equals("Peace Lily", ignoreCase = true) || plantName.equals("Spathiphyllum wallisii", ignoreCase = true) || plantName.equals("Spathiphyllum", ignoreCase = true) -> "peace_lily"
            plantName.equals("Monstera", ignoreCase = true) || plantName.equals("Monstera deliciosa", ignoreCase = true) -> "monstera"
            plantName.equals("Lavender", ignoreCase = true) || plantName.equals("Lavandula angustifolia", ignoreCase = true) || plantName.equals("Lavandula", ignoreCase = true) -> "lavender"
            plantName.equals("Giant Milkweed", ignoreCase = true) || plantName.equals("Calotropis gigantea", ignoreCase = true) -> "giant_milkweed"
            plantName.equals("Touch-me-not", ignoreCase = true) || plantName.equals("Mimosa pudica", ignoreCase = true) -> "touch_me_not"
            else -> "plantlens_logo"
        }
    }
}
