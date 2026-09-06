package com.aakash.callloop.schedule

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.aakash.callloop.R
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
                val scheduledCalls = repository.scheduledCallsFlow.firstOrNull() ?: emptyList()
                ScheduleManager.setScheduledCalls(scheduledCalls)

                val now = System.currentTimeMillis()
                for (scheduledCall in scheduledCalls) {
                    if (scheduledCall.status == ScheduleStatus.PENDING) {
                        if (scheduledCall.scheduledTimestamp > now) {
                            Log.d(TAG, "Restoring pending schedule after reboot - ID: ${scheduledCall.id}")
                            ScheduleManager.registerAlarm(context, scheduledCall)
                        } else {
                            Log.w(TAG, "Scheduled time passed while phone was powered off - ID: ${scheduledCall.id}")
                            val missedCall = scheduledCall.copy(
                                status = ScheduleStatus.MISSED,
                                statusDetail = "Scheduled call was missed because the device was unavailable."
                            )
                            repository.saveScheduledCall(missedCall)
                            ScheduleManager.updateSchedule(scheduledCall.id) { missedCall }
                            showMissedScheduleNotification(context, scheduledCall.phoneNumber)
                        }
                    }
                }
            }
        }
    }

    private fun showMissedScheduleNotification(context: Context, phoneNumber: String) {
        try {
            val builder = NotificationCompat.Builder(context, CallLoopService.CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
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
