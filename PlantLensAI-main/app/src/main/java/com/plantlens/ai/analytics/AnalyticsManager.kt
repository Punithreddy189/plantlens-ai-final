package com.plantlens.ai.analytics

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.plantlens.ai.firebase.FirebaseManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AnalyticsManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val firebaseManager: FirebaseManager,
    private val gson: Gson
) {
    private val PREF_NAME = "plantlens_analytics"
    private val KEY_TOTAL_SCANS = "total_scans"
    private val KEY_MOST_SCANNED = "most_scanned"
    private val KEY_MOST_VIEWED = "most_viewed"
    private val KEY_SEARCH_FREQ = "search_freq"
    private val KEY_LAST_SCAN_DATE = "last_scan_date"
    private val KEY_SCAN_COUNTS = "scan_counts"
    private val KEY_VIEW_COUNTS = "view_counts"

    private val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    private val scope = CoroutineScope(Dispatchers.IO)

    fun trackScan(plantName: String) {
        val totalScans = prefs.getInt(KEY_TOTAL_SCANS, 0) + 1
        val lastScanDate = System.currentTimeMillis()

        // Update counts per plant
        val scanCountsJson = prefs.getString(KEY_SCAN_COUNTS, "{}")
        val type = object : TypeToken<MutableMap<String, Int>>() {}.type
        val scanCounts: MutableMap<String, Int> = gson.fromJson(scanCountsJson, type)
        scanCounts[plantName] = (scanCounts[plantName] ?: 0) + 1

        // Find most scanned plant
        val mostScanned = scanCounts.maxByOrNull { it.value }?.key ?: "None"

        // Save locally
        prefs.edit().apply {
            putInt(KEY_TOTAL_SCANS, totalScans)
            putLong(KEY_LAST_SCAN_DATE, lastScanDate)
            putString(KEY_SCAN_COUNTS, gson.toJson(scanCounts))
            putString(KEY_MOST_SCANNED, mostScanned)
            apply()
        }

        // Sync with Firebase asynchronously
        scope.launch {
            val user = firebaseManager.getCurrentUser()
            if (user != null) {
                val savedCount = firebaseManager.fetchRemoteSavedPlants().size
                firebaseManager.updateUserStatistics(
                    user.uid,
                    scansCount = totalScans,
                    savedCount = savedCount,
                    lastScan = lastScanDate,
                    mostScanned = mostScanned,
                )
                firebaseManager.logAnalyticsEvent(
                    "scan_tracked",
                    mapOf(
                        "plant_name" to plantName,
                        "total_scans" to totalScans,
                    ),
                )
            }
        }
    }

    fun trackView(plantName: String) {
        val viewCountsJson = prefs.getString(KEY_VIEW_COUNTS, "{}")
        val type = object : TypeToken<MutableMap<String, Int>>() {}.type
        val viewCounts: MutableMap<String, Int> = gson.fromJson(viewCountsJson, type)
        viewCounts[plantName] = (viewCounts[plantName] ?: 0) + 1

        val mostViewed = viewCounts.maxByOrNull { it.value }?.key ?: "None"

        prefs.edit().apply {
            putString(KEY_VIEW_COUNTS, gson.toJson(viewCounts))
            putString(KEY_MOST_VIEWED, mostViewed)
            apply()
        }

        scope.launch {
            firebaseManager.logAnalyticsEvent(
                "plant_viewed",
                mapOf("plant_name" to plantName),
            )
        }
    }

    fun trackSearch(searchTerm: String) {
        if (searchTerm.trim().isEmpty()) return
        val term = searchTerm.trim().lowercase()

        val searchFreqJson = prefs.getString(KEY_SEARCH_FREQ, "{}")
        val type = object : TypeToken<MutableMap<String, Int>>() {}.type
        val searchFreq: MutableMap<String, Int> = gson.fromJson(searchFreqJson, type)
        searchFreq[term] = (searchFreq[term] ?: 0) + 1

        prefs.edit().apply {
            putString(KEY_SEARCH_FREQ, gson.toJson(searchFreq))
            apply()
        }

        scope.launch {
            firebaseManager.logAnalyticsEvent(
                "search_performed",
                mapOf("search_term" to term),
            )
        }
    }

    fun getTotalScans(): Int = prefs.getInt(KEY_TOTAL_SCANS, 0)
    
    fun getMostScannedPlant(): String = prefs.getString(KEY_MOST_SCANNED, "None") ?: "None"
    
    fun getMostViewedPlant(): String = prefs.getString(KEY_MOST_VIEWED, "None") ?: "None"
    
    fun getLastScanDate(): Long = prefs.getLong(KEY_LAST_SCAN_DATE, 0L)
    
    fun getSearchFrequencies(): Map<String, Int> {
        val json = prefs.getString(KEY_SEARCH_FREQ, "{}")
        val type = object : TypeToken<Map<String, Int>>() {}.type
        return gson.fromJson(json, type) ?: emptyMap()
    }
}
