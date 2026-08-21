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
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.ContactPhone
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import com.aakash.callloop.ui.theme.StatusActiveGreen
import com.aakash.callloop.ui.theme.StatusActiveGreenContainer
import com.aakash.callloop.ui.theme.StatusCallingBlue
import com.aakash.callloop.ui.theme.StatusCallingBlueContainer
import com.aakash.callloop.ui.theme.StatusErrorRed
import com.aakash.callloop.ui.theme.StatusErrorRedContainer
import java.util.Locale

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CallLoopTheme {
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
    IvrThresholdOption("12 seconds (Default - Recommended)", 12),
    IvrThresholdOption("15 seconds", 15),
    IvrThresholdOption("20 seconds", 20)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Call Loop",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Automated Call Retry",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // Permission Warning Card if denied
            AnimatedVisibility(visible = uiState.permissionDeniedState) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = StatusErrorRedContainer),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Permission Error",
                            tint = StatusErrorRed,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Permission Required",
                                fontWeight = FontWeight.Bold,
                                color = StatusErrorRed
                            )
                            Text(
                                text = uiState.permissionErrorMessage
                                    ?: "Permissions are required to place calls.",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = { openAppSettings(context) },
                                colors = ButtonDefaults.buttonColors(containerColor = StatusErrorRed)
                            ) {
                                Text("OPEN SETTINGS", color = Color.White)
                            }
                        }
                    }
                }
            }

            // Main Configuration Card
            Card(
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Phone Number",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    // Phone Number Input
                    OutlinedTextField(
                        value = uiState.phoneNumberInput,
                        onValueChange = { viewModel.onPhoneNumberChanged(it) },
                        label = { Text("Input (+91 XXXXX XXXXX)") },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !uiState.loopState.isLoopActive,
                        isError = !uiState.isValidPhoneNumber && uiState.phoneNumberInput.isNotBlank(),
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
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        },
                        singleLine = true
                    )

                    if (!uiState.isValidPhoneNumber && uiState.phoneNumberInput.isNotBlank()) {
                        Text(
                            text = "Please enter a valid phone number.",
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 12.sp
                        )
                    }

                    // Maximum Attempts Stepper
                    Text(
                        text = "Maximum Attempts (1–20)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Maximum attempts: ${uiState.maxAttemptsInput}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedButton(
                                onClick = { viewModel.onMaxAttemptsChanged(uiState.maxAttemptsInput - 1) },
                                enabled = !uiState.loopState.isLoopActive && uiState.maxAttemptsInput > 1,
                                modifier = Modifier.size(40.dp),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                            ) {
                                Icon(Icons.Default.Remove, contentDescription = "Decrease")
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Text(
                                text = "${uiState.maxAttemptsInput}",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )

                            Spacer(modifier = Modifier.width(12.dp))

                            OutlinedButton(
                                onClick = { viewModel.onMaxAttemptsChanged(uiState.maxAttemptsInput + 1) },
                                enabled = !uiState.loopState.isLoopActive && uiState.maxAttemptsInput < 20,
                                modifier = Modifier.size(40.dp),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Increase")
                            }
                        }
                    }

                    // Delay Dropdown Selector
                    Text(
                        text = "Delay Between Calls",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
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
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = delayDropdownExpanded) },
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

                    // IVR Filter / Minimum Talk Time Threshold
                    Text(
                        text = "Ignore Carrier IVR / Busy Announcements",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Calls shorter than this threshold (e.g. busy tones / IVR recordings) are treated as unanswered.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = ivrDropdownExpanded) },
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

            // START / STOP Action Button
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
                    colors = ButtonDefaults.buttonColors(containerColor = StatusActiveGreen),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                ) {
                    Icon(Icons.Default.Call, contentDescription = "Start")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "START CALL LOOP",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            } else {
                Button(
                    onClick = { viewModel.stopLoop(context) },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusErrorRed),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                ) {
                    Icon(Icons.Default.CallEnd, contentDescription = "Stop")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "STOP CALL LOOP",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            // Live Call Status Card
            StatusCard(state = uiState.loopState)

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
                    modifier = Modifier.size(16.dp)
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

@Composable
fun StatusCard(state: com.aakash.callloop.domain.CallLoopState) {
    val containerBg = when {
        state.callAnswered -> StatusActiveGreenContainer
        state.isLoopActive -> StatusCallingBlueContainer
        state.status == LoopStatus.MAX_ATTEMPTS_REACHED -> StatusErrorRedContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }

    val statusColor = when {
        state.callAnswered -> StatusActiveGreen
        state.isLoopActive -> StatusCallingBlue
        state.status == LoopStatus.MAX_ATTEMPTS_REACHED || state.status == LoopStatus.ERROR -> StatusErrorRed
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = containerBg),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Status Header Pill
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(statusColor.copy(alpha = 0.15f))
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(statusColor)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (state.isLoopActive) "CALL LOOP ACTIVE" else state.status.label.uppercase(),
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = statusColor
                )
            }

            if (state.callAnswered) {
                Text(
                    text = "CALL ANSWERED",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = StatusActiveGreen
                )
                Text(
                    text = "Loop stopped automatically.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            } else if (state.isLoopActive || state.currentAttempt > 0) {
                Text(
                    text = "Attempt ${state.currentAttempt} / ${state.maxAttempts}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                if (state.phoneNumber.isNotBlank()) {
                    Text(
                        text = "Calling ${state.phoneNumber}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }

                if (state.status == LoopStatus.WAITING && state.countdownSecondsRemaining > 0) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Next call in",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = formatTime(state.countdownSecondsRemaining),
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = statusColor
                    )
                    Text(
                        text = "Waiting ${state.countdownSecondsRemaining} seconds...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else if (state.statusDetail.isNotBlank()) {
                    Text(
                        text = state.statusDetail,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                Text(
                    text = "Ready to start loop",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
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
