package com.plantlens.ai.viewmodels

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.plantlens.ai.analytics.AnalyticsManager
import com.plantlens.ai.interfaces.AuthRepository
import com.plantlens.ai.interfaces.PlantRepository
import com.plantlens.ai.models.Plant
import com.plantlens.ai.models.SavedPlant
import com.plantlens.ai.models.User
import com.plantlens.ai.models.GrowthTimelineEntry
import com.plantlens.ai.utils.ReminderManager
import com.plantlens.ai.utils.Resource
import com.plantlens.ai.firebase.FirebaseManager
import com.plantlens.ai.models.AdminAnalyticsData
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltViewModel
class PlantViewModel @Inject constructor(
    private val plantRepository: PlantRepository,
    private val authRepository: AuthRepository,
    private val analyticsManager: AnalyticsManager,
    private val reminderManager: ReminderManager,
    private val firebaseManager: FirebaseManager,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    // Seeding trigger
    init {
        viewModelScope.launch {
            plantRepository.seedDatabaseIfNeeded(context)
        }
    }

    // --- Authentication states ---
    val currentUser: User?
        get() = authRepository.getCurrentUser()

    // --- Offline Search, Sort, and Filtering System ---
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _selectedCategory = MutableStateFlow("All")
    val selectedCategory: StateFlow<String> = _selectedCategory

    private val _sortAlphabetical = MutableStateFlow(value = true) // true = ASC, false = DSC

    // Combines search query, category, and sort flags for immediate offline updates
    val plantsList: LiveData<List<Plant>> = combine(
        plantRepository.getAllPlants(),
        _searchQuery,
        _selectedCategory,
        _sortAlphabetical
    ) { allPlants, query, category, sortAsc ->
        var filtered = allPlants

        // Filter by search terms
        if (query.isNotEmpty()) {
            filtered = filtered.filter {
                it.name.contains(query, ignoreCase = true) ||
                        it.scientificName.contains(query, ignoreCase = true)
            }
        }

        // Filter by category selection
        if (category != "All") {
            filtered = filtered.filter { it.category == category }
        }

        // Sort alphabetically
        filtered = if (sortAsc) {
            filtered.sortedBy { it.name.lowercase() }
        } else {
            filtered.sortedByDescending { it.name.lowercase() }
        }

        filtered
    }.asLiveData(viewModelScope.coroutineContext)

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        analyticsManager.trackSearch(query)
    }

    fun setCategoryFilter(category: String) {
        _selectedCategory.value = category
    }

    fun toggleSorting() {
        _sortAlphabetical.value = !_sortAlphabetical.value
    }

    // --- Plant Detail Screen ---
    private val _selectedPlantState = MutableLiveData<Resource<Plant>>()
    val selectedPlantState: LiveData<Resource<Plant>> = _selectedPlantState

    fun loadPlantDetails(plantId: String) {
        viewModelScope.launch {
            plantRepository.getPlantById(plantId).collect { resource ->
                _selectedPlantState.value = resource
                (resource as? Resource.Success)?.data?.name?.let { name ->
                    analyticsManager.trackView(name)
                }
            }
        }
    }

    private val _weatherState = MutableLiveData<Resource<com.plantlens.ai.models.WeatherRecord>>()
    val weatherState: LiveData<Resource<com.plantlens.ai.models.WeatherRecord>> = _weatherState

    fun loadWeatherForLocation(latitude: Double, longitude: Double) {
        _weatherState.value = Resource.Loading
        viewModelScope.launch {
            plantRepository.getWeatherData(latitude, longitude).collect { resource ->
                _weatherState.value = resource
            }
        }
    }

    // --- Personal Garden Management ---
    val savedPlantsList: LiveData<List<SavedPlant>> = plantRepository.getSavedPlants().asLiveData(viewModelScope.coroutineContext)
    val savedPlantsCount: LiveData<Int> = plantRepository.getSavedPlantsCount().asLiveData(viewModelScope.coroutineContext)

    private val _gardenOperationState = MutableLiveData<Resource<Unit>?>()
    val gardenOperationState: LiveData<Resource<Unit>?> = _gardenOperationState

    fun addPlantToGarden(plant: Plant, nickname: String) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val frequencyMs = TimeUnit.DAYS.toMillis(plant.wateringFrequency.toLong())
            val nextWater = now + frequencyMs

            val savedPlant = SavedPlant(
                id = UUID.randomUUID().toString(),
                plantId = plant.id,
                plantName = plant.name,
                scientificName = plant.scientificName,
                nickname = nickname.ifEmpty { plant.name },
                addedDate = now,
                lastWatered = now,
                nextWaterDate = nextWater,
                wateringFrequency = plant.wateringFrequency
            )

            plantRepository.savePlant(savedPlant).collect {
                _gardenOperationState.value = it
                if (it is Resource.Success) {
                    // Schedule exact watering reminders
                    reminderManager.scheduleWateringReminder(savedPlant)
                }
            }
        }
    }

    fun addDiseaseHistory(
        plantId: String,
        diseaseName: String,
        severity: String,
        confidence: Float,
        treatment: String,
        healthScore: Int,
        healthStatus: String,
        observations: String,
        recommendations: String,
        assessmentMethod: String,
        plantName: String
    ) {
        viewModelScope.launch {
            val log = com.plantlens.ai.models.DiseaseHistory(
                id = UUID.randomUUID().toString(),
                plantId = plantId,
                diseaseName = diseaseName,
                severity = severity,
                confidence = confidence,
                treatment = treatment,
                timestamp = System.currentTimeMillis(),
                healthScore = healthScore,
                healthStatus = healthStatus,
                observations = observations,
                recommendations = recommendations,
                assessmentMethod = assessmentMethod,
                plantName = plantName
            )
            plantRepository.saveDiseaseHistory(log)
        }
    }

    fun getDiseaseHistoryForPlant(plantId: String): LiveData<List<com.plantlens.ai.models.DiseaseHistory>> {
        return plantRepository.getDiseaseHistoryForPlant(plantId).asLiveData(viewModelScope.coroutineContext)
    }

    fun removePlantFromGarden(savedPlant: SavedPlant) {
        viewModelScope.launch {
            plantRepository.removeSavedPlant(savedPlant).collect {
                _gardenOperationState.value = it
                if (it is Resource.Success) {
                    // Cancel scheduled alarms
                    reminderManager.cancelWateringReminder(savedPlant.id)
                }
            }
        }
    }

    fun waterPlant(savedPlantId: String) {
        viewModelScope.launch {
            plantRepository.updateWateringStatus(savedPlantId).collect { res ->
                _gardenOperationState.value = res
                if (res is Resource.Success) {
                    // Fetch updated details to reschedule AlarmManager
                    viewModelScope.launch {
                        // Small delay to allow db update to finalize
                        kotlinx.coroutines.delay(100)
                        val updated = savedPlantsList.value?.find { it.id == savedPlantId }
                        if (updated != null) {
                            reminderManager.scheduleWateringReminder(updated)
                        }
                    }
                }
            }
        }
    }

    fun scheduleCareReminder(savedPlantId: String, nickname: String, taskType: String, intervalDays: Int) {
        reminderManager.scheduleCareReminder(savedPlantId, nickname, taskType, intervalDays)
    }

    fun cancelCareReminder(savedPlantId: String, taskType: String) {
        reminderManager.cancelCareReminder(savedPlantId, taskType)
    }

    fun rescheduleAllReminders(plants: List<SavedPlant>) {
        reminderManager.rescheduleAll(plants)
    }

    fun resetGardenOperationState() {
        _gardenOperationState.value = null
    }



    // --- Analytics Dashboard (Profile screen) ---
    val totalScansCount: Int
        get() = analyticsManager.getTotalScans()

    val lastScanDate: Long
        get() = analyticsManager.getLastScanDate()

    // --- Growth Timeline Operations ---
    fun getTimelineForPlant(plantId: String): LiveData<List<GrowthTimelineEntry>> {
        return plantRepository.getTimelineForPlant(plantId).asLiveData(viewModelScope.coroutineContext)
    }

    val allGrowthEntries: LiveData<List<GrowthTimelineEntry>> = 
        plantRepository.getAllGrowthEntries().asLiveData(viewModelScope.coroutineContext)

    fun addGrowthUpdate(
        plantId: String,
        heightCm: Double,
        healthScore: Int,
        notes: String,
        imagePath: String,
        thumbnailPath: String,
        assessmentMethod: String
    ) {
        viewModelScope.launch {
            val entry = GrowthTimelineEntry(
                id = UUID.randomUUID().toString(),
                plantId = plantId,
                timestamp = System.currentTimeMillis(),
                heightCm = heightCm,
                healthScore = healthScore,
                notes = notes,
                imagePath = imagePath,
                thumbnailPath = thumbnailPath,
                assessmentMethod = assessmentMethod
            )
            plantRepository.saveGrowthTimelineEntry(entry)
        }
    }

    // --- Garden Analytics Dashboard Card ---
    val gardenAnalytics: LiveData<GardenAnalytics> = combine(
        plantRepository.getSavedPlants(),
        plantRepository.getAllGrowthEntries()
    ) { savedPlants, allLogs ->
        if (savedPlants.isEmpty()) {
            return@combine GardenAnalytics()
        }

        val totalPlants = savedPlants.size
        val totalGrowthLogs = allLogs.size

        // Group logs by plantId
        val logsByPlant = allLogs.groupBy { it.plantId }

        // 1. Average Health Score
        var totalHealth = 0
        savedPlants.forEach { plant ->
            val plantLogs = logsByPlant[plant.plantId] ?: emptyList()
            val latestLog = plantLogs.maxByOrNull { it.timestamp }
            val health = latestLog?.healthScore ?: 100 // default 100 if no log
            totalHealth += health
        }
        val averageHealthScore = (totalHealth / totalPlants)

        // 2. Plants Needing Attention (health < 70)
        var needingAttentionCount = 0
        savedPlants.forEach { plant ->
            val plantLogs = logsByPlant[plant.plantId] ?: emptyList()
            val latestLog = plantLogs.maxByOrNull { it.timestamp }
            val health = latestLog?.healthScore ?: 100
            if (health < 70) {
                needingAttentionCount++
            }
        }

        // 3. Most Improved Plant and Average Height Increase
        var totalHeightInc = 0.0
        var plantsWithMultipleLogs = 0
        var bestImprovement = -9999
        var bestImprovedPlantName = "None"

        savedPlants.forEach { plant ->
            val plantLogs = logsByPlant[plant.plantId]?.sortedBy { it.timestamp } ?: emptyList()
            if (plantLogs.size >= 2) {
                val earliest = plantLogs.first()
                val latest = plantLogs.last()
                
                // Height increase
                val heightInc = latest.heightCm - earliest.heightCm
                totalHeightInc += heightInc
                plantsWithMultipleLogs++

                // Health improvement
                val healthImprovement = latest.healthScore - earliest.healthScore
                if (healthImprovement > bestImprovement && healthImprovement > 0) {
                    bestImprovement = healthImprovement
                    bestImprovedPlantName = plant.nickname
                }
            }
        }

        val averageHeightIncrease = if (plantsWithMultipleLogs > 0) {
            totalHeightInc / plantsWithMultipleLogs
        } else {
            0.0
        }

        GardenAnalytics(
            totalPlants = totalPlants,
            averageHealthScore = averageHealthScore,
            mostImprovedPlant = bestImprovedPlantName,
            totalGrowthLogs = totalGrowthLogs,
            averageHeightIncrease = averageHeightIncrease,
            plantsNeedingAttention = needingAttentionCount
        )
    }.asLiveData(viewModelScope.coroutineContext)

    // --- Admin Analytics dashboard stats ---
    private val _adminStatsState = MutableLiveData<Resource<AdminAnalyticsData>>()
    val adminStatsState: LiveData<Resource<AdminAnalyticsData>> = _adminStatsState

    fun loadAdminStats() {
        _adminStatsState.value = Resource.Loading
        viewModelScope.launch {
            try {
                val stats = firebaseManager.fetchAdminAnalytics()
                _adminStatsState.value = Resource.Success(stats)
            } catch (e: Exception) {
                _adminStatsState.value = Resource.Error(e)
            }
        }
    }

    // --- Personal usage dashboard stats ---
    private val _userUsageState = MutableLiveData<Resource<FirebaseManager.UserUsage>>()
    val userUsageState: LiveData<Resource<FirebaseManager.UserUsage>> = _userUsageState

    private val DATE_FORMAT = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)

    fun loadUserUsage() {
        val user = currentUser ?: return
        _userUsageState.value = Resource.Loading
        viewModelScope.launch {
            try {
                val uid = user.uid
                val todayStr = DATE_FORMAT.format(java.util.Date())
                var usage = firebaseManager.fetchUserUsage(uid)
                if (usage == null) {
                    usage = FirebaseManager.UserUsage(todayScans = 0, cacheHits = 0, plantNetCalls = 0, lastReset = todayStr)
                } else if (usage.lastReset != todayStr) {
                    usage = usage.copy(todayScans = 0, cacheHits = 0, plantNetCalls = 0, lastReset = todayStr)
                }
                _userUsageState.value = Resource.Success(usage)
            } catch (e: Exception) {
                _userUsageState.value = Resource.Error(e)
            }
        }
    }

    fun submitFeedback(
        imageHash: String,
        predictedPlant: String,
        actualPlant: String,
        confidence: Float
    ) {
        viewModelScope.launch {
            firebaseManager.uploadFeedback(imageHash, predictedPlant, actualPlant, confidence, System.currentTimeMillis())
        }
    }
}

data class GardenAnalytics(
    val totalPlants: Int = 0,
    val averageHealthScore: Int = 0,
    val mostImprovedPlant: String = "None",
    val totalGrowthLogs: Int = 0,
    val averageHeightIncrease: Double = 0.0,
    val plantsNeedingAttention: Int = 0
)
