package com.plantlens.ai.utils

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.plantlens.ai.models.SavedPlant
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReminderManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val TAG = "ReminderManager"
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager

    fun scheduleWateringReminder(savedPlant: SavedPlant) {
        if (alarmManager == null) return

        // Set alarm for the nextWaterDate
        val triggerTime = savedPlant.nextWaterDate
        if (triggerTime <= System.currentTimeMillis()) {
            Log.w(TAG, "Next watering date is in the past. Skipping alarm scheduling.")
            return
        }

        val intent = Intent(context, NotificationReceiver::class.java).apply {
            putExtra("plant_id", savedPlant.id)
            putExtra("plant_nickname", savedPlant.nickname)
            putExtra("plant_name", savedPlant.plantName)
        }

        // Unique request code per plant to allow scheduling multiple alarms
        val requestCode = kotlin.math.abs(savedPlant.id.hashCode())

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerTime,
                        pendingIntent
                    )
                    Log.d(TAG, "Exact watering alarm scheduled for ${savedPlant.nickname} at $triggerTime")
                } else {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerTime,
                        pendingIntent
                    )
                    Log.d(TAG, "Inexact watering alarm scheduled for ${savedPlant.nickname} at $triggerTime")
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
                Log.d(TAG, "Alarm scheduled for ${savedPlant.nickname} at $triggerTime")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule watering alarm: ${e.message}")
        }
    }

    fun rescheduleAll(plants: List<SavedPlant>) {
        plants.forEach { plant ->
            if (plant.isSaved && plant.nextWaterDate > System.currentTimeMillis()) {
                scheduleWateringReminder(plant)
            }
        }
    }

    fun cancelWateringReminder(savedPlantId: String) {
        if (alarmManager == null) return

        val intent = Intent(context, NotificationReceiver::class.java)
        val requestCode = kotlin.math.abs(savedPlantId.hashCode())

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )

        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
            Log.d(TAG, "Watering alarm cancelled for plant ID $savedPlantId")
        }
    }

    fun scheduleCareReminder(savedPlantId: String, nickname: String, taskType: String, intervalDays: Int) {
        val workManager = androidx.work.WorkManager.getInstance(context)
        
        // Input data
        val inputData = androidx.work.workDataOf(
            "plant_id" to savedPlantId,
            "plant_nickname" to nickname,
            "task_type" to taskType
        )

        // Unique tag for the work request
        val uniqueTag = "${savedPlantId}_${taskType.lowercase()}"

        // Periodic work request
        val workRequest = androidx.work.PeriodicWorkRequestBuilder<ReminderWorker>(
            intervalDays.toLong(), java.util.concurrent.TimeUnit.DAYS
        )
        .setInputData(inputData)
        .addTag(uniqueTag)
        .build()

        workManager.enqueueUniquePeriodicWork(
            uniqueTag,
            androidx.work.ExistingPeriodicWorkPolicy.UPDATE,
            workRequest
        )
        Log.d(TAG, "Scheduled WorkManager reminder for $nickname: $taskType every $intervalDays days")
    }

    fun cancelCareReminder(savedPlantId: String, taskType: String) {
        val workManager = androidx.work.WorkManager.getInstance(context)
        val uniqueTag = "${savedPlantId}_${taskType.lowercase()}"
        workManager.cancelUniqueWork(uniqueTag)
        Log.d(TAG, "Cancelled WorkManager reminder for plant ID $savedPlantId: $taskType")
    }
}
