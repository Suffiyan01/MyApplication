package com.ridervoice.cockpit.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.HeadsetMic
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ridervoice.cockpit.model.BluetoothHeadsetState
import com.ridervoice.cockpit.model.CockpitTab
import com.ridervoice.cockpit.model.CrashSosState
import com.ridervoice.cockpit.model.IntercomState
import com.ridervoice.cockpit.model.SquadGroupState
import com.ridervoice.cockpit.ui.theme.CarbonBackground
import com.ridervoice.cockpit.ui.theme.CarbonOutline
import com.ridervoice.cockpit.ui.theme.CarbonSurface
import com.ridervoice.cockpit.ui.theme.CarbonSurfaceVariant
import com.ridervoice.cockpit.ui.theme.NeonAmber
import com.ridervoice.cockpit.ui.theme.NeonCyan
import com.ridervoice.cockpit.ui.theme.NeonGreen
import com.ridervoice.cockpit.ui.theme.NeonRose
import com.ridervoice.cockpit.ui.theme.TextPrimary
import com.ridervoice.cockpit.ui.theme.TextSecondary

@Composable
fun TopPersistentStatusBar(
    headset: BluetoothHeadsetState,
    intercom: IntercomState,
    squadGroup: SquadGroupState,
    coRiderCount: Int,
    themeMode: com.ridervoice.cockpit.model.AppThemeMode = com.ridervoice.cockpit.model.AppThemeMode.DARK,
    onToggleThemeMode: () -> Unit = {},
    onTabSelect: (CockpitTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val batteryColor = when {
        headset.batteryPct > 30 -> NeonGreen
        headset.batteryPct in 15..30 -> NeonAmber
        else -> NeonRose
    }

    val transition = rememberInfiniteTransition(label = "speaker_pulse")
    val pulseAlpha by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left Pill: Bluetooth Helmet Battery
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(1.dp, batteryColor.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                    .clickable { onTabSelect(CockpitTab.AUDIO_SOS) }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.HeadsetMic,
                    contentDescription = "Helmet BT",
                    tint = batteryColor,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "${headset.deviceName.take(7)} • ${headset.batteryPct}%",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }

            // Center Pill: Active Speaker / Intercom State
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (intercom.isTransmitting) NeonRose else NeonGreen)
                        .alpha(if (intercom.isTransmitting || intercom.activeSpeakerCallsign != null) pulseAlpha else 1.0f)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = when {
                        intercom.isTransmitting -> "PTT"
                        !squadGroup.isActive -> "NO SQUAD"
                        intercom.activeSpeakerCallsign != null -> "🎙️ ${intercom.activeSpeakerCallsign.take(10)}"
                        else -> "MESH IDLE"
                    },
                    color = if (intercom.isTransmitting) NeonRose else MaterialTheme.colorScheme.onSurface,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }

            // Right Pill: Squad Group Status
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(1.dp, if (squadGroup.isActive) NeonCyan.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp))
                    .clickable { onTabSelect(CockpitTab.SQUAD) }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Group,
                    contentDescription = "Squad Status",
                    tint = if (squadGroup.isActive) NeonCyan else TextSecondary,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (squadGroup.isActive) "${squadGroup.joinCode} (${coRiderCount + 1})" else "NO GROUP",
                    color = if (squadGroup.isActive) NeonCyan else TextSecondary,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }

            // Far Right: Theme Toggle Button (Light / Dark)
            IconButton(
                onClick = onToggleThemeMode,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = if (themeMode.isDark) Icons.Default.DarkMode else Icons.Default.LightMode,
                    contentDescription = "Toggle Appearance Mode",
                    tint = if (themeMode.isDark) NeonCyan else NeonAmber,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
fun BottomGloveNavBar(
    currentTab: CockpitTab,
    onTabSelect: (CockpitTab) -> Unit,
    onSosTrigger: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            NavigationBar(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 68.dp),
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp
            ) {
                CockpitTab.entries.forEach { tab ->
                    val isSelected = (currentTab == tab)
                    val icon = when (tab) {
                        CockpitTab.HUD -> Icons.Default.Speed
                        CockpitTab.SQUAD -> Icons.Default.Radio
                        CockpitTab.MAP_RADAR -> Icons.Default.Map
                        CockpitTab.AI_PLACES -> Icons.Default.Psychology
                        CockpitTab.AUDIO_SOS -> Icons.Default.HeadsetMic
                    }

                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { onTabSelect(tab) },
                        icon = {
                            Icon(
                                imageVector = icon,
                                contentDescription = tab.label,
                                modifier = Modifier.size(24.dp)
                            )
                        },
                        label = {
                            Text(
                                text = tab.label,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = NeonCyan,
                            selectedTextColor = NeonCyan,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            indicatorColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    )
                }
            }

            // Corner Emergency SOS Button
            Button(
                onClick = onSosTrigger,
                modifier = Modifier
                    .heightIn(min = 52.dp)
                    .padding(end = 8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = NeonRose,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "SOS",
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "SOS",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Black
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickAiOverlaySheet(
    isListening: Boolean = false,
    partialTranscript: String = "",
    onStartListening: () -> Unit = {},
    onStopListening: () -> Unit = {},
    onDismiss: () -> Unit,
    onSendPrompt: (String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var textInput by remember { mutableStateOf("") }

    val infiniteTransition = rememberInfiniteTransition(label = "voice_mic_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "micAlpha"
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        scrimColor = Color.Black.copy(alpha = 0.7f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = "AI Copilot",
                    tint = NeonCyan,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "RIDERVOICE COPILOT",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 18.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }

            // Live Speech Listening / Transcription Banner
            if (isListening || partialTranscript.isNotBlank()) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    color = if (isListening) NeonCyan.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isListening) NeonCyan else MaterialTheme.colorScheme.outline
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(if (isListening) NeonCyan else NeonAmber)
                                .alpha(if (isListening) pulseAlpha else 1.0f)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = if (isListening) {
                                partialTranscript.ifBlank { "Listening for helmet mic input..." }
                            } else {
                                "Recognized: $partialTranscript"
                            },
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            Text(
                text = "1-Tap Glove Quick Prompts:",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // Quick Prompt Chips
            val quickChips = listOf(
                "Find nearest fuel stop",
                "Lock Amber Fort as squad goal",
                "Check squad battery status",
                "Broadcast pothole hazard ahead"
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                quickChips.forEach { chipText ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                onSendPrompt(chipText)
                                onDismiss()
                            },
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        border = androidx.compose.foundation.BorderStroke(1.dp, NeonCyan.copy(alpha = 0.3f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Navigation,
                                contentDescription = null,
                                tint = NeonCyan,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = chipText,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 14.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Voice or Text Prompt Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Mic Button Toggle
                IconButton(
                    onClick = {
                        if (isListening) onStopListening() else onStartListening()
                    },
                    modifier = Modifier
                        .size(52.dp)
                        .background(
                            if (isListening) NeonRose else MaterialTheme.colorScheme.surfaceVariant,
                            RoundedCornerShape(12.dp)
                        )
                        .border(
                            1.dp,
                            if (isListening) NeonRose else NeonCyan.copy(alpha = 0.5f),
                            RoundedCornerShape(12.dp)
                        )
                ) {
                    Icon(
                        imageVector = if (isListening) Icons.Default.MicOff else Icons.Default.Mic,
                        contentDescription = "Microphone",
                        tint = if (isListening) Color.White else NeonCyan,
                        modifier = Modifier.alpha(if (isListening) pulseAlpha else 1.0f)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                OutlinedTextField(
                    value = textInput,
                    onValueChange = { textInput = it },
                    modifier = Modifier.weight(1f),
                    placeholder = {
                        Text(
                            text = if (isListening) "Listening to speech..." else "Ask or command Copilot...",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 14.sp
                        )
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.background,
                        unfocusedContainerColor = MaterialTheme.colorScheme.background,
                        focusedBorderColor = NeonCyan,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        focusedTextColor = MaterialTheme.colorScheme.onBackground,
                        unfocusedTextColor = MaterialTheme.colorScheme.onBackground
                    ),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    onClick = {
                        val promptToSend = textInput.ifBlank { partialTranscript }
                        if (promptToSend.isNotBlank()) {
                            onSendPrompt(promptToSend)
                            textInput = ""
                            onDismiss()
                        }
                    },
                    modifier = Modifier
                        .size(52.dp)
                        .background(NeonCyan, RoundedCornerShape(12.dp))
                ) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = "Send",
                        tint = MaterialTheme.colorScheme.background
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun CrashSosModal(
    crashSos: CrashSosState,
    onCancelSos: () -> Unit
) {
    if (!crashSos.isTriggered) return

    Dialog(
        onDismissRequest = { /* Lock dialog during SOS alert */ },
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            shape = RoundedCornerShape(24.dp),
            color = NeonRose,
            shadowElevation = 16.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "CRASH IMPACT",
                    tint = Color.White,
                    modifier = Modifier.size(64.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "CRASH IMPACT DETECTED!",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "G-Force Impact: ${crashSos.impactGForce}G",
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace
                )

                Spacer(modifier = Modifier.height(16.dp))

                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.2f))
                        .border(4.dp, Color.White, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${crashSos.secondsRemaining}",
                        color = Color.White,
                        fontSize = 64.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Black
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = if (crashSos.broadcastDispatched) {
                        "🚨 EMERGENCY DISTRESS BROADCASTED TO SQUAD & HIGHWAY MESH!"
                    } else {
                        "Dispatching emergency GPS beacon to squad & emergency channel in ${crashSos.secondsRemaining} seconds..."
                    },
                    color = Color.White,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = onCancelSos,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 72.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = NeonRose
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        text = "I AM OK — CANCEL SOS",
                        color = NeonRose,
                        fontSize = 18.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Black
                    )
                }
            }
        }
    }
}
