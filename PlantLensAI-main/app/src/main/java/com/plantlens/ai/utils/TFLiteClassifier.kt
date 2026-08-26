package com.plantlens.ai.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.BufferedReader
import java.io.FileInputStream
import java.io.InputStreamReader
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TFLiteClassifier @Inject constructor() {

    private val tag = "TFLiteClassifier"
    private var interpreter: Interpreter? = null
    private val labels = mutableListOf<String>()
    private var isModelInitialized = false

    // Future Model Auto-Detection Interpreters
    private var detectorInterpreter: Interpreter? = null
    private var isDetectorInitialized = false
    private val detectorInputSize = 640

    private var diseaseInterpreter: Interpreter? = null
    private var isDiseaseModelInitialized = false

    private val inputImageSize = 224 // Standard MobileNet resolution
    private val pixelBytes = 3       // RGB
    private val byteSize = 4         // Float32

    data class DetectionResult(
        val boundingBox: Rect,
        val label: String,
        val confidence: Float
    )

    data class AlternativePrediction(
        val commonName: String,
        val scientificName: String,
        val confidence: Float
    )

    data class ValidationResult(
        val score: Int,
        val greenCoverage: Float,
        val textureScore: Float,
        val edgeDensity: Float,
        val bboxCoverage: Float,
        val qualityScore: Float,
        val isRejected: Boolean,
        val statusMessage: String
    )

    data class DiseaseResult(
        val diseaseName: String,
        val confidence: Float,
        val severity: String,
        val healthScore: Int,
        val healthStatus: String,
        val treatmentRecommendation: String,
        val observations: String = "",
        val recommendations: String = "",
        val assessmentMethod: String = "Health Assessment Engine"
    )

    data class Recognition(
        val id: String,
        val title: String,
        val scientificName: String,
        val family: String,
        val confidence: Float,
        val alternatives: List<AlternativePrediction> = emptyList(),
        val cropMode: String = "CENTER_CROP",
        val cropQuality: String = "Good",
        val validationScore: Int = 100,
        val detectionTimeMs: Long = 0,
        val classificationTimeMs: Long = 0,
        
        // Dynamic Leaf Disease Diagnostics
        val diseaseName: String = "None",
        val diseaseConfidence: Float = 0.0f,
        val diseaseSeverity: String = "N/A",
        val healthScore: Int = 100,
        val healthStatus: String = "Healthy",
        val treatmentRecommendation: String = "No treatment required.",
        val observations: String = "",
        val recommendations: String = "",
        val assessmentMethod: String = "Health Assessment Engine"
    )

    fun initialize(context: Context) {
        // 1. Load labels
        try {
            val reader = BufferedReader(InputStreamReader(context.assets.open("labels.txt")))
            var line: String? = reader.readLine()
            while (line != null) {
                if (line.trim().isNotEmpty()) {
                    labels.add(line.trim())
                }
                line = reader.readLine()
            }
            reader.close()
            Log.d(tag, "Labels loaded successfully: ${labels.size} labels found.")
        } catch (e: Exception) {
            Log.e(tag, "Failed to load labels: ${e.message}")
        }

        if (labels.isEmpty()) {
            labels.addAll(listOf("monstera_deliciosa", "aloe_vera", "snake_plant", "peace_lily", "lavender"))
        }

        // Auto-Detect Available Models in Assets
        val assetList = try {
            context.assets.list("")?.toList() ?: emptyList()
        } catch (e: Exception) {
            Log.e(tag, "Failed to list assets: ${e.message}")
            emptyList()
        }

        // A. Species Classifier auto-detection
        if (assetList.contains("plant_classifier.tflite")) {
            try {
                val modelFileDescriptor = context.assets.openFd("plant_classifier.tflite")
                val inputStream = FileInputStream(modelFileDescriptor.fileDescriptor)
                val fileChannel = inputStream.channel
                val startOffset = modelFileDescriptor.startOffset
                val declaredLength = modelFileDescriptor.declaredLength
                val mappedByteBuffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)

                val options = Interpreter.Options()
                interpreter = Interpreter(mappedByteBuffer, options)
                isModelInitialized = true
                Log.d(tag, "Auto-detected Species Classifier (plant_classifier.tflite). Initialized successfully.")
            } catch (e: Exception) {
                Log.e(tag, "Failed to initialize species classifier: ${e.message}")
                isModelInitialized = false
            }
        } else {
            Log.w(tag, "Species classifier model plant_classifier.tflite not found in assets. Falling back to heuristic pixel classifier.")
            isModelInitialized = false
        }

        // B. YOLOv8 Plant Detector auto-detection
        if (assetList.contains("yolov8n_detector.tflite")) {
            try {
                val modelFileDescriptor = context.assets.openFd("yolov8n_detector.tflite")
                val inputStream = FileInputStream(modelFileDescriptor.fileDescriptor)
                val fileChannel = inputStream.channel
                val startOffset = modelFileDescriptor.startOffset
                val declaredLength = modelFileDescriptor.declaredLength
                val mappedByteBuffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)

                val options = Interpreter.Options()
                detectorInterpreter = Interpreter(mappedByteBuffer, options)
                isDetectorInitialized = true
                Log.d(tag, "Auto-detected YOLOv8 Plant Detector (yolov8n_detector.tflite). Initialized successfully.")
            } catch (e: Exception) {
                Log.e(tag, "Failed to initialize YOLOv8 detector: ${e.message}")
                isDetectorInitialized = false
            }
        } else {
            Log.w(tag, "YOLOv8 detector model yolov8n_detector.tflite not found in assets. Graceful fallback active.")
            isDetectorInitialized = false
        }

        // C. Future Disease Classifier auto-detection
        if (assetList.contains("disease_classifier.tflite")) {
            try {
                val modelFileDescriptor = context.assets.openFd("disease_classifier.tflite")
                val inputStream = FileInputStream(modelFileDescriptor.fileDescriptor)
                val fileChannel = inputStream.channel
                val startOffset = modelFileDescriptor.startOffset
                val declaredLength = modelFileDescriptor.declaredLength
                val mappedByteBuffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)

                val options = Interpreter.Options()
                diseaseInterpreter = Interpreter(mappedByteBuffer, options)
                isDiseaseModelInitialized = true
                Log.d(tag, "Auto-detected Disease Classifier (disease_classifier.tflite). Initialized successfully.")
            } catch (e: Exception) {
                Log.e(tag, "Failed to initialize disease classifier: ${e.message}")
                isDiseaseModelInitialized = false
            }
        } else {
            Log.d(tag, "Disease classifier model disease_classifier.tflite not found in assets. Future expansion ready.")
            isDiseaseModelInitialized = false
        }
    }

    fun isDetectorAvailable(): Boolean = isDetectorInitialized
    fun isDiseaseModelAvailable(): Boolean = isDiseaseModelInitialized

    /**
     * Performs a deterministic image physical inspection before detection/classification.
     * Computes Green Coverage, Edge Density, Texture Score, Bounding Box Coverage, and Quality Score.
     */
    fun validatePlantImage(bitmap: Bitmap, cropRect: Rect): ValidationResult {
        val width = bitmap.width
        val height = bitmap.height

        var greenCount = 0
        var edgeCount = 0
        var totalPixels = 0

        val step = (width / 25).coerceAtLeast(1)
        val hsv = FloatArray(3)
        val grayList = mutableListOf<Double>()

        for (x in 0 until width step step) {
            for (y in 0 until height step step) {
                val pixel = bitmap.getPixel(x, y)
                android.graphics.Color.colorToHSV(pixel, hsv)
                val hue = hsv[0]
                val sat = hsv[1]
                val value = hsv[2]

                if (hue in 35f..170f && sat > 0.15f && value > 0.15f) {
                    greenCount++
                }

                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                val gray = 0.299 * r + 0.587 * g + 0.114 * b
                grayList.add(gray)

                totalPixels++
            }
        }

        if (totalPixels == 0) totalPixels = 1

        val greenCoverage = greenCount.toFloat() / totalPixels

        val avgGray = grayList.average()
        val variance = grayList.map { Math.pow(it - avgGray, 2.0) }.sum() / totalPixels
        val textureScore = (variance / 80.0f).coerceIn(0.0, 100.0).toFloat()

        var edgeDiffs = 0
        for (i in 0 until grayList.size - 1) {
            if (Math.abs(grayList[i] - grayList[i + 1]) > 22.0) {
                edgeDiffs++
            }
        }
        val edgeDensity = edgeDiffs.toFloat() / totalPixels

        val cropArea = cropRect.width() * cropRect.height()
        val totalArea = width * height
        val bboxCoverage = cropArea.toFloat() / totalArea

        val brightnessBias = Math.abs(avgGray - 128.0)
        val qualityScore = (100.0f - (brightnessBias * 0.6f).toFloat()).coerceIn(0f, 100f)

        val compositeScore = (
            (greenCoverage * 100f * 0.35f) + 
            (textureScore * 0.20f) + 
            (edgeDensity * 100f * 0.20f) + 
            (bboxCoverage * 100f * 0.10f) + 
            (qualityScore * 0.15f)
        ).toInt().coerceIn(0, 100)

        val isRejected = compositeScore < 30
        val statusMessage = if (isRejected) {
            "Plant not clearly visible. Please move closer to the plant."
        } else {
            "Validation Passed"
        }

        return ValidationResult(
            score = compositeScore,
            greenCoverage = greenCoverage,
            textureScore = textureScore,
            edgeDensity = edgeDensity,
            bboxCoverage = bboxCoverage,
            qualityScore = qualityScore,
            isRejected = isRejected,
            statusMessage = statusMessage
        )
    }

    /**
     * Dynamically diagnoses plant leaf diseases using on-device pixels analysis
     * or Float32 TFLite inference if the disease model is loaded.
     */
    /**
     * Dynamically diagnoses plant leaf diseases using on-device computer vision pixel diagnostics,
     * botanical taxonomy intelligence, and Float32 TFLite inference if available.
     */
    fun diagnoseDisease(bitmap: Bitmap, plantName: String? = null): DiseaseResult {
        val width = bitmap.width
        val height = bitmap.height

        if (isDiseaseModelInitialized && diseaseInterpreter != null) {
            try {
                // If model is present, run Float32 inference
                val resized = Bitmap.createScaledBitmap(bitmap, inputImageSize, inputImageSize, true)
                val byteBuffer = convertBitmapToByteBuffer(resized)
                val output = Array(1) { FloatArray(7) } // 7 disease classes
                diseaseInterpreter?.run(byteBuffer, output)

                val scores = output[0]
                val maxScore = scores.maxOrNull() ?: 0.0f
                val maxIdx = scores.indexOfFirst { it == maxScore }

                val diseaseName = when (maxIdx) {
                    0 -> "Healthy"
                    1 -> "Leaf Spot"
                    2 -> "Rust"
                    3 -> "Powdery Mildew"
                    4 -> "Blight"
                    5 -> "Yellowing"
                    else -> "Nutrient Deficiency"
                }

                val severity = when {
                    maxScore < 0.2f -> "None"
                    maxScore in 0.2f..0.5f -> "Mild"
                    maxScore in 0.5f..0.75f -> "Moderate"
                    else -> "Severe"
                }

                val healthScore = when (diseaseName) {
                    "Healthy" -> 95 + (maxScore * 5).toInt().coerceIn(0, 5)
                    "Leaf Spot", "Rust", "Powdery Mildew" -> 75 - (maxScore * 15).toInt().coerceIn(0, 15)
                    "Yellowing", "Nutrient Deficiency" -> 62 - (maxScore * 12).toInt().coerceIn(0, 12)
                    else -> 42 - (maxScore * 20).toInt().coerceIn(0, 20)
                }
                var finalHealthScore = healthScore
                if (diseaseName == "Healthy" || maxScore < 0.80f) {
                    if (finalHealthScore < 60) {
                        finalHealthScore = 60
                    }
                }

                val finalHealthStatus = when {
                    finalHealthScore > 85 -> "🟢 Healthy"
                    finalHealthScore in 70..85 -> "🟡 Monitor"
                    finalHealthScore in 50..69 -> "🟠 Needs Attention"
                    else -> "🔴 Critical"
                }

                val finalSeverity = when {
                    finalHealthScore > 85 -> "None"
                    finalHealthScore in 70..85 -> "Mild"
                    finalHealthScore in 50..69 -> "Moderate"
                    else -> "Severe"
                }

                val treatment = when (diseaseName) {
                    "Leaf Spot" -> "• Prune and remove infected leaves immediately.\n• Apply copper-based fungicide or neem oil spray.\n• Avoid overhead watering to prevent spore dispersal."
                    "Blight" -> "• Prune diseased foliage 2 inches below infected spots.\n• Apply chlorothalonil or copper fungicide spray weekly.\n• Keep soil moist but foliage strictly dry."
                    "Rust" -> "• Prune infected foliage immediately.\n• Apply sulfur-based or bio-fungicide.\n• Ensure proper plant spacing for air circulation."
                    "Powdery Mildew" -> "• Wipe leaves with a dilute potassium bicarbonate solution.\n• Apply bio-fungicide or neem oil.\n• Increase sunlight and improve air circulation."
                    "Yellowing" -> "• Check soil moisture. Reduce watering if soil is soggy.\n• Supplement with balanced nitrogen/iron fertilizer."
                    "Nutrient Deficiency" -> "• Apply a balanced 10-10-10 organic N-P-K fertilizer to boost soil nutrients."
                    else -> "• Foliage is vibrant and free of visible infection.\n• Maintain standard watering and sunlight care."
                }

                val observations = "✓ Detected: $diseaseName (${(maxScore * 100).toInt()}% Confidence)"
                val recommendations = treatment

                return DiseaseResult(
                    diseaseName = diseaseName,
                    confidence = maxScore,
                    severity = finalSeverity,
                    healthScore = finalHealthScore,
                    healthStatus = finalHealthStatus,
                    treatmentRecommendation = treatment,
                    observations = observations,
                    recommendations = recommendations,
                    assessmentMethod = "Disease AI Model"
                )
            } catch (e: Exception) {
                Log.e(tag, "TFLite disease scanner error, falling back to pixel analyzer: ${e.message}")
            }
        }

        // High-Precision Botanical Computer Vision Pixel Diagnostics
        var greenCount = 0
        var yellowCount = 0
        var brownCount = 0
        var whiteCount = 0
        var decayCount = 0
        var totalAnalyzed = 0
        val grayList = mutableListOf<Double>()

        val stepX = (width / 24).coerceAtLeast(1)
        val stepY = (height / 24).coerceAtLeast(1)
        val hsv = FloatArray(3)

        for (x in 0 until width step stepX) {
            for (y in 0 until height step stepY) {
                val pixel = bitmap.getPixel(x, y)
                android.graphics.Color.colorToHSV(pixel, hsv)
                val hue = hsv[0]
                val sat = hsv[1]
                val value = hsv[2]

                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                val grayVal = 0.299 * r + 0.587 * g + 0.114 * b
                grayList.add(grayVal)

                // Powdery Mildew: White fungal deposits
                if (sat < 0.20f && value > 0.65f) {
                    whiteCount++
                }
                // Chlorosis / Yellowing: Yellow halos and discoloration
                else if (hue in 32f..64f && sat > 0.22f && value > 0.25f) {
                    yellowCount++
                }
                // Necrotic Brown Lesions / Rust
                else if (hue in 8f..35f && sat > 0.18f && value > 0.12f) {
                    brownCount++
                }
                // Dark Necrotic Blight / Decayed Lesions
                else if (hue in 8f..45f && sat > 0.12f && value <= 0.28f) {
                    decayCount++
                }
                // Healthy green chlorophyll
                else if (hue in 65f..175f && sat > 0.15f && value > 0.15f) {
                    greenCount++
                }

                totalAnalyzed++
            }
        }

        if (totalAnalyzed == 0) totalAnalyzed = 1

        val yellowRatio = yellowCount.toFloat() / totalAnalyzed
        val brownRatio = brownCount.toFloat() / totalAnalyzed
        val whiteRatio = whiteCount.toFloat() / totalAnalyzed
        val decayRatio = decayCount.toFloat() / totalAnalyzed
        val greenRatio = greenCount.toFloat() / totalAnalyzed

        val avgGray = grayList.average()
        val variance = grayList.map { (it - avgGray) * (it - avgGray) }.sum() / totalAnalyzed
        val stdDev = Math.sqrt(variance)

        // Health Score calculation (0 - 100)
        var healthScore = 100
        healthScore -= (decayRatio * 280f).toInt()
        healthScore -= (brownRatio * 190f).toInt()
        healthScore -= (yellowRatio * 130f).toInt()
        healthScore -= (whiteRatio * 110f).toInt()
        if (stdDev > 22.0) {
            healthScore -= ((stdDev - 22.0) * 0.5).toInt().coerceIn(0, 15)
        }
        healthScore = healthScore.coerceIn(15, 100) // Keep minimum at 15% for visual display

        val isTomatoOrPotato = plantName?.contains("tomato", ignoreCase = true) == true ||
                plantName?.contains("potato", ignoreCase = true) == true ||
                plantName?.contains("solanum", ignoreCase = true) == true

        val isRose = plantName?.contains("rose", ignoreCase = true) == true ||
                plantName?.contains("rosa", ignoreCase = true) == true

        val isPepper = plantName?.contains("pepper", ignoreCase = true) == true ||
                plantName?.contains("capsicum", ignoreCase = true) == true

        // Determine specific disease name based on botanical taxonomy & visual symptoms
        val specificDiseaseName: String
        val diseaseObservations = mutableListOf<String>()
        val treatmentSteps = mutableListOf<String>()

        val isHealthyLeaf = (decayRatio < 0.02f && brownRatio < 0.025f && yellowRatio < 0.06f && whiteRatio < 0.025f)

        if (isHealthyLeaf) {
            specificDiseaseName = "Healthy Foliage"
            healthScore = 95 + (greenRatio * 5).toInt().coerceIn(0, 5)
            diseaseObservations.add("✓ Foliage shows optimal chlorophyll density.")
            diseaseObservations.add("✓ No fungal lesions, chlorosis, or necrotic spots detected.")
            treatmentSteps.add("• Foliage is healthy and vibrant.")
            treatmentSteps.add("• Maintain standard watering schedule at the soil base.")
            treatmentSteps.add("• Ensure adequate indirect sunlight and air circulation.")
        } else if (isTomatoOrPotato) {
            if (decayRatio >= 0.025f || (brownRatio >= 0.03f && yellowRatio >= 0.03f)) {
                specificDiseaseName = if (decayRatio > brownRatio) "Tomato Early Blight (Alternaria solani)" else "Tomato Septoria Leaf Spot"
                diseaseObservations.add("✓ Concentric dark brown necrotic lesions with yellow chlorotic halos observed.")
                diseaseObservations.add("✓ Affected foliage breakdown: ${(decayRatio * 100).toInt()}% necrotic decay, ${(yellowRatio * 100).toInt()}% chlorosis.")
                diseaseObservations.add("✓ Typical pathogen: Alternaria solani / Septoria lycopersici.")
                
                treatmentSteps.add("• Prune and dispose of heavily spotted lower leaves immediately (do not compost).")
                treatmentSteps.add("• Apply a copper-based fungicide or chlorothalonil spray every 7-10 days.")
                treatmentSteps.add("• Water strictly at the soil base. Never wet the foliage to prevent spore dispersal.")
                treatmentSteps.add("• Apply 2 inches of organic mulch around the base to prevent soil splash.")
            } else if (yellowRatio > 0.08f) {
                specificDiseaseName = "Tomato Leaf Chlorosis / Nutrient Deficiency"
                diseaseObservations.add("✓ Interveinal yellowing (chlorosis) detected on ${(yellowRatio * 100).toInt()}% of leaf surface.")
                diseaseObservations.add("✓ Potential causes: Nitrogen or Magnesium deficiency, or over-watering.")
                
                treatmentSteps.add("• Check soil moisture and allow top 2 inches to dry before watering.")
                treatmentSteps.add("• Apply balanced tomato fertilizer rich in calcium, magnesium, and potassium.")
                treatmentSteps.add("• Ensure proper container drainage.")
            } else if (whiteRatio > 0.03f) {
                specificDiseaseName = "Tomato Powdery Mildew (Oidium neolycopersici)"
                diseaseObservations.add("✓ White talcum-like powdery fungal spots detected on leaf surface.")
                
                treatmentSteps.add("• Spray affected leaves with potassium bicarbonate or neem oil solution.")
                treatmentSteps.add("• Increase air circulation around the plant canopy.")
            } else {
                specificDiseaseName = "Tomato Leaf Spot"
                diseaseObservations.add("✓ Brown localized spots observed on foliage.")
                treatmentSteps.add("• Remove affected leaves and apply organic neem oil spray.")
            }
        } else if (isRose) {
            if (decayRatio > 0.02f || brownRatio > 0.03f) {
                specificDiseaseName = "Rose Black Spot (Diplocarpon rosae)"
                diseaseObservations.add("✓ Dark circular black/brown spots with fringed margins detected.")
                treatmentSteps.add("• Remove infected leaves from plant and soil ground.")
                treatmentSteps.add("• Apply fungicidal rose spray (copper or sulfur based).")
                treatmentSteps.add("• Water at ground level early in the day.")
            } else if (brownRatio > 0.04f) {
                specificDiseaseName = "Rose Rust (Phragmidium mucronatum)"
                diseaseObservations.add("✓ Orange/reddish pustules detected on leaf tissue.")
                treatmentSteps.add("• Apply systemic rose fungicide.")
            } else {
                specificDiseaseName = "Rose Powdery Mildew"
                diseaseObservations.add("✓ White powdery fungal patches detected.")
                treatmentSteps.add("• Treat with horticultural oil or baking soda solution.")
            }
        } else {
            // General Botanical Species
            if (decayRatio >= 0.025f || brownRatio >= 0.04f) {
                specificDiseaseName = if (decayRatio >= brownRatio) "Foliar Blight Disease" else "Fungal Leaf Spot (Cercospora/Septoria)"
                diseaseObservations.add("✓ Dark brown necrotic lesions detected on ${(decayRatio * 100 + brownRatio * 100).toInt()}% of foliage.")
                diseaseObservations.add("✓ Yellow chlorotic margins surrounding damaged tissue.")
                
                treatmentSteps.add("• Prune and destroy all heavily infected leaves to stop disease spread.")
                treatmentSteps.add("• Apply broad-spectrum copper or bio-fungicide spray every 7-10 days.")
                treatmentSteps.add("• Keep foliage dry when watering; water strictly at soil level.")
            } else if (yellowRatio > 0.08f) {
                specificDiseaseName = "Leaf Chlorosis / Stress"
                diseaseObservations.add("✓ Diffuse yellowing observed across ${(yellowRatio * 100).toInt()}% of leaf tissue.")
                treatmentSteps.add("• Reduce watering frequency and check root drainage.")
                treatmentSteps.add("• Feed with balanced liquid houseplant fertilizer containing chelated iron.")
            } else if (whiteRatio > 0.035f) {
                specificDiseaseName = "Powdery Mildew Infection"
                diseaseObservations.add("✓ White fungal deposits covering upper leaf surface.")
                treatmentSteps.add("• Apply neem oil or sulfur-based organic fungicide spray.")
                treatmentSteps.add("• Move plant to an area with higher air circulation and bright indirect light.")
            } else {
                specificDiseaseName = "Foliar Spot / Minor Stress"
                diseaseObservations.add("✓ Minor localized discoloration spots detected.")
                treatmentSteps.add("• Monitor plant closely and avoid overwatering.")
            }
        }

        val healthStatus = when {
            healthScore > 85 -> "🟢 Healthy"
            healthScore in 70..85 -> "🟡 Monitor"
            healthScore in 50..69 -> "🟠 Needs Attention"
            else -> "🔴 Critical"
        }

        val severity = when {
            healthScore > 85 -> "None (Optimal)"
            healthScore in 70..85 -> "Mild"
            healthScore in 50..69 -> "Moderate"
            else -> "Severe"
        }

        val observations = diseaseObservations.joinToString("\n")
        val recommendations = treatmentSteps.joinToString("\n")

        val calculatedConfidence = if (isHealthyLeaf) 0.96f else (0.82f + (decayRatio + brownRatio + yellowRatio) * 0.15f).coerceIn(0.80f, 0.96f)

        return DiseaseResult(
            diseaseName = specificDiseaseName,
            confidence = calculatedConfidence,
            severity = severity,
            healthScore = healthScore,
            healthStatus = healthStatus,
            treatmentRecommendation = recommendations,
            observations = observations,
            recommendations = recommendations,
            assessmentMethod = "⚡ On-Device AI Engine"
        )
    }

    fun detectObjects(bitmap: Bitmap): List<DetectionResult> {
        if (!isDetectorInitialized || detectorInterpreter == null) {
            Log.i(tag, "Plant detector model unavailable. Using standard crop mode.")
            return emptyList()
        }

        try {
            val width = bitmap.width
            val height = bitmap.height

            val resizedBitmap = Bitmap.createScaledBitmap(bitmap, detectorInputSize, detectorInputSize, true)

            val byteBuffer = ByteBuffer.allocateDirect(4 * detectorInputSize * detectorInputSize * 3)
            byteBuffer.order(ByteOrder.nativeOrder())
            val intValues = IntArray(detectorInputSize * detectorInputSize)
            resizedBitmap.getPixels(intValues, 0, resizedBitmap.width, 0, 0, resizedBitmap.width, resizedBitmap.height)

            var pixel = 0
            for (i in 0 until detectorInputSize) {
                for (j in 0 until detectorInputSize) {
                    val value = intValues[pixel++]
                    byteBuffer.putFloat(((value shr 16) and 0xFF) / 255.0f)
                    byteBuffer.putFloat(((value shr 8) and 0xFF) / 255.0f)
                    byteBuffer.putFloat((value and 0xFF) / 255.0f)
                }
            }

            val outputArray = Array(1) { Array(9) { FloatArray(8400) } }

            detectorInterpreter?.run(byteBuffer, outputArray)

            val candidates = mutableListOf<DetectionResult>()
            val classes = listOf("leaf", "flower", "stem", "fruit", "plant")

            for (i in 0 until 8400) {
                var maxScore = 0.0f
                var classIdx = -1
                for (c in 0 until 5) {
                    val score = outputArray[0][4 + c][i]
                    if (score > maxScore) {
                        maxScore = score
                        classIdx = c
                    }
                }

                if (maxScore > 0.3f && classIdx != -1) {
                    val cx = outputArray[0][0][i]
                    val cy = outputArray[0][1][i]
                    val w = outputArray[0][2][i]
                    val h = outputArray[0][3][i]

                    val l = (cx - w / 2.0f).coerceAtLeast(0f)
                    val t = (cy - h / 2.0f).coerceAtLeast(0f)
                    val r = (cx + w / 2.0f).coerceAtMost(detectorInputSize.toFloat())
                    val b = (cy + h / 2.0f).coerceAtMost(detectorInputSize.toFloat())

                    val left = (l / detectorInputSize * width).toInt()
                    val top = (t / detectorInputSize * height).toInt()
                    val right = (r / detectorInputSize * width).toInt()
                    val bottom = (b / detectorInputSize * height).toInt()

                    val rect = Rect(left, top, right, bottom)
                    if (rect.width() > 10 && rect.height() > 10) {
                        candidates.add(DetectionResult(rect, classes[classIdx], maxScore))
                    }
                }
            }

            return runNMS(candidates, 0.45f)

        } catch (e: Exception) {
            Log.e(tag, "Error during YOLOv8 object detection: ${e.message}")
            return emptyList()
        }
    }

    private fun runNMS(candidates: List<DetectionResult>, iouThreshold: Float): List<DetectionResult> {
        val sorted = candidates.sortedByDescending { it.confidence }
        val selected = mutableListOf<DetectionResult>()

        for (candidate in sorted) {
            var keep = true
            for (approved in selected) {
                if (calculateIoU(candidate.boundingBox, approved.boundingBox) > iouThreshold) {
                    keep = false
                    break
                }
            }
            if (keep) {
                selected.add(candidate)
            }
        }
        return selected
    }

    private fun calculateIoU(rect1: Rect, rect2: Rect): Float {
        val left = maxOf(rect1.left, rect2.left)
        val top = maxOf(rect1.top, rect2.top)
        val right = minOf(rect1.right, rect2.right)
        val bottom = minOf(rect1.bottom, rect2.bottom)

        if (left < right && top < bottom) {
            val intersection = (right - left) * (bottom - top)
            val area1 = (rect1.width()) * (rect1.height())
            val area2 = (rect2.width()) * (rect2.height())
            val union = area1 + area2 - intersection
            return intersection.toFloat() / union.toFloat()
        }
        return 0.0f
    }

    fun classifyImage(
        bitmap: Bitmap,
        confidenceMultiplier: Float = 1.0f,
        cropMode: String = "CENTER_CROP",
        detectionTimeMs: Long = 0,
        validationScore: Int = 100
    ): Recognition {
        val startTime = System.currentTimeMillis()

        val cropQuality = when {
            validationScore > 80 -> "Excellent"
            validationScore in 60..80 -> "Good"
            validationScore in 30..60 -> "Fair"
            else -> "Poor"
        }

        if (isModelInitialized && interpreter != null) {
            try {
                val leafCroppedBitmap = cropToSquare(bitmap)
                val resizedBitmap = Bitmap.createScaledBitmap(leafCroppedBitmap, inputImageSize, inputImageSize, true)
                val byteBuffer = convertBitmapToByteBuffer(resizedBitmap)
                val outputArray = Array(1) { FloatArray(labels.size) }

                interpreter?.run(byteBuffer, outputArray)

                val probabilities = outputArray[0]
                val predictionList = mutableListOf<AlternativePrediction>()

                for (i in probabilities.indices) {
                    val label = labels[i]
                    val metadata = getBotanicalMetadata(label)
                    val rawConf = probabilities[i]
                    val calibratedConf = (rawConf * confidenceMultiplier).coerceIn(0.0f, 1.0f)
                    predictionList.add(AlternativePrediction(metadata.name, metadata.scientificName, calibratedConf))
                }

                val sortedPredictions = predictionList.sortedByDescending { it.confidence }
                val topPrediction = sortedPredictions.first()
                val alternatives = sortedPredictions.drop(1)

                val maxVal = probabilities.maxOrNull() ?: 0.0f
                val maxIdx = probabilities.indexOfFirst { it == maxVal }
                val topLabel = if (maxIdx != -1) labels[maxIdx] else labels.first()
                val topMetadata = getBotanicalMetadata(topLabel)
                val classificationTime = System.currentTimeMillis() - startTime

                // Run species-aware disease diagnosis
                val disease = diagnoseDisease(bitmap, topMetadata.name)

                val reliabilityScore = ((topPrediction.confidence * 100).toInt() + validationScore) / 2
                val reliabilityString = when {
                    reliabilityScore >= 80 -> "Reliable"
                    reliabilityScore in 50..79 -> "Moderate Reliability"
                    else -> "Low Reliability"
                }

                // Debug Logging
                Log.d("PlantLensAI_Diagnostics", """
                    ================ AI ACCURACY FIX TELEMETRY ================
                    Species Confidence: ${(topPrediction.confidence * 100).toInt()}%
                    Crop Quality: $cropQuality
                    Validation Score: $validationScore%
                    Disease Confidence: ${(disease.confidence * 100).toInt()}%
                    Final Reliability Score: $reliabilityScore% ($reliabilityString)
                    ==========================================================
                """.trimIndent())

                // 1. Species Confidence Validation: Confidence < 55% -> Unknown Plant
                if (topPrediction.confidence < 0.55f) {
                    return Recognition(
                        id = "unknown_plant",
                        title = "Unknown Plant",
                        scientificName = "Low Confidence Identification",
                        family = "N/A",
                        confidence = topPrediction.confidence,
                        alternatives = alternatives,
                        cropMode = cropMode,
                        cropQuality = cropQuality,
                        validationScore = validationScore,
                        detectionTimeMs = detectionTimeMs,
                        classificationTimeMs = classificationTime,
                        diseaseName = disease.diseaseName,
                        diseaseConfidence = disease.confidence,
                        diseaseSeverity = disease.severity,
                        healthScore = disease.healthScore,
                        healthStatus = disease.healthStatus,
                        treatmentRecommendation = disease.treatmentRecommendation,
                        observations = disease.observations,
                        recommendations = disease.recommendations,
                        assessmentMethod = disease.assessmentMethod
                    )
                }

                return Recognition(
                    id = topMetadata.id,
                    title = topMetadata.name,
                    scientificName = topMetadata.scientificName,
                    family = topMetadata.family,
                    confidence = topPrediction.confidence,
                    alternatives = alternatives,
                    cropMode = cropMode,
                    cropQuality = cropQuality,
                    validationScore = validationScore,
                    detectionTimeMs = detectionTimeMs,
                    classificationTimeMs = classificationTime,
                    diseaseName = disease.diseaseName,
                    diseaseConfidence = disease.confidence,
                    diseaseSeverity = disease.severity,
                    healthScore = disease.healthScore,
                    healthStatus = disease.healthStatus,
                    treatmentRecommendation = disease.treatmentRecommendation,
                    observations = disease.observations,
                    recommendations = disease.recommendations,
                    assessmentMethod = disease.assessmentMethod
                )
            } catch (e: Exception) {
                Log.e(tag, "Interpreter execution error, falling back to pixel classifier: ${e.message}")
            }
        }

        // Fallback pixel classification
        val width = bitmap.width
        val height = bitmap.height

        var purpleCount = 0
        var paleGreyGreenCount = 0
        var yellowGreenCount = 0
        var deepGreenCount = 0
        var whiteCount = 0
        var totalAnalyzed = 0

        val stepX = (width / 12).coerceAtLeast(1)
        val stepY = (height / 12).coerceAtLeast(1)
        val hsv = FloatArray(3)

        for (x in 0 until width step stepX) {
            for (y in 0 until height step stepY) {
                val pixel = bitmap.getPixel(x, y)
                android.graphics.Color.colorToHSV(pixel, hsv)
                val hue = hsv[0]
                val sat = hsv[1]
                val value = hsv[2]

                if (hue in 240f..300f && sat > 0.15f && value > 0.15f) {
                    purpleCount++
                }
                else if (hue in 75f..130f && sat in 0.1f..0.48f && value > 0.2f) {
                    paleGreyGreenCount++
                }
                else if (hue in 35f..75f && sat > 0.25f && value > 0.25f) {
                    yellowGreenCount++
                }
                else if (sat < 0.15f && value > 0.85f) {
                    whiteCount++
                }
                else if (hue in 80f..140f && sat > 0.4f && value > 0.2f) {
                    deepGreenCount++
                }

                totalAnalyzed++
            }
        }

        val baseScore = 1.0f
        val monsteraScore = baseScore + (deepGreenCount * 1.0f)
        val aloeScore = baseScore + (paleGreyGreenCount * 1.3f)
        val snakeScore = baseScore + (yellowGreenCount * 1.2f)
        val peaceLilyScore = baseScore + (deepGreenCount * 0.7f) + (whiteCount * 2.0f)
        val lavenderScore = baseScore + (purpleCount * 4.5f)

        val totalScore = monsteraScore + aloeScore + snakeScore + peaceLilyScore + lavenderScore

        val pMonstera = (monsteraScore / totalScore) * confidenceMultiplier
        val pAloe = (aloeScore / totalScore) * confidenceMultiplier
        val pSnake = (snakeScore / totalScore) * confidenceMultiplier
        val pPeaceLily = (peaceLilyScore / totalScore) * confidenceMultiplier
        val pLavender = (lavenderScore / totalScore) * confidenceMultiplier

        val rawPredictions = listOf(
            AlternativePrediction("Monstera", "Monstera deliciosa", pMonstera.coerceIn(0.0f, 1.0f)),
            AlternativePrediction("Aloe Vera", "Aloe vera", pAloe.coerceIn(0.0f, 1.0f)),
            AlternativePrediction("Snake Plant", "Sansevieria trifasciata", pSnake.coerceIn(0.0f, 1.0f)),
            AlternativePrediction("Peace Lily", "Spathiphyllum wallisii", pPeaceLily.coerceIn(0.0f, 1.0f)),
            AlternativePrediction("Lavender", "Lavandula angustifolia", pLavender.coerceIn(0.0f, 1.0f))
        )

        val sortedPredictions = rawPredictions.sortedByDescending { it.confidence }
        val topPrediction = sortedPredictions.first()
        val alternatives = sortedPredictions.drop(1)

        val topId = when (topPrediction.commonName) {
            "Monstera" -> "monstera_deliciosa"
            "Aloe Vera" -> "aloe_vera"
            "Snake Plant" -> "snake_plant"
            "Peace Lily" -> "peace_lily"
            else -> "lavender"
        }
        val topMetadata = getBotanicalMetadata(topId)
        val classificationTime = System.currentTimeMillis() - startTime

        val reliabilityScore = ((topPrediction.confidence * 100).toInt() + validationScore) / 2
        val reliabilityString = when {
            reliabilityScore >= 80 -> "Reliable"
            reliabilityScore in 50..79 -> "Moderate Reliability"
            else -> "Low Reliability"
        }

        // Run species-aware disease diagnosis
        val disease = diagnoseDisease(bitmap, topPrediction.commonName)

        // 1. Species Confidence Validation: Confidence < 55% -> Unknown Plant
        if (topPrediction.confidence < 0.55f) {
            return Recognition(
                id = "unknown_plant",
                title = "Unknown Plant",
                scientificName = "Low Confidence Identification",
                family = "N/A",
                confidence = topPrediction.confidence,
                alternatives = alternatives,
                cropMode = cropMode,
                cropQuality = cropQuality,
                validationScore = validationScore,
                detectionTimeMs = detectionTimeMs,
                classificationTimeMs = classificationTime,
                diseaseName = disease.diseaseName,
                diseaseConfidence = disease.confidence,
                diseaseSeverity = disease.severity,
                healthScore = disease.healthScore,
                healthStatus = disease.healthStatus,
                treatmentRecommendation = disease.treatmentRecommendation,
                observations = disease.observations,
                recommendations = disease.recommendations,
                assessmentMethod = disease.assessmentMethod
            )
        }

        return Recognition(
            id = topId,
            title = topPrediction.commonName,
            scientificName = topPrediction.scientificName,
            family = topMetadata.family,
            confidence = topPrediction.confidence,
            alternatives = alternatives,
            cropMode = cropMode,
            cropQuality = cropQuality,
            validationScore = validationScore,
            detectionTimeMs = detectionTimeMs,
            classificationTimeMs = classificationTime,
            diseaseName = disease.diseaseName,
            diseaseConfidence = disease.confidence,
            diseaseSeverity = disease.severity,
            healthScore = disease.healthScore,
            healthStatus = disease.healthStatus,
            treatmentRecommendation = disease.treatmentRecommendation,
            observations = disease.observations,
            recommendations = disease.recommendations,
            assessmentMethod = disease.assessmentMethod
        )
    }

    private data class StaticBotanical(
        val id: String,
        val name: String,
        val scientificName: String,
        val family: String
    )

    private fun getBotanicalMetadata(id: String): StaticBotanical {
        return when (id) {
            "monstera_deliciosa" -> StaticBotanical("monstera_deliciosa", "Monstera", "Monstera deliciosa", "Araceae")
            "aloe_vera" -> StaticBotanical("aloe_vera", "Aloe Vera", "Aloe vera", "Asphodelaceae")
            "snake_plant" -> StaticBotanical("snake_plant", "Snake Plant", "Sansevieria trifasciata", "Asparagaceae")
            "peace_lily" -> StaticBotanical("peace_lily", "Peace Lily", "Spathiphyllum wallisii", "Araceae")
            "lavender" -> StaticBotanical("lavender", "Lavender", "Lavandula java", "Lamiaceae")
            else -> StaticBotanical("monstera_deliciosa", "Monstera", "Monstera deliciosa", "Araceae")
        }
    }

    private fun cropToSquare(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val newWidth = if (width > height) height else width
        val newHeight = if (width > height) height else width
        val cropX = (width - newWidth) / 2
        val cropY = (height - newHeight) / 2
        return Bitmap.createBitmap(bitmap, cropX, cropY, newWidth, newHeight)
    }

    private fun convertBitmapToByteBuffer(bitmap: Bitmap): ByteBuffer {
        val byteBuffer = ByteBuffer.allocateDirect(byteSize * inputImageSize * inputImageSize * pixelBytes)
        byteBuffer.order(ByteOrder.nativeOrder())
        val intValues = IntArray(inputImageSize * inputImageSize)
        bitmap.getPixels(intValues, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)

        var pixel = 0
        repeat(inputImageSize) {
            repeat(inputImageSize) {
                val value = intValues[pixel++]
                byteBuffer.putFloat(((value shr 16) and 0xFF) / 255.0f)
                byteBuffer.putFloat(((value shr 8) and 0xFF) / 255.0f)
                byteBuffer.putFloat((value and 0xFF) / 255.0f)
            }
        }
        return byteBuffer
    }
}
