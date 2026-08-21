package com.aakash.callloop.domain

enum class LoopStatus(val label: String) {
    READY("Ready"),
    CALLING("Calling"),
    RINGING("Ringing"),
    CONNECTED("Connected"),
    CALL_ENDED("Call Ended"),
    WAITING("Waiting for next attempt"),
    MAX_ATTEMPTS_REACHED("Maximum attempts reached"),
    STOPPED_BY_USER("Stopped by user"),
    PERMISSION_REQUIRED("Permission required"),
    CALL_UNAVAILABLE("Call unavailable"),
    ERROR("Error")
}

data class CallLoopState(
    val isLoopActive: Boolean = false,
    val currentAttempt: Int = 0,
    val maxAttempts: Int = 5,
    val delaySeconds: Int = 30,
    val minAnswerDurationSeconds: Int = 12,
    val phoneNumber: String = "",
    val status: LoopStatus = LoopStatus.READY,
    val statusDetail: String = "",
    val countdownSecondsRemaining: Int = 0,
    val callAnswered: Boolean = false,
    val errorMessage: String? = null
)
