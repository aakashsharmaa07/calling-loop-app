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
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.CallLog
import android.telecom.TelecomManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.aakash.callloop.R
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
import kotlinx.coroutines.withContext

class CallLoopService : Service() {

    companion object {
        private const val TAG = "CallLoopService"

        const val ACTION_START_LOOP = "com.aakash.callloop.action.START"
        const val ACTION_STOP_LOOP = "com.aakash.callloop.action.STOP"

        const val EXTRA_PHONE_NUMBER = "extra_phone_number"
        const val EXTRA_MAX_ATTEMPTS = "extra_max_attempts"
        const val EXTRA_DELAY_SECONDS = "extra_delay_seconds"
        const val EXTRA_MIN_ANSWER_DURATION = "extra_min_answer_duration"
        const val EXTRA_SIM_PREFERENCE = "extra_sim_preference"

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
    @Volatile
    private var hasVibratedThisAttempt: Boolean = false

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
                val simPreference = intent.getIntExtra(EXTRA_SIM_PREFERENCE, 0)

                safeStartForeground("Call Loop Active", "Preparing outgoing call to $phoneNumber...")
                startCallLoop(phoneNumber, maxAttempts, delaySeconds, minAnswerDuration, simPreference)
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
        minAnswerDurationSecs: Int,
        simPreference: Int
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

        val activePending = ScheduleManager.scheduledCalls.value.firstOrNull { it.phoneNumber == cleanedNumber && it.isPending }
        if (activePending != null) {
            ScheduleManager.updateSchedule(activePending.id) {
                it.copy(
                    status = ScheduleStatus.RUNNING,
                    statusDetail = "Call loop session active"
                )
            }
        }

        setupCallStateMonitor()

        loopJob = serviceScope.launch {
            try {
                for (attempt in 1..maxAttempts) {
                    if (!CallLoopManager.state.value.isLoopActive) break

                    wasOffHook = false
                    currentAttemptCallEnded = false
                    hasVibratedThisAttempt = false

                    CallLoopManager.updateState {
                        it.copy(
                            currentAttempt = attempt,
                            status = LoopStatus.CALLING,
                            statusDetail = "Calling $cleanedNumber...",
                            countdownSecondsRemaining = 0
                        )
                    }

                    updateNotification("Attempt $attempt / $maxAttempts", "Calling $cleanedNumber")

                    Log.d(TAG, "[CallLoop][CallState] DIALING — Attempt $attempt/$maxAttempts to $cleanedNumber (SIM: $simPreference)")

                    val attemptStartTime = System.currentTimeMillis()
                    val callPlaced = placeCall(cleanedNumber, attempt, simPreference)
                    if (!callPlaced) {
                        Log.e(TAG, "[CallLoop][CallState] CALL_REQUEST_FAILED — Telecom system rejected outgoing call request")
                        stopCallLoop(LoopStatus.ERROR, "Unable to place call via Android Telecom system.")
                        return@launch
                    }

                    Log.d(TAG, "[CallLoop][CallState] CALL_REQUEST_ACCEPTED — Outgoing call accepted. Monitoring call state...")

                    val callWaitStartTime = SystemClock.elapsedRealtime()

                    while (!currentAttemptCallEnded && CallLoopManager.state.value.isLoopActive) {
                        val elapsedSecs = (SystemClock.elapsedRealtime() - callWaitStartTime) / 1000
                        if (elapsedSecs > 0 && elapsedSecs % 5 == 0L) {
                            Log.d(TAG, "[CallLoop][CallState] RINGING / IN-CALL — elapsed = ${elapsedSecs}s (wasOffHook=$wasOffHook, callEnded=$currentAttemptCallEnded)")
                        }
                        delay(500)
                    }

                    if (!CallLoopManager.state.value.isLoopActive) break

                    Log.d(TAG, "[CallLoop][CallState] DISCONNECTED / IDLE — Attempt $attempt ended. Checking CallLog...")

                    val isAnswered = checkIfCallWasAnswered(attemptStartTime, cleanedNumber, minAnswerDurationSecs)
                    if (isAnswered) {
                        Log.d(TAG, "[CallLoop][AnswerDetection] ANSWERED — CallLog confirmed duration > ${minAnswerDurationSecs}s!")
                        vibrateOnConnect()
                        CallLoopManager.updateState {
                            it.copy(
                                status = LoopStatus.CONNECTED,
                                statusDetail = "CALL ANSWERED — LOOP STOPPED",
                                callAnswered = true,
                                isLoopActive = false
                            )
                        }
                        updateNotification("Call Answered", "Call connected. Loop stopped.")
                        markScheduleCompleted("CALL ANSWERED — LOOP STOPPED")
                        stopCallLoop(LoopStatus.CONNECTED, "CALL ANSWERED — LOOP STOPPED")
                        return@launch
                    } else {
                        Log.d(TAG, "[CallLoop][AnswerDetection] Rejected: Call duration <= ${minAnswerDurationSecs}s or unanswered")
                    }

                    CallLoopManager.updateState {
                        it.copy(
                            status = LoopStatus.CALL_ENDED,
                            statusDetail = "Attempt $attempt ended without answer"
                        )
                    }

                    if (attempt < maxAttempts) {
                        Log.d(TAG, "[CallLoop][Retry] Retry scheduled: Waiting $delaySeconds seconds before attempt ${attempt + 1}")
                        startCountdown(delaySeconds, attempt, maxAttempts, cleanedNumber)
                    } else {
                        Log.d(TAG, "[CallLoop][Retry] Maximum attempts ($maxAttempts) reached")
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
    private fun placeCall(phoneNumber: String, attemptIndex: Int, simPreference: Int): Boolean {
        return try {
            val uri = Uri.fromParts("tel", phoneNumber, null)
            val telecomManager = getSystemService(Context.TELECOM_SERVICE) as? TelecomManager

            if (telecomManager != null && ContextCompat.checkSelfPermission(this, android.Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
                val simModeStr = when (simPreference) {
                    1 -> "SIM_1"
                    2 -> "SIM_2"
                    3 -> "ALTERNATE"
                    else -> "DEFAULT"
                }
                Log.d(TAG, "[CallLoop][SIM] Requested mode = $simModeStr")
                Log.d(TAG, "[CallLoop][SIM] Attempt = $attemptIndex")

                val extras = Bundle()
                if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
                    val accounts = telecomManager.callCapablePhoneAccounts
                    if (!accounts.isNullOrEmpty()) {
                        val handleToUse = when (simPreference) {
                            1 -> accounts.getOrNull(0)
                            2 -> accounts.getOrNull(1) ?: accounts.getOrNull(0)
                            3 -> if (attemptIndex % 2 != 0) accounts.getOrNull(0) else (accounts.getOrNull(1) ?: accounts.getOrNull(0))
                            else -> null
                        }
                        if (handleToUse != null) {
                            extras.putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, handleToUse)
                            Log.d(TAG, "[CallLoop][SIM] Resolved PhoneAccount = $handleToUse")
                            Log.d(TAG, "[CallLoop][SIM] Passing PhoneAccountHandle to TelecomManager")
                        } else {
                            Log.d(TAG, "[CallLoop][SIM] Using system default SIM (no explicit PhoneAccountHandle)")
                        }
                    } else {
                        Log.d(TAG, "[CallLoop][SIM] No callCapablePhoneAccounts found, using default")
                    }
                }
                telecomManager.placeCall(uri, extras)
                Log.d(TAG, "TelecomManager.placeCall executed for tel:$phoneNumber with SIM: $simPreference")
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

    private fun vibrateOnConnect() {
        if (hasVibratedThisAttempt) return
        hasVibratedThisAttempt = true

        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }

            if (vibrator?.hasVibrator() == true) {
                val pattern = longArrayOf(0, 250, 150, 250)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(pattern, -1)
                }
                Log.d(TAG, "Distinct connect vibration triggered")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to trigger connect vibration", e)
        }
    }

    private fun setupCallStateMonitor() {
        if (isCallStateRegistered) return

        callStateMonitor = CallStateMonitor(this) { newState ->
            Log.d(TAG, "[CallLoop][CallState] State changed: $newState")
            when (newState) {
                PhoneCallState.RINGING -> {
                    Log.d(TAG, "[CallLoop][CallState] RINGING")
                    CallLoopManager.updateState { it.copy(status = LoopStatus.RINGING) }
                    updateNotification("Ringing...", "Call dialing recipient")
                }
                PhoneCallState.OFFHOOK -> {
                    wasOffHook = true
                    Log.d(TAG, "[CallLoop][CallState] ACTIVE / OFFHOOK")
                    CallLoopManager.updateState {
                        it.copy(
                            status = LoopStatus.RINGING,
                            statusDetail = "Calling / Active"
                        )
                    }
                }
                PhoneCallState.IDLE -> {
                    Log.d(TAG, "[CallLoop][CallState] DISCONNECTED / IDLE (wasOffHook=$wasOffHook)")
                    if (wasOffHook) {
                        currentAttemptCallEnded = true
                    }
                }
            }
        }
        callStateMonitor?.register()
        isCallStateRegistered = true
    }

    private suspend fun checkIfCallWasAnswered(
        attemptStartTime: Long,
        dialedNumber: String,
        minAnswerDurationSecs: Int
    ): Boolean = withContext(Dispatchers.IO) {
        if (!wasOffHook) {
            Log.d(TAG, "[CallLoop][AnswerDetection] Rejected: Handset never went OFFHOOK (unanswered/rejected)")
            return@withContext false
        }

        if (ContextCompat.checkSelfPermission(this@CallLoopService, android.Manifest.permission.READ_CALL_LOG)
            != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "[CallLoop][AnswerDetection] READ_CALL_LOG permission not granted")
            return@withContext false
        }

        // Retry up to 5 times (total ~2 seconds) to allow Android OS SQLite to commit the call log entry
        val cleanedDialed = PhoneNumberUtils.cleanPhoneNumber(dialedNumber)
        val searchSuffix = if (cleanedDialed.length >= 8) cleanedDialed.takeLast(8) else cleanedDialed

        for (retry in 1..5) {
            delay(400)
            try {
                val minDate = (attemptStartTime - 3000L).coerceAtLeast(0L)
                val cursor = contentResolver.query(
                    CallLog.Calls.CONTENT_URI,
                    arrayOf(CallLog.Calls.TYPE, CallLog.Calls.DURATION, CallLog.Calls.NUMBER, CallLog.Calls.DATE),
                    "${CallLog.Calls.DATE} >= ?",
                    arrayOf(minDate.toString()),
                    "${CallLog.Calls.DATE} DESC"
                )

                cursor?.use { c ->
                    val typeIndex = c.getColumnIndex(CallLog.Calls.TYPE)
                    val durationIndex = c.getColumnIndex(CallLog.Calls.DURATION)
                    val numberIndex = c.getColumnIndex(CallLog.Calls.NUMBER)

                    while (c.moveToNext()) {
                        val callType = if (typeIndex >= 0) c.getInt(typeIndex) else -1
                        val durationSeconds = if (durationIndex >= 0) c.getInt(durationIndex) else 0
                        val rawNumber = if (numberIndex >= 0) c.getString(numberIndex) ?: "" else ""
                        val cleanedRecordNumber = PhoneNumberUtils.cleanPhoneNumber(rawNumber)

                        val isMatchingNumber = searchSuffix.isEmpty() ||
                                cleanedRecordNumber.endsWith(searchSuffix) ||
                                cleanedDialed.endsWith(if (cleanedRecordNumber.length >= 8) cleanedRecordNumber.takeLast(8) else cleanedRecordNumber)

                        if (isMatchingNumber) {
                            Log.d(TAG, "[CallLoop][AnswerDetection] CallLog match (retry $retry) — Type: $callType, Duration: ${durationSeconds}s, Number: $rawNumber")

                            if (callType == CallLog.Calls.MISSED_TYPE ||
                                callType == CallLog.Calls.REJECTED_TYPE ||
                                callType == CallLog.Calls.BLOCKED_TYPE) {
                                Log.d(TAG, "[CallLoop][AnswerDetection] Rejected: Call type is missed/rejected/blocked (type=$callType)")
                                return@withContext false
                            }

                            if (callType == CallLog.Calls.OUTGOING_TYPE) {
                                if (durationSeconds > minAnswerDurationSecs) {
                                    Log.d(TAG, "[CallLoop][AnswerDetection] ANSWERED: Duration ${durationSeconds}s > threshold ${minAnswerDurationSecs}s")
                                    return@withContext true
                                } else {
                                    Log.d(TAG, "[CallLoop][AnswerDetection] Rejected: Duration ${durationSeconds}s <= threshold ${minAnswerDurationSecs}s (still ringing/IVR/busy)")
                                    return@withContext false
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "[CallLoop][AnswerDetection] Error checking CallLog on retry $retry", e)
            }
        }
        Log.d(TAG, "[CallLoop][AnswerDetection] Rejected: No matching CallLog entry found within window (unanswered)")
        false
    }

    private fun markScheduleCompleted(detail: String) {
        val runningSchedule = ScheduleManager.scheduledCalls.value.firstOrNull { it.status == ScheduleStatus.RUNNING }
            ?: ScheduleManager.scheduledCalls.value.firstOrNull { it.phoneNumber == CallLoopManager.state.value.phoneNumber && it.isPending }

        if (runningSchedule != null) {
            ScheduleManager.updateSchedule(runningSchedule.id) {
                it.copy(
                    status = ScheduleStatus.COMPLETED,
                    statusDetail = detail
                )
            }
            val repository = ScheduleRepository(applicationContext)
            serviceScope.launch {
                ScheduleManager.getScheduleById(runningSchedule.id)?.let {
                    repository.saveScheduledCall(it)
                }
            }
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

        val runningSchedule = ScheduleManager.scheduledCalls.value.firstOrNull { it.status == ScheduleStatus.RUNNING }
            ?: ScheduleManager.scheduledCalls.value.firstOrNull { it.phoneNumber == CallLoopManager.state.value.phoneNumber && it.isPending }

        if (runningSchedule != null) {
            ScheduleManager.updateSchedule(runningSchedule.id) {
                it.copy(
                    status = targetScheduleStatus,
                    statusDetail = detail
                )
            }
            val repository = ScheduleRepository(applicationContext)
            serviceScope.launch {
                ScheduleManager.getScheduleById(runningSchedule.id)?.let {
                    repository.saveScheduledCall(it)
                }
            }
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
            .setSmallIcon(R.drawable.ic_notification)
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
