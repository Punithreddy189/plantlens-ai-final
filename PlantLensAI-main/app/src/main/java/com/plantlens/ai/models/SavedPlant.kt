package com.plantlens.ai.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.IgnoreExtraProperties
import java.io.Serializable
import java.util.Date

@IgnoreExtraProperties
@Entity(tableName = "saved_plants")
data class SavedPlant(
    @PrimaryKey var id: String = "",
    var plantId: String = "",
    var plantName: String = "",
    var scientificName: String = "",
    var nickname: String = "",
    var healthStatus: String = "healthy",
    var disease: String = "Healthy",
    var confidence: Int = 95,
    var isSaved: Boolean = true,
    var addedDate: Long = System.currentTimeMillis(),
    var createdAt: Long = System.currentTimeMillis(),
    var lastWatered: Long = 0L,
    var nextWaterDate: Long = 0L,
    var wateringFrequency: Int = 7 // in days
) : Serializable {

    companion object {
        fun fromDocument(doc: DocumentSnapshot): SavedPlant? {
            return try {
                val data = doc.data ?: return null
                val id = doc.id.ifEmpty { data["id"] as? String ?: "" }
                val plantId = data["plantId"] as? String ?: ""
                val plantName = data["plantName"] as? String ?: ""
                val scientificName = data["scientificName"] as? String ?: ""
                val nickname = data["nickname"] as? String ?: ""
                val healthStatus = data["healthStatus"] as? String ?: "healthy"
                val disease = data["disease"] as? String ?: "Healthy"
                
                val confidence = when (val c = data["confidence"]) {
                    is Number -> c.toInt()
                    is String -> c.toIntOrNull() ?: 95
                    else -> 95
                }
                
                val isSaved = when (val s = data["isSaved"]) {
                    is Boolean -> s
                    is Number -> s.toInt() != 0
                    is String -> s.toBoolean()
                    else -> true
                }

                fun parseToLong(value: Any?): Long {
                    return when (value) {
                        is Number -> value.toLong()
                        is Timestamp -> value.toDate().time
                        is Date -> value.time
                        is String -> value.toLongOrNull() ?: 0L
                        else -> 0L
                    }
                }

                val addedDate = parseToLong(data["addedDate"])
                val createdAtRaw = parseToLong(data["createdAt"])
                val createdAt = if (createdAtRaw != 0L) createdAtRaw else System.currentTimeMillis()
                val lastWatered = parseToLong(data["lastWatered"])
                val nextWaterDate = parseToLong(data["nextWaterDate"])
                
                val wateringFrequency = when (val wf = data["wateringFrequency"]) {
                    is Number -> wf.toInt()
                    is String -> wf.toIntOrNull() ?: 7
                    else -> 7
                }

                SavedPlant(
                    id = id,
                    plantId = plantId,
                    plantName = plantName,
                    scientificName = scientificName,
                    nickname = nickname,
                    healthStatus = healthStatus,
                    disease = disease,
                    confidence = confidence,
                    isSaved = isSaved,
                    addedDate = addedDate,
                    createdAt = createdAt,
                    lastWatered = lastWatered,
                    nextWaterDate = nextWaterDate,
                    wateringFrequency = wateringFrequency
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}
