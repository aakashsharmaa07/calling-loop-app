package com.aakash.callloop.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aakash.callloop.data.PreferencesRepository
import com.aakash.callloop.domain.CallLoopManager
import com.aakash.callloop.domain.CallLoopState
import com.aakash.callloop.schedule.ScheduleManager
import com.aakash.callloop.schedule.ScheduleRepository
import com.aakash.callloop.schedule.ScheduledCall
import com.aakash.callloop.schedule.ScheduleStatus
import com.aakash.callloop.utils.PhoneNumberUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

data class MainUiState(
    val selectedTab: Int = 0, // 0 = Immediate Manual, 1 = Scheduled
    val phoneNumberInput: String = "+91 ",
    val maxAttemptsInput: Int = 5,
    val delaySecondsInput: Int = 30,
    val minAnswerDurationInput: Int = 12,
    val simPreferenceInput: Int = 0, // 0 = Default, 1 = SIM 1, 2 = SIM 2, 3 = Alternate
    val autoSpeakerInput: Boolean = true,
    val themeModeInput: String = "DARK",
    val isValidPhoneNumber: Boolean = true,
    val loopState: CallLoopState = CallLoopState(),
    val scheduledCalls: List<ScheduledCall> = emptyList(),
    val scheduledCall: ScheduledCall = ScheduledCall(),
    val scheduleErrorMessage: String? = null,
    val permissionDeniedState: Boolean = false,
    val permissionErrorMessage: String? = null
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val preferencesRepository = PreferencesRepository(application)
    private val scheduleRepository = ScheduleRepository(application)

    private val _selectedTab = MutableStateFlow(0)
    private val _phoneNumberInput = MutableStateFlow("+91 ")
    private val _maxAttemptsInput = MutableStateFlow(5)
    private val _delaySecondsInput = MutableStateFlow(30)
    private val _minAnswerDurationInput = MutableStateFlow(12)
    private val _simPreferenceInput = MutableStateFlow(0)
    private val _autoSpeakerInput = MutableStateFlow(true)
    private val _themeModeInput = MutableStateFlow("DARK")
    private val _scheduleErrorMessage = MutableStateFlow<String?>(null)
    private val _permissionDenied = MutableStateFlow(false)
    private val _permissionError = MutableStateFlow<String?>(null)

    private val _userInputsFlow = combine(
        _selectedTab,
        _phoneNumberInput,
        _maxAttemptsInput,
        _delaySecondsInput,
        _minAnswerDurationInput
    ) { tab, phone, maxAttempts, delaySecs, minAnswerDuration ->
        Tuple5(tab, phone, maxAttempts, delaySecs, minAnswerDuration)
    }

    private val _hardwarePrefsFlow = combine(
        _simPreferenceInput,
        _autoSpeakerInput,
        _themeModeInput,
        _scheduleErrorMessage
    ) { simPref, autoSpeaker, theme, schedError ->
        Tuple4(simPref, autoSpeaker, theme, schedError)
    }

    private val _permFlow = combine(
        _permissionDenied,
        _permissionError
    ) { denied, error ->
        Pair(denied, error)
    }

    val uiState: StateFlow<MainUiState> = combine(
        _userInputsFlow,
        _hardwarePrefsFlow,
        CallLoopManager.state,
        ScheduleManager.scheduledCalls,
        _permFlow
    ) { (tab, phone, maxAttempts, delaySecs, minAnswerDuration), (simPref, autoSpeaker, theme, schedError), loopState, scheduledCalls, (permDenied, permError) ->
        val activeOrFirst = scheduledCalls.firstOrNull { it.isRunning || it.isPending } ?: scheduledCalls.firstOrNull() ?: ScheduledCall()
        MainUiState(
            selectedTab = tab,
            phoneNumberInput = phone,
            maxAttemptsInput = maxAttempts,
            delaySecondsInput = delaySecs,
            minAnswerDurationInput = minAnswerDuration,
            simPreferenceInput = simPref,
            autoSpeakerInput = autoSpeaker,
            themeModeInput = theme,
            isValidPhoneNumber = PhoneNumberUtils.isValidPhoneNumber(phone),
            loopState = loopState,
            scheduledCalls = scheduledCalls,
            scheduledCall = activeOrFirst,
            scheduleErrorMessage = schedError,
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
            preferencesRepository.userPreferencesFlow.collect { prefs ->
                _phoneNumberInput.value = prefs.phoneNumber
                _maxAttemptsInput.value = prefs.maxAttempts
                _delaySecondsInput.value = prefs.delaySeconds
                _minAnswerDurationInput.value = prefs.minAnswerDurationSeconds
                _themeModeInput.value = prefs.themeMode
                _simPreferenceInput.value = prefs.simPreference
                _autoSpeakerInput.value = prefs.autoSpeaker
            }
        }

        // Load multi-schedule collection from ScheduleRepository
        viewModelScope.launch {
            scheduleRepository.scheduledCallsFlow.collect { calls ->
                val now = System.currentTimeMillis()
                val updated = calls.map { call ->
                    val isStale = (call.status == ScheduleStatus.RUNNING || call.status == ScheduleStatus.PENDING) &&
                            call.scheduledTimestamp > 0 &&
                            call.scheduledTimestamp < now - 60_000L &&
                            !CallLoopManager.state.value.isLoopActive
                    if (isStale) {
                        call.copy(
                            status = ScheduleStatus.COMPLETED,
                            statusDetail = "Scheduled session finished"
                        )
                    } else call
                }
                ScheduleManager.setScheduledCalls(updated)
            }
        }
    }

    fun onTabSelected(tabIndex: Int) {
        _selectedTab.value = tabIndex
    }

    fun onPhoneNumberChanged(number: String) {
        _phoneNumberInput.value = number
        viewModelScope.launch {
            preferencesRepository.savePhoneNumber(number)
        }
    }

    fun onMaxAttemptsChanged(attempts: Int) {
        val clamped = attempts.coerceIn(1, 20)
        _maxAttemptsInput.value = clamped
        viewModelScope.launch {
            preferencesRepository.saveMaxAttempts(clamped)
        }
    }

    fun onDelaySecondsChanged(delaySecs: Int) {
        val clamped = delaySecs.coerceAtLeast(5)
        _delaySecondsInput.value = clamped
        viewModelScope.launch {
            preferencesRepository.saveDelaySeconds(clamped)
        }
    }

    fun onMinAnswerDurationChanged(durationSecs: Int) {
        val clamped = durationSecs.coerceIn(3, 30)
        _minAnswerDurationInput.value = clamped
        viewModelScope.launch {
            preferencesRepository.saveMinAnswerDuration(clamped)
        }
    }

    fun onSimPreferenceChanged(mode: Int) {
        _simPreferenceInput.value = mode
        viewModelScope.launch {
            preferencesRepository.saveSimPreference(mode)
        }
    }

    fun onAutoSpeakerChanged(enabled: Boolean) {
        _autoSpeakerInput.value = enabled
        viewModelScope.launch {
            preferencesRepository.saveAutoSpeaker(enabled)
        }
    }

    fun onThemeModeChanged(mode: String) {
        _themeModeInput.value = mode
        viewModelScope.launch {
            preferencesRepository.saveThemeMode(mode)
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
        val simPref = _simPreferenceInput.value
        val autoSpeaker = _autoSpeakerInput.value

        if (!PhoneNumberUtils.isValidPhoneNumber(phone)) {
            return
        }

        CallLoopManager.startLoop(
            context = context,
            phoneNumber = phone,
            maxAttempts = maxAttempts,
            delaySeconds = delaySecs,
            minAnswerDurationSeconds = minAnswerDuration,
            simPreference = simPref,
            autoSpeaker = autoSpeaker
        )
    }

    fun stopLoop(context: Context) {
        CallLoopManager.stopLoop(context)
    }

    fun scheduleCall(
        context: Context,
        year: Int,
        month: Int,
        dayOfMonth: Int,
        hourOfDay: Int,
        minute: Int
    ) {
        _scheduleErrorMessage.value = null
        val phone = _phoneNumberInput.value
        val maxAttempts = _maxAttemptsInput.value
        val delaySecs = _delaySecondsInput.value
        val minAnswerDuration = _minAnswerDurationInput.value

        if (!PhoneNumberUtils.isValidPhoneNumber(phone)) {
            _scheduleErrorMessage.value = "Please enter a valid phone number."
            return
        }

        val targetCal = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month)
            set(Calendar.DAY_OF_MONTH, dayOfMonth)
            set(Calendar.HOUR_OF_DAY, hourOfDay)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val targetTimestamp = targetCal.timeInMillis
        val now = System.currentTimeMillis()

        if (targetTimestamp <= now) {
            _scheduleErrorMessage.value = "Please select a future time."
            return
        }

        val scheduledCall = ScheduleManager.scheduleCall(
            context = context,
            phoneNumber = phone,
            maxAttempts = maxAttempts,
            delaySeconds = delaySecs,
            minAnswerDurationSeconds = minAnswerDuration,
            targetTimestamp = targetTimestamp
        )

        viewModelScope.launch {
            scheduleRepository.saveScheduledCall(scheduledCall)
        }
    }

    fun cancelSchedule(context: Context, scheduleId: String) {
        _scheduleErrorMessage.value = null
        ScheduleManager.cancelSchedule(context, scheduleId)
        viewModelScope.launch {
            ScheduleManager.getScheduleById(scheduleId)?.let {
                scheduleRepository.saveScheduledCall(it)
            }
        }
    }

    fun clearCompletedSchedules() {
        val activeOnly = ScheduleManager.scheduledCalls.value.filter { it.isPending || it.isRunning }
        ScheduleManager.setScheduledCalls(activeOnly)
        viewModelScope.launch {
            scheduleRepository.saveAll(activeOnly)
        }
    }
}

private data class Tuple4<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D
)

private data class Tuple5<A, B, C, D, E>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D,
    val fifth: E
)
