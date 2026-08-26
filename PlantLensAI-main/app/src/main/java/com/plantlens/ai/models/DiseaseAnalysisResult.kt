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
        get() = healthScore >= 80 && (
            diseaseName.isNullOrBlank() ||
            diseaseName.equals("None", ignoreCase = true) ||
            diseaseName.equals("Healthy", ignoreCase = true)
        )

    val isWarning: Boolean
        get() = !isHealthy && healthScore >= 50

    val isCritical: Boolean
        get() = !isHealthy && healthScore < 50

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
