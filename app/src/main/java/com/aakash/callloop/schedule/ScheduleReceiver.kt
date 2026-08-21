package com.aakash.callloop.schedule

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.aakash.callloop.domain.CallLoopManager

class ScheduleReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_TRIGGER_SCHEDULED_CALL = "com.aakash.callloop.action.TRIGGER_SCHEDULED_CALL"

        const val EXTRA_SCHEDULE_ID = "extra_schedule_id"
        const val EXTRA_PHONE_NUMBER = "extra_phone_number"
        const val EXTRA_MAX_ATTEMPTS = "extra_max_attempts"
        const val EXTRA_DELAY_SECONDS = "extra_delay_seconds"
        const val EXTRA_MIN_ANSWER_DURATION = "extra_min_answer_duration"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_TRIGGER_SCHEDULED_CALL) {
            val phoneNumber = intent.getStringExtra(EXTRA_PHONE_NUMBER) ?: ""
            val maxAttempts = intent.getIntExtra(EXTRA_MAX_ATTEMPTS, 5)
            val delaySeconds = intent.getIntExtra(EXTRA_DELAY_SECONDS, 30)
            val minAnswerDuration = intent.getIntExtra(EXTRA_MIN_ANSWER_DURATION, 12)

            if (phoneNumber.isNotBlank()) {
                ScheduleManager.updateState {
                    it.copy(
                        status = ScheduleStatus.RUNNING,
                        statusDetail = "Scheduled call starting..."
                    )
                }

                // Reuse the EXACT existing Call Loop engine
                CallLoopManager.startLoop(
                    context = context,
                    phoneNumber = phoneNumber,
                    maxAttempts = maxAttempts,
                    delaySeconds = delaySeconds,
                    minAnswerDurationSeconds = minAnswerDuration
                )
            }
        }
    }
}
