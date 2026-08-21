package com.aakash.callloop.schedule

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.aakash.callloop.domain.CallLoopManager
import com.aakash.callloop.service.CallLoopService

class ScheduleReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "ScheduleReceiver"

        const val ACTION_TRIGGER_SCHEDULED_CALL = "com.aakash.callloop.action.TRIGGER_SCHEDULED_CALL"

        const val EXTRA_SCHEDULE_ID = "extra_schedule_id"
        const val EXTRA_PHONE_NUMBER = "extra_phone_number"
        const val EXTRA_MAX_ATTEMPTS = "extra_max_attempts"
        const val EXTRA_DELAY_SECONDS = "extra_delay_seconds"
        const val EXTRA_MIN_ANSWER_DURATION = "extra_min_answer_duration"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_TRIGGER_SCHEDULED_CALL) {
            Log.d(TAG, "ScheduleReceiver triggered by AlarmManager")

            // Acquire CPU wake lock to ensure background thread executes cleanly
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            val wakeLock = powerManager?.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "CallLoop:ScheduledCallWakeLock"
            )
            try {
                wakeLock?.acquire(10000)
            } catch (e: Exception) {
                Log.e(TAG, "Error acquiring wake lock", e)
            }

            val phoneNumber = intent.getStringExtra(EXTRA_PHONE_NUMBER) ?: ""
            val maxAttempts = intent.getIntExtra(EXTRA_MAX_ATTEMPTS, 5)
            val delaySeconds = intent.getIntExtra(EXTRA_DELAY_SECONDS, 30)
            val minAnswerDuration = intent.getIntExtra(EXTRA_MIN_ANSWER_DURATION, 12)

            val currentSchedule = ScheduleManager.scheduledState.value

            // Verify schedule has not been cancelled
            if (currentSchedule.status == ScheduleStatus.CANCELLED) {
                Log.w(TAG, "Scheduled call was cancelled prior to alarm execution. Ignoring.")
                try { wakeLock?.release() } catch (_: Exception) {}
                return
            }

            // Verify CALL_PHONE permission at execution time
            val hasPhonePermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CALL_PHONE
            ) == PackageManager.PERMISSION_GRANTED

            if (!hasPhonePermission) {
                Log.e(TAG, "CALL_PHONE permission is missing at schedule execution time")
                ScheduleManager.updateState {
                    it.copy(
                        status = ScheduleStatus.MISSED,
                        statusDetail = "Scheduled call failed: Required phone permission unavailable"
                    )
                }
                showPermissionMissingNotification(context, phoneNumber)
                try { wakeLock?.release() } catch (_: Exception) {}
                return
            }

            if (phoneNumber.isNotBlank()) {
                Log.d(TAG, "Starting Call Loop Engine for scheduled number: $phoneNumber")
                ScheduleManager.updateState {
                    it.copy(
                        status = ScheduleStatus.RUNNING,
                        statusDetail = "Scheduled call loop starting..."
                    )
                }

                // Invoke the EXACT existing Call Loop engine
                CallLoopManager.startLoop(
                    context = context,
                    phoneNumber = phoneNumber,
                    maxAttempts = maxAttempts,
                    delaySeconds = delaySeconds,
                    minAnswerDurationSeconds = minAnswerDuration
                )
            }

            try {
                if (wakeLock?.isHeld == true) {
                    wakeLock.release()
                }
            } catch (_: Exception) {}
        }
    }

    private fun showPermissionMissingNotification(context: Context, phoneNumber: String) {
        try {
            val builder = NotificationCompat.Builder(context, CallLoopService.CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_menu_call)
                .setContentTitle("Scheduled Call Could Not Start")
                .setContentText("Required phone permission is unavailable for $phoneNumber.")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)

            val notificationManager = NotificationManagerCompat.from(context)
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                notificationManager.notify(1003, builder.build())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show permission notification", e)
        }
    }
}
