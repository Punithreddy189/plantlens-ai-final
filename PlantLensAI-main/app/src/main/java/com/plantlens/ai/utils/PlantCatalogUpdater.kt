package com.plantlens.ai.utils

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.plantlens.ai.database.PlantDao
import com.plantlens.ai.models.Plant
import java.io.InputStreamReader

object PlantCatalogUpdater {
    private const val TAG = "PlantCatalogUpdater"

    suspend fun updateCatalogIfNeeded(context: Context, plantDao: PlantDao, gson: Gson) {
        try {
            Log.d(TAG, "Updating plant catalog from assets/plants.json...")
            val inputStream = context.assets.open("plants.json")
            val reader = InputStreamReader(inputStream)
            val type = object : TypeToken<List<Plant>>() {}.type
            val plantsList: List<Plant> = gson.fromJson(reader, type)
            reader.close()

            // Update only the seeded plant catalog records
            plantDao.insertPlants(plantsList)
            Log.d(TAG, "Plant catalog successfully updated with ${plantsList.size} items.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update plant catalog: ${e.message}", e)
        }
    }
}
