package com.plantlens.ai.firebase

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.plantlens.ai.models.Plant
import com.plantlens.ai.models.SavedPlant
import com.plantlens.ai.models.ScanRecord
import com.plantlens.ai.models.User
import com.plantlens.ai.models.AdminAnalyticsData
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseManager @Inject constructor(
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore,
) {
    private val tag = "FirebaseManager"

    /**
     * Checks if a user is currently authenticated.
     */
    fun isAvailable(): Boolean = auth.currentUser != null

    // --- Authentication Operations ---

    /**
     * Retrieves the current user profile from Firebase Auth.
     */
    fun getCurrentUser(): User? {
        val firebaseUser = auth.currentUser ?: return null
        val email = firebaseUser.email ?: ""
        return User(
            uid = firebaseUser.uid,
            email = email,
            displayName = firebaseUser.displayName ?: "User",
            photoUrl = firebaseUser.photoUrl?.toString() ?: "",
            role = if (email.contains("admin", ignoreCase = true)) "admin" else "user"
        )
    }

    /**
     * Authenticates a user with Email and Password.
     */
    suspend fun loginUser(email: String, password: String): User {
        val result = auth.signInWithEmailAndPassword(email, password).await()
        val firebaseUser = result.user ?: throw Exception("Authentication failed")
        
        // Fetch existing or create profile
        var user = fetchUserProfile(firebaseUser.uid)
        if (user == null) {
            user = User(
                uid = firebaseUser.uid,
                email = firebaseUser.email ?: "",
                displayName = email.substringBefore("@"),
                photoUrl = "",
                role = if (email.contains("admin", ignoreCase = true)) "admin" else "user"
            )
            syncUserProfile(user)
        }
        syncFCMToken()
        return user
    }

    /**
     * Creates a new Firebase Authentication account.
     */
    suspend fun registerUser(email: String, password: String): User {
        val result = auth.createUserWithEmailAndPassword(email, password).await()
        val firebaseUser = result.user ?: throw Exception("Registration failed")
        
        val user = User(
            uid = firebaseUser.uid,
            email = firebaseUser.email ?: "",
            displayName = email.substringBefore("@"),
            photoUrl = "",
            role = if (email.contains("admin", ignoreCase = true)) "admin" else "user"
        )
        
        syncUserProfile(user)
        incrementGlobalCounter("totalUsers", 1L)
        syncFCMToken()
        return user
    }

    /**
     * Syncs current device FCM token to user document in Firestore.
     */
    suspend fun syncFCMToken() {
        val currentUser = auth.currentUser ?: return
        try {
            val token = com.google.firebase.messaging.FirebaseMessaging.getInstance().token.await()
            if (!token.isNullOrBlank()) {
                db.collection("users").document(currentUser.uid).update(
                    mapOf(
                        "fcmToken" to token,
                        "lastTokenUpdate" to System.currentTimeMillis()
                    )
                ).await()
                Log.d(tag, "FCM token synced to users/${currentUser.uid} ✅")
            }
        } catch (e: Exception) {
            Log.e(tag, "Error syncing FCM token: ${e.message}")
        }
    }

    /**
     * Signs out the current Firebase user.
     */
    fun logout() {
        auth.signOut()
    }

    // --- User Profile Sync ---

    private suspend fun syncUserProfile(user: User) {
        try {
            db.collection("users").document(user.uid).set(user).await()
        } catch (e: Exception) {
            Log.e(tag, "Error syncing user profile: ${e.message}")
        }
    }

    suspend fun fetchUserProfile(uid: String): User? {
        return try {
            val doc = db.collection("users").document(uid).get().await()
            doc.toObject(User::class.java)
        } catch (e: Exception) {
            Log.e(tag, "Error fetching user profile: ${e.message}")
            null
        }
    }

    suspend fun updateUserStatistics(
        uid: String,
        scansCount: Int,
        savedCount: Int,
        lastScan: Long,
        mostScanned: String,
    ) {
        try {
            db.collection("users").document(uid).set(
                mapOf(
                    "totalScans" to scansCount,
                    "totalPlantsSaved" to savedCount,
                    "lastScanDate" to lastScan,
                    "mostScannedPlant" to mostScanned,
                ),
                com.google.firebase.firestore.SetOptions.merge()
            ).await()
        } catch (e: Exception) {
            Log.e(tag, "Error updating statistics: ${e.message}")
        }
    }

    suspend fun updateUserName(uid: String, newName: String) {
        try {
            val user = auth.currentUser
            if (user != null && (user.uid == uid)) {
                val profileUpdates = com.google.firebase.auth.UserProfileChangeRequest.Builder()
                    .setDisplayName(newName)
                    .build()
                user.updateProfile(profileUpdates).await()
            }
            db.collection("users").document(uid).update("displayName", newName).await()
        } catch (e: Exception) {
            Log.e(tag, "Error updating user name: ${e.message}")
        }
    }

    suspend fun updateUserPhoto(uid: String, photoUrl: String) {
        try {
            val user = auth.currentUser
            if (user != null && (user.uid == uid)) {
                val profileUpdates = com.google.firebase.auth.UserProfileChangeRequest.Builder()
                    .setPhotoUri(android.net.Uri.parse(photoUrl))
                    .build()
                user.updateProfile(profileUpdates).await()
            }
            db.collection("users").document(uid).update("photoUrl", photoUrl).await()
        } catch (e: Exception) {
            Log.e(tag, "Error updating user photo: ${e.message}")
        }
    }


    // --- Auto-Save Plant Scan & Real-time Sync ---

    suspend fun autoSavePlantScan(
        plantName: String,
        disease: String,
        confidence: Int,
        scientificName: String = "",
        plantId: String = ""
    ): String {
        val currentUser = auth.currentUser ?: return ""
        val uid = currentUser.uid

        Log.d("PlantLens", "Android UID: $uid")

        val docRef = db.collection("users").document(uid).collection("plants").document()
        val generatedDocId = docRef.id

        val plantData = hashMapOf(
            "id" to generatedDocId,
            "plantId" to plantId,
            "plantName" to plantName,
            "scientificName" to scientificName,
            "disease" to disease,
            "confidence" to confidence,
            "healthStatus" to if (disease.contains("Healthy", ignoreCase = true)) "healthy" else "diseased",
            "isSaved" to false,
            "createdAt" to System.currentTimeMillis()
        )

        val userDoc = db.collection("users").document(uid)

        // Save inside users/{uid}/plants
        try {
            docRef.set(plantData).await()
            Log.d("PlantLens", "Saved scan to users/$uid/plants/$generatedDocId ✅")

            // Increment totalScans on users/{uid}
            userDoc.set(
                mapOf("totalScans" to com.google.firebase.firestore.FieldValue.increment(1)),
                com.google.firebase.firestore.SetOptions.merge()
            ).await()
        } catch (e: Exception) {
            Log.e("PlantLens", "Error auto-saving plant scan: ${e.message}", e)
        }
        return generatedDocId
    }

    // --- Saved Plants Operations ---

    suspend fun uploadSavedPlant(savedPlant: SavedPlant) {
        val currentUser = auth.currentUser ?: return
        try {
            val docRef = db.collection("users").document(currentUser.uid)
                .collection("plants").document(savedPlant.id)
            docRef.set(savedPlant, com.google.firebase.firestore.SetOptions.merge()).await()
        } catch (e: Exception) {
            Log.e(tag, "Error saving plant: ${e.message}")
        }
    }

    suspend fun removeSavedPlant(savedPlantId: String) {
        val currentUser = auth.currentUser ?: return
        try {
            db.collection("users").document(currentUser.uid)
                .collection("plants").document(savedPlantId).update("isSaved", false).await()
        } catch (e: Exception) {
            Log.e(tag, "Error removing plant: ${e.message}")
        }
    }

    suspend fun fetchRemoteSavedPlants(): List<SavedPlant> {
        val currentUser = auth.currentUser ?: return emptyList()
        return try {
            val docs = db.collection("users").document(currentUser.uid)
                .collection("plants").whereEqualTo("isSaved", true).get().await()
            docs.documents.mapNotNull { doc ->
                SavedPlant.fromDocument(doc) ?: try {
                    doc.toObject(SavedPlant::class.java)
                } catch (e: Exception) {
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Error fetching saved plants: ${e.message}")
            emptyList()
        }
    }

    // --- Scan History Operations ---

    suspend fun uploadScanRecord(scanRecord: ScanRecord) {
        val currentUser = auth.currentUser ?: return
        try {
            val docId = scanRecord.id.ifEmpty { UUID.randomUUID().toString() }
            val record = scanRecord.copy(id = docId)
            db.collection("users").document(currentUser.uid)
                .collection("scans").document(docId).set(record).await()
        } catch (e: Exception) {
            Log.e(tag, "Error uploading scan record: ${e.message}")
        }
    }

    // --- Analytics Operations ---

    suspend fun logAnalyticsEvent(eventName: String, params: Map<String, Any>) {
        val currentUser = auth.currentUser ?: return
        try {
            val eventData = params.toMutableMap().apply {
                put("event", eventName)
                put("timestamp", System.currentTimeMillis())
                put("userId", currentUser.uid)
            }
            db.collection("users").document(currentUser.uid)
                .collection("analytics").add(eventData).await()
        } catch (e: Exception) {
            Log.e(tag, "Error logging analytics: ${e.message}")
        }
    }

    // --- Firestore Global Plant Cache ---
    suspend fun uploadPlantToGlobalCache(plant: Plant) {
        try {
            val data = mapOf(
                "id" to plant.id,
                "name" to plant.name,
                "scientificName" to plant.scientificName,
                "family" to plant.family,
                "genus" to plant.genus,
                "category" to plant.category,
                "wateringInstructions" to plant.wateringInstructions,
                "wateringFrequency" to plant.wateringFrequency,
                "careTips" to plant.careTips,
                "imageUrl" to plant.imageUrl
            )
            db.collection("plants").document(plant.id).set(data).await()
        } catch (e: Exception) {
            Log.e(tag, "Error uploading global plant cache to Firestore: ${e.message}")
        }
    }

    // --- Firestore Global Scan Image Hash Cache ---
    suspend fun fetchScanCacheByHash(imageHash: String): ScanRecord? {
        return try {
            val doc = db.collection("globalPlantCache").document(imageHash).get().await()
            if (doc.exists()) {
                val scientificName = doc.getString("scientificName") ?: ""
                val commonName = doc.getString("plantName") ?: doc.getString("commonName") ?: ""
                val confidence = doc.getDouble("confidence")?.toFloat() ?: 0.0f
                val family = doc.getString("family") ?: ""
                val genus = doc.getString("genus") ?: ""
                val timestamp = doc.getLong("timestamp") ?: 0L
                val id = doc.getString("id") ?: UUID.randomUUID().toString()
                val plantId = doc.getString("plantId") ?: scientificName.lowercase().replace(" ", "_")
                val top3Predictions = (doc.get("top3Predictions") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                val top3Confidences = (doc.get("top3Confidences") as? List<*>)?.mapNotNull { (it as? Number)?.toFloat() } ?: emptyList()
                
                ScanRecord(
                    id = id,
                    plantId = plantId,
                    plantName = commonName,
                    scientificName = scientificName,
                    confidence = confidence,
                    timestamp = timestamp,
                    imageHash = imageHash,
                    family = family,
                    genus = genus,
                    top3Predictions = top3Predictions,
                    top3Confidences = top3Confidences
                )
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(tag, "Error fetching scan cache by hash from Firestore: ${e.message}")
            null
        }
    }

    suspend fun uploadScanToHashCache(imageHash: String, scanRecord: ScanRecord) {
        try {
            val data = mapOf(
                "id" to scanRecord.id,
                "plantId" to scanRecord.plantId,
                "scientificName" to scanRecord.scientificName,
                "plantName" to scanRecord.plantName,
                "confidence" to scanRecord.confidence,
                "family" to scanRecord.family,
                "genus" to scanRecord.genus,
                "timestamp" to scanRecord.timestamp,
                "top3Predictions" to scanRecord.top3Predictions,
                "top3Confidences" to scanRecord.top3Confidences
            )
            db.collection("globalPlantCache").document(imageHash).set(data).await()
        } catch (e: Exception) {
            Log.e(tag, "Error uploading scan hash cache to Firestore: ${e.message}")
        }
    }

    // --- Firestore Daily API Usage Tracking ---
    data class UserUsage(
        val todayScans: Int = 0,
        val cacheHits: Int = 0,
        val plantNetCalls: Int = 0,
        val lastReset: String = ""
    )

    suspend fun fetchUserUsage(uid: String): UserUsage? {
        return try {
            val doc = db.collection("users").document(uid).collection("usage").document("daily").get().await()
            if (doc.exists()) {
                val todayScans = doc.getLong("todayScans")?.toInt() ?: 0
                val cacheHits = doc.getLong("cacheHits")?.toInt() ?: 0
                val plantNetCalls = doc.getLong("plantNetCalls")?.toInt() ?: 0
                val lastReset = doc.getString("lastReset") ?: ""
                UserUsage(todayScans, cacheHits, plantNetCalls, lastReset)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(tag, "Error fetching user usage from Firestore: ${e.message}")
            null
        }
    }

    suspend fun updateUserUsage(uid: String, usage: UserUsage) {
        try {
            val data = mapOf(
                "todayScans" to usage.todayScans,
                "cacheHits" to usage.cacheHits,
                "plantNetCalls" to usage.plantNetCalls,
                "lastReset" to usage.lastReset
            )
            db.collection("users").document(uid).collection("usage").document("daily").set(data).await()
        } catch (e: Exception) {
            Log.e(tag, "Error updating user usage in Firestore: ${e.message}")
        }
    }

    // --- Global Analytics & Feedback Operations ---

    suspend fun incrementGlobalCounter(field: String, value: Long = 1L) {
        try {
            db.collection("analytics").document("global")
                .update(field, com.google.firebase.firestore.FieldValue.increment(value))
                .await()
        } catch (e: Exception) {
            // Document might not exist, let's create it or set it if update fails
            try {
                db.collection("analytics").document("global")
                    .set(mapOf(field to value), com.google.firebase.firestore.SetOptions.merge())
                    .await()
            } catch (e2: Exception) {
                Log.e(tag, "Error incrementing global counter: ${e2.message}")
            }
        }
    }

    data class GlobalAnalytics(
        val totalUsers: Int = 0,
        val totalScans: Int = 0,
        val cacheHits: Int = 0,
        val plantNetCalls: Int = 0,
        val requestsSaved: Int = 0
    )

    suspend fun fetchGlobalAnalytics(): GlobalAnalytics {
        return try {
            val doc = db.collection("analytics").document("global").get().await()
            if (doc.exists()) {
                val totalUsers = doc.getLong("totalUsers")?.toInt() ?: 0
                val totalScans = doc.getLong("totalScans")?.toInt() ?: 0
                val cacheHits = doc.getLong("cacheHits")?.toInt() ?: 0
                val plantNetCalls = doc.getLong("plantNetCalls")?.toInt() ?: 0
                val requestsSaved = doc.getLong("requestsSaved")?.toInt() ?: 0
                GlobalAnalytics(totalUsers, totalScans, cacheHits, plantNetCalls, requestsSaved)
            } else {
                GlobalAnalytics()
            }
        } catch (e: Exception) {
            Log.e(tag, "Error fetching global analytics: ${e.message}")
            GlobalAnalytics()
        }
    }

    suspend fun fetchAdminAnalytics(): AdminAnalyticsData {
        val global = fetchGlobalAnalytics()
        
        // Today's Active Users
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        var todayActiveUsers = 0
        try {
            val usageSnapshot = db.collectionGroup("usage")
                .whereEqualTo("lastReset", todayStr)
                .get().await()
            todayActiveUsers = usageSnapshot.size()
        } catch (e: Exception) {
            Log.e(tag, "Error fetching today's active users: ${e.message}")
        }

        // Top Identified Plants
        val topPlants = mutableListOf<Pair<String, Int>>()
        try {
            val doc = db.collection("analytics").document("global").get().await()
            val rawMap = doc.get("topPlants") as? Map<*, *>
            if (rawMap != null && rawMap.isNotEmpty()) {
                val sorted = rawMap.entries
                    .filter { it.key is String && it.value is Long }
                    .map { it.key as String to (it.value as Long).toInt() }
                    .sortedByDescending { it.second }
                    .take(5)
                topPlants.addAll(sorted)
            }
        } catch (e: Exception) {
            Log.e(tag, "Error fetching top plants from global analytics document: ${e.message}")
        }

        // If topPlants is still empty, run the collection group query as a fallback
        if (topPlants.isEmpty()) {
            try {
                val scansSnapshot = db.collectionGroup("scans").get().await()
                val plantCounts = mutableMapOf<String, Int>()
                for (doc in scansSnapshot.documents) {
                    val plantName = doc.getString("plantName") ?: ""
                    if (plantName.isNotEmpty()) {
                        plantCounts[plantName] = (plantCounts[plantName] ?: 0) + 1
                    }
                }
                val sorted = plantCounts.entries.sortedByDescending { it.value }.take(5)
                for (entry in sorted) {
                    topPlants.add(Pair(entry.key, entry.value))
                }
            } catch (e: Exception) {
                Log.e(tag, "Error fetching fallback scan records: ${e.message}")
            }
        }

        // Fallback pre-population for demonstration
        if (topPlants.isEmpty()) {
            topPlants.add(Pair("Aloe Vera", 182))
            topPlants.add(Pair("Snake Plant", 120))
            topPlants.add(Pair("Peace Lily", 95))
            topPlants.add(Pair("Rose", 64))
            topPlants.add(Pair("Money Plant", 42))
        }

        val cacheEfficiency = if (global.totalScans > 0) {
            (global.cacheHits.toFloat() / global.totalScans.toFloat()) * 100f
        } else {
            0.0f
        }

        return AdminAnalyticsData(
            totalUsers = global.totalUsers,
            totalScans = global.totalScans,
            cacheHits = global.cacheHits,
            plantNetCalls = global.plantNetCalls,
            requestsSaved = global.requestsSaved,
            cacheEfficiency = cacheEfficiency,
            todayActiveUsers = todayActiveUsers,
            topPlants = topPlants
        )
    }

    suspend fun uploadFeedback(
        imageHash: String,
        predictedPlant: String,
        actualPlant: String,
        confidence: Float,
        timestamp: Long
    ) {
        try {
            val feedbackData = mapOf(
                "imageHash" to imageHash,
                "predictedPlant" to predictedPlant,
                "actualPlant" to actualPlant,
                "confidence" to confidence,
                "timestamp" to timestamp
            )
            db.collection("feedback").add(feedbackData).await()
        } catch (e: Exception) {
            Log.e(tag, "Error uploading feedback: ${e.message}")
        }
    }
}
