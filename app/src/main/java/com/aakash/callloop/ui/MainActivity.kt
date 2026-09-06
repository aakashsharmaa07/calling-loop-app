package com.aakash.callloop.ui

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.ContactsContract
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContactPhone
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.window.Dialog
import com.aakash.callloop.data.Country
import com.aakash.callloop.data.CountryData
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import androidx.core.content.ContextCompat
import com.aakash.callloop.domain.LoopStatus
import com.aakash.callloop.schedule.ScheduleStatus
import com.aakash.callloop.ui.theme.CallLoopTheme
import com.aakash.callloop.ui.theme.DeepCoffee
import com.aakash.callloop.ui.theme.GlassBorderDark
import com.aakash.callloop.ui.theme.GlassBorderLight
import com.aakash.callloop.ui.theme.MutedAsh
import com.aakash.callloop.ui.theme.RawWalnut
import com.aakash.callloop.ui.theme.RoastedCoffee
import com.aakash.callloop.ui.theme.SoftPaper
import com.aakash.callloop.ui.theme.StatusError
import com.aakash.callloop.ui.theme.StatusErrorContainer
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val uiState by viewModel.uiState.collectAsState()
            val isDarkTheme = uiState.themeModeInput == "DARK"

            CallLoopTheme(darkTheme = isDarkTheme) {
                MainScreen(viewModel = viewModel)
            }
        }
    }
}

data class DelayOption(val label: String, val seconds: Int)
data class IvrThresholdOption(val label: String, val seconds: Int)

val delayOptions = listOf(
    DelayOption("5 seconds", 5),
    DelayOption("10 seconds", 10),
    DelayOption("15 seconds", 15),
    DelayOption("30 seconds (Default)", 30),
    DelayOption("1 minute", 60),
    DelayOption("2 minutes", 120),
    DelayOption("5 minutes", 300),
    DelayOption("10 minutes", 600)
)

