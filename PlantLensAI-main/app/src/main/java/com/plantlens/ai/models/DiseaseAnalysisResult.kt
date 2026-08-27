package com.plantlens.ai.models

import java.io.Serializable

/**
 * Unified data contract for Plant Disease & Foliar Health diagnostics.
 * Supports complete null-safety and offline fallback modes.
 */
data class DiseaseAnalysisResult(
    val diseaseName: String? = null,
    val confidence: Float? = null,
    val severity: String? = null,
    val treatment: String? = null,
    val healthScore: Int = 100,
    val healthStatus: String = "Healthy",
    val observations: String? = null,
    val recommendations: String? = null,
    val assessmentMethod: String = "On-Device AI"
) : Serializable {

    val isHealthy: Boolean
        get() {
            val dName = diseaseName?.trim()?.lowercase() ?: ""
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

            val statusIsHealthy = healthStatus.lowercase().contains("healthy") ||
                healthStatus.lowercase().contains("optimal") ||
                healthStatus.lowercase().contains("normal")

            return isNoDiseaseNamed || statusIsHealthy || (healthScore >= 75 && !hasCriticalDiseaseKeyword(dName))
        }

    private fun hasCriticalDiseaseKeyword(dName: String): Boolean {
        return dName.contains("blight") || dName.contains("spot") || dName.contains("rust") ||
               dName.contains("mildew") || dName.contains("rot") || dName.contains("wilt") ||
               dName.contains("canker") || dName.contains("mosaic") || dName.contains("lesion")
    }

    val isWarning: Boolean
        get() = !isHealthy && (healthScore >= 50 || healthStatus.lowercase().contains("monitor") || healthStatus.lowercase().contains("attention") || healthStatus.lowercase().contains("mild"))

    val isCritical: Boolean
        get() = !isHealthy && !isWarning

    companion object {
        fun empty(): DiseaseAnalysisResult = DiseaseAnalysisResult()
    }
}

/**
 * UI State representation for disease diagnosis rendering.
 */
sealed class DiagnosisUIState {
    object Loading : DiagnosisUIState()
    data class Success(val data: DiseaseAnalysisResult) : DiagnosisUIState()
    object Empty : DiagnosisUIState()
    data class Error(val message: String) : DiagnosisUIState()
}
