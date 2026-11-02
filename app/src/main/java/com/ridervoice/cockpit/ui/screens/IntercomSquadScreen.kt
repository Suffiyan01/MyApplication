package com.ridervoice.cockpit.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.HeadsetMic
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.VolumeMute
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.ridervoice.cockpit.model.CoRider
import com.ridervoice.cockpit.model.IntercomChannel
import com.ridervoice.cockpit.model.RiderRole
import com.ridervoice.cockpit.model.RiderVoiceUiState
import com.ridervoice.cockpit.ui.theme.NeonAmber
import com.ridervoice.cockpit.ui.theme.NeonCyan
import com.ridervoice.cockpit.ui.theme.NeonGreen
import com.ridervoice.cockpit.ui.theme.NeonRose
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

@Composable
fun IntercomSquadScreen(
    uiState: RiderVoiceUiState,
    onTogglePttLatch: () -> Unit,
    onSetPttHeld: (Boolean) -> Unit,
    onToggleVox: (Boolean) -> Unit,
    onSetVoxThreshold: (Float) -> Unit,
    onSelectChannel: (IntercomChannel) -> Unit,
    onCreateGroup: (String) -> Unit,
    onJoinGroup: (String) -> Unit,
    onDeactivateGroup: () -> Unit,
    onAddCoRider: (String, RiderRole) -> Unit,
    onRemoveCoRider: (String) -> Unit,
    onAssignLead: (String) -> Unit = {},
    onToggleMuteCoRider: (String) -> Unit,
    onToggleSoloWhisperCoRider: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val squadGroup = uiState.squadGroup
    val intercom = uiState.intercom
    val coRiders = uiState.coRiders
    val scrollState = rememberScrollState()

    var showAddRiderDialog by remember { mutableStateOf(false) }
    var showQrDialog by remember { mutableStateOf(false) }
    var showJoinDialog by remember { mutableStateOf(false) }
    var showCameraScannerModal by remember { mutableStateOf(false) }

    var newRiderCallsign by remember { mutableStateOf("") }
    var newRiderRole by remember { mutableStateOf(RiderRole.PACK_RIDER) }

    var groupNameInput by remember { mutableStateOf("") }
    var joinCodeInput by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(12.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Squad Group Header & Authority Card
        if (squadGroup.isActive) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, if (squadGroup.isCurrentUserLeader) NeonCyan else NeonAmber),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = squadGroup.groupName,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (squadGroup.isCurrentUserLeader) NeonCyan.copy(alpha = 0.2f) else NeonAmber.copy(alpha = 0.2f),
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        if (squadGroup.isCurrentUserLeader) NeonCyan else NeonAmber
                                    )
                                ) {
                                    Text(
                                        text = if (squadGroup.isCurrentUserLeader) "LEAD (YOU)" else "MEMBER (YOU)",
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                        color = if (squadGroup.isCurrentUserLeader) NeonCyan else NeonAmber,
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Black
                                    )
                                }
                            }
                            Text(
                                text = "Code: ${squadGroup.joinCode} • ${squadGroup.transportMode.label}",
                                color = NeonCyan,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = NeonGreen.copy(alpha = 0.2f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, NeonGreen)
                        ) {
                            Text(
                                text = "LIVE MESH",
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                color = NeonGreen,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { showQrDialog = true },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = NeonCyan
                            ),
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, NeonCyan)
                        ) {
                            Icon(
                                imageVector = Icons.Default.QrCode,
                                contentDescription = "Squad QR Code",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "SQUAD QR",
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Button(
                            onClick = { showCameraScannerModal = true },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = NeonGreen
                            ),
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, NeonGreen)
                        ) {
                            Icon(
                                imageVector = Icons.Default.QrCodeScanner,
                                contentDescription = "Scan QR Code",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "SCAN QR CODE",
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                if (squadGroup.isCurrentUserLeader) {
                                    showAddRiderDialog = true
                                } else {
                                    onAddCoRider("", RiderRole.PACK_RIDER)
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (squadGroup.isCurrentUserLeader) NeonCyan else MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, if (squadGroup.isCurrentUserLeader) NeonCyan else MaterialTheme.colorScheme.outline)
                        ) {
                            Icon(
                                imageVector = Icons.Default.GroupAdd,
                                contentDescription = "Add Rider",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (squadGroup.isCurrentUserLeader) "+ ADD RIDER" else "+ ADD (LEAD ONLY)",
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Button(
                            onClick = onDeactivateGroup,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (squadGroup.isCurrentUserLeader) NeonRose else MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, if (squadGroup.isCurrentUserLeader) NeonRose else MaterialTheme.colorScheme.outline)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PowerSettingsNew,
                                contentDescription = "Deactivate",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "DEACTIVATE",
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        } else {
            // Inactive Squad Onboarding
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "CREATE NEW SQUAD GROUP (BECOME LEAD)",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 14.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = groupNameInput,
                        onValueChange = { groupNameInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Group Name (e.g. Aravalli Canyon Pack)", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonCyan,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                        ),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = { onCreateGroup(groupNameInput) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = MaterialTheme.colorScheme.background),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("START SQUAD & GENERATE QR CODE", fontSize = 13.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black)
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "JOIN SQUAD BY CAMERA QR SCAN / CODE",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 14.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = { showCameraScannerModal = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = NeonGreen, contentColor = MaterialTheme.colorScheme.background),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(imageVector = Icons.Default.QrCodeScanner, contentDescription = "Scan QR", modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("OPEN CAMERA TO SCAN QR CODE", fontSize = 13.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black)
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = joinCodeInput,
                        onValueChange = { joinCodeInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Or Enter Code (e.g. RV-8492)", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonGreen,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                        ),
                        singleLine = true
                    )

                    if (joinCodeInput.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = { onJoinGroup(joinCodeInput) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = NeonGreen),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("JOIN VIA TYPED CODE", fontSize = 12.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // 2. Giant 180dp Glove PTT Circle Button
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "PUSH TO TALK (PTT)",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(14.dp))

                val isTx = intercom.isTransmitting
                val pttColor = if (isTx) NeonRose else NeonCyan

                val infiniteTransition = rememberInfiniteTransition(label = "pttPulse")
                val pulseAlpha by infiniteTransition.animateFloat(
                    initialValue = 0.3f,
                    targetValue = 0.8f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(800),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "alphaPulse"
                )

                Box(
                    modifier = Modifier
                        .size(180.dp)
                        .clip(CircleShape)
                        .background(pttColor.copy(alpha = if (isTx) pulseAlpha else 0.15f))
                        .border(
                            width = 4.dp,
                            color = pttColor,
                            shape = CircleShape
                        )
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onPress = {
                                    onSetPttHeld(true)
                                    tryAwaitRelease()
                                    onSetPttHeld(false)
                                },
                                onTap = { onTogglePttLatch() }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = if (isTx) Icons.Default.Mic else Icons.Default.HeadsetMic,
                            contentDescription = "PTT",
                            tint = pttColor,
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (isTx) "TRANSMITTING" else "HOLD OR LATCH",
                            color = pttColor,
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Black
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "ACTIVE MESH CHANNEL",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    IntercomChannel.entries.forEach { ch ->
                        val isSelected = (squadGroup.activeChannel == ch)
                        FilterChip(
                            selected = isSelected,
                            onClick = { onSelectChannel(ch) },
                            label = {
                                Text(
                                    text = ch.freqLabel.take(5),
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = NeonCyan,
                                selectedLabelColor = MaterialTheme.colorScheme.background,
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "VOX Hands-Free Mic",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Triggers voice transmit at ${intercom.voxThresholdDb.toInt()} dB",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp
                        )
                    }

                    Switch(
                        checked = intercom.isVoxEnabled,
                        onCheckedChange = { onToggleVox(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.background,
                            checkedTrackColor = NeonCyan
                        )
                    )
                }

                if (intercom.isVoxEnabled) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Slider(
                        value = intercom.voxThresholdDb,
                        onValueChange = { onSetVoxThreshold(it) },
                        valueRange = -50f..-10f,
                        colors = SliderDefaults.colors(
                            thumbColor = NeonCyan,
                            activeTrackColor = NeonCyan,
                            inactiveTrackColor = MaterialTheme.colorScheme.outline
                        )
                    )
                }
            }
        }

        // 3. Squad Roster Title
        Text(
            text = "SQUAD MESH ROSTER (${coRiders.size + 1} RIDERS)",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold
        )

        // My Roster Item
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = androidx.compose.foundation.BorderStroke(1.dp, if (squadGroup.isCurrentUserLeader) NeonCyan else NeonAmber),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (squadGroup.isCurrentUserLeader) NeonCyan else NeonAmber
                    ) {
                        Text(
                            text = if (squadGroup.isCurrentUserLeader) "LEAD" else "MEMBER",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            color = MaterialTheme.colorScheme.background,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Black
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = if (squadGroup.isCurrentUserLeader) "You (Road Captain)" else "You (Pack Member)",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "🎧 Local Helmet • 🔋 84%",
                            color = NeonGreen,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                Text(
                    text = "${uiState.telemetry.speedKmh} km/h",
                    color = NeonCyan,
                    fontSize = 16.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Black
                )
            }
        }

        // Co-Riders Cards
        coRiders.forEach { rider ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (rider.isSpeaking) NeonGreen else MaterialTheme.colorScheme.outline
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(rider.role.colorHex)
                            ) {
                                Text(
                                    text = rider.role.badge,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    color = MaterialTheme.colorScheme.background,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Black
                                )
                            }

                            Spacer(modifier = Modifier.width(6.dp))

                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (rider.isInternetRelay) NeonAmber.copy(alpha = 0.2f) else NeonGreen.copy(alpha = 0.2f),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (rider.isInternetRelay) NeonAmber else NeonGreen
                                )
                            ) {
                                Text(
                                    text = rider.connectionLabel,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    color = if (rider.isInternetRelay) NeonAmber else NeonGreen,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            Column {
                                Text(
                                    text = rider.callsign,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )

                                val batColor = if (rider.helmetBatteryPct < 20) NeonRose else MaterialTheme.colorScheme.onSurfaceVariant
                                Text(
                                    text = "🎧 ${rider.helmetDeviceName}: ${rider.helmetBatteryPct}%",
                                    color = batColor,
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "${rider.speedKmh} km/h",
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 15.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${if (rider.isAhead) "+" else "-"}${rider.distanceMeters}m",
                                color = if (rider.isAhead) NeonCyan else NeonAmber,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (rider.isSpeaking) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = NeonGreen.copy(alpha = 0.2f)
                            ) {
                                Text(
                                    text = "🎙️ SPEAKING...",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    color = NeonGreen,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        } else {
                            Spacer(modifier = Modifier.width(1.dp))
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            IconButton(onClick = { onToggleMuteCoRider(rider.callsign) }) {
                                Icon(
                                    imageVector = if (rider.isMutedByMe) Icons.Default.VolumeMute else Icons.Default.VolumeUp,
                                    contentDescription = "Mute",
                                    tint = if (rider.isMutedByMe) NeonRose else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            IconButton(onClick = { onToggleSoloWhisperCoRider(rider.callsign) }) {
                                Icon(
                                    imageVector = Icons.Default.HeadsetMic,
                                    contentDescription = "Solo Whisper",
                                    tint = if (rider.isSoloWhisper) NeonCyan else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            if (squadGroup.isCurrentUserLeader) {
                                TextButton(
                                    onClick = { onAssignLead(rider.callsign) },
                                    colors = ButtonDefaults.textButtonColors(contentColor = NeonCyan)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Star,
                                        contentDescription = "Make Lead",
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text("MAKE LEAD", fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                                }

                                IconButton(onClick = { onRemoveCoRider(rider.callsign) }) {
                                    Icon(
                                        imageVector = Icons.Default.PersonRemove,
                                        contentDescription = "Remove",
                                        tint = NeonRose
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }

    // 1. Live CameraX + ML Kit Barcode Scanner Fullscreen Modal
    if (showCameraScannerModal) {
        QrCodeCameraScannerModal(
            onQrCodeScanned = { code ->
                showCameraScannerModal = false
                showJoinDialog = false
                onJoinGroup(code)
            },
            onDismiss = { showCameraScannerModal = false }
        )
    }

    // 2. Squad Standard ISO QR Code Display & Invite Dialog
    if (showQrDialog) {
        val qrPayload = if (!squadGroup.hotspotSsid.isNullOrBlank()) {
            "WIFI:S:${squadGroup.hotspotSsid};T:WPA;P:${squadGroup.hotspotPassword};;JOIN:${squadGroup.joinCode}"
        } else {
            squadGroup.joinCode
        }

        AlertDialog(
            onDismissRequest = { showQrDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = {
                Text(
                    text = "SQUAD LOCAL HOTSPOT & QR CODE",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 16.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Scan with Camera to auto-connect to Local Wi-Fi Hotspot & join '${squadGroup.groupName}'",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )

                    SquadQrCodeView(joinCode = qrPayload)

                    if (!squadGroup.hotspotSsid.isNullOrBlank()) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            border = androidx.compose.foundation.BorderStroke(1.dp, NeonGreen)
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "📶 HOTSPOT: ${squadGroup.hotspotSsid}",
                                    color = NeonGreen,
                                    fontSize = 13.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "PASS: ${squadGroup.hotspotPassword} • CODE: ${squadGroup.joinCode}",
                                    color = NeonCyan,
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    } else {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            border = androidx.compose.foundation.BorderStroke(1.dp, NeonCyan)
                        ) {
                            Text(
                                text = "CODE: ${squadGroup.joinCode}",
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                color = NeonCyan,
                                fontSize = 18.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }

                    Text(
                        text = if (squadGroup.isCurrentUserLeader) "You are Squad LEAD. Hotspot & Mesh Active." else "Squad Member View.",
                        color = if (squadGroup.isCurrentUserLeader) NeonGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { showQrDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = MaterialTheme.colorScheme.background)
                ) {
                    Text("CLOSE", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    // 3. Add Rider Dialog
    if (showAddRiderDialog) {
        AlertDialog(
            onDismissRequest = { showAddRiderDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = {
                Text(
                    text = "ADD CO-RIDER (LEAD CONTROL)",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 16.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = newRiderCallsign,
                        onValueChange = { newRiderCallsign = it },
                        label = { Text("Co-Rider Callsign (e.g. Kabir)", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonCyan,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                        ),
                        singleLine = true
                    )

                    Text(
                        text = "Pack Role:",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        RiderRole.entries.forEach { role ->
                            FilterChip(
                                selected = (newRiderRole == role),
                                onClick = { newRiderRole = role },
                                label = {
                                    Text(
                                        text = role.badge,
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = NeonCyan,
                                    selectedLabelColor = MaterialTheme.colorScheme.background
                                )
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newRiderCallsign.isNotBlank()) {
                            onAddCoRider(newRiderCallsign, newRiderRole)
                            newRiderCallsign = ""
                            showAddRiderDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = MaterialTheme.colorScheme.background)
                ) {
                    Text("ADD TO SQUAD", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddRiderDialog = false }) {
                    Text("CANCEL", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }
}

@OptIn(ExperimentalGetImage::class)
@Composable
fun QrCodeCameraScannerModal(
    onQrCodeScanned: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    val isCodeScanned = remember { AtomicBoolean(false) }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    val options = remember {
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE, Barcode.FORMAT_ALL_FORMATS)
            .build()
    }
    val scanner = remember { BarcodeScanning.getClient(options) }

    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
            try {
                scanner.close()
            } catch (e: Exception) {
                // Defensive cleanup
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (hasCameraPermission) {
                AndroidView(
                    factory = { ctx ->
                        val previewView = PreviewView(ctx).apply {
                            scaleType = PreviewView.ScaleType.FILL_CENTER
                        }

                        val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                        cameraProviderFuture.addListener({
                            val cameraProvider = cameraProviderFuture.get()
                            val preview = Preview.Builder()
                                .build().also {
                                    it.setSurfaceProvider(previewView.surfaceProvider)
                                }

                            val imageAnalysis = ImageAnalysis.Builder()
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .build()

                            imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                                val mediaImage = imageProxy.image
                                if (mediaImage != null && !isCodeScanned.get()) {
                                    try {
                                        val inputImage = InputImage.fromMediaImage(
                                            mediaImage,
                                            imageProxy.imageInfo.rotationDegrees
                                        )
                                        scanner.process(inputImage)
                                            .addOnSuccessListener { barcodes ->
                                                if (isCodeScanned.get()) return@addOnSuccessListener
                                                for (barcode in barcodes) {
                                                    val rawValue = barcode.rawValue ?: barcode.displayValue
                                                    if (!rawValue.isNullOrBlank()) {
                                                        val cleanCode = parseSquadCodeFromQr(rawValue)
                                                        if (cleanCode != null && isCodeScanned.compareAndSet(false, true)) {
                                                            ContextCompat.getMainExecutor(ctx).execute {
                                                                onQrCodeScanned(cleanCode)
                                                            }
                                                            break
                                                        }
                                                    }
                                                }
                                            }
                                            .addOnFailureListener {
                                                // Ignore frame analysis errors
                                            }
                                            .addOnCompleteListener {
                                                imageProxy.close()
                                            }
                                    } catch (e: Exception) {
                                        imageProxy.close()
                                    }
                                } else {
                                    imageProxy.close()
                                }
                            }

                            try {
                                cameraProvider.unbindAll()
                                val camera = cameraProvider.bindToLifecycle(
                                    lifecycleOwner,
                                    CameraSelector.DEFAULT_BACK_CAMERA,
                                    preview,
                                    imageAnalysis
                                )
                                val factory = previewView.meteringPointFactory
                                val point = factory.createPoint(previewView.width / 2f, previewView.height / 2f)
                                val action = androidx.camera.core.FocusMeteringAction.Builder(point, androidx.camera.core.FocusMeteringAction.FLAG_AF)
                                    .setAutoCancelDuration(3, java.util.concurrent.TimeUnit.SECONDS)
                                    .build()
                                camera.cameraControl.startFocusAndMetering(action)
                            } catch (e: Exception) {
                                // Defensive
                            }
                        }, ContextCompat.getMainExecutor(ctx))

                        previewView
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Viewfinder Overlay Frame with Scanning Line
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "POINT CAMERA AT SQUAD QR CODE",
                            color = NeonCyan,
                            fontSize = 14.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.background.copy(alpha = 0.85f), shape = RoundedCornerShape(8.dp))
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        Box(
                            modifier = Modifier
                                .size(240.dp)
                                .border(3.dp, NeonGreen, shape = RoundedCornerShape(20.dp))
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        Text(
                            text = "Auto-scanning QR Code to join main intercom channel...",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.background.copy(alpha = 0.85f), shape = RoundedCornerShape(8.dp))
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "CAMERA PERMISSION REQUIRED",
                        color = NeonRose,
                        fontSize = 16.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Camera access is required to scan the Squad QR code and join the intercom.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                        colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = MaterialTheme.colorScheme.background)
                    ) {
                        Text("GRANT CAMERA PERMISSION", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                    }
                }
            }

            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(24.dp)
                    .background(MaterialTheme.colorScheme.background.copy(alpha = 0.85f), shape = CircleShape)
            ) {
                Icon(imageVector = Icons.Default.Close, contentDescription = "Close Scanner", tint = MaterialTheme.colorScheme.onBackground)
            }
        }
    }
}

fun parseSquadCodeFromQr(raw: String): String? {
    val trimmed = raw.trim()
    return when {
        trimmed.contains("RV-", ignoreCase = true) -> {
            val idx = trimmed.indexOf("RV-", ignoreCase = true)
            trimmed.substring(idx).take(7).uppercase()
        }
        trimmed.isNotBlank() && trimmed.length in 3..12 -> trimmed.uppercase()
        else -> null
    }
}

@Composable
fun SquadQrCodeView(
    joinCode: String,
    modifier: Modifier = Modifier
) {
    val matrix = remember(joinCode) {
        QrCodeGenerator.generateMatrix(joinCode)
    }

    Box(
        modifier = modifier
            .background(Color.White, shape = RoundedCornerShape(16.dp))
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(220.dp)) {
            val quietZone = 2
            val qrSize = matrix.size
            val totalModules = qrSize + quietZone * 2
            val cellSize = size.width / totalModules

            drawRect(color = Color.White)

            for (r in 0 until qrSize) {
                for (c in 0 until qrSize) {
                    if (matrix[r][c]) {
                        drawRect(
                            color = Color.Black,
                            topLeft = androidx.compose.ui.geometry.Offset((c + quietZone) * cellSize, (r + quietZone) * cellSize),
                            size = androidx.compose.ui.geometry.Size(cellSize, cellSize)
                        )
                    }
                }
            }
        }
    }
}

object QrCodeGenerator {
    private val EXP_TABLE = IntArray(256)
    private val LOG_TABLE = IntArray(256)

    init {
        var x = 1
        for (i in 0 until 255) {
            EXP_TABLE[i] = x
            LOG_TABLE[x] = i
            x = x shl 1
            if (x >= 256) {
                x = x xor 285
            }
        }
        EXP_TABLE[255] = EXP_TABLE[0]
    }

    private fun gfMultiply(x: Int, y: Int): Int {
        if (x == 0 || y == 0) return 0
        return EXP_TABLE[(LOG_TABLE[x] + LOG_TABLE[y]) % 255]
    }

    private fun polyMultiply(p1: IntArray, p2: IntArray): IntArray {
        val result = IntArray(p1.size + p2.size - 1)
        for (i in p1.indices) {
            for (j in p2.indices) {
                result[i + j] = result[i + j] xor gfMultiply(p1[i], p2[j])
            }
        }
        return result
    }

    private fun getGeneratorPoly(degree: Int): IntArray {
        var g = intArrayOf(1)
        for (i in 0 until degree) {
            g = polyMultiply(g, intArrayOf(1, EXP_TABLE[i]))
        }
        return g
    }

    fun generateMatrix(text: String): Array<BooleanArray> {
        val size = 21 // Version 1 QR Code
        val matrix = Array(size) { BooleanArray(size) }
        val reserved = Array(size) { BooleanArray(size) }

        fun setModule(r: Int, c: Int, valBit: Boolean) {
            matrix[r][c] = valBit
            reserved[r][c] = true
        }

        fun drawFinder(r: Int, c: Int) {
            for (dr in -1..7) {
                for (dc in -1..7) {
                    val nr = r + dr
                    val nc = c + dc
                    if (nr in 0 until size && nc in 0 until size) {
                        val isBlack = (dr in 0..6 && (dc == 0 || dc == 6)) ||
                                (dc in 0..6 && (dr == 0 || dr == 6)) ||
                                (dr in 2..4 && dc in 2..4)
                        setModule(nr, nc, isBlack)
                    }
                }
            }
        }

        drawFinder(0, 0)
        drawFinder(0, 14)
        drawFinder(14, 0)

        for (i in 8 until 13) {
            setModule(6, i, i % 2 == 0)
            setModule(i, 6, i % 2 == 0)
        }

        setModule(13, 8, true)

        for (i in 0..8) {
            reserved[8][i] = true
            reserved[i][8] = true
            reserved[8][size - 1 - i] = true
            reserved[size - 1 - i][8] = true
        }

        val alphaMap = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ $%*+-./:"
        val upperText = text.uppercase().trim()

        val bits = mutableListOf<Boolean>()
        bits.addAll(listOf(false, false, true, false))

        val count = upperText.length
        for (b in 8 downTo 0) {
            bits.add(((count shr b) and 1) == 1)
        }

        var idx = 0
        while (idx < upperText.length) {
            if (idx + 1 < upperText.length) {
                val val1 = alphaMap.indexOf(upperText[idx]).coerceAtLeast(0)
                val val2 = alphaMap.indexOf(upperText[idx + 1]).coerceAtLeast(0)
                val num = val1 * 45 + val2
                for (b in 10 downTo 0) {
                    bits.add(((num shr b) and 1) == 1)
                }
                idx += 2
            } else {
                val val1 = alphaMap.indexOf(upperText[idx]).coerceAtLeast(0)
                for (b in 5 downTo 0) {
                    bits.add(((val1 shr b) and 1) == 1)
                }
                idx += 1
            }
        }

        repeat(kotlin.math.min(4, 152 - bits.size)) { bits.add(false) }
        while (bits.size % 8 != 0) bits.add(false)

        val padBytes = listOf(0xEC, 0x11)
        var padIdx = 0
        while (bits.size < 152) {
            val p = padBytes[padIdx % 2]
            for (b in 7 downTo 0) {
                bits.add(((p shr b) and 1) == 1)
            }
            padIdx++
        }

        val dataBytes = IntArray(19)
        for (i in 0 until 19) {
            var bVal = 0
            for (b in 0 until 8) {
                if (bits[i * 8 + b]) bVal = bVal or (1 shl (7 - b))
            }
            dataBytes[i] = bVal
        }

        val genPoly = getGeneratorPoly(7)
        val msgPoly = IntArray(26)
        System.arraycopy(dataBytes, 0, msgPoly, 0, 19)

        for (i in 0 until 19) {
            val coef = msgPoly[i]
            if (coef != 0) {
                for (j in genPoly.indices) {
                    msgPoly[i + j] = msgPoly[i + j] xor gfMultiply(genPoly[j], coef)
                }
            }
        }

        val ecBytes = IntArray(7)
        System.arraycopy(msgPoly, 19, ecBytes, 0, 7)

        val totalBits = mutableListOf<Boolean>()
        for (b in dataBytes) {
            for (i in 7 downTo 0) totalBits.add(((b shr i) and 1) == 1)
        }
        for (b in ecBytes) {
            for (i in 7 downTo 0) totalBits.add(((b shr i) and 1) == 1)
        }

        val formatBits = booleanArrayOf(true, true, true, false, true, true, true, true, true, false, false, false, true, false, false)

        val fPos1 = arrayOf(
            Pair(8, 0), Pair(8, 1), Pair(8, 2), Pair(8, 3), Pair(8, 4), Pair(8, 5),
            Pair(8, 7), Pair(8, 8), Pair(7, 8), Pair(5, 8), Pair(4, 8), Pair(3, 8),
            Pair(2, 8), Pair(1, 8), Pair(0, 8)
        )
        val fPos2 = arrayOf(
            Pair(20, 8), Pair(19, 8), Pair(18, 8), Pair(17, 8), Pair(16, 8), Pair(15, 8), Pair(14, 8),
            Pair(8, 13), Pair(8, 14), Pair(8, 15), Pair(8, 16), Pair(8, 17), Pair(8, 18), Pair(8, 19), Pair(8, 20)
        )

        for (i in 0 until 15) {
            val b = formatBits[i]
            matrix[fPos1[i].first][fPos1[i].second] = b
            matrix[fPos2[i].first][fPos2[i].second] = b
        }

        var bitIndex = 0
        var col = size - 1
        var upward = true

        while (col > 0) {
            if (col == 6) col--

            val rows = if (upward) (size - 1 downTo 0).toList() else (0 until size).toList()
            for (r in rows) {
                for (cOffset in 0..1) {
                    val c = col - cOffset
                    if (!reserved[r][c]) {
                        val bitVal = if (bitIndex < totalBits.size) totalBits[bitIndex++] else false
                        val maskedVal = if ((r + c) % 2 == 0) !bitVal else bitVal
                        matrix[r][c] = maskedVal
                    }
                }
            }
            upward = !upward
            col -= 2
        }

        return matrix
    }
}
