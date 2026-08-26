package com.plantlens.ai.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import com.plantlens.ai.R
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class ReminderWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val TAG = "ReminderWorker"
    private val CHANNEL_ID = "plant_care_reminders"

    override suspend fun doWork(): Result {
        val plantId = inputData.getString("plant_id") ?: "unknown"
        val plantNickname = inputData.getString("plant_nickname") ?: "Plant"
        val taskType = inputData.getString("task_type") ?: "Care"

        Log.d(TAG, "Executing care reminder for plant $plantNickname ($plantId) - Task: $taskType")

        val title = when (taskType.lowercase()) {
            "water" -> applicationContext.getString(R.string.reminder_water_title)
            "fertilizer" -> applicationContext.getString(R.string.reminder_fertilizer_title)
            "repot" -> applicationContext.getString(R.string.reminder_repot_title)
            "pruning" -> applicationContext.getString(R.string.reminder_prune_title)
            else -> applicationContext.getString(R.string.reminder_generic_title)
        }

        val message = when (taskType.lowercase()) {
            "water" -> applicationContext.getString(R.string.reminder_water_message, plantNickname)
            "fertilizer" -> applicationContext.getString(R.string.reminder_fertilizer_message, plantNickname)
            "repot" -> applicationContext.getString(R.string.reminder_repot_message, plantNickname)
            "pruning" -> applicationContext.getString(R.string.reminder_prune_message, plantNickname)
            else -> applicationContext.getString(R.string.reminder_generic_message, plantNickname)
        }

        try {
            sendNotification(title, message, plantId.hashCode() + taskType.hashCode())
            return Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error showing care notification: ${e.message}")
            return Result.failure()
        }
    }

    private fun sendNotification(title: String, text: String, notificationId: Int) {
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Plant Care Reminders",
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

        notificationManager.notify(notificationId, notification)
    }
}
