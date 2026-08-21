package com.aakash.callloop.domain

import android.content.Context
import android.content.Intent
import android.os.Build
import com.aakash.callloop.service.CallLoopService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object CallLoopManager {
    private val _state = MutableStateFlow(CallLoopState())
    val state: StateFlow<CallLoopState> = _state.asStateFlow()

    fun updateState(transform: (CallLoopState) -> CallLoopState) {
        _state.value = transform(_state.value)
    }

    fun startLoop(
        context: Context,
        phoneNumber: String,
        maxAttempts: Int,
        delaySeconds: Int,
        minAnswerDurationSeconds: Int = 12
    ) {
        val intent = Intent(context, CallLoopService::class.java).apply {
            action = CallLoopService.ACTION_START_LOOP
            putExtra(CallLoopService.EXTRA_PHONE_NUMBER, phoneNumber)
            putExtra(CallLoopService.EXTRA_MAX_ATTEMPTS, maxAttempts)
            putExtra(CallLoopService.EXTRA_DELAY_SECONDS, delaySeconds)
            putExtra(CallLoopService.EXTRA_MIN_ANSWER_DURATION, minAnswerDurationSeconds)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    fun stopLoop(context: Context) {
        val intent = Intent(context, CallLoopService::class.java).apply {
            action = CallLoopService.ACTION_STOP_LOOP
        }
        context.startService(intent)
    }
}
