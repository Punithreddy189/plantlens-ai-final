package com.plantlens.ai.fragments

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.plantlens.ai.R
import com.plantlens.ai.adapters.GrowthTimelineAdapter
import com.plantlens.ai.databinding.FragmentPlantDetailsBinding
import com.plantlens.ai.models.Plant
import com.plantlens.ai.models.GrowthTimelineEntry
import com.plantlens.ai.models.SavedPlant
import com.plantlens.ai.utils.Resource
import com.plantlens.ai.viewmodels.PlantViewModel
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@AndroidEntryPoint
class PlantDetailsFragment : Fragment() {

    private var _binding: FragmentPlantDetailsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: PlantViewModel by viewModels()
    private var plantId: String = ""
    private var currentPlant: Plant? = null

    private lateinit var timelineAdapter: GrowthTimelineAdapter
    private var selectedPhotoPath = ""

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPlantDetailsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        plantId = arguments?.getString("plantId") ?: ""

        setupListeners()
        observeViewModel()

        if (plantId.isNotEmpty()) {
            viewModel.loadPlantDetails(plantId)
        }
    }

    private fun setupListeners() {

        // Mock Progress Photo Capture
        binding.btnSelectPhoto.setOnClickListener {
            try {
                val cacheDir = requireContext().cacheDir
                val tempFile = File(cacheDir, "mock_progress_${System.currentTimeMillis()}.jpg")
                tempFile.createNewFile()
                
                // Draw a beautiful custom placeholder image representing progressive foliage
                val bitmap = android.graphics.Bitmap.createBitmap(450, 450, android.graphics.Bitmap.Config.ARGB_8888)
                val canvas = android.graphics.Canvas(bitmap)
                canvas.drawColor(Color.parseColor("#059669")) // Emerald primary background
                
                val paint = android.graphics.Paint().apply {
                    color = Color.WHITE
                    textSize = 28f
                    isAntiAlias = true
                    textAlign = android.graphics.Paint.Align.CENTER
                }
                canvas.drawText("PlantLens AI", 225f, 200f, paint)
                
                paint.textSize = 20f
                paint.color = Color.parseColor("#D1FAE5")
                canvas.drawText("Progress Snapshot", 225f, 250f, paint)
                
                val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
                paint.textSize = 16f
                paint.color = Color.parseColor("#A7F3D0")
                canvas.drawText(sdf.format(Date()), 225f, 300f, paint)

                val fos = java.io.FileOutputStream(tempFile)
                bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, fos)
                fos.close()

                selectedPhotoPath = tempFile.absolutePath
                binding.tvSelectedPhotoPath.text = getString(R.string.photo_captured_format, tempFile.name)
                Toast.makeText(requireContext(), getString(R.string.photo_captured_format, tempFile.name), Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                selectedPhotoPath = "mock_progress_photo.jpg"
                binding.tvSelectedPhotoPath.text = getString(R.string.photo_captured_format, "mock_progress_photo.jpg")
            }
        }

        // Submit Growth Log Form
        binding.btnSubmitLog.setOnClickListener {
            val heightText = binding.etHeight.text.toString().trim()
            val healthText = binding.etHealth.text.toString().trim()
            val notes = binding.etNotes.text.toString().trim()

            val heightCm = heightText.toDoubleOrNull()
            val healthScore = healthText.toIntOrNull()

            if (heightCm == null || healthScore == null || healthScore !in 0..100) {
                Toast.makeText(requireContext(), getString(R.string.error_invalid_log_input), Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            viewModel.addGrowthUpdate(
                plantId = plantId,
                heightCm = heightCm,
                healthScore = healthScore,
                notes = notes,
                imagePath = selectedPhotoPath,
                thumbnailPath = selectedPhotoPath, // matching thumbnailPath
                assessmentMethod = "Health Assessment Engine"
            )

            Toast.makeText(requireContext(), getString(R.string.growth_log_success), Toast.LENGTH_SHORT).show()

            // Clear form inputs
            binding.etHeight.setText("")
            binding.etHealth.setText("")
            binding.etNotes.setText("")
            binding.tvSelectedPhotoPath.text = getString(R.string.no_photo_selected)
            selectedPhotoPath = ""
        }
    }

    private fun observeViewModel() {
        viewModel.selectedPlantState.observe(viewLifecycleOwner) { resource ->
            when (resource) {
                is Resource.Loading -> {
                    // Show loading indicators
                }
                is Resource.Success -> {
                    currentPlant = resource.data
                    displayPlantDetails(resource.data)
                    fetchLocationAndLoadWeather()
                }
                is Resource.Error -> {
                    Toast.makeText(requireContext(), resource.message, Toast.LENGTH_LONG).show()
                }
            }
        }

        // Check if this plant is saved in the garden to unlock the dashboard
        viewModel.savedPlantsList.observe(viewLifecycleOwner) { list ->
            val savedPlant = list.find { it.plantId == plantId }
            if (savedPlant != null) {
                binding.gardenDashboardContainer.visibility = View.VISIBLE
                setupGardenDashboard(savedPlant)
            } else {
                binding.gardenDashboardContainer.visibility = View.GONE
            }
        }

        // Observe Plant Disease History and Foliar Diagnostics
        if (plantId.isNotEmpty()) {
            viewModel.getDiseaseHistoryForPlant(plantId).observe(viewLifecycleOwner) { historyList ->
                if (historyList.isNullOrEmpty()) {
                    binding.detailDiseaseCard.visibility = View.VISIBLE
                    val greenColor = ContextCompat.getColor(requireContext(), R.color.success)
                    val greenBg = ContextCompat.getColor(requireContext(), R.color.success_background)
                    
                    binding.detailHealthStatusCard.setCardBackgroundColor(greenBg)
                    binding.detailHealthStatusIcon.text = "🌿"
                    binding.detailHealthStatusTitle.text = getString(R.string.foliage_healthy_title)
                    binding.detailHealthStatusTitle.setTextColor(greenColor)
                    binding.detailDiseaseNameText.text = getString(R.string.healthy_foliage)
                    binding.detailDiseaseNameText.setTextColor(Color.parseColor("#065F46"))
                    binding.detailHealthScoreText.text = "100%"
                    binding.detailHealthScoreText.setTextColor(greenColor)
                    binding.detailHealthProgressBar.progress = 100
                    binding.detailHealthProgressBar.progressTintList = ColorStateList.valueOf(greenColor)
                    binding.detailDiseaseObservationsText.text = getString(R.string.standard_care_maintenance)
                    binding.detailTreatmentText.text = getString(R.string.standard_care_maintenance)
                } else {
                    val latest = historyList.maxByOrNull { it.timestamp } ?: historyList.first()
                    binding.detailDiseaseCard.visibility = View.VISIBLE
                    val dName = latest.diseaseName.trim().lowercase()
                    val isNoDiseaseNamed = dName.isEmpty() ||
                        dName.contains("none") ||
                        dName.contains("healthy") ||
                        dName.contains("no disease") ||
                        dName.contains("not detected") ||
                        dName.contains("optimal") ||
                        dName.contains("clean") ||
                        dName.contains("normal") ||
                        dName.contains("free of visible infection") ||
                        dName == "n/a"

                    val isHealthy = isNoDiseaseNamed || latest.healthStatus.lowercase().contains("healthy") || (latest.healthScore >= 75 && !dName.contains("blight") && !dName.contains("spot") && !dName.contains("rust") && !dName.contains("mildew"))
                    val isWarning = !isHealthy && (latest.healthScore >= 50 || latest.healthStatus.lowercase().contains("monitor") || latest.healthStatus.lowercase().contains("attention"))

                    val score = if (isHealthy) {
                        if (latest.healthScore < 80) 95 else latest.healthScore.coerceIn(80, 100)
                    } else {
                        latest.healthScore.coerceIn(0, 100)
                    }

                    val statusColor = when {
                        isHealthy -> ContextCompat.getColor(requireContext(), R.color.success)
                        isWarning -> ContextCompat.getColor(requireContext(), R.color.warning)
                        else -> ContextCompat.getColor(requireContext(), R.color.error)
                    }

                    val statusBg = when {
                        isHealthy -> ContextCompat.getColor(requireContext(), R.color.success_background)
                        isWarning -> ContextCompat.getColor(requireContext(), R.color.warning_background)
                        else -> ContextCompat.getColor(requireContext(), R.color.error_background)
                    }

                    val statusIcon = when {
                        isHealthy -> "🌿"
                        isWarning -> "⚠️"
                        else -> "🚨"
                    }

                    val statusTitle = when {
                        isHealthy -> getString(R.string.foliage_healthy_title)
                        isWarning -> getString(R.string.foliage_warning_title)
                        else -> getString(R.string.foliage_critical_title)
                    }

                    val nameColor = when {
                        isHealthy -> Color.parseColor("#065F46")
                        isWarning -> Color.parseColor("#92400E")
                        else -> Color.parseColor("#991B1B")
                    }

                    binding.detailHealthStatusCard.setCardBackgroundColor(statusBg)
                    binding.detailHealthStatusIcon.text = statusIcon
                    binding.detailHealthStatusTitle.text = statusTitle
                    binding.detailHealthStatusTitle.setTextColor(statusColor)
                    binding.detailDiseaseNameText.text = if (isHealthy) getString(R.string.healthy_foliage) else latest.diseaseName.ifBlank { "Foliar Stress / Blight" }
                    binding.detailDiseaseNameText.setTextColor(nameColor)
                    binding.detailHealthScoreText.text = "$score%"
                    binding.detailHealthScoreText.setTextColor(statusColor)
                    binding.detailHealthProgressBar.progress = score
                    binding.detailHealthProgressBar.progressTintList = ColorStateList.valueOf(statusColor)

                    binding.detailDiseaseObservationsText.text = latest.observations.ifBlank { getString(R.string.standard_care_maintenance) }
                    binding.detailTreatmentText.text = latest.treatment.ifBlank { latest.recommendations.ifBlank { getString(R.string.standard_care_maintenance) } }
                }
            }
        }

        viewModel.weatherState.observe(viewLifecycleOwner) { resource ->
            when (resource) {
                is Resource.Loading -> {
                    binding.detailWeatherRecommendation.text = getString(R.string.loading_weather_care)
                }
                is Resource.Success -> {
                    val weather = resource.data
                    val locale = java.util.Locale.getDefault()
                    val percentFormat = java.text.NumberFormat.getInstance(locale).apply { maximumFractionDigits = 0 }
                    val oneDecimalFormat = java.text.NumberFormat.getInstance(locale).apply { 
                        maximumFractionDigits = 1
                        minimumFractionDigits = 1
                    }

                    binding.detailWeatherTemp.text = getString(R.string.weather_temp_format, oneDecimalFormat.format(weather.temperature))
                    binding.detailWeatherHumidity.text = getString(R.string.weather_humidity_format, oneDecimalFormat.format(weather.humidity))
                    binding.detailWeatherRain.text = getString(R.string.weather_rain_format, percentFormat.format(weather.rainProbability))
                    binding.detailWeatherUv.text = getString(R.string.weather_uv_format, oneDecimalFormat.format(weather.uvIndex))
                    binding.detailWeatherWind.text = getString(R.string.weather_wind_format, oneDecimalFormat.format(weather.windSpeed))

                    val wateringMsg = StringBuilder()
                    wateringMsg.append(getString(R.string.weather_sync_info)).append(" ")

                    val plant = currentPlant
                    if (plant != null) {
                        var wateringCycle = plant.wateringFrequency
                        val ruleApplied = ArrayList<String>()

                        if (weather.temperature > 35.0) {
                            wateringCycle = (wateringCycle - 2).coerceAtLeast(1)
                            ruleApplied.add(getString(R.string.weather_rule_high_temp, wateringCycle))
                        }
                        if (weather.humidity > 80.0) {
                            wateringCycle = wateringCycle + 2
                            ruleApplied.add(getString(R.string.weather_rule_high_humidity, wateringCycle))
                        }
                        if (weather.rainProbability > 60.0) {
                            ruleApplied.add(getString(R.string.weather_rule_rain, weather.rainProbability.toInt()))
                        }
                        if (weather.temperature < 10.0) {
                            ruleApplied.add(getString(R.string.weather_rule_cold))
                        }
                        if (weather.uvIndex > 8.0) {
                            ruleApplied.add(getString(R.string.weather_rule_uv, weather.uvIndex))
                        }

                        if (ruleApplied.isEmpty()) {
                            wateringMsg.append(getString(R.string.weather_rule_optimal, wateringCycle))
                        } else {
                            wateringMsg.append(ruleApplied.joinToString("\n"))
                        }
                    } else {
                        wateringMsg.append("Ready.")
                    }

                    binding.detailWeatherRecommendation.text = wateringMsg.toString()
                }
                is Resource.Error -> {
                    binding.detailWeatherRecommendation.text = getString(R.string.error_loading_stats_format, resource.message)
                }
            }
        }
    }

    private fun fetchLocationAndLoadWeather() {
        try {
            val locationManager = requireContext().getSystemService(android.content.Context.LOCATION_SERVICE) as android.location.LocationManager
            
            val hasFine = ContextCompat.checkSelfPermission(requireContext(), android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
            val hasCoarse = ContextCompat.checkSelfPermission(requireContext(), android.Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
            
            var lat = 12.9716 // Default Bangalore
            var lng = 77.5946
            
            if (hasFine || hasCoarse) {
                val gpsLoc = locationManager.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER)
                val netLoc = locationManager.getLastKnownLocation(android.location.LocationManager.NETWORK_PROVIDER)
                val location = gpsLoc ?: netLoc
                location?.let {
                    lat = it.latitude
                    lng = it.longitude
                }
            }
            viewModel.loadWeatherForLocation(lat, lng)
        } catch (e: Exception) {
            viewModel.loadWeatherForLocation(12.9716, 77.5946) // Fallback default
        }
    }

    private fun getLocalizedCategory(category: String): String {
        return when (category.lowercase()) {
            "indoor" -> getString(R.string.indoor_plants_chip)
            "outdoor" -> getString(R.string.outdoor_plants_chip)
            "succulent", "succulents" -> getString(R.string.succulents_chip)
            "medicinal" -> getString(R.string.medicinal_plants_chip)
            "flowering" -> getString(R.string.flowering_plants_chip)
            else -> category
        }
    }

    private fun displayPlantDetails(plant: Plant?) {
        if (plant == null) {
            binding.detailPlantName.text = getString(R.string.unknown_plant)
            binding.detailScientificName.text = ""
            binding.detailPlantImage.setImageResource(com.plantlens.ai.utils.PlantImageMapper.getDrawableRes("Unknown Plant"))
            binding.detailCategoryBadge.visibility = View.GONE
            binding.detailCareTips.visibility = View.GONE
            binding.detailAdvantages.visibility = View.GONE
            binding.detailDisadvantages.visibility = View.GONE
            
            // Hide optional sections
            binding.plantUsesCard.visibility = View.GONE
            binding.localNamesCard.visibility = View.GONE
            binding.detailWeatherCard.visibility = View.GONE
            binding.gardenDashboardContainer.visibility = View.GONE
            return
        }

        binding.detailCategoryBadge.visibility = View.VISIBLE
        binding.detailCareTips.visibility = View.VISIBLE
        binding.detailAdvantages.visibility = View.VISIBLE
        binding.detailDisadvantages.visibility = View.VISIBLE
        binding.plantUsesCard.visibility = View.VISIBLE
        binding.localNamesCard.visibility = View.VISIBLE
        binding.detailWeatherCard.visibility = View.VISIBLE

        binding.detailPlantName.text = com.plantlens.ai.utils.TranslationManager.getPlantName(plant)
        binding.detailScientificName.text = plant.scientificName
        
        binding.detailPlantImage.setImageResource(com.plantlens.ai.utils.PlantImageMapper.getDrawableRes(plant.name))

        val numberFormat = java.text.NumberFormat.getInstance(java.util.Locale.getDefault())
        val localizedCategory = getLocalizedCategory(plant.category)
        binding.detailCategoryBadge.text = getString(R.string.detail_category_badge_format, localizedCategory, numberFormat.format(plant.wateringFrequency))

        // Format bulleted lists
        binding.detailCareTips.text = plant.careTips.joinToString("\n") { "• ${com.plantlens.ai.utils.TranslationManager.translate("care_tips", it)}" }
        binding.detailAdvantages.text = plant.advantages.joinToString("\n") { "• ${com.plantlens.ai.utils.TranslationManager.translate("advantages", it)}" }
        binding.detailDisadvantages.text = plant.disadvantages.joinToString("\n") { "• ${com.plantlens.ai.utils.TranslationManager.translate("disadvantages", it)}" }

        // Format plant uses
        val translatedUses = plant.uses.map { 
            val translated = com.plantlens.ai.utils.TranslationManager.translate("plant_uses", it)
            "• ${translated.replaceFirstChar { char -> char.uppercase() }}"
        }
        val usesStr = translatedUses.joinToString("\n")
        val defaultUseTrans = com.plantlens.ai.utils.TranslationManager.translate("plant_uses", "Standard decorative houseplant.")
        binding.detailPlantUses.text = if (usesStr.isEmpty()) "• $defaultUseTrans" else usesStr

        // Format local regional names
        val names = StringBuilder()
        val languageList = listOf(
            "en" to "English", "ta" to "Tamil", "te" to "Telugu", "hi" to "Hindi", 
            "kn" to "Kannada", "ml" to "Malayalam", "bn" to "Bengali", "mr" to "Marathi", 
            "gu" to "Gujarati", "pa" to "Punjabi"
        )
        for ((code, langName) in languageList) {
            val name = plant.localNames[code]
            if (!name.isNullOrEmpty()) {
                names.append("• $langName: $name\n")
            }
        }
        binding.detailLocalNames.text = if (names.isEmpty()) getString(R.string.no_regional_names) else names.toString().trim()
    }

    private fun setupGardenDashboard(savedPlant: SavedPlant) {
        // 1. Setup last watered & scheduling info
        val dateFormat = java.text.DateFormat.getDateInstance(java.text.DateFormat.LONG, java.util.Locale.getDefault())
        val lastWateredStr = if (savedPlant.lastWatered > 0L) dateFormat.format(Date(savedPlant.lastWatered)) else getString(R.string.never)
        val nextWaterStr = dateFormat.format(Date(savedPlant.nextWaterDate))
        binding.tvDashboardWateringInfo.text = getString(R.string.dashboard_watering_info_format, lastWateredStr, nextWaterStr)

        // 2. Setup timeline recycler view
        timelineAdapter = GrowthTimelineAdapter()
        binding.rvGrowthTimeline.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = timelineAdapter
        }

        // 3. Observe growth entries for this plant
        viewModel.getTimelineForPlant(plantId).observe(viewLifecycleOwner) { entries ->
            timelineAdapter.submitList(entries)
            setupChart(entries)

            // Calculate My Plant Journey metrics
            val sortedEntries = entries.sortedBy { it.timestamp }
            val numberFormat = java.text.NumberFormat.getInstance(java.util.Locale.getDefault())
            
            if (sortedEntries.isNotEmpty()) {
                val firstDate = dateFormat.format(Date(sortedEntries.first().timestamp))
                binding.tvFirstScanDate.text = firstDate
                binding.tvTotalScansCount.text = numberFormat.format(sortedEntries.size)
                
                val healthScores = sortedEntries.map { it.healthScore }
                binding.tvHealthProgression.text = getString(R.string.health_score_progression, healthScores.joinToString(" → "))
                
                val trend = if (sortedEntries.size >= 2) {
                    val firstScore = sortedEntries.first().healthScore
                    val lastScore = sortedEntries.last().healthScore
                    if (lastScore > firstScore) {
                        getString(R.string.trend_improving)
                    } else if (lastScore < firstScore) {
                        getString(R.string.trend_declining)
                    } else {
                        getString(R.string.stable_trend)
                    }
                } else {
                    getString(R.string.stable_trend)
                }
                binding.tvHealthTrend.text = trend
            } else {
                binding.tvFirstScanDate.text = dateFormat.format(Date(savedPlant.addedDate))
                binding.tvTotalScansCount.text = numberFormat.format(0)
                binding.tvHealthTrend.text = getString(R.string.stable_trend)
                binding.tvHealthProgression.text = "No growth history available"
            }
        }

        // 4. Setup Care Reminders Config
        val sharedPref = requireContext().getSharedPreferences("plant_reminders_${savedPlant.id}", android.content.Context.MODE_PRIVATE)
        binding.switchWaterReminder.isChecked = sharedPref.getBoolean("water_enabled", true)
        binding.etWaterInterval.setText(sharedPref.getInt("water_interval", savedPlant.wateringFrequency).toString())

        binding.switchFertilizerReminder.isChecked = sharedPref.getBoolean("fertilizer_enabled", false)
        binding.etFertilizerInterval.setText(sharedPref.getInt("fertilizer_interval", 30).toString())

        binding.switchRepotReminder.isChecked = sharedPref.getBoolean("repot_enabled", false)
        binding.etRepotInterval.setText(sharedPref.getInt("repot_interval", 180).toString())

        binding.switchPruningReminder.isChecked = sharedPref.getBoolean("pruning_enabled", false)
        binding.etPruningInterval.setText(sharedPref.getInt("pruning_interval", 90).toString())

        binding.btnSaveReminders.setOnClickListener {
            val waterInterval = binding.etWaterInterval.text.toString().toIntOrNull() ?: 7
            val fertilizerInterval = binding.etFertilizerInterval.text.toString().toIntOrNull() ?: 30
            val repotInterval = binding.etRepotInterval.text.toString().toIntOrNull() ?: 180
            val pruningInterval = binding.etPruningInterval.text.toString().toIntOrNull() ?: 90

            val editor = sharedPref.edit()
            
            // Water
            val waterEnabled = binding.switchWaterReminder.isChecked
            editor.putBoolean("water_enabled", waterEnabled)
            editor.putInt("water_interval", waterInterval)
            if (waterEnabled) {
                viewModel.scheduleCareReminder(savedPlant.id, savedPlant.nickname, "Water", waterInterval)
            } else {
                viewModel.cancelCareReminder(savedPlant.id, "Water")
            }

            // Fertilizer
            val fertilizerEnabled = binding.switchFertilizerReminder.isChecked
            editor.putBoolean("fertilizer_enabled", fertilizerEnabled)
            editor.putInt("fertilizer_interval", fertilizerInterval)
            if (fertilizerEnabled) {
                viewModel.scheduleCareReminder(savedPlant.id, savedPlant.nickname, "Fertilizer", fertilizerInterval)
            } else {
                viewModel.cancelCareReminder(savedPlant.id, "Fertilizer")
            }

            // Repot
            val repotEnabled = binding.switchRepotReminder.isChecked
            editor.putBoolean("repot_enabled", repotEnabled)
            editor.putInt("repot_interval", repotInterval)
            if (repotEnabled) {
                viewModel.scheduleCareReminder(savedPlant.id, savedPlant.nickname, "Repot", repotInterval)
            } else {
                viewModel.cancelCareReminder(savedPlant.id, "Repot")
            }

            // Pruning
            val pruningEnabled = binding.switchPruningReminder.isChecked
            editor.putBoolean("pruning_enabled", pruningEnabled)
            editor.putInt("pruning_interval", pruningInterval)
            if (pruningEnabled) {
                viewModel.scheduleCareReminder(savedPlant.id, savedPlant.nickname, "Pruning", pruningInterval)
            } else {
                viewModel.cancelCareReminder(savedPlant.id, "Pruning")
            }

            editor.apply()
            Toast.makeText(requireContext(), getString(R.string.toast_reminders_updated), Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupChart(entries: List<GrowthTimelineEntry>) {
        val chart = binding.growthChart
        if (entries.isEmpty()) {
            chart.clear()
            chart.setNoDataText(getString(R.string.chart_no_data))
            chart.setNoDataTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary_light))
            chart.invalidate()
            return
        }

        // Sort entries ascending by timestamp
        val sortedEntries = entries.sortedBy { it.timestamp }

        val heightValues = ArrayList<Entry>()
        val healthValues = ArrayList<Entry>()

        val sdf = SimpleDateFormat("MMM dd", Locale.getDefault())

        sortedEntries.forEachIndexed { index, entry ->
            heightValues.add(Entry(index.toFloat(), entry.heightCm.toFloat()))
            healthValues.add(Entry(index.toFloat(), entry.healthScore.toFloat()))
        }

        val heightDataSet = LineDataSet(heightValues, "Height (cm)").apply {
            color = Color.parseColor("#10B981") // Emerald Green
            setCircleColor(Color.parseColor("#10B981"))
            lineWidth = 3f
            circleRadius = 5f
            setDrawCircleHole(false)
            valueTextSize = 10f
            valueTextColor = Color.parseColor("#10B981")
            setDrawFilled(true)
            fillColor = Color.parseColor("#D1FAE5")
            mode = LineDataSet.Mode.CUBIC_BEZIER
        }

        val healthDataSet = LineDataSet(healthValues, "Health Score (%)").apply {
            color = Color.parseColor("#F59E0B") // Amber Orange
            setCircleColor(Color.parseColor("#F59E0B"))
            lineWidth = 3f
            circleRadius = 5f
            setDrawCircleHole(false)
            valueTextSize = 10f
            valueTextColor = Color.parseColor("#F59E0B")
            setDrawFilled(true)
            fillColor = Color.parseColor("#FEF3C7")
            mode = LineDataSet.Mode.CUBIC_BEZIER
        }

        val lineData = LineData(heightDataSet, healthDataSet)
        chart.data = lineData

        // Configure axes
        val xAxis = chart.xAxis
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.setDrawGridLines(false)
        xAxis.textColor = Color.parseColor("#718096")
        xAxis.valueFormatter = object : com.github.mikephil.charting.formatter.ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                val index = value.toInt()
                return if (index in sortedEntries.indices) {
                    sdf.format(Date(sortedEntries[index].timestamp))
                } else ""
            }
        }
        xAxis.granularity = 1f
        xAxis.labelRotationAngle = -15f

        chart.axisLeft.apply {
            textColor = Color.parseColor("#718096")
            setDrawGridLines(true)
            gridColor = Color.parseColor("#E2E8F0")
        }
        chart.axisRight.isEnabled = false // disable right axis

        chart.description.isEnabled = false
        chart.legend.apply {
            textColor = Color.parseColor("#718096")
            form = Legend.LegendForm.LINE
            textSize = 11f
        }

        chart.animateY(800)
        chart.invalidate()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
