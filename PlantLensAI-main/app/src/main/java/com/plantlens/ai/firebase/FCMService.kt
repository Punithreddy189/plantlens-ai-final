package com.plantlens.ai.firebase

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.plantlens.ai.PlantLensApplication
import com.plantlens.ai.activities.MainActivity

class FCMService : FirebaseMessagingService() {

    private val TAG = "FCMService"

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "Refreshed FCM token: $token")
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (!uid.isNullOrEmpty()) {
            FirebaseFirestore.getInstance()
                .collection("users")
                .document(uid)
                .update(
                    mapOf(
                        "fcmToken" to token,
                        "lastTokenUpdate" to System.currentTimeMillis()
                    )
                )
                .addOnSuccessListener {
                    Log.d(TAG, "FCM token updated in Firestore for UID $uid")
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Failed to update FCM token in Firestore: ${e.message}")
                }
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d(TAG, "FCM Message received from: ${remoteMessage.from}")

        val title = remoteMessage.notification?.title
            ?: remoteMessage.data["title"]
            ?: "💧 PlantLens AI Alert"
        val body = remoteMessage.notification?.body
            ?: remoteMessage.data["body"]
            ?: remoteMessage.data["message"]
            ?: "You have an update in your Garden."

        val plantId = remoteMessage.data["plant_id"] ?: ""
        val navigateToGarden = remoteMessage.data["navigate_to_garden"]?.toBoolean() ?: true

        sendNotification(title, body, plantId, navigateToGarden)
    }

    private fun sendNotification(
        title: String,
        messageBody: String,
        plantId: String = "",
        navigateToGarden: Boolean = true
    ) {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra("navigate_to_garden", navigateToGarden)
            if (plantId.isNotEmpty()) {
                putExtra("plant_id", plantId)
            }
        }

        val requestCode = if (plantId.isNotEmpty()) kotlin.math.abs(plantId.hashCode()) else System.currentTimeMillis().toInt()
        val pendingIntent = PendingIntent.getActivity(
            this, requestCode, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notificationBuilder = NotificationCompat.Builder(this, PlantLensApplication.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(messageBody)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(requestCode, notificationBuilder.build())
    }
}
