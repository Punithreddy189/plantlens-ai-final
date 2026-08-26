package com.plantlens.ai.utils

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.core.app.NotificationCompat
import android.app.NotificationManager
import android.app.NotificationChannel
import android.os.Build

class WateringWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val TAG = "WateringWorker"
    private val CHANNEL_ID = "watering_reminders"

    override suspend fun doWork(): Result {
        Log.d(TAG, "Starting periodic watering & weather check background job.")
        
        try {
            // Weather intelligence check (simulating rain expected or heat warning based on forecast)
            // Rain = 1, Heat = 2, Frost = 3, Stable = 0
            val mockWeatherScenario = (0..3).random()
            
            val notificationTitle: String
            val notificationText: String
            
            when (mockWeatherScenario) {
                1 -> {
                    notificationTitle = "🌧 Rain Expected Tomorrow"
                    notificationText = "Skip scheduled watering. Outdoor plants will be watered naturally."
                }
                2 -> {
                    notificationTitle = "🔥 Heat Warning"
                    notificationText = "Increase watering frequency. Check soil moisture on potted plants."
                }
                3 -> {
                    notificationTitle = "❄ Frost Alert"
                    notificationText = "Temperatures dropping tonight. Move sensitive potted plants indoors."
                }
                else -> {
                    notificationTitle = "💧 Water Peace Lily Today"
                    notificationText = "It has been 3 days since the last watering. Keep your garden healthy!"
                }
            }
            
            sendNotification(notificationTitle, notificationText)
            return Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error in WateringWorker: ${e.message}")
            return Result.failure()
        }
    }

    private fun sendNotification(title: String, text: String) {
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Watering Reminders & Weather Alerts",
                NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(1001, notification)
    }
}
