package com.plantlens.ai.network

import android.graphics.Bitmap
import android.util.Log
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.plantlens.ai.utils.TFLiteClassifier
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GeminiPlantService @Inject constructor(
    private val generativeModel: GenerativeModel,
    private val classifier: TFLiteClassifier
) {
    private val TAG = "GeminiPlantService"

    suspend fun diagnosePlant(
        bitmap: Bitmap,
        identifiedPlantName: String? = null,
        scientificName: String? = null
    ): ClassificationResponse {
        val isPreIdentified = !identifiedPlantName.isNullOrBlank()
        
        val prompt = """
        You are an expert botanical pathologist and plant disease recognition AI.

        FIRST MANDATORY STEP:
        Determine if this image actually contains a real plant, tree, flower, leaf, succulent, shrub, fruit, crop, or botanical subject.
        
        - If the image contains a PERSON, human face, human skin, hands, body, animal, pet, vehicle, electronic gadget, furniture, clothing, food dish, or any non-plant object:
          You MUST set "is_plant": false, "success": false, and "error_message": "No plant detected in this photo. Please aim the camera directly at a plant, flower, or leaf."
          Leave plant fields blank or set to "Not a plant".
          
        - If the image DOES contain a plant, flower, tree, or leaf:
          Set "is_plant": true, "success": true, and "error_message": "".
          Carefully examine the leaf surface, foliar spots, margins, and stems for any pathological symptoms:
          - Small circular dark brown/black spots with tan/gray centers and distinct bright yellow halos (e.g. Septoria Leaf Spot (Septoria lycopersici), Early Blight (Alternaria solani), Bacterial Spot (Xanthomonas)).
          - Large necrotic blight lesions with concentric target rings or tissue decay (e.g. Early Blight, Late Blight, Anthracnose).
          - White powdery or velvety fungal patches (e.g. Powdery Mildew, Downy Mildew, Leaf Mold).
          - Orange/reddish pustules (e.g. Rust).
          - Diffuse interveinal yellowing (pure nutrient chlorosis) without any necrotic spots.

          CRITICAL PATHOLOGY RULE:
          If the foliage shows dark brown or black necrotic spots with yellow halos (such as on tomato, rose, or garden foliage), do NOT diagnose simple chlorosis or watering stress. You MUST diagnose the specific fungal/bacterial leaf spot disease (e.g. 'Tomato Septoria Leaf Spot (Septoria lycopersici)', 'Tomato Early Blight (Alternaria solani)', 'Rose Black Spot', 'Bacterial Leaf Spot').
          
          If symptoms are present:
          - Set "health_status": "Diseased" or "Critical".
          - Provide the precise common and scientific disease name (e.g. 'Tomato Septoria Leaf Spot (Septoria lycopersici)', 'Tomato Early Blight (Alternaria solani)', 'Rose Black Spot (Diplocarpon rosae)').
          - In "treatment", give clear step-by-step actionable remedies (pruning infected leaves, no overhead watering, mulch, copper-based or chlorothalonil fungicide spray).
          - In "description", detail visible lesion morphology and yellow chlorotic halos.
          
          If foliage is completely clean, green, and disease-free:
          - Set "health_status": "Healthy".
          - Set "disease": "None (Optimal Foliage)".

        Return ONLY a raw JSON object (strictly NO markdown, NO ```json, NO backticks) matching this format:
        {
          "is_plant": true or false,
          "success": true or false,
          "error_message": "",
          "plant_name": "Most familiar common name (or 'Not a plant')",
          "scientific_name": "Latin Scientific Name (or '')",
          "confidence": 0.95,
          "health_status": "Healthy" or "Diseased" or "Critical",
          "disease": "Specific Disease Name (e.g. 'Tomato Early Blight' or 'None (Optimal Foliage)')",
          "description": "2-3 sentences detailing visible foliar symptoms",
          "treatment": "Actionable treatment and fungicide remedies",
          "prevention": "Preventive cultural practices",
          "watering": "Precise watering instructions",
          "sunlight": "Sunlight requirements",
          "fertilizer": "Fertilizer recommendations"
        }
        """.trimIndent()

        try {
            Log.d(TAG, "Sending prompt to Gemini 1.5 Flash (isPreIdentified=$isPreIdentified, name=$identifiedPlantName)...")
            val response = generativeModel.generateContent(
                content {
                    image(bitmap)
                    text(prompt)
                }
            )

            val rawText = response.text ?: ""
            Log.d(TAG, "Gemini Response received: $rawText")
            return parseGeminiJson(rawText, identifiedPlantName, scientificName, bitmap)
        } catch (e: Exception) {
            Log.w(TAG, "Gemini analysis unavailable (${e.message}). Utilizing On-Device Botanical AI Engine.")
            return buildFallbackResponse(bitmap, identifiedPlantName, scientificName, e.message)
        }
    }

    private fun parseGeminiJson(
        raw: String, 
        fallbackName: String?, 
        fallbackSciName: String?,
        bitmap: Bitmap
    ): ClassificationResponse {
        try {
            // Clean any potential markdown backticks or formatting
            var cleaned = raw.trim()
            if (cleaned.startsWith("```json")) {
                cleaned = cleaned.removePrefix("```json")
            } else if (cleaned.startsWith("```")) {
                cleaned = cleaned.removePrefix("```")
            }
            if (cleaned.endsWith("```")) {
                cleaned = cleaned.removeSuffix("```")
            }
            cleaned = cleaned.trim()

            val firstBrace = cleaned.indexOf('{')
            val lastBrace = cleaned.lastIndexOf('}')
            if (firstBrace != -1 && lastBrace != -1 && lastBrace > firstBrace) {
                cleaned = cleaned.substring(firstBrace, lastBrace + 1)
            }

            val json = JSONObject(cleaned)
            val isPlant = json.optBoolean("is_plant", true)
            val success = json.optBoolean("success", isPlant)
            val errorMessage = json.optString("error_message")

            if (!isPlant || !success) {
                return ClassificationResponse(
                    success = false,
                    is_plant = false,
                    error_message = if (errorMessage.isNotBlank()) errorMessage else "No plant detected. Please aim at a plant, leaf, or flower.",
                    plant_name = "Not a plant",
                    scientific_name = "",
                    confidence = 0.0,
                    health_status = "Unknown",
                    disease = "None"
                )
            }

            val plantName = json.optString("plant_name").ifBlank { fallbackName ?: "Identified Plant" }
            val sciName = json.optString("scientific_name").ifBlank { fallbackSciName ?: "Botanical Species" }
            val confidence = json.optDouble("confidence", 0.92)
            var healthStatus = json.optString("health_status").ifBlank { "Healthy" }
            var disease = json.optString("disease").ifBlank { "None (Healthy Foliage)" }
            var description = json.optString("description").ifBlank { "Plant foliage is vibrant and free of visible infection." }
            var treatment = json.optString("treatment").ifBlank { "Maintain regular plant care and optimal sunlight." }
            val prevention = json.optString("prevention").ifBlank { "Ensure adequate air circulation and clean drainage." }
            val watering = json.optString("watering").ifBlank { "Water when top 2 inches of soil feel dry." }
            val sunlight = json.optString("sunlight").ifBlank { "Bright indirect light." }
            val fertilizer = json.optString("fertilizer").ifBlank { "Apply balanced fertilizer monthly during growing season." }

            val isNoDisease = disease.contains("None", ignoreCase = true) ||
                    disease.contains("Healthy", ignoreCase = true) ||
                    disease.contains("No disease", ignoreCase = true) ||
                    disease.contains("Optimal", ignoreCase = true) ||
                    disease.contains("Not detected", ignoreCase = true) ||
                    healthStatus.contains("Healthy", ignoreCase = true)

            if (isNoDisease) {
                healthStatus = "Healthy"
                disease = "None (Healthy Foliage)"
                if (treatment.contains("fungicide", ignoreCase = true) || treatment.contains("prune diseased", ignoreCase = true)) {
                    treatment = "Foliage is vibrant and free of visible infection. Maintain standard watering and sunlight care."
                }
            }

            return ClassificationResponse(
                success = true,
                is_plant = true,
                error_message = "",
                plant_name = plantName,
                scientific_name = sciName,
                confidence = confidence,
                health_status = healthStatus,
                disease = disease,
                description = description,
                treatment = treatment,
                watering = watering,
                sunlight = sunlight,
                fertilizer = fertilizer,
                prevention = prevention
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse JSON directly, using on-device CV fallback: ${e.message}")
            return buildFallbackResponse(bitmap, fallbackName, fallbackSciName, "Parsed with fallback")
        }
    }

    private fun buildFallbackResponse(
        bitmap: Bitmap,
        name: String?, 
        sciName: String?,
        errorMsg: String?
    ): ClassificationResponse {
        val safeName = name ?: "Identified Plant"
        val safeSci = sciName ?: "Botanical Species"
        val diagnosis = classifier.diagnoseDisease(bitmap, safeName)

        return ClassificationResponse(
            success = true,
            is_plant = true,
            error_message = "",
            plant_name = safeName,
            scientific_name = safeSci,
            confidence = diagnosis.confidence.toDouble(),
            health_status = diagnosis.healthStatus,
            disease = diagnosis.diseaseName,
            description = diagnosis.observations,
            treatment = diagnosis.treatmentRecommendation,
            watering = "Water moderately at soil base when top inch of soil is dry.",
            sunlight = "Provide moderate to bright indirect sunlight.",
            fertilizer = "Feed with balanced houseplant fertilizer once a month.",
            prevention = diagnosis.recommendations
        )
    }
}
