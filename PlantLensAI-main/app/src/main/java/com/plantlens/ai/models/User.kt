package com.plantlens.ai.models

import com.google.firebase.firestore.IgnoreExtraProperties
import java.io.Serializable

@IgnoreExtraProperties
data class User(
    val uid: String = "",
    val email: String = "",
    val displayName: String = "",
    val photoUrl: String = "",
    val totalPlantsSaved: Int = 0,
    val totalScans: Int = 0,
    val lastScanDate: Long = 0L,
    val mostScannedPlant: String = "None",
    val role: String = "user"
) : Serializable
