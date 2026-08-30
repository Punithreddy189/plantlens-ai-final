package com.plantlens.ai.fragments

import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.plantlens.ai.R
import com.plantlens.ai.databinding.FragmentPlantResultBinding
import com.plantlens.ai.models.Plant
import com.plantlens.ai.utils.Resource
import androidx.lifecycle.lifecycleScope
import com.plantlens.ai.utils.LocationManager
import com.plantlens.ai.repository.WeatherRepository
import com.plantlens.ai.viewmodels.PlantViewModel
import com.plantlens.ai.firebase.FirebaseManager
import android.content.res.ColorStateList
import com.plantlens.ai.models.DiseaseAnalysisResult
import com.plantlens.ai.models.DiagnosisUIState
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.io.Serializable
import javax.inject.Inject

@AndroidEntryPoint
class PlantResultFragment : Fragment() {

    @Inject
    lateinit var locationManager: LocationManager

    @Inject
    lateinit var weatherRepository: WeatherRepository

    @Inject
    lateinit var firebaseManager: FirebaseManager

    private var _binding: FragmentPlantResultBinding? = null
    private val binding get() = _binding!!

    private val viewModel: PlantViewModel by viewModels()
    private var matchedPlant: Plant? = null
    private var confidenceScore: Float = 0.0f
    private var voiceDiagnosisManager: com.plantlens.ai.utils.VoiceDiagnosisManager? = null

    private val requestLocationPermissionLauncher =
        registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val fineGranted = permissions[android.Manifest.permission.ACCESS_FINE_LOCATION] ?: false
            val coarseGranted = permissions[android.Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
            if (fineGranted || coarseGranted) {
                fetchLocationAndWeather()
            } else {
                useFallbackWeather()
            }
        }

