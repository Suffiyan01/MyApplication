package com.ridervoice.cockpit.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ridervoice.cockpit.model.MessageSender
import com.ridervoice.cockpit.model.PlaceCategory
import com.ridervoice.cockpit.model.PlaceSearchResult
import com.ridervoice.cockpit.model.RiderVoiceUiState
import com.ridervoice.cockpit.ui.theme.NeonCyan
import com.ridervoice.cockpit.ui.theme.NeonGreen
import com.ridervoice.cockpit.ui.theme.TextSecondary

@Composable
fun AiCopilotPlacesScreen(
    uiState: RiderVoiceUiState,
    onExecutePrompt: (String) -> Unit,
    onSearchPlaces: (String, PlaceCategory) -> Unit,
    onSetSharedGoal: (PlaceSearchResult) -> Unit,
    onStartVoiceListening: () -> Unit = {},
    onStopVoiceListening: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val messages = uiState.copilotMessages
    val places = uiState.placeResults
    val currentCategory = uiState.selectedPlaceCategory
    val currentGoal = uiState.sharedGoal

    var promptInput by remember { mutableStateOf("") }
    var searchQueryInput by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 1. AI Copilot Chat Feed Area (Top Half)
        Card(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = androidx.compose.foundation.BorderStroke(1.dp, NeonCyan.copy(alpha = 0.4f)),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "AI Copilot",
                            tint = NeonCyan,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "VOICE AI COPILOT & TOOL ENGINE",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = "GEMINI NATIVE",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            color = NeonCyan,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                // Scrollable Chat Feed
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(messages) { msg ->
                        val isRider = (msg.sender == MessageSender.RIDER)
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = if (isRider) Alignment.End else Alignment.Start
                        ) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isRider) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.background,
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (isRider) NeonCyan else MaterialTheme.colorScheme.outline
                                )
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    if (msg.toolBadge != null) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = NeonCyan.copy(alpha = 0.2f),
                                            modifier = Modifier.padding(bottom = 4.dp)
                                        ) {
                                            Text(
                                                text = "TOOL EXECUTED: ${msg.toolBadge}",
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                color = NeonCyan,
                                                fontSize = 10.sp,
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = FontWeight.Black
                                            )
                                        }
                                    }

                                    Text(
                                        text = msg.text,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Live Speech Banner if Listening
                if (uiState.isVoiceListening || uiState.voicePartialTranscript.isNotBlank()) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                            .clip(RoundedCornerShape(10.dp)),
                        color = NeonCyan.copy(alpha = 0.15f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, NeonCyan)
                    ) {
                        Text(
                            text = "🎙️ ${uiState.voicePartialTranscript.ifBlank { "Listening to helmet mic..." }}",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            color = NeonCyan,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Input Bar (Mic + TextField + Send)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(
                        onClick = {
                            if (uiState.isVoiceListening) onStopVoiceListening() else onStartVoiceListening()
                        },
                        modifier = Modifier
                            .size(52.dp)
                            .background(
                                if (uiState.isVoiceListening) NeonCyan else MaterialTheme.colorScheme.surfaceVariant,
                                RoundedCornerShape(12.dp)
                            )
                    ) {
                        Icon(
                            imageVector = if (uiState.isVoiceListening) Icons.Default.Mic else Icons.Default.MicOff,
                            contentDescription = "Voice Input",
                            tint = if (uiState.isVoiceListening) MaterialTheme.colorScheme.background else NeonCyan
                        )
                    }

                    OutlinedTextField(
                        value = promptInput,
                        onValueChange = { promptInput = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Ask Copilot (e.g. 'nearest fuel')", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonCyan,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                        ),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    IconButton(
                        onClick = {
                            if (promptInput.isNotBlank()) {
                                onExecutePrompt(promptInput)
                                promptInput = ""
                            }
                        },
                        modifier = Modifier
                            .size(52.dp)
                            .background(NeonCyan, RoundedCornerShape(12.dp))
                    ) {
                        Icon(imageVector = Icons.Default.Send, contentDescription = "Send", tint = MaterialTheme.colorScheme.background)
                    }
                }
            }
        }

        // 2. Free POI Place Search & Category Filter
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = searchQueryInput,
                        onValueChange = {
                            searchQueryInput = it
                            onSearchPlaces(it, currentCategory)
                        },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Search rider stops & POIs...", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp) },
                        leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = "Search", tint = NeonCyan) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonCyan,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                        ),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Category Filter Chips
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(PlaceCategory.entries) { cat ->
                        FilterChip(
                            selected = (currentCategory == cat),
                            onClick = { onSearchPlaces(searchQueryInput, cat) },
                            label = {
                                Text(
                                    text = cat.label,
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
            }
        }

        // 3. Nearby Stops Results List
        Text(
            text = "NEARBY HIGHWAY STOPS (${places.size} FOUND)",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold
        )

        LazyColumn(
            modifier = Modifier
                .weight(0.9f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(places) { place ->
                val isGoalLocked = (currentGoal?.placeId == place.placeId)

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isGoalLocked) NeonGreen else MaterialTheme.colorScheme.outline
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isGoalLocked) NeonGreen.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isGoalLocked) Icons.Default.CheckCircle else Icons.Default.LocalGasStation,
                                    contentDescription = place.category.label,
                                    tint = if (isGoalLocked) NeonGreen else NeonCyan,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            Column {
                                Text(
                                    text = place.title,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${place.subtitle} • ${place.distanceKm} km (+${place.detourMinutes} min)",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        // 1-Tap Sync Goal Button
                        Button(
                            onClick = { onSetSharedGoal(place) },
                            modifier = Modifier.heightIn(min = 44.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isGoalLocked) NeonGreen else NeonCyan,
                                contentColor = MaterialTheme.colorScheme.background
                            ),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Navigation,
                                    contentDescription = "Sync Goal",
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (isGoalLocked) "ACTIVE ✓" else "SYNC GOAL",
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Black
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
