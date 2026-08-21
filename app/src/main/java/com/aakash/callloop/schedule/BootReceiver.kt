package com.aakash.callloop.schedule

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val repository = ScheduleRepository(context)
            CoroutineScope(Dispatchers.IO).launch {
                val scheduledCall = repository.scheduledCallFlow.firstOrNull()
                if (scheduledCall != null && scheduledCall.status == ScheduleStatus.PENDING) {
                    val now = System.currentTimeMillis()
                    if (scheduledCall.scheduledTimestamp > now) {
                        // Re-schedule alarm cleanly after reboot
                        ScheduleManager.scheduleCall(
                            context = context,
                            phoneNumber = scheduledCall.phoneNumber,
                            maxAttempts = scheduledCall.maxAttempts,
                            delaySeconds = scheduledCall.delaySeconds,
                            minAnswerDurationSeconds = scheduledCall.minAnswerDurationSeconds,
                            targetTimestamp = scheduledCall.scheduledTimestamp
                        )
                    } else {
                        // Expired while device was off
                        repository.saveScheduledCall(
                            scheduledCall.copy(
                                status = ScheduleStatus.EXPIRED,
                                statusDetail = "Scheduled time passed while phone was off"
                            )
                        )
                    }
                }
            }
        }
    }
}