    private fun checkAndRequestLocationPermission() {
        if (locationManager.hasLocationPermission()) {
            fetchLocationAndWeather()
        } else {
            requestLocationPermissionLauncher.launch(
                arrayOf(
                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                    android.Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    private fun fetchLocationAndWeather() {
        lifecycleScope.launch {
            val locationResult = locationManager.getCurrentLocation()
            if (locationResult != null) {
                binding.weatherLocationText.text = getString(R.string.location_label, locationResult.cityName)
                loadWeather(locationResult.latitude, locationResult.longitude)
            } else {
                useFallbackWeather()
            }
        }
    }

    private fun useFallbackWeather() {
        binding.weatherLocationText.text = getString(R.string.location_label, "Bangalore (Fallback)")
        loadWeather(12.9716, 77.5946)
    }

    private fun loadWeather(latitude: Double, longitude: Double) {
        lifecycleScope.launch {
            weatherRepository.getWeatherData(latitude, longitude).collect { resource ->
                when (resource) {
                    is Resource.Loading -> {
                        binding.wateringAdjustText.text = getString(R.string.loading_weather_care)
                    }
                    is Resource.Success -> {
                        val weather = resource.data
                        val standardInterval = matchedPlant?.wateringFrequency ?: 7
                        val recommendation = weatherRepository.getWateringRecommendation(weather, standardInterval)

                        val locale = java.util.Locale.getDefault()
                        val percentFormat = java.text.NumberFormat.getInstance(locale).apply { maximumFractionDigits = 0 }
                        val oneDecimalFormat = java.text.NumberFormat.getInstance(locale).apply {
                            maximumFractionDigits = 1
                            minimumFractionDigits = 1
                        }

                        binding.weatherTempText.text = getString(R.string.weather_temp_format, oneDecimalFormat.format(weather.temperature))
                        binding.weatherHumidityText.text = getString(R.string.weather_humidity_format, oneDecimalFormat.format(weather.humidity))
                        binding.weatherRainText.text = getString(R.string.weather_rain_format, percentFormat.format(weather.rainProbability))
                        binding.weatherUvText.text = getString(R.string.weather_uv_format, oneDecimalFormat.format(weather.uvIndex))
                        binding.weatherWindText.text = getString(R.string.weather_wind_format, oneDecimalFormat.format(weather.windSpeed))

                        binding.wateringAdjustText.text = recommendation.message
                    }
                    is Resource.Error -> {
                        binding.wateringAdjustText.text = getString(R.string.error_loading_stats_format, resource.message ?: "Unknown error")
                    }
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPlantResultBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Clear previous operation states to prevent auto-navigation
        viewModel.resetGardenOperationState()

        matchedPlant = arguments?.getSerializableCompat("matched_plant", Plant::class.java)
        confidenceScore = arguments?.getFloat("confidence_score") ?: 0.0f
        voiceDiagnosisManager = com.plantlens.ai.utils.VoiceDiagnosisManager(requireContext())

        displayResults()
        setupListeners()
        observeViewModel()
    }

    private fun <T : Serializable> Bundle.getSerializableCompat(key: String, clazz: Class<T>): T? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getSerializable(key, clazz)
        } else {
            @Suppress("DEPRECATION", "UNCHECKED_CAST")
            getSerializable(key) as? T
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

    private fun displayResults() {
        val plant = matchedPlant ?: return
        val pct = (confidenceScore * 100).toInt()

        val top3Predictions = arguments?.getStringArrayList("top3_predictions") ?: emptyList()
        val top3Confidences = (arguments?.getSerializableCompat("top3_confidences", ArrayList::class.java))?.filterIsInstance<Float>() ?: emptyList()

        val top5Commons = arguments?.getStringArrayList("top5_commons") ?: emptyList()
        val top5Scientifics = arguments?.getStringArrayList("top5_scientifics") ?: emptyList()
        val top5Confidences = arguments?.getFloatArray("top5_confidences") ?: floatArrayOf()
            
        val cropMode = arguments?.getString("crop_mode") ?: "CENTER_CROP"
        val cropQuality = arguments?.getString("crop_quality") ?: "Good"

        val numberFormat = java.text.NumberFormat.getInstance(java.util.Locale.getDefault())
        val localizedPlantName = com.plantlens.ai.utils.TranslationManager.getPlantName(plant)

        // 7. Android Result Screen: Display Common Name, Scientific Name, Family, Genus, Confidence Badge, Top 3 Predictions
        val isTrulyUnknown = plant.name.equals("Not a plant", ignoreCase = true) || 
                             plant.name.equals("Unrecognized Plant", ignoreCase = true) ||
                             (plant.name.isBlank() && plant.scientificName.isBlank())

        val labelText = when {
            isTrulyUnknown -> getString(R.string.unrecognized_plant_format, pct)
            pct <= 80 -> getString(R.string.medium_confidence_format, pct)
            else -> getString(R.string.high_confidence_format, pct)
        }

        // 1. Truly Unknown Plant -> Unrecognized / Low Confidence State (Red Badge)
        if (isTrulyUnknown) {
            val isUnknownSpecies = plant.name == "Unknown Plant Species"
            binding.confidenceScoreText.text = if (isUnknownSpecies) getString(R.string.unknown_plant_species_format, pct) else getString(R.string.unrecognized_plant_format, pct)
            binding.matchBadge.setCardBackgroundColor(ContextCompat.getColor(requireContext(), R.color.error_background))
            binding.confidenceScoreText.setTextColor(ContextCompat.getColor(requireContext(), R.color.error))

            binding.resultPlantName.text = if (isUnknownSpecies) getString(R.string.unknown_plant_species) else getString(R.string.unrecognized_plant)
            binding.resultScientificName.text = ""
            binding.resultCategory.visibility = View.GONE
            binding.resultWaterSchedule.visibility = View.GONE
            
            // Still display Top 3 suggestions card, but disable/hide Save to Garden, Disease Analysis, Watering Schedule
            binding.detailedResultsContainer.visibility = View.VISIBLE
            binding.top3Card.visibility = View.VISIBLE
            binding.diseaseDiagnosisCard.visibility = View.GONE
            binding.weatherWateringCard.visibility = View.GONE
            binding.saveInputsCard.visibility = View.GONE
            
            binding.unrecognizedWarningCard.visibility = View.VISIBLE
            binding.unrecognizedWarningText.text = if (isUnknownSpecies) {
                getString(R.string.warning_unknown_species_desc)
            } else {
                getString(R.string.warning_unrecognized_plant_desc)
            }
            
            binding.viewDetailsButton.visibility = View.GONE
            binding.saveToGardenButton.visibility = View.GONE
            binding.nicknameInput.visibility = View.GONE

            val params = binding.retryScanButton.layoutParams as android.widget.LinearLayout.LayoutParams
            params.weight = 2.0f
            binding.retryScanButton.layoutParams = params

            bindAdvancedMetrics(
                plant, top3Predictions, top3Confidences,
                top5Commons, top5Scientifics, top5Confidences
            )
        }
        // 2. Recognized Plant -> Allow saving, view details, and weather watering schedule
        else {
            binding.confidenceScoreText.text = labelText
            if (pct <= 80) {
                binding.matchBadge.setCardBackgroundColor(ContextCompat.getColor(requireContext(), R.color.warning_background))
                binding.confidenceScoreText.setTextColor(ContextCompat.getColor(requireContext(), R.color.warning))
            } else {
                binding.matchBadge.setCardBackgroundColor(ContextCompat.getColor(requireContext(), R.color.success_background))
                binding.confidenceScoreText.setTextColor(ContextCompat.getColor(requireContext(), R.color.success))
            }

            binding.resultPlantName.text = localizedPlantName
            binding.resultScientificName.text = buildString {
                append(plant.scientificName)
                if (plant.family.isNotEmpty()) {
                    append("\n").append(getString(R.string.family_label, plant.family))
                }
                if (plant.genus.isNotEmpty()) {
                    append(" | ").append(getString(R.string.genus_label, plant.genus))
                }
            }
            binding.resultCategory.visibility = View.VISIBLE
            binding.resultCategory.text = getString(R.string.result_category_format, getLocalizedCategory(plant.category), cropMode, cropQuality)
            binding.resultWaterSchedule.visibility = View.VISIBLE
            binding.resultWaterSchedule.text = getString(R.string.result_watering_cycle_format, numberFormat.format(plant.wateringFrequency))

            binding.unrecognizedWarningCard.visibility = View.GONE
            binding.detailedResultsContainer.visibility = View.VISIBLE
            binding.top3Card.visibility = View.VISIBLE
            binding.diseaseDiagnosisCard.visibility = View.VISIBLE
            binding.weatherWateringCard.visibility = View.VISIBLE
            binding.saveInputsCard.visibility = View.VISIBLE

            binding.viewDetailsButton.visibility = View.VISIBLE
            binding.saveToGardenButton.visibility = View.VISIBLE
            binding.nicknameInput.visibility = View.VISIBLE
            binding.nicknameInput.setText(getString(R.string.nickname_default_format, localizedPlantName))

            checkAndRequestLocationPermission()

            bindAdvancedMetrics(
                plant, top3Predictions, top3Confidences,
                top5Commons, top5Scientifics, top5Confidences
            )

            // Render Plant Health & Disease Diagnostics
            val diseaseResult = extractDiseaseAnalysisResult()
            android.util.Log.d("DEBUG_UI_RAW_JSON", "UI received: disease='${diseaseResult.diseaseName}', severity='${diseaseResult.severity}', status='${diseaseResult.healthStatus}', isHealthy=${diseaseResult.isHealthy}, score=${diseaseResult.healthScore}")
            renderDiagnosis(diseaseResult)
        }
    }

    private fun extractDiseaseAnalysisResult(): DiseaseAnalysisResult {
        val fromBundle = arguments?.getSerializableCompat("disease_analysis_result", DiseaseAnalysisResult::class.java)
        if (fromBundle != null) return fromBundle

        val diseaseName = arguments?.getString("disease_name")
        val diseaseConfidence = arguments?.getFloat("disease_confidence")
        val diseaseSeverity = arguments?.getString("disease_severity")
        val treatment = arguments?.getString("treatment_recommendation")
        val healthScore = arguments?.getInt("health_score", 100) ?: 100
        val healthStatus = arguments?.getString("health_status") ?: "Healthy"
        val observations = arguments?.getString("observations")
        val recommendations = arguments?.getString("recommendations")
        val assessmentMethod = arguments?.getString("assessment_method") ?: "On-Device AI"
        val soilType = arguments?.getString("soil_type") ?: "Loamy soil"
        val soilPh = arguments?.getString("soil_ph") ?: "6.0 - 7.0"
        val soilDrainage = arguments?.getString("soil_drainage") ?: "Well-drained"
        val soilRec = arguments?.getString("soil_recommendation") ?: "Mix garden soil with compost and sand."
        val confidenceReason = arguments?.getString("confidence_reason")

        return DiseaseAnalysisResult(
            diseaseName = diseaseName,
            confidence = diseaseConfidence,
            severity = diseaseSeverity,
            treatment = treatment,
            healthScore = healthScore,
            healthStatus = healthStatus,
            observations = observations,
            recommendations = recommendations,
            assessmentMethod = assessmentMethod,
            soilType = soilType,
            soilPh = soilPh,
            soilDrainage = soilDrainage,
            soilRecommendation = soilRec,
            confidenceReason = confidenceReason
        )
    }

    private fun renderDiagnosis(result: DiseaseAnalysisResult) {
        val rawScore = result.healthScore
        val score = if (result.isHealthy) {
            if (rawScore < 80) 95 else rawScore.coerceIn(80, 100)
        } else {
            rawScore.coerceIn(0, 100)
        }
        binding.healthScoreProgressBar.progress = score
        binding.healthScorePercentText.text = "$score / 100"

        // Clean confidence header matching web standard layout
        binding.resultConfidenceReason.visibility = View.GONE

        // Diagnostic Engine Tag (e.g., gemini-2.0-flash / gemini-1.5-pro)
        val engineName = result.assessmentMethod.takeIf { it.isNotBlank() } ?: "gemini-2.0-flash"
        binding.diagnosticEngineBadge.text = engineName

        // Observations / Symptoms
        val obs = result.observations?.takeIf { it.isNotBlank() }
        if (obs != null) {
            binding.diseaseObservationsSection.visibility = View.VISIBLE
            binding.diseaseObservationsText.text = obs
        } else {
            binding.diseaseObservationsSection.visibility = View.GONE
        }

        // Treatment Plan
        val treatment = result.treatment?.takeIf { it.isNotBlank() } ?: result.recommendations?.takeIf { it.isNotBlank() }
        if (treatment != null) {
            binding.treatmentPlanCard.visibility = View.VISIBLE
            binding.treatmentPlanText.text = treatment
        } else {
            binding.treatmentPlanCard.visibility = View.VISIBLE
            binding.treatmentPlanText.text = getString(R.string.standard_care_maintenance)
        }

        // Soil & Agronomy Requirements
        val rawSoilType = result.soilType?.takeIf { it.isNotBlank() && it != "N/A" } ?: arguments?.getString("soil_type") ?: "Loamy soil"
        val soilPh = result.soilPh?.takeIf { it.isNotBlank() && it != "N/A" } ?: arguments?.getString("soil_ph") ?: "6.0 - 6.8"
        val rawSoilDrainage = result.soilDrainage?.takeIf { it.isNotBlank() && it != "N/A" } ?: arguments?.getString("soil_drainage") ?: "Well-drained"
        val soilRec = result.soilRecommendation?.takeIf { it.isNotBlank() && it != "N/A" } ?: arguments?.getString("soil_recommendation") ?: "Mix garden soil with 30% organic compost and sand for optimal root aeration."

        val localizedSoilType = com.plantlens.ai.utils.TranslationManager.translateSoilType(rawSoilType)
        val localizedSoilDrainage = com.plantlens.ai.utils.TranslationManager.translateSoilDrainage(rawSoilDrainage)

        binding.soilTypeText.text = localizedSoilType
        binding.soilPhText.text = soilPh
        binding.soilDrainageText.text = localizedSoilDrainage
        binding.soilMixRecommendationText.text = soilRec

        // Offline notice
        val isOffline = result.assessmentMethod.contains("Pixel", ignoreCase = true) || result.assessmentMethod.contains("On-Device", ignoreCase = true)
        binding.offlineDiagnosticNotice.visibility = if (isOffline) View.VISIBLE else View.GONE

        // Severity & Badge Color
        val displayedSeverity = if (result.isHealthy) {
            "None (Optimal)"
        } else {
            val rawSeverity = result.severity?.takeIf { it.isNotBlank() && it != "N/A" }
            rawSeverity ?: "Moderate"
        }
        val localizedSeverity = com.plantlens.ai.utils.TranslationManager.translateSeverity(displayedSeverity)
        binding.diseaseSeverityBadge.text = getString(R.string.severity_label_format, localizedSeverity)

        val (sevColor, sevBg) = when {
            result.isHealthy || displayedSeverity.contains("Optimal", true) || displayedSeverity.contains("Healthy", true) ->
                Pair(android.graphics.Color.parseColor("#065F46"), android.graphics.Color.parseColor("#D1FAE5"))
            displayedSeverity.contains("Low", true) ->
                Pair(android.graphics.Color.parseColor("#854D0E"), android.graphics.Color.parseColor("#FEF9C3"))
            displayedSeverity.contains("Moderate", true) || displayedSeverity.contains("Medium", true) ->
                Pair(android.graphics.Color.parseColor("#9A3412"), android.graphics.Color.parseColor("#FFEDD5"))
            displayedSeverity.contains("Critical", true) ->
                Pair(android.graphics.Color.parseColor("#881337"), android.graphics.Color.parseColor("#FFE4E6"))
            else -> // High / Severe
                Pair(android.graphics.Color.parseColor("#991B1B"), android.graphics.Color.parseColor("#FEE2E2"))
        }
        binding.diseaseSeverityBadge.setTextColor(sevColor)
        binding.diseaseSeverityBadge.backgroundTintList = ColorStateList.valueOf(sevBg)

        // State Classification & Color Mapping
        when {
            result.isHealthy -> showHealthyDiagnosisUI(result, score)
            result.isWarning -> showWarningDiagnosisUI(result, score)
            else -> showCriticalDiagnosisUI(result, score)
        }
    }

    private fun showHealthyDiagnosisUI(result: DiseaseAnalysisResult, score: Int) {
        val greenColor = ContextCompat.getColor(requireContext(), R.color.success)
        val greenBg = ContextCompat.getColor(requireContext(), R.color.success_background)

        binding.healthStatusCard.setCardBackgroundColor(greenBg)
        binding.healthStatusIcon.text = "🌿"
        binding.healthStatusTitle.text = getString(R.string.foliage_healthy_title)
        binding.healthStatusTitle.setTextColor(greenColor)
        val dText = result.diseaseName?.takeIf { it.isNotBlank() } ?: "None (Healthy Plant)"
        binding.diseaseNameText.text = dText
        binding.diseaseNameText.setTextColor(android.graphics.Color.parseColor("#065F46"))
        binding.healthScorePercentText.setTextColor(greenColor)
        binding.healthScoreProgressBar.progressTintList = ColorStateList.valueOf(greenColor)
    }

    private fun showWarningDiagnosisUI(result: DiseaseAnalysisResult, score: Int) {
        val amberColor = ContextCompat.getColor(requireContext(), R.color.warning)
        val amberBg = ContextCompat.getColor(requireContext(), R.color.warning_background)

        val diseaseLabel = result.diseaseName?.takeIf {
            it.isNotBlank() && !it.contains("None", true) && !it.contains("Healthy", true) && !it.contains("No disease", true)
        } ?: "Cercospora Leaf Spot / Early Blight"

        binding.healthStatusCard.setCardBackgroundColor(amberBg)
        binding.healthStatusIcon.text = "⚠️"
        binding.healthStatusTitle.text = "Disease Detected"
        binding.healthStatusTitle.setTextColor(amberColor)
        binding.diseaseNameText.text = diseaseLabel
        binding.diseaseNameText.setTextColor(android.graphics.Color.parseColor("#92400E"))
        binding.healthScorePercentText.setTextColor(amberColor)
        binding.healthScoreProgressBar.progressTintList = ColorStateList.valueOf(amberColor)
    }

    private fun showCriticalDiagnosisUI(result: DiseaseAnalysisResult, score: Int) {
        val redColor = ContextCompat.getColor(requireContext(), R.color.error)
        val redBg = ContextCompat.getColor(requireContext(), R.color.error_background)

        val diseaseLabel = result.diseaseName?.takeIf {
            it.isNotBlank() && !it.contains("None", true) && !it.contains("Healthy", true) && !it.contains("No disease", true)
        } ?: "Cercospora Leaf Spot / Early Blight"

        binding.healthStatusCard.setCardBackgroundColor(redBg)
        binding.healthStatusIcon.text = "🚨"
        binding.healthStatusTitle.text = "Critical Disease Detected"
        binding.healthStatusTitle.setTextColor(redColor)
        binding.diseaseNameText.text = diseaseLabel
        binding.diseaseNameText.setTextColor(android.graphics.Color.parseColor("#991B1B"))
        binding.healthScorePercentText.setTextColor(redColor)
        binding.healthScoreProgressBar.progressTintList = ColorStateList.valueOf(redColor)
    }

    private fun bindAdvancedMetrics(
        plant: Plant,
        top3Names: List<String>,
        top3Confs: List<Float>,
        top5Commons: List<String>,
        top5Scientifics: List<String>,
        top5Confidences: FloatArray
    ) {
        val matchesToBind = top5Commons.ifEmpty { listOf(plant.name) + top3Names }
        val confsToBind = if (top5Confidences.isNotEmpty()) top5Confidences.toList() else listOf(confidenceScore) + top3Confs

        binding.top3Row1Name.text = "1. ${matchesToBind.getOrNull(0) ?: plant.name}"
        val pct1 = ((confsToBind.getOrNull(0) ?: confidenceScore) * 100).toInt()
        binding.top3Row1Percent.text = "$pct1%"
        binding.top3Row1Progress.progress = pct1

        if (matchesToBind.size > 1) {
            binding.top3Row2.visibility = View.VISIBLE
            binding.top3Row2Name.text = "2. ${matchesToBind[1]}"
            val pct2 = (confsToBind[1] * 100).toInt()
            binding.top3Row2Percent.text = "$pct2%"
            binding.top3Row2Progress.progress = pct2
        } else {
            binding.top3Row2.visibility = View.GONE
        }

        if (matchesToBind.size > 2) {
            binding.top3Row3.visibility = View.VISIBLE
            binding.top3Row3Name.text = "3. ${matchesToBind[2]}"
            val pct3 = (confsToBind[2] * 100).toInt()
            binding.top3Row3Percent.text = "$pct3%"
            binding.top3Row3Progress.progress = pct3
        } else {
            binding.top3Row3.visibility = View.GONE
        }

        // Limit strictly to Top 3 predictions on screen: Hide Row 4 and Row 5
        binding.top3Row4.visibility = View.GONE
        binding.top3Row5.visibility = View.GONE
    }

    private fun setupListeners() {
        val plant = matchedPlant ?: return

        var lastClickTime = 0L
        binding.matchBadge.setOnClickListener {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastClickTime < 500) {
                showDeveloperBenchmarkDialog()
            }
            lastClickTime = currentTime
        }

        binding.saveToGardenButton.setOnClickListener {
            val isUnknown = plant.id == "unknown_plant" || plant.name.contains("Unrecognized", ignoreCase = true) || plant.name.contains("Unknown", ignoreCase = true) || plant.id.isEmpty()
            if (!isUnknown) {
                val nickname = binding.nicknameInput.text.toString().trim()
                viewModel.addPlantToGarden(plant, nickname)

                val diseaseResult = extractDiseaseAnalysisResult()

                viewModel.addDiseaseHistory(
                    plantId = plant.id,
                    diseaseName = diseaseResult.diseaseName ?: "None",
                    severity = diseaseResult.severity ?: "N/A",
                    confidence = diseaseResult.confidence ?: 0.0f,
                    treatment = diseaseResult.treatment ?: "",
                    healthScore = diseaseResult.healthScore,
                    healthStatus = diseaseResult.healthStatus,
                    observations = diseaseResult.observations ?: "",
                    recommendations = diseaseResult.recommendations ?: "",
                    assessmentMethod = diseaseResult.assessmentMethod,
                    plantName = plant.name
                )

                // Seed the initial growth timeline progression log
                viewModel.addGrowthUpdate(
                    plantId = plant.id,
                    heightCm = 12.0, // initial baseline height in cm
                    healthScore = diseaseResult.healthScore,
                    notes = "Initial classification & registration. ${diseaseResult.observations ?: ""}",
                    imagePath = "",
                    thumbnailPath = "",
                    assessmentMethod = diseaseResult.assessmentMethod
                )
            } else {
                Toast.makeText(requireContext(), getString(R.string.error_cannot_save_unrecognized), Toast.LENGTH_SHORT).show()
            }
        }

        binding.viewDetailsButton.setOnClickListener {
            val isUnknown = plant.id == "unknown_plant" || plant.name.contains("Unrecognized", ignoreCase = true) || plant.name.contains("Unknown", ignoreCase = true) || plant.id.isEmpty()
            if (!isUnknown) {
                val bundle = Bundle().apply {
                    putString("plantId", plant.id)
                }
                findNavController().navigate(R.id.action_result_to_details, bundle)
            }
        }

        binding.retryScanButton.setOnClickListener {
            findNavController().navigate(R.id.action_result_to_scanner)
        }

        binding.btnCorrect.setOnClickListener {
            val imageHash = arguments?.getString("image_hash") ?: ""
            viewModel.submitFeedback(
                imageHash = imageHash,
                predictedPlant = plant.name,
                actualPlant = plant.name,
                confidence = confidenceScore
            )
            Toast.makeText(requireContext(), getString(R.string.toast_feedback_thanks), Toast.LENGTH_SHORT).show()
            binding.feedbackCard.visibility = View.GONE
        }

        binding.btnIncorrect.setOnClickListener {
            val builder = com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            builder.setTitle(getString(R.string.dialog_feedback_title))
            val inputView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_feedback_input, null)
            val textInput = inputView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.feedbackActualNameInput)
            builder.setView(inputView)
            builder.setPositiveButton(getString(R.string.save)) { _, _ ->
                val actualName = textInput.text.toString().trim()
                if (actualName.isNotEmpty()) {
                    val imageHash = arguments?.getString("image_hash") ?: ""
                    viewModel.submitFeedback(
                        imageHash = imageHash,
                        predictedPlant = plant.name,
                        actualPlant = actualName,
                        confidence = confidenceScore
                    )
                    Toast.makeText(requireContext(), getString(R.string.toast_feedback_thanks), Toast.LENGTH_SHORT).show()
                    binding.feedbackCard.visibility = View.GONE
                } else {
                    Toast.makeText(requireContext(), getString(R.string.error_name_empty), Toast.LENGTH_SHORT).show()
                }
            }
            builder.show()
        }
        binding.speakDiagnosisButton.setOnClickListener {
            if (voiceDiagnosisManager?.isSpeaking() == true) {
                voiceDiagnosisManager?.stop()
                binding.speakDiagnosisIcon.text = "🔊"
                binding.speakDiagnosisText.text = "Listen"
            } else {
                val plantName = binding.resultPlantName.text.toString()
                val status = binding.healthStatusTitle.text.toString()
                val disease = binding.diseaseNameText.text.toString()
                val obs = binding.diseaseObservationsText.text.toString()
                val treatment = binding.treatmentPlanText.text.toString()
                val soilType = binding.soilTypeText.text.toString()
                val soilPh = binding.soilPhText.text.toString()
                val soilDrainage = binding.soilDrainageText.text.toString()
                val soilMix = binding.soilMixRecommendationText.text.toString()

                val speechText = buildString {
                    append(plantName).append(". ")
                    append(status).append(": ").append(disease).append(". ")
                    if (obs.isNotBlank()) append(obs).append(". ")
                    if (treatment.isNotBlank()) append(treatment).append(". ")
                    append("Soil requirements: ").append(soilType)
                    append(", pH ").append(soilPh)
                    append(", Drainage: ").append(soilDrainage).append(". ")
                    if (soilMix.isNotBlank()) append(soilMix)
                }

                binding.speakDiagnosisIcon.text = "⏹"
                binding.speakDiagnosisText.text = getString(R.string.speaking_label)

                voiceDiagnosisManager?.speak(speechText) { isSpeaking ->
                    activity?.runOnUiThread {
                        if (isSpeaking) {
                            binding.speakDiagnosisIcon.text = "⏹"
                            binding.speakDiagnosisText.text = getString(R.string.speaking_label)
                        } else {
                            binding.speakDiagnosisIcon.text = "🔊"
                            binding.speakDiagnosisText.text = getString(R.string.replay_label)
                        }
                    }
                }
            }
        }
    }

    private fun showDeveloperBenchmarkDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_dev_benchmark, null)

        val cropMode = arguments?.getString("crop_mode") ?: "CENTER_CROP"
        val cropQuality = arguments?.getString("crop_quality") ?: "Good"
        val validationScore = arguments?.getInt("validation_score") ?: 100
        val detectionTime = arguments?.getLong("detection_time") ?: 0L
        val classificationTime = arguments?.getLong("classification_time") ?: 0L
        val top5Commons = arguments?.getStringArrayList("top5_commons") ?: emptyList()
        val top5Scientifics = arguments?.getStringArrayList("top5_scientifics") ?: emptyList()
        val top5Confidences = arguments?.getFloatArray("top5_confidences") ?: floatArrayOf()

        // Persistent analytics stats
        val totalScans = arguments?.getInt("analytics_total_scans") ?: 0
        val successScans = arguments?.getInt("analytics_success_scans") ?: 0
        val rejectedScans = arguments?.getInt("analytics_rejected_scans") ?: 0
        val avgConfidence = arguments?.getFloat("analytics_avg_confidence") ?: 0.0f
        val avgTime = arguments?.getLong("analytics_avg_time") ?: 0L

        // Expanded telemetry fields
        val healthScore = arguments?.getInt("health_score") ?: 100
        val healthStatus = arguments?.getString("health_status") ?: "🟢 Healthy"
        val assessmentMethod = arguments?.getString("assessment_method") ?: "Health Assessment Engine"

        val reliabilityScoreVal = ((confidenceScore * 100).toInt() + validationScore) / 2
        val reliabilityString = when {
            reliabilityScoreVal >= 80 -> "Reliable"
            reliabilityScoreVal in 50..79 -> "Moderate Reliability"
            else -> "Low Reliability"
        }

        dialogView.findViewById<android.widget.TextView>(R.id.devDeviceModel).text = 
            "${Build.MANUFACTURER} ${Build.MODEL} | $reliabilityString ($reliabilityScoreVal%)"
        dialogView.findViewById<android.widget.TextView>(R.id.devCropType).text = "$cropMode ($cropQuality)"
        
        val detectionConf = if (cropMode == "CENTER_CROP" || top5Confidences.isEmpty()) "N/A" else {
            String.format(java.util.Locale.US, "%.1f%%", top5Confidences[0] * 100.0)
        }
        dialogView.findViewById<android.widget.TextView>(R.id.devDetectionConf).text = detectionConf

        dialogView.findViewById<android.widget.TextView>(R.id.devDetectionTime).text = "$detectionTime ms"
        dialogView.findViewById<android.widget.TextView>(R.id.devClassificationTime).text = "$classificationTime ms"
        dialogView.findViewById<android.widget.TextView>(R.id.devTotalLatency).text = "${detectionTime + classificationTime} ms"

        // Populate Persisted Analytics Diagnostics
        dialogView.findViewById<android.widget.TextView>(R.id.devAnalyticsTotal).text = totalScans.toString()
        dialogView.findViewById<android.widget.TextView>(R.id.devAnalyticsSuccess).text = successScans.toString()
        dialogView.findViewById<android.widget.TextView>(R.id.devAnalyticsRejected).text = rejectedScans.toString()
        dialogView.findViewById<android.widget.TextView>(R.id.devAnalyticsAvgConf).text = String.format(java.util.Locale.US, "%.1f%%", avgConfidence * 100.0)
        dialogView.findViewById<android.widget.TextView>(R.id.devAnalyticsAvgTime).text = "$avgTime ms"

        val predictionsBuilder = StringBuilder()
        predictionsBuilder.append("--- Health Diagnostic Diagnostics ---\n")
        predictionsBuilder.append("Assessment Method: $assessmentMethod\n")
        predictionsBuilder.append("Foliage Health Score: $healthScore/100 ($healthStatus)\n")
        predictionsBuilder.append("Scanner Validation Score: $validationScore/100\n")
        predictionsBuilder.append("\n--- Top-5 Species Probabilities ---\n")
        for (i in top5Commons.indices) {
            val name = top5Commons[i]
            val scientific = top5Scientifics.getOrNull(i) ?: ""
            val conf = (top5Confidences.getOrNull(i) ?: 0.0f) * 100.0
            predictionsBuilder.append("${i + 1}. $name ($scientific) - ${String.format(java.util.Locale.US, "%.1f%%", conf)}\n")
        }
        dialogView.findViewById<android.widget.TextView>(R.id.devPredictionsList).text = predictionsBuilder.toString().trim()

        val dialog = com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setView(dialogView)
            .create()

        dialogView.findViewById<android.widget.Button>(R.id.btnDismissBenchmark).setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun observeViewModel() {
        viewModel.gardenOperationState.observe(viewLifecycleOwner) { resource ->
            when (resource) {
                is Resource.Loading -> {
                    binding.saveToGardenButton.isEnabled = false
                    binding.saveToGardenButton.text = getString(R.string.saving_label)
                }
                is Resource.Success -> {
                    binding.saveToGardenButton.isEnabled = true
                    binding.saveToGardenButton.text = getString(R.string.save_plant_btn)
                    viewModel.resetGardenOperationState()

                    val commonName = matchedPlant?.name ?: "Plant"
                    val localizedPlantName = com.plantlens.ai.utils.TranslationManager.getPlantName(matchedPlant ?: Plant(id = "", name = commonName))
                    Toast.makeText(requireContext(), getString(R.string.toast_saved_success, localizedPlantName), Toast.LENGTH_SHORT).show()

                    if (findNavController().currentDestination?.id == R.id.plantResultFragment) {
                        findNavController().navigate(R.id.action_result_to_saved)
                    }
                }
                is Resource.Error -> {
                    binding.saveToGardenButton.isEnabled = true
                    binding.saveToGardenButton.text = getString(R.string.save_plant_btn)
                    Toast.makeText(requireContext(), resource.message, Toast.LENGTH_LONG).show()
                }
                null -> {}
            }
        }
    }

    override fun onPause() {
        super.onPause()
        voiceDiagnosisManager?.stop()
        _binding?.speakDiagnosisIcon?.text = "🔊"
        _binding?.speakDiagnosisText?.text = getString(R.string.listen_label)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        voiceDiagnosisManager?.shutdown()
        voiceDiagnosisManager = null
        _binding = null
    }
}
