package com.aakash.callloop.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aakash.callloop.data.PreferencesRepository
import com.aakash.callloop.domain.CallLoopManager
import com.aakash.callloop.domain.CallLoopState
import com.aakash.callloop.utils.PhoneNumberUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MainUiState(
    val phoneNumberInput: String = "+91 ",
    val maxAttemptsInput: Int = 5,
    val delaySecondsInput: Int = 30,
    val minAnswerDurationInput: Int = 12,
    val themeModeInput: String = "DARK",
    val isValidPhoneNumber: Boolean = true,
    val loopState: CallLoopState = CallLoopState(),
    val permissionDeniedState: Boolean = false,
    val permissionErrorMessage: String? = null
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = PreferencesRepository(application)

    private val _phoneNumberInput = MutableStateFlow("+91 ")
    private val _maxAttemptsInput = MutableStateFlow(5)
    private val _delaySecondsInput = MutableStateFlow(30)
    private val _minAnswerDurationInput = MutableStateFlow(12)
    private val _themeModeInput = MutableStateFlow("DARK")
    private val _permissionDenied = MutableStateFlow(false)
    private val _permissionError = MutableStateFlow<String?>(null)

    private val _userInputsFlow = combine(
        _phoneNumberInput,
        _maxAttemptsInput,
        _delaySecondsInput,
        _minAnswerDurationInput
    ) { phone, maxAttempts, delaySecs, minAnswerDuration ->
        Tuple4(phone, maxAttempts, delaySecs, minAnswerDuration)
    }

    private val _themeAndPermFlow = combine(
        _themeModeInput,
        _permissionDenied,
        _permissionError
    ) { theme, denied, error ->
        Triple(theme, denied, error)
    }

    val uiState: StateFlow<MainUiState> = combine(
        _userInputsFlow,
        CallLoopManager.state,
        _themeAndPermFlow
    ) { (phone, maxAttempts, delaySecs, minAnswerDuration), loopState, (theme, permDenied, permError) ->
        MainUiState(
            phoneNumberInput = phone,
            maxAttemptsInput = maxAttempts,
            delaySecondsInput = delaySecs,
            minAnswerDurationInput = minAnswerDuration,
            themeModeInput = theme,
            isValidPhoneNumber = PhoneNumberUtils.isValidPhoneNumber(phone),
            loopState = loopState,
            permissionDeniedState = permDenied,
            permissionErrorMessage = permError
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = MainUiState()
    )

    init {
        // Load initial preferences from DataStore
        viewModelScope.launch {
            repository.userPreferencesFlow.collect { prefs ->
                _phoneNumberInput.value = prefs.phoneNumber
                _maxAttemptsInput.value = prefs.maxAttempts
                _delaySecondsInput.value = prefs.delaySeconds
                _minAnswerDurationInput.value = prefs.minAnswerDurationSeconds
                _themeModeInput.value = prefs.themeMode
            }
        }
    }

    fun onPhoneNumberChanged(number: String) {
        _phoneNumberInput.value = number
        viewModelScope.launch {
            repository.savePhoneNumber(number)
        }
    }

    fun onMaxAttemptsChanged(attempts: Int) {
        val clamped = attempts.coerceIn(1, 20)
        _maxAttemptsInput.value = clamped
        viewModelScope.launch {
            repository.saveMaxAttempts(clamped)
        }
    }

    fun onDelaySecondsChanged(delaySecs: Int) {
        val clamped = delaySecs.coerceAtLeast(5)
        _delaySecondsInput.value = clamped
        viewModelScope.launch {
            repository.saveDelaySeconds(clamped)
        }
    }

    fun onMinAnswerDurationChanged(durationSecs: Int) {
        val clamped = durationSecs.coerceIn(3, 30)
        _minAnswerDurationInput.value = clamped
        viewModelScope.launch {
            repository.saveMinAnswerDuration(clamped)
        }
    }

    fun onThemeModeChanged(mode: String) {
        _themeModeInput.value = mode
        viewModelScope.launch {
            repository.saveThemeMode(mode)
        }
    }

    fun onContactSelected(rawNumber: String) {
        val cleaned = PhoneNumberUtils.cleanPhoneNumber(rawNumber)
        onPhoneNumberChanged(cleaned)
    }

    fun setPermissionDenied(denied: Boolean, message: String? = null) {
        _permissionDenied.value = denied
        _permissionError.value = message
    }

    fun startLoop(context: Context) {
        val phone = _phoneNumberInput.value
        val maxAttempts = _maxAttemptsInput.value
        val delaySecs = _delaySecondsInput.value
        val minAnswerDuration = _minAnswerDurationInput.value

        if (!PhoneNumberUtils.isValidPhoneNumber(phone)) {
            return
        }

        CallLoopManager.startLoop(
            context = context,
            phoneNumber = phone,
            maxAttempts = maxAttempts,
            delaySeconds = delaySecs,
            minAnswerDurationSeconds = minAnswerDuration
        )
    }

    fun stopLoop(context: Context) {
        CallLoopManager.stopLoop(context)
    }
}

private data class Tuple4<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D
)
