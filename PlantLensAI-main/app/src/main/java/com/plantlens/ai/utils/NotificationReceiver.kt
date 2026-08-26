package com.plantlens.ai.utils

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.plantlens.ai.R
import com.plantlens.ai.PlantLensApplication
import com.plantlens.ai.activities.MainActivity

class NotificationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val plantId = intent.getStringExtra("plant_id") ?: ""
        val plantNickname = intent.getStringExtra("plant_nickname") ?: "Your plant"
        val plantName = intent.getStringExtra("plant_name") ?: "Plant"

        sendWateringNotification(context, plantId, plantNickname, plantName)
    }

    private fun sendWateringNotification(
        context: Context,
        plantId: String,
        plantNickname: String,
        plantName: String
    ) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        if (notificationManager == null) return

        val mainIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("navigate_to_garden", true)
            putExtra("plant_id", plantId)
        }

        val requestCode = if (plantId.isNotEmpty()) kotlin.math.abs(plantId.hashCode()) else 1001

        val pendingIntent = PendingIntent.getActivity(
            context,
            requestCode,
            mainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, PlantLensApplication.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(context.getString(R.string.watering_reminder_title))
            .setContentText(context.getString(R.string.watering_reminder_text, plantNickname, plantName))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(requestCode, notification)
    }
}
