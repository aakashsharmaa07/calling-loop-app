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

    private val _scheduledCalls = MutableStateFlow<List<ScheduledCall>>(emptyList())
    val scheduledCalls: StateFlow<List<ScheduledCall>> = _scheduledCalls.asStateFlow()

    fun setScheduledCalls(list: List<ScheduledCall>) {
        _scheduledCalls.value = list
    }

    fun getScheduleById(id: String): ScheduledCall? {
        return _scheduledCalls.value.firstOrNull { it.id == id }
    }

    fun updateSchedule(id: String, transform: (ScheduledCall) -> ScheduledCall) {
        val currentList = _scheduledCalls.value.toMutableList()
        val index = currentList.indexOfFirst { it.id == id }
        if (index >= 0) {
            currentList[index] = transform(currentList[index])
            _scheduledCalls.value = currentList
        }
    }

    fun getRequestCode(scheduleId: String): Int {
        return Math.abs(scheduleId.hashCode()) % 90000 + 1000
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
    ): ScheduledCall {
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

        val updatedList = _scheduledCalls.value.toMutableList()
        updatedList.add(0, scheduledCall) // Add newest at top
        _scheduledCalls.value = updatedList

        Log.d(TAG, "Multi-Schedule created - ID: $scheduleId, Phone: $phoneNumber, Target: $targetTimestamp")

        registerAlarm(context, scheduledCall)
        return scheduledCall
    }

    @SuppressLint("ScheduleExactAlarm")
    fun registerAlarm(context: Context, scheduledCall: ScheduledCall) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        val requestCode = getRequestCode(scheduledCall.id)

        val intent = Intent(context, ScheduleReceiver::class.java).apply {
            action = ScheduleReceiver.ACTION_TRIGGER_SCHEDULED_CALL
            putExtra(ScheduleReceiver.EXTRA_SCHEDULE_ID, scheduledCall.id)
            putExtra(ScheduleReceiver.EXTRA_PHONE_NUMBER, scheduledCall.phoneNumber)
            putExtra(ScheduleReceiver.EXTRA_MAX_ATTEMPTS, scheduledCall.maxAttempts)
            putExtra(ScheduleReceiver.EXTRA_DELAY_SECONDS, scheduledCall.delaySeconds)
            putExtra(ScheduleReceiver.EXTRA_MIN_ANSWER_DURATION, scheduledCall.minAnswerDurationSeconds)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val showIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val showPendingIntent = PendingIntent.getActivity(
            context,
            requestCode,
            showIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (alarmManager != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    val alarmClockInfo = AlarmManager.AlarmClockInfo(scheduledCall.scheduledTimestamp, showPendingIntent)
                    alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)
                    Log.d(TAG, "Alarm registered via setAlarmClock (code $requestCode) for timestamp: ${scheduledCall.scheduledTimestamp}")
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        scheduledCall.scheduledTimestamp,
                        pendingIntent
                    )
                    Log.d(TAG, "Alarm registered via setExactAndAllowWhileIdle (code $requestCode) for timestamp: ${scheduledCall.scheduledTimestamp}")
                } else {
                    alarmManager.setExact(
                        AlarmManager.RTC_WAKEUP,
                        scheduledCall.scheduledTimestamp,
                        pendingIntent
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register exact alarm, attempting fallback", e)
            try {
                alarmManager?.set(
                    AlarmManager.RTC_WAKEUP,
                    scheduledCall.scheduledTimestamp,
                    pendingIntent
                )
            } catch (_: Exception) {}
        }
    }

    fun cancelSchedule(context: Context, scheduleId: String) {
        Log.d(TAG, "Cancelling scheduled call ID: $scheduleId")
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        val requestCode = getRequestCode(scheduleId)

        val intent = Intent(context, ScheduleReceiver::class.java).apply {
            action = ScheduleReceiver.ACTION_TRIGGER_SCHEDULED_CALL
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        try {
            alarmManager?.cancel(pendingIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Error cancelling alarm", e)
        }

        updateSchedule(scheduleId) {
            it.copy(
                status = ScheduleStatus.CANCELLED,
                statusDetail = "Schedule cancelled by user"
            )
        }
    }
}
