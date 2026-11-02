package com.ridervoice.cockpit.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberMarkerState
import com.ridervoice.cockpit.R
import com.ridervoice.cockpit.model.RiderRole
import com.ridervoice.cockpit.model.RiderVoiceUiState
import com.ridervoice.cockpit.ui.theme.NeonCyan
import kotlinx.coroutines.launch

@Composable
fun RideMapScreen(
    uiState: RiderVoiceUiState,
    onMapLongClick: ((LatLng) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val telemetry = uiState.telemetry
    val coRiders = uiState.coRiders
    val hazards = uiState.hazards
    val places = uiState.placeResults
    val goal = uiState.sharedGoal
    val isDark = uiState.themeMode.isDark

    val currentLatLng = LatLng(telemetry.myLocation.lat, telemetry.myLocation.lon)

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.Builder()
            .target(currentLatLng)
            .zoom(17f)
            .bearing(telemetry.compassHeadingDeg.toFloat())
            .tilt(35f)
            .build()
    }

    var isCameraFollowingRider by remember { mutableStateOf(true) }

    LaunchedEffect(currentLatLng, telemetry.compassHeadingDeg) {
        if (isCameraFollowingRider) {
            cameraPositionState.animate(
                CameraUpdateFactory.newCameraPosition(
                    CameraPosition.Builder()
                        .target(currentLatLng)
                        .zoom(17f)
                        .bearing(telemetry.compassHeadingDeg.toFloat())
                        .tilt(35f)
                        .build()
                ),
                1000
            )
        }
    }

    val mapProperties = remember(context, isDark) {
        val styleOptions = if (isDark) {
            try {
                MapStyleOptions.loadRawResourceStyle(context, R.raw.map_style_carbon)
            } catch (e: Exception) {
                null
            }
        } else {
            null // Standard clean daylight Google Maps style
        }
        MapProperties(
            isMyLocationEnabled = false,
            mapStyleOptions = styleOptions
        )
    }

    val mapUiSettings = remember {
        MapUiSettings(
            zoomControlsEnabled = false,
            compassEnabled = true,
            myLocationButtonEnabled = false
        )
    }

    Box(modifier = modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = mapProperties,
            uiSettings = mapUiSettings,
            onMapClick = {
                isCameraFollowingRider = false
            },
            onMapLongClick = { latLng ->
                onMapLongClick?.invoke(latLng)
            }
        ) {
            if (goal != null && goal.routePolyline.isNotEmpty()) {
                Polyline(
                    points = goal.routePolyline.map { LatLng(it.lat, it.lon) },
                    color = NeonCyan,
                    width = 12f
                )

                Marker(
                    state = rememberMarkerState(position = LatLng(goal.targetPoint.lat, goal.targetPoint.lon)),
                    title = "GOAL: ${goal.name}",
                    snippet = "ETA ${goal.etaMinutes} min • ${goal.distanceRemainingKm} km",
                    icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_CYAN)
                )
            }

            Marker(
                state = rememberMarkerState(position = currentLatLng),
                title = "You (Road Captain)",
                snippet = "${telemetry.speedKmh} km/h • Heading ${telemetry.compassHeadingDeg}°",
                icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE),
                flat = true,
                rotation = telemetry.compassHeadingDeg.toFloat()
            )

            coRiders.forEach { rider ->
                val riderLatLng = LatLng(rider.location.lat, rider.location.lon)
                val hue = when (rider.role) {
                    RiderRole.ROAD_CAPTAIN -> BitmapDescriptorFactory.HUE_CYAN
                    RiderRole.PACK_RIDER -> BitmapDescriptorFactory.HUE_GREEN
                    RiderRole.TAIL_GUNNER -> BitmapDescriptorFactory.HUE_ORANGE
                }
                Marker(
                    state = rememberMarkerState(position = riderLatLng),
                    title = "${rider.callsign} [${rider.role.badge}]",
                    snippet = "${rider.speedKmh} km/h • Helmet ${rider.helmetBatteryPct}%",
                    icon = BitmapDescriptorFactory.defaultMarker(hue)
                )
            }

            hazards.forEach { hazard ->
                val hazardLatLng = LatLng(hazard.location.lat, hazard.location.lon)
                Marker(
                    state = rememberMarkerState(position = hazardLatLng),
                    title = "HAZARD: ${hazard.type.label}",
                    snippet = "Reported by ${hazard.reportedByCallsign}",
                    icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)
                )
            }

            places.forEach { place ->
                val placeLatLng = LatLng(place.location.lat, place.location.lon)
                Marker(
                    state = rememberMarkerState(position = placeLatLng),
                    title = place.title,
                    snippet = "${place.subtitle} (${place.distanceKm} km)",
                    icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_YELLOW)
                )
            }
        }

        FloatingActionButton(
            onClick = {
                isCameraFollowingRider = true
                coroutineScope.launch {
                    cameraPositionState.animate(
                        CameraUpdateFactory.newCameraPosition(
                            CameraPosition.Builder()
                                .target(currentLatLng)
                                .zoom(17f)
                                .bearing(telemetry.compassHeadingDeg.toFloat())
                                .tilt(35f)
                                .build()
                        ),
                        800
                    )
                }
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = NeonCyan,
            shape = CircleShape
        ) {
            Icon(
                imageVector = Icons.Default.MyLocation,
                contentDescription = "Recenter Map"
            )
        }
    }
}
