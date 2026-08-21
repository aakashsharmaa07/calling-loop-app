package com.aakash.callloop.schedule

enum class ScheduleStatus(val label: String) {
    NONE("None"),
    PENDING("Scheduled"),
    RUNNING("Active"),
    COMPLETED("Completed"),
    CANCELLED("Cancelled"),
    EXPIRED("Expired"),
    MISSED("Missed")
}

data class ScheduledCall(
    val id: String = "",
    val phoneNumber: String = "",
    val maxAttempts: Int = 5,
    val delaySeconds: Int = 30,
    val minAnswerDurationSeconds: Int = 12,
    val scheduledTimestamp: Long = 0L,
    val status: ScheduleStatus = ScheduleStatus.NONE,
    val statusDetail: String = ""
)
