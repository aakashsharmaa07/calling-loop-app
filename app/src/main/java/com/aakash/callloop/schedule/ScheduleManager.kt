package com.aakash.callloop.schedule

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.aakash.callloop.domain.CallLoopManager
import com.aakash.callloop.ui.MainActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

object ScheduleManager {
    private const val TAG = "ScheduleManager"

    private val _scheduledState = MutableStateFlow(ScheduledCall())
    val scheduledState: StateFlow<ScheduledCall> = _scheduledState.asStateFlow()

    fun updateState(transform: (ScheduledCall) -> ScheduledCall) {
        _scheduledState.value = transform(_scheduledState.value)
    }

    fun canScheduleExactAlarms(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            alarmManager?.canScheduleExactAlarms() ?: true
        } else {
            true
        }
    }

    @SuppressLint("ScheduleExactAlarm")
    fun scheduleCall(
        context: Context,
        phoneNumber: String,
        maxAttempts: Int,
        delaySeconds: Int,
        minAnswerDurationSeconds: Int,
        targetTimestamp: Long
    ): Boolean {
        // Enforce ONE active pending schedule
        if (_scheduledState.value.status == ScheduleStatus.PENDING) {
            Log.w(TAG, "Schedule creation rejected: A schedule is already pending.")
            return false
        }

        val scheduleId = UUID.randomUUID().toString()
        val scheduledCall = ScheduledCall(
            id = scheduleId,
            phoneNumber = phoneNumber,
            maxAttempts = maxAttempts,
            delaySeconds = delaySeconds,
            minAnswerDurationSeconds = minAnswerDurationSeconds,
            scheduledTimestamp = targetTimestamp,
            status = ScheduleStatus.PENDING,
            statusDetail = "Waiting for scheduled time"
        )

        _scheduledState.value = scheduledCall
        Log.d(TAG, "Schedule created - ID: $scheduleId, Phone: $phoneNumber, Target: $targetTimestamp")

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        val intent = Intent(context, ScheduleReceiver::class.java).apply {
            action = ScheduleReceiver.ACTION_TRIGGER_SCHEDULED_CALL
            putExtra(ScheduleReceiver.EXTRA_SCHEDULE_ID, scheduleId)
            putExtra(ScheduleReceiver.EXTRA_PHONE_NUMBER, phoneNumber)
            putExtra(ScheduleReceiver.EXTRA_MAX_ATTEMPTS, maxAttempts)
            putExtra(ScheduleReceiver.EXTRA_DELAY_SECONDS, delaySeconds)
            putExtra(ScheduleReceiver.EXTRA_MIN_ANSWER_DURATION, minAnswerDurationSeconds)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            1002,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val showIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val showPendingIntent = PendingIntent.getActivity(
            context,
            0,
            showIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (alarmManager != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    val alarmClockInfo = AlarmManager.AlarmClockInfo(targetTimestamp, showPendingIntent)
                    alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)
                    Log.d(TAG, "Alarm registered via setAlarmClock for timestamp: $targetTimestamp")
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        targetTimestamp,
                        pendingIntent
                    )
                    Log.d(TAG, "Alarm registered via setExactAndAllowWhileIdle for timestamp: $targetTimestamp")
                } else {
                    alarmManager.setExact(
                        AlarmManager.RTC_WAKEUP,
                        targetTimestamp,
                        pendingIntent
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register exact alarm, attempting fallback", e)
            try {
                alarmManager?.set(
                    AlarmManager.RTC_WAKEUP,
                    targetTimestamp,
                    pendingIntent
                )
            } catch (_: Exception) {}
        }

        return true
    }

    fun cancelSchedule(context: Context) {
        Log.d(TAG, "Cancelling scheduled call ID: ${_scheduledState.value.id}")
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        val intent = Intent(context, ScheduleReceiver::class.java).apply {
            action = ScheduleReceiver.ACTION_TRIGGER_SCHEDULED_CALL
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            1002,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        try {
            alarmManager?.cancel(pendingIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Error cancelling alarm", e)
        }

        _scheduledState.value = _scheduledState.value.copy(
            status = ScheduleStatus.CANCELLED,
            statusDetail = "Schedule cancelled by user"
        )

        // Stop active call loop if running
        CallLoopManager.stopLoop(context)
    }
}
