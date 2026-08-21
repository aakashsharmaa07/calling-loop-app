package com.aakash.callloop.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.SystemClock
import android.provider.CallLog
import android.telecom.TelecomManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.aakash.callloop.domain.CallLoopManager
import com.aakash.callloop.domain.CallLoopState
import com.aakash.callloop.domain.LoopStatus
import com.aakash.callloop.schedule.ScheduleManager
import com.aakash.callloop.schedule.ScheduleRepository
import com.aakash.callloop.schedule.ScheduleStatus
import com.aakash.callloop.telephony.CallStateMonitor
import com.aakash.callloop.telephony.PhoneCallState
import com.aakash.callloop.ui.MainActivity
import com.aakash.callloop.utils.PhoneNumberUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class CallLoopService : Service() {

    companion object {
        private const val TAG = "CallLoopService"

        const val ACTION_START_LOOP = "com.aakash.callloop.action.START"
        const val ACTION_STOP_LOOP = "com.aakash.callloop.action.STOP"

        const val EXTRA_PHONE_NUMBER = "extra_phone_number"
        const val EXTRA_MAX_ATTEMPTS = "extra_max_attempts"
        const val EXTRA_DELAY_SECONDS = "extra_delay_seconds"
        const val EXTRA_MIN_ANSWER_DURATION = "extra_min_answer_duration"

        const val NOTIFICATION_CHANNEL_ID = "call_loop_service_channel"
        const val CHANNEL_ID = "call_loop_service_channel"
        private const val NOTIFICATION_ID = 1001
    }

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var loopJob: Job? = null

    private var callStateMonitor: CallStateMonitor? = null
    private var isCallStateRegistered = false

    @Volatile
    private var wasOffHook: Boolean = false
    @Volatile
    private var currentAttemptCallEnded: Boolean = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        Log.d(TAG, "onStartCommand received action: $action")

        when (action) {
            ACTION_START_LOOP -> {
                val phoneNumber = intent.getStringExtra(EXTRA_PHONE_NUMBER) ?: ""
                val maxAttempts = intent.getIntExtra(EXTRA_MAX_ATTEMPTS, 5).coerceIn(1, 20)
                val delaySeconds = intent.getIntExtra(EXTRA_DELAY_SECONDS, 30).coerceAtLeast(5)
                val minAnswerDuration = intent.getIntExtra(EXTRA_MIN_ANSWER_DURATION, 12).coerceIn(3, 30)

                safeStartForeground("Call Loop Active", "Preparing outgoing call to $phoneNumber...")
                startCallLoop(phoneNumber, maxAttempts, delaySeconds, minAnswerDuration)
            }
            ACTION_STOP_LOOP -> {
                Log.d(TAG, "ACTION_STOP_LOOP received from notification or UI")
                stopCallLoop(LoopStatus.STOPPED_BY_USER, "Call loop stopped by user.")
            }
        }
        return START_NOT_STICKY
    }

    private fun safeStartForeground(title: String, contentText: String) {
        val notification = buildNotification(title, contentText)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            try {
                startForeground(NOTIFICATION_ID, notification)
            } catch (_: Exception) {}
        }
    }

    private fun startCallLoop(
        phoneNumber: String,
        maxAttempts: Int,
        delaySeconds: Int,
        minAnswerDurationSecs: Int
    ) {
        val cleanedNumber = PhoneNumberUtils.cleanPhoneNumber(phoneNumber)
        if (!PhoneNumberUtils.isValidPhoneNumber(cleanedNumber)) {
            stopCallLoop(LoopStatus.ERROR, "Invalid phone number provided.")
            return
        }

        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CALL_PHONE)
            != PackageManager.PERMISSION_GRANTED) {
            stopCallLoop(LoopStatus.PERMISSION_REQUIRED, "Phone permission is required to place calls.")
            return
        }

        loopJob?.cancel()

        CallLoopManager.updateState {
            CallLoopState(
                isLoopActive = true,
                currentAttempt = 0,
                maxAttempts = maxAttempts,
                delaySeconds = delaySeconds,
                minAnswerDurationSeconds = minAnswerDurationSecs,
                phoneNumber = cleanedNumber,
                status = LoopStatus.CALLING,
                statusDetail = "Starting attempt 1 of $maxAttempts",
                countdownSecondsRemaining = 0,
                callAnswered = false,
                errorMessage = null
            )
        }

        ScheduleManager.updateState {
            if (it.status == ScheduleStatus.PENDING || it.status == ScheduleStatus.RUNNING) {
                it.copy(
                    status = ScheduleStatus.RUNNING,
                    statusDetail = "Call loop session active"
                )
            } else it
        }

        setupCallStateMonitor()

        loopJob = serviceScope.launch {
            try {
                for (attempt in 1..maxAttempts) {
                    if (!CallLoopManager.state.value.isLoopActive) break

                    wasOffHook = false
                    currentAttemptCallEnded = false

                    CallLoopManager.updateState {
                        it.copy(
                            currentAttempt = attempt,
                            status = LoopStatus.CALLING,
                            statusDetail = "Calling $cleanedNumber...",
                            countdownSecondsRemaining = 0
                        )
                    }

                    updateNotification("Attempt $attempt / $maxAttempts", "Calling $cleanedNumber")

                    Log.d(TAG, "CALL_REQUEST_STARTED — Attempt $attempt/$maxAttempts to $cleanedNumber")

                    val callPlaced = placeCall(cleanedNumber)
                    if (!callPlaced) {
                        Log.e(TAG, "CALL_REQUEST_FAILED — Telecom system rejected outgoing call request")
                        stopCallLoop(LoopStatus.ERROR, "Unable to place call via Android Telecom system.")
                        return@launch
                    }

                    Log.d(TAG, "CALL_REQUEST_ACCEPTED — Outgoing call accepted by Telecom")

                    val callWaitStartTime = SystemClock.elapsedRealtime()
                    while (!currentAttemptCallEnded && CallLoopManager.state.value.isLoopActive) {
                        val elapsed = SystemClock.elapsedRealtime() - callWaitStartTime
                        if (elapsed > 90_000L) {
                            Log.w(TAG, "Call attempt timeout after 90 seconds")
                            break
                        }
                        delay(500)
                    }

                    if (!CallLoopManager.state.value.isLoopActive) break

                    Log.d(TAG, "CALL_STATE_DISCONNECTED — Attempt $attempt ended")

                    val isAnswered = checkIfCallWasAnswered(minAnswerDurationSecs)
                    if (isAnswered) {
                        Log.d(TAG, "SESSION_COMPLETED — Call was answered by recipient!")
                        CallLoopManager.updateState {
                            it.copy(
                                status = LoopStatus.CONNECTED,
                                statusDetail = "CALL ANSWERED — LOOP STOPPED",
                                callAnswered = true,
                                isLoopActive = false
                            )
                        }
                        updateNotification("Call Answered", "Call loop completed successfully.")
                        stopCallLoop(LoopStatus.CONNECTED, "CALL ANSWERED — LOOP STOPPED")
                        return@launch
                    }

                    CallLoopManager.updateState {
                        it.copy(
                            status = LoopStatus.CALL_ENDED,
                            statusDetail = "Attempt $attempt ended without answer"
                        )
                    }

                    if (attempt < maxAttempts) {
                        Log.d(TAG, "RETRY_SCHEDULED — Waiting $delaySeconds seconds before attempt ${attempt + 1}")
                        startCountdown(delaySeconds, attempt, maxAttempts, cleanedNumber)
                    } else {
                        Log.d(TAG, "SESSION_COMPLETED — Maximum attempts ($maxAttempts) reached")
                        CallLoopManager.updateState {
                            it.copy(
                                status = LoopStatus.MAX_ATTEMPTS_REACHED,
                                statusDetail = "Maximum attempts reached ($maxAttempts/$maxAttempts)",
                                isLoopActive = false
                            )
                        }
                        updateNotification("Maximum attempts reached", "No answer received after $maxAttempts attempts.")
                        stopCallLoop(LoopStatus.MAX_ATTEMPTS_REACHED, "Maximum attempts reached.")
                        return@launch
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Call loop error", e)
                stopCallLoop(LoopStatus.ERROR, "Call loop stopped due to an error.")
            }
        }
    }

    private suspend fun startCountdown(
        delaySeconds: Int,
        completedAttempt: Int,
        maxAttempts: Int,
        phoneNumber: String
    ) {
        CallLoopManager.updateState {
            it.copy(
                status = LoopStatus.WAITING,
                statusDetail = "Waiting $delaySeconds seconds for next attempt...",
                countdownSecondsRemaining = delaySeconds
            )
        }

        for (sec in delaySeconds downTo 1) {
            if (!CallLoopManager.state.value.isLoopActive) break

            CallLoopManager.updateState {
                it.copy(
                    countdownSecondsRemaining = sec,
                    statusDetail = "Waiting $sec seconds..."
                )
            }
            updateNotification(
                "Waiting $sec seconds",
                "Next attempt ${completedAttempt + 1} / $maxAttempts to $phoneNumber"
            )
            delay(1000)
        }
    }

    @SuppressLint("MissingPermission")
    private fun placeCall(phoneNumber: String): Boolean {
        return try {
            val uri = Uri.fromParts("tel", phoneNumber, null)
            val telecomManager = getSystemService(Context.TELECOM_SERVICE) as? TelecomManager

            if (telecomManager != null && ContextCompat.checkSelfPermission(this, android.Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
                val extras = Bundle()
                telecomManager.placeCall(uri, extras)
                Log.d(TAG, "TelecomManager.placeCall executed for tel:$phoneNumber")
                true
            } else {
                val callIntent = Intent(Intent.ACTION_CALL, uri).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                startActivity(callIntent)
                Log.d(TAG, "startActivity(ACTION_CALL) executed for tel:$phoneNumber")
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to place real call via Telecom system", e)
            false
        }
    }

    private fun setupCallStateMonitor() {
        if (isCallStateRegistered) return

        callStateMonitor = CallStateMonitor(this) { newState ->
            Log.d(TAG, "CallStateMonitor state changed: $newState")
            when (newState) {
                PhoneCallState.RINGING -> {
                    CallLoopManager.updateState { it.copy(status = LoopStatus.RINGING) }
                    updateNotification("Ringing...", "Call dialing recipient")
                }
                PhoneCallState.OFFHOOK -> {
                    wasOffHook = true
                    CallLoopManager.updateState {
                        it.copy(
                            status = LoopStatus.RINGING,
                            statusDetail = "Calling / Active"
                        )
                    }
                }
                PhoneCallState.IDLE -> {
                    if (wasOffHook) {
                        currentAttemptCallEnded = true
                    }
                }
            }
        }
        callStateMonitor?.register()
        isCallStateRegistered = true
    }

    private fun checkIfCallWasAnswered(minAnswerDurationSecs: Int): Boolean {
        if (!wasOffHook) {
            Log.d(TAG, "Call check: Handset never went OFFHOOK (unanswered/rejected)")
            return false
        }

        return try {
            val cursor = contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(CallLog.Calls.TYPE, CallLog.Calls.DURATION),
                null,
                null,
                "${CallLog.Calls.DATE} DESC"
            )

            cursor?.use { c ->
                if (c.moveToFirst()) {
                    val typeIndex = c.getColumnIndex(CallLog.Calls.TYPE)
                    val durationIndex = c.getColumnIndex(CallLog.Calls.DURATION)

                    if (typeIndex >= 0 && durationIndex >= 0) {
                        val callType = c.getInt(typeIndex)
                        val durationSeconds = c.getInt(durationIndex)

                        Log.d(TAG, "CallLog query result — Type: $callType, Duration: ${durationSeconds}s")

                        if (callType == CallLog.Calls.MISSED_TYPE ||
                            callType == CallLog.Calls.REJECTED_TYPE ||
                            callType == CallLog.Calls.BLOCKED_TYPE) {
                            return false
                        }

                        if (durationSeconds > minAnswerDurationSecs) {
                            return true
                        }
                    }
                }
            }
            false
        } catch (e: Exception) {
            Log.e(TAG, "Error checking CallLog", e)
            false
        }
    }

    private fun stopCallLoop(status: LoopStatus, detail: String) {
        Log.d(TAG, "SESSION_STOPPED — Stopping Call Loop Service with status: $status ($detail)")

        loopJob?.cancel()

        CallLoopManager.updateState {
            it.copy(
                isLoopActive = false,
                status = status,
                statusDetail = detail,
                countdownSecondsRemaining = 0
            )
        }

        val targetScheduleStatus = when (status) {
            LoopStatus.CONNECTED, LoopStatus.MAX_ATTEMPTS_REACHED -> ScheduleStatus.COMPLETED
            LoopStatus.STOPPED_BY_USER -> ScheduleStatus.CANCELLED
            else -> ScheduleStatus.CANCELLED
        }

        ScheduleManager.updateState {
            if (it.status == ScheduleStatus.RUNNING || it.status == ScheduleStatus.PENDING) {
                it.copy(
                    status = targetScheduleStatus,
                    statusDetail = detail
                )
            } else it
        }

        val repository = ScheduleRepository(applicationContext)
        serviceScope.launch {
            repository.saveScheduledCall(ScheduleManager.scheduledState.value)
        }

        if (isCallStateRegistered) {
            callStateMonitor?.unregister()
            isCallStateRegistered = false
        }

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Call Loop Foreground Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows active call loop status and controls"
                setSound(null, null)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(title: String, contentText: String): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingOpenIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, CallLoopService::class.java).apply {
            action = ACTION_STOP_LOOP
        }
        val pendingStopIntent = PendingIntent.getService(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .setContentTitle(title)
            .setContentText(contentText)
            .setContentIntent(pendingOpenIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "STOP", pendingStopIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun updateNotification(title: String, contentText: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(title, contentText))
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "CallLoopService onDestroy called")
        serviceScope.cancel()
    }
}
