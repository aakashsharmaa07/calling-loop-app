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
import android.os.IBinder
import android.os.SystemClock
import android.provider.CallLog
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.aakash.callloop.R
import com.aakash.callloop.domain.CallLoopManager
import com.aakash.callloop.domain.CallLoopState
import com.aakash.callloop.domain.LoopStatus
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
import kotlinx.coroutines.withContext

class CallLoopService : Service() {

    companion object {
        const val ACTION_START_LOOP = "com.aakash.callloop.action.START"
        const val ACTION_STOP_LOOP = "com.aakash.callloop.action.STOP"

        const val EXTRA_PHONE_NUMBER = "extra_phone_number"
        const val EXTRA_MAX_ATTEMPTS = "extra_max_attempts"
        const val EXTRA_DELAY_SECONDS = "extra_delay_seconds"
        const val EXTRA_MIN_ANSWER_DURATION = "extra_min_answer_duration"

        private const val NOTIFICATION_CHANNEL_ID = "call_loop_service_channel"
        private const val NOTIFICATION_ID = 1001
    }

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var loopJob: Job? = null
    private var countdownJob: Job? = null

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
        when (intent?.action) {
            ACTION_START_LOOP -> {
                val phoneNumber = intent.getStringExtra(EXTRA_PHONE_NUMBER) ?: ""
                val maxAttempts = intent.getIntExtra(EXTRA_MAX_ATTEMPTS, 5).coerceIn(1, 20)
                val delaySeconds = intent.getIntExtra(EXTRA_DELAY_SECONDS, 30).coerceAtLeast(5)
                val minAnswerDuration = intent.getIntExtra(EXTRA_MIN_ANSWER_DURATION, 12).coerceIn(3, 30)

                safeStartForeground("Call Loop Active", "Dialing $phoneNumber...")
                startCallLoop(phoneNumber, maxAttempts, delaySeconds, minAnswerDuration)
            }
            ACTION_STOP_LOOP -> {
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
        countdownJob?.cancel()

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

        setupCallStateMonitor()

        loopJob = serviceScope.launch {
            try {
                for (attempt in 1..maxAttempts) {
                    if (!CallLoopManager.state.value.isLoopActive) break

                    // Reset flags per attempt
                    wasOffHook = false
                    currentAttemptCallEnded = false

                    // Update UI state for current attempt
                    CallLoopManager.updateState {
                        it.copy(
                            currentAttempt = attempt,
                            status = LoopStatus.CALLING,
                            statusDetail = "Calling $cleanedNumber...",
                            countdownSecondsRemaining = 0
                        )
                    }

                    updateNotification("Attempt $attempt / $maxAttempts", "Calling $cleanedNumber")

                    // Place phone call
                    val callPlaced = placeCall(cleanedNumber)
                    if (!callPlaced) {
                        stopCallLoop(LoopStatus.ERROR, "The call could not be completed.")
                        return@launch
                    }

                    // Wait for call to connect and then end (OFFHOOK -> IDLE)
                    val callWaitStartTime = SystemClock.elapsedRealtime()
                    while (!currentAttemptCallEnded && CallLoopManager.state.value.isLoopActive) {
                        val elapsed = SystemClock.elapsedRealtime() - callWaitStartTime
                        if (elapsed > 90_000L) {
                            break
                        }
                        delay(500)
                    }

                    if (!CallLoopManager.state.value.isLoopActive) break

                    // Check Call Log to see if call was genuinely answered by a person (duration > minAnswerDurationSecs)
                    val isAnswered = checkIfCallWasAnswered(minAnswerDurationSecs)
                    if (isAnswered) {
                        CallLoopManager.updateState {
                            it.copy(
                                status = LoopStatus.CONNECTED,
                                statusDetail = "CALL ANSWERED — LOOP STOPPED",
                                callAnswered = true,
                                isLoopActive = false
                            )
                        }
                        updateNotification("Call Answered", "Call loop stopped successfully.")
                        stopCallLoop(LoopStatus.CONNECTED, "CALL ANSWERED — LOOP STOPPED")
                        return@launch
                    }

                    // Call ended without being answered (or ended as carrier IVR / busy / switched off)
                    CallLoopManager.updateState {
                        it.copy(
                            status = LoopStatus.CALL_ENDED,
                            statusDetail = "Attempt $attempt ended without answer"
                        )
                    }

                    if (attempt < maxAttempts) {
                        // Start countdown for next attempt
                        startCountdown(delaySeconds, attempt, maxAttempts, cleanedNumber)
                    } else {
                        // Max attempts reached
                        CallLoopManager.updateState {
                            it.copy(
                                status = LoopStatus.MAX_ATTEMPTS_REACHED,
                                statusDetail = "Maximum attempts reached ($maxAttempts/$maxAttempts)",
                                isLoopActive = false
                            )
                        }
                        updateNotification("Maximum attempts reached", "No answer received.")
                        stopCallLoop(LoopStatus.MAX_ATTEMPTS_REACHED, "Maximum attempts reached.")
                        return@launch
                    }
                }
            } catch (e: Exception) {
                stopCallLoop(LoopStatus.ERROR, "Call loop stopped due to an unexpected error.")
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
                "Next call attempt ${completedAttempt + 1} / $maxAttempts"
            )
            delay(1000)
        }
    }

    @SuppressLint("MissingPermission")
    private fun placeCall(phoneNumber: String): Boolean {
        return try {
            val callIntent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$phoneNumber")).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(callIntent)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun setupCallStateMonitor() {
        if (isCallStateRegistered) return

        callStateMonitor = CallStateMonitor(this) { newState ->
            when (newState) {
                PhoneCallState.RINGING -> {
                    CallLoopManager.updateState { it.copy(status = LoopStatus.RINGING) }
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

    private fun unsetupCallStateMonitor() {
        if (isCallStateRegistered) {
            callStateMonitor?.unregister()
            callStateMonitor = null
            isCallStateRegistered = false
        }
    }

    private suspend fun checkIfCallWasAnswered(minAnswerDurationSecs: Int): Boolean = withContext(Dispatchers.IO) {
        if (ContextCompat.checkSelfPermission(this@CallLoopService, android.Manifest.permission.READ_CALL_LOG)
            == PackageManager.PERMISSION_GRANTED) {
            try {
                // Short wait to allow Android OS CallLog database sync
                delay(1000)

                val cursor = contentResolver.query(
                    CallLog.Calls.CONTENT_URI,
                    arrayOf(CallLog.Calls.DURATION, CallLog.Calls.TYPE, CallLog.Calls.DATE),
                    null,
                    null,
                    "${CallLog.Calls.DATE} DESC"
                )
                cursor?.use {
                    if (it.moveToFirst()) {
                        val durationIndex = it.getColumnIndex(CallLog.Calls.DURATION)
                        val typeIndex = it.getColumnIndex(CallLog.Calls.TYPE)
                        val dateIndex = it.getColumnIndex(CallLog.Calls.DATE)

                        if (durationIndex >= 0 && typeIndex >= 0 && dateIndex >= 0) {
                            val durationSeconds = it.getLong(durationIndex)
                            val callType = it.getInt(typeIndex)
                            val callDate = it.getLong(dateIndex)
                            val isRecentCall = (System.currentTimeMillis() - callDate) < 180_000L

                            if (isRecentCall) {
                                // If call was Missed, Rejected, or Blocked -> NOT answered!
                                if (callType == CallLog.Calls.MISSED_TYPE ||
                                    callType == CallLog.Calls.REJECTED_TYPE ||
                                    callType == CallLog.Calls.BLOCKED_TYPE) {
                                    return@withContext false
                                }

                                // Talk duration MUST be strictly greater than minAnswerDurationSecs.
                                // Carrier IVR announcements (busy, switched off, unanswered IVR) last <= 12s, so they are ignored!
                                return@withContext durationSeconds > minAnswerDurationSecs
                            }
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        return@withContext false
    }

    private fun stopCallLoop(finalStatus: LoopStatus, message: String) {
        loopJob?.cancel()
        countdownJob?.cancel()
        unsetupCallStateMonitor()

        CallLoopManager.updateState {
            it.copy(
                isLoopActive = false,
                status = finalStatus,
                statusDetail = message,
                countdownSecondsRemaining = 0,
                callAnswered = (finalStatus == LoopStatus.CONNECTED),
                errorMessage = if (finalStatus == LoopStatus.ERROR) message else null
            )
        }

        try {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } catch (_: Exception) {}
        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Call Loop Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows live call status during automated call loop"
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(title: String, contentText: String): Notification {
        val stopIntent = Intent(this, CallLoopService::class.java).apply {
            action = ACTION_STOP_LOOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val mainActivityIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this,
            0,
            mainActivityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(contentText)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(contentPendingIntent)
            .setOngoing(true)
            .addAction(R.drawable.ic_launcher_foreground, "STOP LOOP", stopPendingIntent)
            .build()
    }

    private fun updateNotification(title: String, contentText: String) {
        try {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            notificationManager?.notify(NOTIFICATION_ID, buildNotification(title, contentText))
        } catch (_: Exception) {}
    }

    override fun onDestroy() {
        loopJob?.cancel()
        countdownJob?.cancel()
        unsetupCallStateMonitor()
        serviceScope.cancel()
        super.onDestroy()
    }
}
