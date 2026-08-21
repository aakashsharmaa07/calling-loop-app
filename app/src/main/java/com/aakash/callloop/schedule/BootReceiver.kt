package com.aakash.callloop.schedule

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.aakash.callloop.service.CallLoopService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.d(TAG, "BootReceiver triggered with action: $action")

        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            action == "android.intent.action.LOCKED_BOOT_COMPLETED") {

            val repository = ScheduleRepository(context)
            CoroutineScope(Dispatchers.IO).launch {
                val scheduledCall = repository.scheduledCallFlow.firstOrNull()
                if (scheduledCall != null && scheduledCall.status == ScheduleStatus.PENDING) {
                    val now = System.currentTimeMillis()
                    if (scheduledCall.scheduledTimestamp > now) {
                        Log.d(TAG, "Restoring pending schedule after reboot - ID: ${scheduledCall.id}")
                        ScheduleManager.scheduleCall(
                            context = context,
                            phoneNumber = scheduledCall.phoneNumber,
                            maxAttempts = scheduledCall.maxAttempts,
                            delaySeconds = scheduledCall.delaySeconds,
                            minAnswerDurationSeconds = scheduledCall.minAnswerDurationSeconds,
                            targetTimestamp = scheduledCall.scheduledTimestamp
                        )
                    } else {
                        Log.w(TAG, "Scheduled time passed while phone was powered off - ID: ${scheduledCall.id}")
                        val missedCall = scheduledCall.copy(
                            status = ScheduleStatus.MISSED,
                            statusDetail = "Scheduled call was missed because the device was unavailable."
                        )
                        repository.saveScheduledCall(missedCall)
                        ScheduleManager.updateState { missedCall }
                        showMissedScheduleNotification(context, scheduledCall.phoneNumber)
                    }
                }
            }
        }
    }

    private fun showMissedScheduleNotification(context: Context, phoneNumber: String) {
        try {
            val builder = NotificationCompat.Builder(context, CallLoopService.CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_menu_call)
                .setContentTitle("Scheduled Call Missed")
                .setContentText("Call to $phoneNumber was missed because device was unavailable.")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)

            val notificationManager = NotificationManagerCompat.from(context)
            notificationManager.notify(1004, builder.build())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show missed schedule notification", e)
        }
    }
}
