package com.aakash.callloop.ui

import android.Manifest
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContactPhone
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.aakash.callloop.domain.LoopStatus
import com.aakash.callloop.ui.theme.CallLoopTheme
import com.aakash.callloop.ui.theme.DarkSapphire
import com.aakash.callloop.ui.theme.DeepNavy
import com.aakash.callloop.ui.theme.GlassBorderDark
import com.aakash.callloop.ui.theme.GlassBorderLight
import com.aakash.callloop.ui.theme.IceBlue
import com.aakash.callloop.ui.theme.PowderBlue
import com.aakash.callloop.ui.theme.Sapphire
import com.aakash.callloop.ui.theme.StatusError
import com.aakash.callloop.ui.theme.StatusErrorContainer
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
            viewModel.startLoop(context)
        } else {
            viewModel.setPermissionDenied(
                true,
                "Phone, Call State, Call Log, and Contacts permissions are required."
            )
        }
    }

    // Contact Picker Launcher using Intent.ACTION_PICK for phoneUri
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
                    .padding(horizontal = 24.dp, vertical = 16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {

                // Header with Light/Dark Theme Toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp, bottom = 8.dp),
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

                    // Minimal Theme Toggle
                    IconButton(
                        onClick = {
                            val newTheme = if (isDarkTheme) "LIGHT" else "DARK"
                            viewModel.onThemeModeChanged(newTheme)
                        }
                    ) {
                        Icon(
                            imageVector = if (isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                            contentDescription = "Toggle Theme",
                            tint = Sapphire
                        )
                    }
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

                        OutlinedTextField(
                            value = uiState.phoneNumberInput,
                            onValueChange = { viewModel.onPhoneNumberChanged(it) },
                            placeholder = { Text("+91 XXXXX XXXXX", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)) },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !uiState.loopState.isLoopActive,
                            isError = !uiState.isValidPhoneNumber && uiState.phoneNumberInput.isNotBlank(),
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Sapphire,
                                unfocusedBorderColor = if (isDarkTheme) GlassBorderDark else GlassBorderLight,
                                disabledBorderColor = GlassBorderDark.copy(alpha = 0.5f),
                                errorBorderColor = StatusError,
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent
                            ),
                            trailingIcon = {
                                IconButton(
                                    onClick = {
                                        val pickIntent = Intent(
                                            Intent.ACTION_PICK,
                                            ContactsContract.CommonDataKinds.Phone.CONTENT_URI
                                        )
                                        contactPickerLauncher.launch(pickIntent)
                                    },
                                    enabled = !uiState.loopState.isLoopActive
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContactPhone,
                                        contentDescription = "Select Contact",
                                        tint = Sapphire
                                    )
                                }
                            },
                            singleLine = true
                        )

                        if (!uiState.isValidPhoneNumber && uiState.phoneNumberInput.isNotBlank()) {
                            Text(
                                text = "Please enter a valid phone number.",
                                color = StatusError,
                                fontSize = 12.sp
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
                                    OutlinedButton(
                                        onClick = { viewModel.onMaxAttemptsChanged(uiState.maxAttemptsInput - 1) },
                                        enabled = !uiState.loopState.isLoopActive && uiState.maxAttemptsInput > 1,
                                        modifier = Modifier.size(40.dp),
                                        shape = RoundedCornerShape(12.dp),
                                        border = androidx.compose.foundation.BorderStroke(
                                            1.dp,
                                            if (isDarkTheme) GlassBorderDark else GlassBorderLight
                                        ),
                                        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                                    ) {
                                        Icon(Icons.Default.Remove, contentDescription = "Decrease", tint = MaterialTheme.colorScheme.onSurface)
                                    }

                                    Spacer(modifier = Modifier.width(16.dp))

                                    Text(
                                        text = "${uiState.maxAttemptsInput}",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Sapphire
                                    )

                                    Spacer(modifier = Modifier.width(16.dp))

                                    OutlinedButton(
                                        onClick = { viewModel.onMaxAttemptsChanged(uiState.maxAttemptsInput + 1) },
                                        enabled = !uiState.loopState.isLoopActive && uiState.maxAttemptsInput < 20,
                                        modifier = Modifier.size(40.dp),
                                        shape = RoundedCornerShape(12.dp),
                                        border = androidx.compose.foundation.BorderStroke(
                                            1.dp,
                                            if (isDarkTheme) GlassBorderDark else GlassBorderLight
                                        ),
                                        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = "Increase", tint = MaterialTheme.colorScheme.onSurface)
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
                                        focusedBorderColor = Sapphire,
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
                                        focusedBorderColor = Sapphire,
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
                                                viewModel.onMinAnswerDurationChanged(option.seconds)
                                                ivrDropdownExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // START / STOP Primary Action Button
                if (!uiState.loopState.isLoopActive) {
                    Button(
                        onClick = {
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
                            containerColor = Sapphire,
                            disabledContainerColor = Sapphire.copy(alpha = 0.4f)
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
                            color = Color.White,
                            letterSpacing = 1.sp
                        )
                    }
                } else {
                    Button(
                        onClick = { viewModel.stopLoop(context) },
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

                // Live Call Status Card (Calm Monitoring Interface)
                StatusCard(state = uiState.loopState, isDarkTheme = isDarkTheme)

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
}

@Composable
fun StatusCard(state: com.aakash.callloop.domain.CallLoopState, isDarkTheme: Boolean) {
    val statusColor = when {
        state.callAnswered -> Sapphire
        state.isLoopActive -> Sapphire
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
            // Status Header Pill
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
                    color = Sapphire
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
                        color = Sapphire,
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
