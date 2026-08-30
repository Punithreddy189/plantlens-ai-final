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
    val assessmentMethod: String = "On-Device AI",
    val soilType: String? = "Loamy soil",
    val soilPh: String? = "6.0 - 7.0",
    val soilDrainage: String? = "Well-drained",
    val soilRecommendation: String? = "Mix garden soil with compost and sand.",
    val confidenceReason: String? = null
) : Serializable {

    val isHealthy: Boolean
        get() {
            val sev = severity?.trim()?.lowercase() ?: ""
            return sev.contains("none") || sev.contains("optimal")
        }

    val isWarning: Boolean
        get() {
            val sev = severity?.trim()?.lowercase() ?: ""
            return !isHealthy && (sev.contains("low") || sev.contains("moderate") || sev.contains("medium"))
        }

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
