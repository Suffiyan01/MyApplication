package com.ridervoice.cockpit.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CompassCalibration
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ridervoice.cockpit.model.GeoPointData
import com.ridervoice.cockpit.model.HazardType
import com.ridervoice.cockpit.model.RiderVoiceUiState
import com.ridervoice.cockpit.ui.theme.NeonAmber
import com.ridervoice.cockpit.ui.theme.NeonCyan
import com.ridervoice.cockpit.ui.theme.NeonGreen
import com.ridervoice.cockpit.ui.theme.NeonRose
import com.ridervoice.cockpit.ui.theme.TextSecondary
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun RadarMapNavigationScreen(
    uiState: RiderVoiceUiState,
    onToggleRadarMode: (Boolean) -> Unit,
    onBroadcastHazard: (HazardType) -> Unit,
    onSetSharedGoalLocation: ((GeoPointData) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val telemetry = uiState.telemetry
    val isRadarMode = uiState.isMapInRadarMode

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 1. Top Mode Switcher Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(
                    selected = !isRadarMode,
                    onClick = { onToggleRadarMode(false) },
                    label = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.Map, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("LIVE MAP (GOOGLE)", fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                        }
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = NeonCyan,
                        selectedLabelColor = MaterialTheme.colorScheme.background,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )

                FilterChip(
                    selected = isRadarMode,
                    onClick = { onToggleRadarMode(true) },
                    label = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.Radar, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("360° RADAR", fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                        }
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = NeonGreen,
                        selectedLabelColor = MaterialTheme.colorScheme.background,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }
        }

        // 2. Main Map / Radar Display Frame
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .border(1.dp, if (isRadarMode) NeonGreen else NeonCyan, RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surface)
        ) {
            if (isRadarMode) {
                // 360° Tactical Radar Sweep View
                TacticalRadarSweepView(uiState = uiState)
            } else {
                // Native Free Map Engine
                RideMapScreen(uiState = uiState)
            }

            // Floating Telemetry Compass Badge Bottom-Left
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp)
                    .clip(RoundedCornerShape(12.dp)),
                color = MaterialTheme.colorScheme.background.copy(alpha = 0.85f),
                border = androidx.compose.foundation.BorderStroke(1.dp, NeonCyan)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.CompassCalibration,
                        contentDescription = "Compass",
                        tint = NeonCyan,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "${telemetry.speedKmh} KM/H • ${telemetry.compassHeadingDeg}° ${telemetry.compassCardinal}",
                        color = MaterialTheme.colorScheme.onBackground,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // 3. 1-Tap Highway Hazard Broadcast Bar
        Text(
            text = "1-TAP HIGHWAY HAZARD BROADCAST",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val hazards = listOf(
                HazardType.POTHOLE to NeonAmber,
                HazardType.SPEED_TRAP to NeonCyan,
                HazardType.OIL_GRAVEL to NeonRose,
                HazardType.ROADBLOCK to NeonRose
            )

            hazards.forEach { (type, color) ->
                Button(
                    onClick = { onBroadcastHazard(type) },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 52.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = color
                    ),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, color)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = type.label,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = type.label.take(5),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TacticalRadarSweepView(
    uiState: RiderVoiceUiState,
    modifier: Modifier = Modifier
) {
    val coRiders = uiState.coRiders
    val hazards = uiState.hazards

    val transition = rememberInfiniteTransition(label = "radar_sweep")
    val sweepAngle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sweepAngle"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF071318)),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val maxRadius = size.minDimension / 2.2f

            // Radar Concentric Circles (100m, 250m, 500m)
            val circleSteps = listOf(0.33f, 0.66f, 1.0f)
            circleSteps.forEach { step ->
                drawCircle(
                    color = NeonGreen.copy(alpha = 0.3f),
                    radius = maxRadius * step,
                    center = center,
                    style = Stroke(width = 1.dp.toPx())
                )
            }

            // Crosshair Lines
            drawLine(
                color = NeonGreen.copy(alpha = 0.3f),
                start = Offset(center.x, center.y - maxRadius),
                end = Offset(center.x, center.y + maxRadius),
                strokeWidth = 1.dp.toPx()
            )
            drawLine(
                color = NeonGreen.copy(alpha = 0.3f),
                start = Offset(center.x - maxRadius, center.y),
                end = Offset(center.x + maxRadius, center.y),
                strokeWidth = 1.dp.toPx()
            )

            // Dynamic Rotating Radar Sweep Line
            rotate(degrees = sweepAngle, pivot = center) {
                drawLine(
                    color = NeonGreen,
                    start = center,
                    end = Offset(center.x, center.y - maxRadius),
                    strokeWidth = 2.dp.toPx()
                )

                val sweepPath = Path().apply {
                    moveTo(center.x, center.y)
                    lineTo(center.x, center.y - maxRadius)
                    arcTo(
                        rect = androidx.compose.ui.geometry.Rect(
                            center.x - maxRadius,
                            center.y - maxRadius,
                            center.x + maxRadius,
                            center.y + maxRadius
                        ),
                        startAngleDegrees = -90f,
                        sweepAngleDegrees = -45f,
                        forceMoveTo = false
                    )
                    close()
                }
                drawPath(
                    path = sweepPath,
                    color = NeonGreen.copy(alpha = 0.15f)
                )
            }

            // Draw Self (Center Arrow Marker)
            drawCircle(
                color = NeonCyan,
                radius = 7.dp.toPx(),
                center = center
            )

            // Draw Co-Rider Radar Targets
            coRiders.forEach { rider ->
                val distRatio = (rider.distanceMeters / 500f).coerceIn(0.1f, 1.0f)
                val rad = Math.toRadians((rider.bearingDeg - 90).toDouble())
                val rx = center.x + (maxRadius * distRatio * cos(rad)).toFloat()
                val ry = center.y + (maxRadius * distRatio * sin(rad)).toFloat()

                val targetColor = Color(rider.role.colorHex)
                drawCircle(
                    color = targetColor,
                    radius = 6.dp.toPx(),
                    center = Offset(rx, ry)
                )
            }

            // Draw Hazard Targets
            hazards.forEach { hz ->
                val distRatio = (hz.distanceMeters / 500f).coerceIn(0.1f, 1.0f)
                val rad = Math.toRadians((hz.bearingDeg - 90).toDouble())
                val hx = center.x + (maxRadius * distRatio * cos(rad)).toFloat()
                val hy = center.y + (maxRadius * distRatio * sin(rad)).toFloat()

                drawCircle(
                    color = Color(hz.type.colorHex),
                    radius = 5.dp.toPx(),
                    center = Offset(hx, hy)
                )
            }
        }
    }
}
