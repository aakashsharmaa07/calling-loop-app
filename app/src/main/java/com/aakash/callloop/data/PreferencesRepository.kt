package com.aakash.callloop.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "call_loop_settings")

data class UserPreferences(
    val phoneNumber: String,
    val countryCode: String = "IN",
    val maxAttempts: Int,
    val delaySeconds: Int,
    val minAnswerDurationSeconds: Int,
    val themeMode: String,
    val simPreference: Int = 0 // 0 = Default, 1 = SIM 1, 2 = SIM 2, 3 = Alternate
)

class PreferencesRepository(private val context: Context) {

    private object PreferencesKeys {
        val PHONE_NUMBER = stringPreferencesKey("phone_number")
        val COUNTRY_CODE = stringPreferencesKey("country_code")
        val MAX_ATTEMPTS = intPreferencesKey("max_attempts")
        val DELAY_SECONDS = intPreferencesKey("delay_seconds")
        val MIN_ANSWER_DURATION = intPreferencesKey("min_answer_duration")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val SIM_PREFERENCE = intPreferencesKey("sim_preference")
    }

    val userPreferencesFlow: Flow<UserPreferences> = context.dataStore.data
        .map { preferences ->
            val phoneNumber = preferences[PreferencesKeys.PHONE_NUMBER] ?: ""
            val countryCode = preferences[PreferencesKeys.COUNTRY_CODE] ?: "IN"
            val maxAttempts = (preferences[PreferencesKeys.MAX_ATTEMPTS] ?: 5).coerceIn(1, 20)
            val delaySeconds = (preferences[PreferencesKeys.DELAY_SECONDS] ?: 30).coerceAtLeast(5)
            val minAnswerDuration = (preferences[PreferencesKeys.MIN_ANSWER_DURATION] ?: 12).coerceIn(3, 30)
            val themeMode = preferences[PreferencesKeys.THEME_MODE] ?: "DARK"
            val simPreference = preferences[PreferencesKeys.SIM_PREFERENCE] ?: 0
            UserPreferences(phoneNumber, countryCode, maxAttempts, delaySeconds, minAnswerDuration, themeMode, simPreference)
        }

    suspend fun savePhoneNumber(phoneNumber: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.PHONE_NUMBER] = phoneNumber
        }
    }

    suspend fun saveCountryCode(countryCode: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.COUNTRY_CODE] = countryCode
        }
    }

    suspend fun saveMaxAttempts(maxAttempts: Int) {
        val clamped = maxAttempts.coerceIn(1, 20)
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.MAX_ATTEMPTS] = clamped
        }
    }

    suspend fun saveDelaySeconds(delaySeconds: Int) {
        val clamped = delaySeconds.coerceAtLeast(5)
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.DELAY_SECONDS] = clamped
        }
    }

    suspend fun saveMinAnswerDuration(seconds: Int) {
        val clamped = seconds.coerceIn(3, 30)
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.MIN_ANSWER_DURATION] = clamped
        }
    }

    suspend fun saveThemeMode(mode: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.THEME_MODE] = mode
        }
    }

    suspend fun saveSimPreference(mode: Int) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.SIM_PREFERENCE] = mode
        }
    }
}
