package com.aakash.callloop.schedule

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.scheduleDataStore: DataStore<Preferences> by preferencesDataStore(name = "call_loop_schedule")

class ScheduleRepository(private val context: Context) {

    private object PreferencesKeys {
        val SCHEDULE_ID = stringPreferencesKey("schedule_id")
        val PHONE_NUMBER = stringPreferencesKey("phone_number")
        val MAX_ATTEMPTS = intPreferencesKey("max_attempts")
        val DELAY_SECONDS = intPreferencesKey("delay_seconds")
        val MIN_ANSWER_DURATION = intPreferencesKey("min_answer_duration")
        val SCHEDULED_TIMESTAMP = longPreferencesKey("scheduled_timestamp")
        val STATUS = stringPreferencesKey("status")
        val STATUS_DETAIL = stringPreferencesKey("status_detail")
    }

    val scheduledCallFlow: Flow<ScheduledCall> = context.scheduleDataStore.data
        .map { preferences ->
            val id = preferences[PreferencesKeys.SCHEDULE_ID] ?: ""
            val phoneNumber = preferences[PreferencesKeys.PHONE_NUMBER] ?: ""
            val maxAttempts = preferences[PreferencesKeys.MAX_ATTEMPTS] ?: 5
            val delaySeconds = preferences[PreferencesKeys.DELAY_SECONDS] ?: 30
            val minAnswerDuration = preferences[PreferencesKeys.MIN_ANSWER_DURATION] ?: 12
            val scheduledTimestamp = preferences[PreferencesKeys.SCHEDULED_TIMESTAMP] ?: 0L
            val statusStr = preferences[PreferencesKeys.STATUS] ?: ScheduleStatus.NONE.name
            val statusDetail = preferences[PreferencesKeys.STATUS_DETAIL] ?: ""

            val status = try {
                ScheduleStatus.valueOf(statusStr)
            } catch (_: Exception) {
                ScheduleStatus.NONE
            }

            ScheduledCall(
                id = id,
                phoneNumber = phoneNumber,
                maxAttempts = maxAttempts,
                delaySeconds = delaySeconds,
                minAnswerDurationSeconds = minAnswerDuration,
                scheduledTimestamp = scheduledTimestamp,
                status = status,
                statusDetail = statusDetail
            )
        }

    suspend fun saveScheduledCall(scheduledCall: ScheduledCall) {
        context.scheduleDataStore.edit { preferences ->
            preferences[PreferencesKeys.SCHEDULE_ID] = scheduledCall.id
            preferences[PreferencesKeys.PHONE_NUMBER] = scheduledCall.phoneNumber
            preferences[PreferencesKeys.MAX_ATTEMPTS] = scheduledCall.maxAttempts
            preferences[PreferencesKeys.DELAY_SECONDS] = scheduledCall.delaySeconds
            preferences[PreferencesKeys.MIN_ANSWER_DURATION] = scheduledCall.minAnswerDurationSeconds
            preferences[PreferencesKeys.SCHEDULED_TIMESTAMP] = scheduledCall.scheduledTimestamp
            preferences[PreferencesKeys.STATUS] = scheduledCall.status.name
            preferences[PreferencesKeys.STATUS_DETAIL] = scheduledCall.statusDetail
        }
    }

    suspend fun clearScheduledCall() {
        context.scheduleDataStore.edit { preferences ->
            preferences.clear()
        }
    }
}
