package com.aakash.callloop.schedule

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

private val Context.scheduleDataStore: DataStore<Preferences> by preferencesDataStore(name = "call_loop_schedule")

class ScheduleRepository(private val context: Context) {

    private object PreferencesKeys {
        val SCHEDULED_CALLS_JSON = stringPreferencesKey("scheduled_calls_json")

        // Legacy single-schedule keys for automatic migration
        val LEGACY_SCHEDULE_ID = stringPreferencesKey("schedule_id")
        val LEGACY_PHONE_NUMBER = stringPreferencesKey("phone_number")
        val LEGACY_MAX_ATTEMPTS = androidx.datastore.preferences.core.intPreferencesKey("max_attempts")
        val LEGACY_DELAY_SECONDS = androidx.datastore.preferences.core.intPreferencesKey("delay_seconds")
        val LEGACY_MIN_ANSWER_DURATION = androidx.datastore.preferences.core.intPreferencesKey("min_answer_duration")
        val LEGACY_SCHEDULED_TIMESTAMP = androidx.datastore.preferences.core.longPreferencesKey("scheduled_timestamp")
        val LEGACY_STATUS = stringPreferencesKey("status")
        val LEGACY_STATUS_DETAIL = stringPreferencesKey("status_detail")
    }

    val scheduledCallsFlow: Flow<List<ScheduledCall>> = context.scheduleDataStore.data
        .map { preferences ->
            val jsonStr = preferences[PreferencesKeys.SCHEDULED_CALLS_JSON]
            if (!jsonStr.isNullOrBlank()) {
                parseJsonList(jsonStr)
            } else {
                // Check if legacy single-schedule exists
                val legacyId = preferences[PreferencesKeys.LEGACY_SCHEDULE_ID]
                if (!legacyId.isNullOrBlank()) {
                    val legacyPhone = preferences[PreferencesKeys.LEGACY_PHONE_NUMBER] ?: ""
                    val legacyMaxAttempts = preferences[PreferencesKeys.LEGACY_MAX_ATTEMPTS] ?: 5
                    val legacyDelay = preferences[PreferencesKeys.LEGACY_DELAY_SECONDS] ?: 30
                    val legacyMinAnswer = preferences[PreferencesKeys.LEGACY_MIN_ANSWER_DURATION] ?: 12
                    val legacyTime = preferences[PreferencesKeys.LEGACY_SCHEDULED_TIMESTAMP] ?: 0L
                    val legacyStatusStr = preferences[PreferencesKeys.LEGACY_STATUS] ?: ScheduleStatus.NONE.name
                    val legacyDetail = preferences[PreferencesKeys.LEGACY_STATUS_DETAIL] ?: ""
                    val legacyStatus = try { ScheduleStatus.valueOf(legacyStatusStr) } catch (_: Exception) { ScheduleStatus.NONE }

                    listOf(
                        ScheduledCall(
                            id = legacyId,
                            phoneNumber = legacyPhone,
                            maxAttempts = legacyMaxAttempts,
                            delaySeconds = legacyDelay,
                            minAnswerDurationSeconds = legacyMinAnswer,
                            scheduledTimestamp = legacyTime,
                            status = legacyStatus,
                            statusDetail = legacyDetail
                        )
                    )
                } else {
                    emptyList()
                }
            }
        }

    suspend fun saveScheduledCall(scheduledCall: ScheduledCall) {
        context.scheduleDataStore.edit { preferences ->
            val currentList = parseJsonList(preferences[PreferencesKeys.SCHEDULED_CALLS_JSON]).toMutableList()
            val index = currentList.indexOfFirst { it.id == scheduledCall.id }
            if (index >= 0) {
                currentList[index] = scheduledCall
            } else {
                currentList.add(scheduledCall)
            }
            preferences[PreferencesKeys.SCHEDULED_CALLS_JSON] = toJsonList(currentList)
        }
    }

    suspend fun saveAll(calls: List<ScheduledCall>) {
        context.scheduleDataStore.edit { preferences ->
            preferences[PreferencesKeys.SCHEDULED_CALLS_JSON] = toJsonList(calls)
        }
    }

    suspend fun removeScheduledCall(id: String) {
        context.scheduleDataStore.edit { preferences ->
            val currentList = parseJsonList(preferences[PreferencesKeys.SCHEDULED_CALLS_JSON]).filter { it.id != id }
            preferences[PreferencesKeys.SCHEDULED_CALLS_JSON] = toJsonList(currentList)
        }
    }

    suspend fun clearAll() {
        context.scheduleDataStore.edit { preferences ->
            preferences.clear()
        }
    }

    private fun parseJsonList(jsonStr: String?): List<ScheduledCall> {
        if (jsonStr.isNullOrBlank()) return emptyList()
        val list = mutableListOf<ScheduledCall>()
        try {
            val arr = JSONArray(jsonStr)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val statusStr = obj.optString("status", ScheduleStatus.NONE.name)
                val status = try { ScheduleStatus.valueOf(statusStr) } catch (_: Exception) { ScheduleStatus.NONE }

                list.add(
                    ScheduledCall(
                        id = obj.optString("id", UUID.randomUUID().toString()),
                        phoneNumber = obj.optString("phoneNumber", ""),
                        maxAttempts = obj.optInt("maxAttempts", 5),
                        delaySeconds = obj.optInt("delaySeconds", 30),
                        minAnswerDurationSeconds = obj.optInt("minAnswerDurationSeconds", 12),
                        scheduledTimestamp = obj.optLong("scheduledTimestamp", 0L),
                        status = status,
                        statusDetail = obj.optString("statusDetail", ""),
                        simPreference = obj.optInt("simPreference", 0)
                    )
                )
            }
        } catch (_: Exception) {}
        return list
    }

    private fun toJsonList(list: List<ScheduledCall>): String {
        val arr = JSONArray()
        for (call in list) {
            val obj = JSONObject().apply {
                put("id", call.id)
                put("phoneNumber", call.phoneNumber)
                put("maxAttempts", call.maxAttempts)
                put("delaySeconds", call.delaySeconds)
                put("minAnswerDurationSeconds", call.minAnswerDurationSeconds)
                put("scheduledTimestamp", call.scheduledTimestamp)
                put("status", call.status.name)
                put("statusDetail", call.statusDetail)
                put("simPreference", call.simPreference)
            }
            arr.put(obj)
        }
        return arr.toString()
    }
}