val ivrThresholdOptions = listOf(
    IvrThresholdOption("5 seconds", 5),
    IvrThresholdOption("8 seconds", 8),
    IvrThresholdOption("10 seconds", 10),
    IvrThresholdOption("12 seconds (Recommended)", 12),
    IvrThresholdOption("15 seconds", 15),
    IvrThresholdOption("20 seconds", 20)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val isDarkTheme = uiState.themeModeInput == "DARK"

    // Runtime Permission Launcher
    val requiredPermissions = mutableListOf(
        Manifest.permission.CALL_PHONE,
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.READ_CALL_LOG,
        Manifest.permission.READ_CONTACTS
    ).apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }.toTypedArray()

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissionsResult ->
        val allGranted = permissionsResult.values.all { it }
        if (allGranted) {
            viewModel.setPermissionDenied(false)
            if (uiState.selectedTab == 0) {
                viewModel.startLoop(context)
            }
        } else {
            viewModel.setPermissionDenied(
                true,
                "Phone, Call State, Call Log, and Contacts permissions are required."
            )
        }
    }

    // Contact Picker Launcher
    val contactPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == ComponentActivity.RESULT_OK) {
            result.data?.data?.let { contactUri ->
                extractPhoneNumberFromContact(context, contactUri)?.let { selectedNumber ->
                    viewModel.onContactSelected(selectedNumber)
                }
            }
        }
    }

    val haptic = LocalHapticFeedback.current

    // Date & Time picker state for Scheduled tab
    val currentCal = Calendar.getInstance().apply { add(Calendar.MINUTE, 5) }
    var selectedYear by remember { mutableStateOf(currentCal.get(Calendar.YEAR)) }
    var selectedMonth by remember { mutableStateOf(currentCal.get(Calendar.MONTH)) }
    var selectedDay by remember { mutableStateOf(currentCal.get(Calendar.DAY_OF_MONTH)) }
    var selectedHour by remember { mutableStateOf(currentCal.get(Calendar.HOUR_OF_DAY)) }
    var selectedMinute by remember { mutableStateOf(currentCal.get(Calendar.MINUTE)) }

    var showCountryPicker by remember { mutableStateOf(false) }
    var isPhoneFocused by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Scaffold(
            containerColor = Color.Transparent
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .imePadding()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {

                // Header with Theme Toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "CALL LOOP",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground,
                            letterSpacing = 2.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Automated Call Retry",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            val newTheme = if (isDarkTheme) "LIGHT" else "DARK"
                            viewModel.onThemeModeChanged(newTheme)
                        }
                    ) {
                        Icon(
                            imageVector = if (isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                            contentDescription = "Toggle Theme",
                            tint = if (isDarkTheme) SoftPaper else RoastedCoffee
                        )
                    }
                }

                // Mode Selector Tabs (Immediate vs Scheduled)
                TabRow(
                    selectedTabIndex = uiState.selectedTab,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    indicator = { tabPositions ->
                        TabRowDefaults.Indicator(
                            Modifier.tabIndicatorOffset(tabPositions[uiState.selectedTab]),
                            color = if (isDarkTheme) SoftPaper else RoastedCoffee
                        )
                    },
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .border(
                            1.dp,
                            if (isDarkTheme) GlassBorderDark else GlassBorderLight,
                            RoundedCornerShape(14.dp)
                        )
                ) {
                    Tab(
                        selected = uiState.selectedTab == 0,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.onTabSelected(0)
                        },
                        text = {
                            Text(
                                "IMMEDIATE",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                letterSpacing = 1.sp
                            )
                        }
                    )
                    Tab(
                        selected = uiState.selectedTab == 1,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.onTabSelected(1)
                        },
                        text = {
                            Text(
                                "SCHEDULED",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                letterSpacing = 1.sp
                            )
                        }
                    )
                }

                // Permission Warning Card if denied
                AnimatedVisibility(
                    visible = uiState.permissionDeniedState,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Surface(
                        color = StatusErrorContainer,
                        shape = RoundedCornerShape(16.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, StatusError.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Permission Error",
                                tint = StatusError,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Permission Required",
                                    fontWeight = FontWeight.Bold,
                                    color = StatusError,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = uiState.permissionErrorMessage
                                        ?: "Permissions are required to place calls.",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = { openAppSettings(context) },
                                    colors = ButtonDefaults.buttonColors(containerColor = StatusError),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("OPEN SETTINGS", fontSize = 12.sp, color = Color.White)
                                }
                            }
                        }
                    }
                }

                // Phone Number Input Card
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(18.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isDarkTheme) GlassBorderDark else GlassBorderLight
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "PHONE NUMBER",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            letterSpacing = 1.2.sp
                        )

                        // Two-Part Phone Input Container
                        Surface(
                            color = if (isDarkTheme) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            shape = RoundedCornerShape(14.dp),
                            border = androidx.compose.foundation.BorderStroke(
                                1.5.dp,
                                when {
                                    !uiState.isValidPhoneNumber && uiState.nationalPhoneNumber.isNotBlank() -> StatusError
                                    isPhoneFocused -> if (isDarkTheme) SoftPaper else RoastedCoffee
                                    else -> if (isDarkTheme) GlassBorderDark else GlassBorderLight
                                }
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp)
                                    .padding(horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Left: Country Selector Button [ 🇮🇳 +91 ▾ ]
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable(enabled = !uiState.loopState.isLoopActive) {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            showCountryPicker = true
                                        }
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = uiState.selectedCountry.flagEmoji,
                                        fontSize = 18.sp
                                    )
                                    Text(
                                        text = uiState.selectedCountry.dialCode,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Icon(
                                        imageVector = Icons.Default.ArrowDropDown,
                                        contentDescription = "Select Country",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                // Subtle Vertical Divider
                                Box(
                                    modifier = Modifier
                                        .width(1.dp)
                                        .height(26.dp)
                                        .background(
                                            if (isDarkTheme) GlassBorderDark else GlassBorderLight
                                        )
                                )

                                // Right: Local Phone Number Input Field
                                BasicTextField(
                                    value = uiState.nationalPhoneNumber,
                                    onValueChange = { input ->
                                        viewModel.onPhoneNumberChanged(input)
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(horizontal = 12.dp)
                                        .onFocusChanged { focusState ->
                                            isPhoneFocused = focusState.isFocused
                                        },
                                    enabled = !uiState.loopState.isLoopActive,
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Phone,
                                        imeAction = ImeAction.Done
                                    ),
                                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 16.sp
                                    ),
                                    cursorBrush = SolidColor(if (isDarkTheme) SoftPaper else RoastedCoffee),
                                    decorationBox = { innerTextField ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(modifier = Modifier.weight(1f)) {
                                                if (uiState.nationalPhoneNumber.isEmpty()) {
                                                    Text(
                                                        text = "Phone number",
                                                        style = MaterialTheme.typography.bodyLarge,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                                        fontSize = 16.sp
                                                    )
                                                }
                                                innerTextField()
                                            }
                                        }
                                    }
                                )

                                // Trailing Contact Picker Button
                                IconButton(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        val pickIntent = Intent(
                                            Intent.ACTION_PICK,
                                            ContactsContract.CommonDataKinds.Phone.CONTENT_URI
                                        )
                                        contactPickerLauncher.launch(pickIntent)
                                    },
                                    enabled = !uiState.loopState.isLoopActive,
                                    modifier = Modifier.padding(end = 4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContactPhone,
                                        contentDescription = "Select Contact",
                                        tint = if (isDarkTheme) SoftPaper else RoastedCoffee
                                    )
                                }
                            }
                        }

                        // Inline Validation Message
                        if (!uiState.isValidPhoneNumber && uiState.nationalPhoneNumber.isNotBlank()) {
                            val errorMsg = if (uiState.selectedCountry.code == "IN") {
                                "Enter a valid 10-digit phone number"
                            } else {
                                "Enter a valid ${uiState.selectedCountry.minDigits}–${uiState.selectedCountry.maxDigits} digit phone number"
                            }
                            Text(
                                text = errorMsg,
                                color = StatusError,
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                // Controls Card (Max Attempts, Delay, IVR Filter)
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(18.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isDarkTheme) GlassBorderDark else GlassBorderLight
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        // Maximum Attempts Stepper
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "MAXIMUM ATTEMPTS (1–20)",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                letterSpacing = 1.2.sp
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "${uiState.maxAttemptsInput} attempts",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    RepeatingStepperButton(
                                        onClick = {
                                            if (uiState.maxAttemptsInput > 1) {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                viewModel.onMaxAttemptsChanged(uiState.maxAttemptsInput - 1)
                                            }
                                        },
                                        enabled = !uiState.loopState.isLoopActive && uiState.maxAttemptsInput > 1,
                                        border = androidx.compose.foundation.BorderStroke(
                                            1.dp,
                                            if (isDarkTheme) GlassBorderDark else GlassBorderLight
                                        )
                                    ) {
                                        Icon(
                                            Icons.Default.Remove,
                                            contentDescription = "Decrease",
                                            tint = if (!uiState.loopState.isLoopActive && uiState.maxAttemptsInput > 1) {
                                                MaterialTheme.colorScheme.onSurface
                                            } else {
                                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                            }
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(16.dp))

                                    Text(
                                        text = "${uiState.maxAttemptsInput}",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isDarkTheme) SoftPaper else RoastedCoffee
                                    )

                                    Spacer(modifier = Modifier.width(16.dp))

                                    RepeatingStepperButton(
                                        onClick = {
                                            if (uiState.maxAttemptsInput < 20) {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                viewModel.onMaxAttemptsChanged(uiState.maxAttemptsInput + 1)
                                            }
                                        },
                                        enabled = !uiState.loopState.isLoopActive && uiState.maxAttemptsInput < 20,
                                        border = androidx.compose.foundation.BorderStroke(
                                            1.dp,
                                            if (isDarkTheme) GlassBorderDark else GlassBorderLight
                                        )
                                    ) {
                                        Icon(
                                            Icons.Default.Add,
                                            contentDescription = "Increase",
                                            tint = if (!uiState.loopState.isLoopActive && uiState.maxAttemptsInput < 20) {
                                                MaterialTheme.colorScheme.onSurface
                                            } else {
                                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        // Delay Dropdown Selector
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "DELAY BETWEEN CALLS",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                letterSpacing = 1.2.sp
                            )

                            var delayDropdownExpanded by remember { mutableStateOf(false) }
                            val currentSelectedDelayLabel = delayOptions.find { it.seconds == uiState.delaySecondsInput }?.label
                                ?: "${uiState.delaySecondsInput} seconds"

                            ExposedDropdownMenuBox(
                                expanded = delayDropdownExpanded,
                                onExpandedChange = {
                                    if (!uiState.loopState.isLoopActive) delayDropdownExpanded = !delayDropdownExpanded
                                }
                            ) {
                                OutlinedTextField(
                                    value = currentSelectedDelayLabel,
                                    onValueChange = {},
                                    readOnly = true,
                                    enabled = !uiState.loopState.isLoopActive,
                                    shape = RoundedCornerShape(14.dp),
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = delayDropdownExpanded) },
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = if (isDarkTheme) SoftPaper else RoastedCoffee,
                                        unfocusedBorderColor = if (isDarkTheme) GlassBorderDark else GlassBorderLight,
                                        disabledBorderColor = GlassBorderDark.copy(alpha = 0.5f)
                                    ),
                                    modifier = Modifier
                                        .menuAnchor()
                                        .fillMaxWidth()
                                )

                                ExposedDropdownMenu(
                                    expanded = delayDropdownExpanded,
                                    onDismissRequest = { delayDropdownExpanded = false }
                                ) {
                                    delayOptions.forEach { option ->
                                        DropdownMenuItem(
                                            text = { Text(option.label) },
                                            onClick = {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                viewModel.onDelaySecondsChanged(option.seconds)
                                                delayDropdownExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        // Carrier IVR Announcement Filter
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "CARRIER IVR / BUSY FILTER",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                letterSpacing = 1.2.sp
                            )

                            var ivrDropdownExpanded by remember { mutableStateOf(false) }
                            val currentSelectedIvrLabel = ivrThresholdOptions.find { it.seconds == uiState.minAnswerDurationInput }?.label
                                ?: "${uiState.minAnswerDurationInput} seconds"

                            ExposedDropdownMenuBox(
                                expanded = ivrDropdownExpanded,
                                onExpandedChange = {
                                    if (!uiState.loopState.isLoopActive) ivrDropdownExpanded = !ivrDropdownExpanded
                                }
                            ) {
                                OutlinedTextField(
                                    value = currentSelectedIvrLabel,
                                    onValueChange = {},
                                    readOnly = true,
                                    enabled = !uiState.loopState.isLoopActive,
                                    shape = RoundedCornerShape(14.dp),
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = ivrDropdownExpanded) },
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = if (isDarkTheme) SoftPaper else RoastedCoffee,
                                        unfocusedBorderColor = if (isDarkTheme) GlassBorderDark else GlassBorderLight,
                                        disabledBorderColor = GlassBorderDark.copy(alpha = 0.5f)
                                    ),
                                    modifier = Modifier
                                        .menuAnchor()
                                        .fillMaxWidth()
                                )

                                ExposedDropdownMenu(
                                    expanded = ivrDropdownExpanded,
                                    onDismissRequest = { ivrDropdownExpanded = false }
                                ) {
                                    ivrThresholdOptions.forEach { option ->
                                        DropdownMenuItem(
                                            text = { Text(option.label) },
                                            onClick = {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                viewModel.onMinAnswerDurationChanged(option.seconds)
                                                ivrDropdownExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        // SIM Selection Preference
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "SIM SELECTION",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                letterSpacing = 1.2.sp
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                val simOptions = listOf(
                                    0 to "Default",
                                    1 to "SIM 1",
                                    2 to "SIM 2",
                                    3 to "Alternate"
                                )
                                simOptions.forEach { (mode, label) ->
                                    val isSelected = uiState.simPreferenceInput == mode
                                    Surface(
                                        color = if (isSelected) (if (isDarkTheme) SoftPaper else RoastedCoffee) else MaterialTheme.colorScheme.background,
                                        shape = RoundedCornerShape(10.dp),
                                        border = androidx.compose.foundation.BorderStroke(
                                            1.dp,
                                            if (isSelected) (if (isDarkTheme) SoftPaper else RoastedCoffee) else (if (isDarkTheme) GlassBorderDark else GlassBorderLight)
                                        ),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(38.dp)
                                            .clickable(enabled = !uiState.loopState.isLoopActive) {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                viewModel.onSimPreferenceChanged(mode)
                                            }
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = label,
                                                fontSize = 11.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                color = if (isSelected) (if (isDarkTheme) RoastedCoffee else SoftPaper) else MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // TAB 0: IMMEDIATE MANUAL MODE
                if (uiState.selectedTab == 0) {
                    if (!uiState.loopState.isLoopActive) {
                        Button(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                val hasPermissions = requiredPermissions.all { perm ->
                                    ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
                                }
                                if (hasPermissions) {
                                    viewModel.startLoop(context)
                                } else {
                                    permissionLauncher.launch(requiredPermissions)
                                }
                            },
                            enabled = uiState.isValidPhoneNumber && uiState.phoneNumberInput.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isDarkTheme) SoftPaper else RoastedCoffee,
                                disabledContainerColor = (if (isDarkTheme) SoftPaper else RoastedCoffee).copy(alpha = 0.4f)
                            ),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                        ) {
                            Text(
                                text = "START CALL LOOP",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (isDarkTheme) RoastedCoffee else SoftPaper,
                                letterSpacing = 1.sp
                            )
                        }
                    } else {
                        Button(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.stopLoop(context)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = StatusErrorContainer),
                            border = androidx.compose.foundation.BorderStroke(1.dp, StatusError.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                        ) {
                            Text(
                                text = "STOP CALL LOOP",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = StatusError,
                                letterSpacing = 1.sp
                            )
                        }
                    }

                    // Live Call Status Card
                    StatusCard(state = uiState.loopState, isDarkTheme = isDarkTheme)
                }

                // TAB 1: SCHEDULED CALLING MODE
                if (uiState.selectedTab == 1) {
                    // Date & Time Picker Card
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(18.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isDarkTheme) GlassBorderDark else GlassBorderLight
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                text = "SCHEDULE DATE & TIME",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                letterSpacing = 1.2.sp
                            )

                            val dateFormatted = remember(selectedYear, selectedMonth, selectedDay) {
                                val cal = Calendar.getInstance().apply {
                                    set(selectedYear, selectedMonth, selectedDay)
                                }
                                val today = Calendar.getInstance()
                                if (cal.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
                                    cal.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR)) {
                                    "Today"
                                } else {
                                    SimpleDateFormat("MMM dd, yyyy", Locale.US).format(cal.time)
                                }
                            }

                            val timeFormatted = remember(selectedHour, selectedMinute) {
                                val cal = Calendar.getInstance().apply {
                                    set(Calendar.HOUR_OF_DAY, selectedHour)
                                    set(Calendar.MINUTE, selectedMinute)
                                }
                                SimpleDateFormat("h:mm a", Locale.US).format(cal.time)
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // Date Picker Trigger
                                Surface(
                                    color = MaterialTheme.colorScheme.background,
                                    shape = RoundedCornerShape(12.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, if (isDarkTheme) GlassBorderDark else GlassBorderLight),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable {
                                            DatePickerDialog(
                                                context,
                                                { _, year, month, dayOfMonth ->
                                                    selectedYear = year
                                                    selectedMonth = month
                                                    selectedDay = dayOfMonth
                                                },
                                                selectedYear,
                                                selectedMonth,
                                                selectedDay
                                            ).apply {
                                                datePicker.minDate = System.currentTimeMillis() - 1000
                                            }.show()
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.CalendarMonth, contentDescription = "Date", modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(text = dateFormatted, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                    }
                                }

                                // Time Picker Trigger
                                Surface(
                                    color = MaterialTheme.colorScheme.background,
                                    shape = RoundedCornerShape(12.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, if (isDarkTheme) GlassBorderDark else GlassBorderLight),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable {
                                            TimePickerDialog(
                                                context,
                                                { _, hourOfDay, minute ->
                                                    selectedHour = hourOfDay
                                                    selectedMinute = minute
                                                },
                                                selectedHour,
                                                selectedMinute,
                                                false
                                            ).show()
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.AccessTime, contentDescription = "Time", modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(text = timeFormatted, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                    }
                                }
                            }

                            uiState.scheduleErrorMessage?.let { errMsg ->
                                Text(
                                    text = errMsg,
                                    color = StatusError,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }

                    var lastScheduleClickTime by remember { mutableLongStateOf(0L) }

                    // Primary Schedule Button
                    Button(
                        onClick = {
                            val now = android.os.SystemClock.elapsedRealtime()
                            if (now - lastScheduleClickTime < 1000L) {
                                return@Button
                            }
                            lastScheduleClickTime = now
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            val hasPermissions = requiredPermissions.all { perm ->
                                ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
                            }
                            val canScheduleExact = com.aakash.callloop.schedule.ScheduleManager.canScheduleExactAlarms(context)

                            if (!canScheduleExact && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                val exactIntent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                }
                                context.startActivity(exactIntent)
                            } else if (hasPermissions) {
                                viewModel.scheduleCall(
                                    context = context,
                                    year = selectedYear,
                                    month = selectedMonth,
                                    dayOfMonth = selectedDay,
                                    hourOfDay = selectedHour,
                                    minute = selectedMinute
                                )
                            } else {
                                permissionLauncher.launch(requiredPermissions)
                            }
                        },
                        enabled = uiState.isValidPhoneNumber && uiState.phoneNumberInput.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isDarkTheme) SoftPaper else RoastedCoffee,
                            disabledContainerColor = (if (isDarkTheme) SoftPaper else RoastedCoffee).copy(alpha = 0.4f)
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                    ) {
                        Text(
                            text = "SCHEDULE CALL",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isDarkTheme) RoastedCoffee else SoftPaper,
                            letterSpacing = 1.sp
                        )
                    }

                    // Scheduled Calls List
                    if (uiState.scheduledCalls.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val pendingCount = uiState.scheduledCalls.count { it.isPending || it.isRunning }
                                Text(
                                    text = "SCHEDULED CALLS ($pendingCount)",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    letterSpacing = 1.2.sp
                                )

                                if (uiState.scheduledCalls.any { it.status == ScheduleStatus.COMPLETED || it.status == ScheduleStatus.CANCELLED || it.status == ScheduleStatus.MISSED }) {
                                    Text(
                                        text = "Clear Inactive",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isDarkTheme) SoftPaper else RoastedCoffee,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .clickable {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                viewModel.clearCompletedSchedules()
                                            }
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            uiState.scheduledCalls.forEach { item ->
                                ScheduledCallCard(
                                    scheduledCall = item,
                                    isDarkTheme = isDarkTheme,
                                    onCancelClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        viewModel.cancelSchedule(context, item.id)
                                    }
                                )
                            }
                        }
                    }
                }

                // Safety Disclaimer Note
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Info",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Use only for calls you are authorized to make.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }

    if (showCountryPicker) {
        CountryPickerDialog(
            selectedCountry = uiState.selectedCountry,
            onCountrySelected = { country ->
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                viewModel.onCountrySelected(country)
                showCountryPicker = false
            },
            onDismissRequest = {
                showCountryPicker = false
            },
            isDarkTheme = isDarkTheme
        )
    }
}

@Composable
private fun RepeatingStepperButton(
    onClick: () -> Unit,
    enabled: Boolean,
    border: androidx.compose.foundation.BorderStroke,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val currentOnClick by rememberUpdatedState(onClick)
    val currentEnabled by rememberUpdatedState(enabled)
    val coroutineScope = rememberCoroutineScope()

    Surface(
        shape = RoundedCornerShape(12.dp),
        border = border,
        color = Color.Transparent,
        modifier = modifier
            .size(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .pointerInput(currentEnabled) {
                if (!currentEnabled) return@pointerInput
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    val job = coroutineScope.launch {
                        currentOnClick()
                        kotlinx.coroutines.delay(350L)
                        while (isActive && currentEnabled) {
                            currentOnClick()
                            kotlinx.coroutines.delay(85L)
                        }
                    }
                    waitForUpOrCancellation()
                    job.cancel()
                }
            }
    ) {
        Box(contentAlignment = Alignment.Center) {
            content()
        }
    }
}

@Composable
fun StatusCard(
    state: com.aakash.callloop.domain.CallLoopState,
    isDarkTheme: Boolean
) {
    val statusColor = when {
        state.callAnswered -> if (isDarkTheme) SoftPaper else RoastedCoffee
        state.isLoopActive -> if (isDarkTheme) SoftPaper else RoastedCoffee
        state.status == LoopStatus.MAX_ATTEMPTS_REACHED || state.status == LoopStatus.ERROR -> StatusError
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(18.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isDarkTheme) GlassBorderDark else GlassBorderLight
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(statusColor.copy(alpha = 0.15f))
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(statusColor)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (state.isLoopActive) "CALL LOOP ACTIVE" else state.status.label.uppercase(),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp,
                    color = statusColor,
                    letterSpacing = 1.sp
                )
            }

            if (state.callAnswered) {
                Text(
                    text = "CALL ANSWERED",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isDarkTheme) SoftPaper else RoastedCoffee
                )
                Text(
                    text = "Loop stopped automatically.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else if (state.isLoopActive || state.currentAttempt > 0) {
                if (state.phoneNumber.isNotBlank()) {
                    Text(
                        text = state.phoneNumber,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Text(
                    text = "Attempt ${formatAttempt(state.currentAttempt)} / ${formatAttempt(state.maxAttempts)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (state.status == LoopStatus.WAITING && state.countdownSecondsRemaining > 0) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "NEXT ATTEMPT IN",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 1.2.sp
                    )
                    Text(
                        text = formatTime(state.countdownSecondsRemaining),
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isDarkTheme) SoftPaper else RoastedCoffee,
                        letterSpacing = 1.sp
                    )
                } else if (state.statusDetail.isNotBlank()) {
                    Text(
                        text = state.statusDetail,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                Text(
                    text = "Ready to start call loop",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun ScheduledCallCard(
    scheduledCall: com.aakash.callloop.schedule.ScheduledCall,
    isDarkTheme: Boolean,
    onCancelClick: () -> Unit
) {
    if (scheduledCall.status == ScheduleStatus.NONE) return

    val statusColor = when (scheduledCall.status) {
        ScheduleStatus.PENDING -> if (isDarkTheme) SoftPaper else RoastedCoffee
        ScheduleStatus.RUNNING -> if (isDarkTheme) SoftPaper else RoastedCoffee
        ScheduleStatus.CANCELLED, ScheduleStatus.EXPIRED -> StatusError
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    // Live countdown to scheduled start time
    var remainingMillis by remember { mutableLongStateOf(0L) }
    LaunchedEffect(scheduledCall.scheduledTimestamp, scheduledCall.status) {
        while (scheduledCall.status == ScheduleStatus.PENDING) {
            val diff = scheduledCall.scheduledTimestamp - System.currentTimeMillis()
            remainingMillis = if (diff > 0) diff else 0L
            delay(1000)
        }
    }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(18.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isDarkTheme) GlassBorderDark else GlassBorderLight
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(statusColor.copy(alpha = 0.15f))
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(statusColor)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = when (scheduledCall.status) {
                        ScheduleStatus.PENDING -> "CALL SCHEDULED"
                        ScheduleStatus.RUNNING -> "SCHEDULED CALL ACTIVE"
                        else -> scheduledCall.status.label.uppercase()
                    },
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp,
                    color = statusColor,
                    letterSpacing = 1.sp
                )
            }

            if (scheduledCall.phoneNumber.isNotBlank()) {
                Text(
                    text = scheduledCall.phoneNumber,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            if (scheduledCall.scheduledTimestamp > 0) {
                val formattedTime = SimpleDateFormat("EEE, MMM dd 'at' h:mm a", Locale.US).format(scheduledCall.scheduledTimestamp)
                Text(
                    text = "Starts: $formattedTime",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${scheduledCall.maxAttempts} attempts · Every ${formatSecondsLabel(scheduledCall.delaySeconds)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (scheduledCall.status == ScheduleStatus.PENDING && remainingMillis > 0) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "STARTS IN",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 1.2.sp
                )
                Text(
                    text = formatDurationMs(remainingMillis),
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isDarkTheme) SoftPaper else RoastedCoffee,
                    letterSpacing = 1.sp
                )
            }

            if (scheduledCall.status == ScheduleStatus.PENDING) {
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = onCancelClick,
                    colors = ButtonDefaults.buttonColors(containerColor = StatusErrorContainer),
                    border = androidx.compose.foundation.BorderStroke(1.dp, StatusError.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("CANCEL SCHEDULE", color = StatusError, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }
    }
}

private fun formatSecondsLabel(seconds: Int): String {
    return if (seconds >= 60) "${seconds / 60}m" else "${seconds}s"
}

private fun formatDurationMs(millis: Long): String {
    val totalSecs = millis / 1000
    val hrs = totalSecs / 3600
    val mins = (totalSecs % 3600) / 60
    val secs = totalSecs % 60
    return String.format(Locale.US, "%02d:%02d:%02d", hrs, mins, secs)
}

private fun formatAttempt(attempt: Int): String {
    return String.format(Locale.US, "%02d", attempt)
}

private fun formatTime(seconds: Int): String {
    val mins = seconds / 60
    val secs = seconds % 60
    return String.format(Locale.US, "%02d:%02d", mins, secs)
}

private fun openAppSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", context.packageName, null)
    }
    context.startActivity(intent)
}

private fun extractPhoneNumberFromContact(context: Context, contactUri: Uri): String? {
    return try {
        var phoneNumber: String? = null
        val cursor: Cursor? = context.contentResolver.query(
            contactUri,
            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
            null,
            null,
            null
        )
        cursor?.use { c ->
            if (c.moveToFirst()) {
                val numIndex = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                if (numIndex >= 0) {
                    phoneNumber = c.getString(numIndex)
                }
            }
        }
        phoneNumber
    } catch (e: Exception) {
        null
    }
}

@Composable
private fun CountryPickerDialog(
    selectedCountry: Country,
    onCountrySelected: (Country) -> Unit,
    onDismissRequest: () -> Unit,
    isDarkTheme: Boolean
) {
    var searchQuery by remember { mutableStateOf("") }
    val filteredCountries = remember(searchQuery) {
        if (searchQuery.isBlank()) {
            CountryData.countries
        } else {
            val q = searchQuery.trim().lowercase()
            CountryData.countries.filter {
                it.name.lowercase().contains(q) ||
                it.dialCode.contains(q) ||
                it.code.lowercase().contains(q)
            }
        }
    }

    Dialog(onDismissRequest = onDismissRequest) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.75f),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                if (isDarkTheme) GlassBorderDark else GlassBorderLight
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "SELECT COUNTRY",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        letterSpacing = 1.2.sp
                    )
                    IconButton(onClick = onDismissRequest) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Search Input
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text(
                            "Search country or dial code...",
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            fontSize = 14.sp
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = if (isDarkTheme) SoftPaper else RoastedCoffee,
                        unfocusedBorderColor = if (isDarkTheme) GlassBorderDark else GlassBorderLight,
                        cursorColor = if (isDarkTheme) SoftPaper else RoastedCoffee
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Countries List
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(filteredCountries, key = { it.code }) { country ->
                        val isSelected = country.code == selectedCountry.code
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (isSelected) {
                                        (if (isDarkTheme) SoftPaper else RoastedCoffee).copy(alpha = 0.12f)
                                    } else Color.Transparent
                                )
                                .clickable {
                                    onCountrySelected(country)
                                }
                                .padding(horizontal = 12.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = country.flagEmoji, fontSize = 22.sp)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = country.name,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = country.dialCode,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isSelected) (if (isDarkTheme) SoftPaper else RoastedCoffee)
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

