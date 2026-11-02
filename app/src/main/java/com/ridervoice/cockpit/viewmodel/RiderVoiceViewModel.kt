package com.ridervoice.cockpit.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ridervoice.cockpit.domain.CopilotToolRegistry
import com.ridervoice.cockpit.domain.GeoMathAndFreeMapEngine
import com.ridervoice.cockpit.model.BluetoothHeadsetState
import com.ridervoice.cockpit.model.CoRider
import com.ridervoice.cockpit.model.CockpitTab
import com.ridervoice.cockpit.model.CopilotMessage
import com.ridervoice.cockpit.model.CrashSosState
import com.ridervoice.cockpit.model.GeoPointData
import com.ridervoice.cockpit.model.HazardType
import com.ridervoice.cockpit.model.IntercomChannel
import com.ridervoice.cockpit.model.MessageSender
import com.ridervoice.cockpit.model.PlaceCategory
import com.ridervoice.cockpit.model.PlaceSearchResult
import com.ridervoice.cockpit.model.RiderRole
import com.ridervoice.cockpit.model.RiderVoiceUiState
import com.ridervoice.cockpit.model.RoadHazard
import com.ridervoice.cockpit.model.SharedDestination
import com.ridervoice.cockpit.model.SquadGroupState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.ridervoice.cockpit.domain.HotspotQrData
import com.ridervoice.cockpit.domain.HybridSquadRouter
import com.ridervoice.cockpit.domain.LocalHotspotManager
import com.ridervoice.cockpit.domain.NearbySquadMeshManager
import com.ridervoice.cockpit.domain.RealRideSensorManager
import com.ridervoice.cockpit.model.ConnectionType
import com.ridervoice.cockpit.model.TransportMode
import kotlin.math.cos
import kotlin.math.sin

class RiderVoiceViewModel(
    private val appContainer: com.ridervoice.cockpit.di.AppContainer? = null
) : ViewModel() {

    class Factory(private val appContainer: com.ridervoice.cockpit.di.AppContainer) : androidx.lifecycle.ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(RiderVoiceViewModel::class.java)) {
                return RiderVoiceViewModel(appContainer) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }

    private val _uiState = MutableStateFlow(RiderVoiceUiState())
    val uiState: StateFlow<RiderVoiceUiState> = _uiState.asStateFlow()

    private val _latestSpokenText = MutableStateFlow<String?>(null)
    val latestSpokenText: StateFlow<String?> = _latestSpokenText.asStateFlow()

    private val _location = MutableStateFlow<android.location.Location?>(null)
    val location: StateFlow<android.location.Location?> = _location.asStateFlow()

    private var nearbyMeshManager: NearbySquadMeshManager? = null
    private var realRideSensorManager: RealRideSensorManager? = null
    private var localHotspotManager: LocalHotspotManager? = null
    private var hybridSquadRouter: HybridSquadRouter? = null
    private var systemControlManager: com.ridervoice.cockpit.domain.SystemControlManager? = appContainer?.systemControlManager

    fun setSystemControlManager(manager: com.ridervoice.cockpit.domain.SystemControlManager) {
        this.systemControlManager = manager
    }

    fun setLocalHotspotManager(manager: LocalHotspotManager) {
        this.localHotspotManager = manager
    }

    fun setHybridSquadRouter(router: HybridSquadRouter) {
        this.hybridSquadRouter = router
    }

    fun updateRealGpsLocation(loc: android.location.Location) {
        _location.value = loc
        _uiState.update { state ->
            val geoPoint = GeoPointData(loc.latitude, loc.longitude)
            val rawSpeed = if (loc.hasSpeed()) (loc.speed * 3.6f).toInt() else state.telemetry.speedKmh
            val speed = if (state.telemetry.speedKmh == 0) rawSpeed else ((state.telemetry.speedKmh * 0.7f) + (rawSpeed * 0.3f)).toInt()
            val heading = if (loc.hasBearing()) loc.bearing.toInt() else state.telemetry.compassHeadingDeg
            val alt = if (loc.hasAltitude()) loc.altitude.toInt() else state.telemetry.altitudeMeters

            state.copy(
                telemetry = state.telemetry.copy(
                    myLocation = geoPoint,
                    speedKmh = speed,
                    compassHeadingDeg = heading,
                    altitudeMeters = alt
                )
            )
        }
    }

    fun setNearbyMeshManager(manager: NearbySquadMeshManager) {
        this.nearbyMeshManager = manager
    }

    fun setRealRideSensorManager(manager: RealRideSensorManager) {
        this.realRideSensorManager = manager
    }

    fun calibrateZeroLean() {
        realRideSensorManager?.calibrateZeroLean()
        _uiState.update {
            it.copy(
                telemetry = it.telemetry.copy(
                    leanAngleDeg = 0f,
                    peakLeanLeftDeg = 0f,
                    peakLeanRightDeg = 0f
                ),
                lastSystemBannerToast = "Calibrated Upright Handlebar Mount Lean Angle to 0°"
            )
        }
    }

    fun addOrUpdateP2PCoRider(coRider: CoRider) {
        _uiState.update { state ->
            val myLoc = state.telemetry.myLocation
            val dist = GeoMathAndFreeMapEngine.calculateDistanceMeters(myLoc, coRider.location)
            val bearing = GeoMathAndFreeMapEngine.calculateBearingDeg(myLoc, coRider.location)
            val updatedRider = coRider.copy(
                distanceMeters = dist,
                bearingDeg = bearing
            )

            val realPeersOnly = state.coRiders.filter { !it.id.startsWith("rider-0") }
            val existingIndex = realPeersOnly.indexOfFirst { it.id == coRider.id || it.callsign == coRider.callsign }
            val newCoRiders = if (existingIndex >= 0) {
                realPeersOnly.toMutableList().apply { set(existingIndex, updatedRider) }
            } else {
                realPeersOnly + updatedRider
            }

            state.copy(coRiders = newCoRiders)
        }
    }

    fun updateCoRiderConnectionType(peerId: String, connType: ConnectionType, connLabel: String) {
        _uiState.update { state ->
            val updated = state.coRiders.map { rider ->
                if (rider.id == peerId || rider.callsign == peerId) {
                    rider.copy(
                        connectionType = connType,
                        connectionLabel = connLabel,
                        isInternetRelay = (connType == ConnectionType.CELLULAR_CLOUD)
                    )
                } else rider
            }
            state.copy(coRiders = updated)
        }
    }

    fun receiveP2PGoalOrHazard(goal: SharedDestination?, hazard: RoadHazard?) {
        _uiState.update { state ->
            var newState = state
            if (goal != null) {
                newState = newState.copy(sharedGoal = goal)
            }
            if (hazard != null && state.hazards.none { it.id == hazard.id }) {
                newState = newState.copy(
                    hazards = listOf(hazard) + newState.hazards,
                    lastSystemBannerToast = "SQUAD WARNING: ${hazard.type.label} reported ahead by ${hazard.reportedByCallsign}!"
                )
                _latestSpokenText.value = hazard.type.warningSpeech
            }
            newState
        }
    }

    fun clearSpokenText() {
        _latestSpokenText.value = null
    }

    fun speakText(text: String) {
        _latestSpokenText.value = text
    }

    private var meshBroadcastJob: Job? = null
    private var crashCountdownJob: Job? = null

    init {
        setupInitialCopilotWelcome()
        startRealMeshBroadcastLoop()
    }

    private fun setupInitialCopilotWelcome() {
        viewModelScope.launch {
            val initialCopilotWelcome = listOf(
                CopilotMessage(
                    id = "msg-welcome",
                    sender = MessageSender.AI_COPILOT,
                    text = "RiderVoice Copilot active. Real GPS location & sensor monitoring enabled.",
                    timestampLabel = "Just now"
                )
            )
            _uiState.update { state ->
                state.copy(copilotMessages = initialCopilotWelcome)
            }
        }
    }

    private fun startRealMeshBroadcastLoop() {
        meshBroadcastJob?.cancel()
        meshBroadcastJob = viewModelScope.launch {
            while (true) {
                delay(1000)
                val state = _uiState.value
                if (state.squadGroup.isActive) {
                    if (hybridSquadRouter != null) {
                        hybridSquadRouter?.broadcastTelemetry(
                            myCallsign = if (state.squadGroup.isCurrentUserLeader) "Leader" else "Member",
                            role = if (state.squadGroup.isCurrentUserLeader) RiderRole.ROAD_CAPTAIN else RiderRole.PACK_RIDER,
                            location = state.telemetry.myLocation,
                            speedKmh = state.telemetry.speedKmh,
                            headingDeg = state.telemetry.compassHeadingDeg.toFloat(),
                            batteryPct = state.headset.batteryPct,
                            isSpeaking = state.intercom.isTransmitting,
                            sharedGoal = state.sharedGoal,
                            latestHazard = state.hazards.firstOrNull()
                        )
                    } else {
                        nearbyMeshManager?.broadcastTelemetryPacket(
                            myCallsign = if (state.squadGroup.isCurrentUserLeader) "Leader" else "Member",
                            role = if (state.squadGroup.isCurrentUserLeader) RiderRole.ROAD_CAPTAIN else RiderRole.PACK_RIDER,
                            location = state.telemetry.myLocation,
                            speedKmh = state.telemetry.speedKmh,
                            headingDeg = state.telemetry.compassHeadingDeg.toFloat(),
                            batteryPct = state.headset.batteryPct,
                            isSpeaking = state.intercom.isTransmitting,
                            sharedGoal = state.sharedGoal,
                            latestHazard = state.hazards.firstOrNull()
                        )
                    }
                }
            }
        }
    }

    // Tab Navigation
    fun selectTab(tab: CockpitTab) {
        _uiState.update { it.copy(currentTab = tab) }
    }

    // Intercom Actions
    fun togglePttLatch() {
        _uiState.update {
            val newLatch = !it.intercom.isPttLatched
            it.copy(intercom = it.intercom.copy(isPttLatched = newLatch))
        }
        if (_uiState.value.intercom.isTransmitting) {
            nearbyMeshManager?.startPttStream()
        } else {
            nearbyMeshManager?.stopPttStream()
        }
    }

    fun setPttHeld(held: Boolean) {
        _uiState.update { it.copy(intercom = it.intercom.copy(isPttHeld = held)) }
        if (_uiState.value.intercom.isTransmitting) {
            nearbyMeshManager?.startPttStream()
        } else {
            nearbyMeshManager?.stopPttStream()
        }
    }

    fun toggleVox(enabled: Boolean) {
        _uiState.update { it.copy(intercom = it.intercom.copy(isVoxEnabled = enabled)) }
    }

    fun setVoxThreshold(db: Float) {
        _uiState.update { it.copy(intercom = it.intercom.copy(voxThresholdDb = db)) }
    }

    fun selectIntercomChannel(channel: IntercomChannel) {
        _uiState.update {
            it.copy(
                squadGroup = it.squadGroup.copy(activeChannel = channel),
                lastSystemBannerToast = "Switched to ${channel.title} (${channel.freqLabel})"
            )
        }
    }

    // Group Management
    fun createSquadGroup(groupName: String) {
        val code = "RV-${(1000..9999).random()}"
        _uiState.update {
            it.copy(
                squadGroup = SquadGroupState(
                    groupName = groupName.ifBlank { "Aravalli Canyon Pack" },
                    joinCode = code,
                    isActive = true,
                    isCurrentUserLeader = true,
                    transportMode = TransportMode.HYBRID_MESH_CELLULAR
                ),
                lastSystemBannerToast = "Created Group '$groupName' (Code: $code)"
            )
        }
        nearbyMeshManager?.startAdvertisingGroup(code, "Captain")
        startLocalHotspotForSquad()
    }

    fun startLocalHotspotForSquad() {
        val currentCode = _uiState.value.squadGroup.joinCode
        localHotspotManager?.startHotspot(
            squadCode = currentCode,
            onHotspotStarted = { ssid, pwd, _ ->
                _uiState.update { state ->
                    state.copy(
                        squadGroup = state.squadGroup.copy(
                            hotspotSsid = ssid,
                            hotspotPassword = pwd,
                            transportMode = TransportMode.HYBRID_MESH_CELLULAR
                        ),
                        lastSystemBannerToast = "Local Wi-Fi Hotspot '$ssid' Active! Share QR code with squad."
                    )
                }
            },
            onHotspotFailed = { reason ->
                _uiState.update { state ->
                    state.copy(lastSystemBannerToast = "Hotspot Warning: $reason")
                }
            }
        )
    }

    fun joinSquadWithHotspotPayload(qrData: HotspotQrData) {
        if (qrData.ssid.isNotBlank()) {
            localHotspotManager?.connectToHotspot(
                ssid = qrData.ssid,
                passphrase = qrData.password,
                onConnected = {
                    _uiState.update { state ->
                        state.copy(lastSystemBannerToast = "CONNECTED to Squad Local Hotspot '${qrData.ssid}'!")
                    }
                    joinSquadGroup(qrData.squadCode)
                },
                onFailed = { reason ->
                    _uiState.update { state ->
                        state.copy(lastSystemBannerToast = "Hotspot Connect Failed: $reason. Joining Squad ${qrData.squadCode} via Cellular Cloud...")
                    }
                    joinSquadGroup(qrData.squadCode)
                }
            )
        } else {
            joinSquadGroup(qrData.squadCode)
        }
    }

    fun joinSquadGroup(joinCode: String) {
        val code = joinCode.ifBlank { "RV-8492" }.uppercase().trim()
        _uiState.update {
            it.copy(
                squadGroup = it.squadGroup.copy(
                    joinCode = code,
                    isActive = true,
                    isCurrentUserLeader = false,
                    activeChannel = IntercomChannel.SQUAD_MAIN
                ),
                currentTab = CockpitTab.SQUAD,
                lastSystemBannerToast = "CONNECTED TO SQUAD $code — Main Intercom Channel (CH-01) Active! Hold PTT to talk."
            )
        }
        nearbyMeshManager?.startJoiningGroup(code)
    }

    fun deactivateSquadGroup() {
        if (!_uiState.value.squadGroup.isCurrentUserLeader) {
            _uiState.update { it.copy(lastSystemBannerToast = "Only Squad LEAD can deactivate the squad group!") }
            return
        }
        _uiState.update {
            it.copy(
                squadGroup = it.squadGroup.copy(isActive = false),
                lastSystemBannerToast = "Squad Group Deactivated. Intercom Disconnected."
            )
        }
        nearbyMeshManager?.stopGroupSession()
    }

    fun assignRiderAsLead(targetCallsign: String) {
        if (!_uiState.value.squadGroup.isCurrentUserLeader) {
            _uiState.update { it.copy(lastSystemBannerToast = "Only current Squad LEAD can re-assign leadership!") }
            return
        }
        _uiState.update { state ->
            val updatedRiders = state.coRiders.map { rider ->
                if (rider.callsign == targetCallsign) {
                    rider.copy(role = RiderRole.ROAD_CAPTAIN)
                } else {
                    if (rider.role == RiderRole.ROAD_CAPTAIN) rider.copy(role = RiderRole.PACK_RIDER) else rider
                }
            }
            state.copy(
                squadGroup = state.squadGroup.copy(
                    isCurrentUserLeader = false,
                    createdByCallsign = targetCallsign
                ),
                coRiders = updatedRiders,
                lastSystemBannerToast = "Transferred Squad LEAD to $targetCallsign. You are now a MEMBER."
            )
        }
    }

    fun addCoRider(callsign: String, role: RiderRole) {
        if (!_uiState.value.squadGroup.isCurrentUserLeader) {
            _uiState.update { it.copy(lastSystemBannerToast = "Only Squad LEAD can add new riders!") }
            return
        }
        val newRider = CoRider(
            id = "rider-${System.currentTimeMillis()}",
            name = callsign,
            callsign = callsign.ifBlank { "NewRider" },
            role = role,
            location = GeoPointData(
                _uiState.value.telemetry.myLocation.lat + 0.001,
                _uiState.value.telemetry.myLocation.lon + 0.001
            ),
            speedKmh = 78,
            distanceMeters = 150,
            bearingDeg = 25f,
            isAhead = true,
            helmetDeviceName = "Cardo Spirit",
            helmetBatteryPct = 90
        )
        _uiState.update {
            it.copy(
                coRiders = it.coRiders + newRider,
                lastSystemBannerToast = "Added ${newRider.callsign} [${role.badge}] to Squad"
            )
        }
    }

    fun removeCoRider(callsign: String) {
        if (!_uiState.value.squadGroup.isCurrentUserLeader) {
            _uiState.update { it.copy(lastSystemBannerToast = "Only Squad LEAD can remove riders from squad!") }
            return
        }
        _uiState.update {
            it.copy(
                coRiders = it.coRiders.filter { r -> r.callsign != callsign },
                lastSystemBannerToast = "Removed $callsign from Squad"
            )
        }
    }

    fun toggleMuteCoRider(callsign: String) {
        _uiState.update { state ->
            val updated = state.coRiders.map { r ->
                if (r.callsign == callsign) r.copy(isMutedByMe = !r.isMutedByMe) else r
            }
            state.copy(coRiders = updated)
        }
    }

    fun toggleSoloWhisperCoRider(callsign: String) {
        _uiState.update { state ->
            val updated = state.coRiders.map { r ->
                if (r.callsign == callsign) r.copy(isSoloWhisper = !r.isSoloWhisper) else r
            }
            state.copy(coRiders = updated)
        }
    }

    // Hazard Reporting
    fun broadcastHazard(type: HazardType) {
        val myLoc = _uiState.value.telemetry.myLocation
        val newHazard = RoadHazard(
            id = "hz-${System.currentTimeMillis()}",
            type = type,
            location = myLoc,
            distanceMeters = 0,
            bearingDeg = 0f,
            reportedByCallsign = "You",
            reportedAgoSec = 0
        )
        _uiState.update {
            it.copy(
                hazards = listOf(newHazard) + it.hazards,
                lastSystemBannerToast = "REPORTED HAZARD: ${type.label} at current GPS!"
            )
        }
        _latestSpokenText.value = type.warningSpeech
    }

    fun triggerRegroupPing() {
        _uiState.update {
            it.copy(lastSystemBannerToast = "REGROUP PING DISPATCHED! Signalled Road Captain to slow pack.")
        }
    }

    // Place Search & Goal Sync
    fun searchPlaces(query: String, category: PlaceCategory) {
        _uiState.update { it.copy(selectedPlaceCategory = category, placeSearchQuery = query) }
        viewModelScope.launch {
            val near = _uiState.value.telemetry.myLocation
            val results = GeoMathAndFreeMapEngine.searchPlacesFree(query, category, near)
            _uiState.update { it.copy(placeResults = results) }
        }
    }

    fun setSharedGoalDestination(place: PlaceSearchResult) {
        if (!_uiState.value.squadGroup.isCurrentUserLeader) {
            _uiState.update { it.copy(lastSystemBannerToast = "Only Squad LEAD can lock/sync new shared pack destination!") }
            return
        }
        viewModelScope.launch {
            val start = _uiState.value.telemetry.myLocation
            val route = GeoMathAndFreeMapEngine.fetchRoutePolylineFree(start, place.location)
            val newGoal = SharedDestination(
                placeId = place.placeId,
                name = place.title,
                address = place.subtitle,
                category = place.category,
                targetPoint = place.location,
                routePolyline = route,
                distanceRemainingKm = place.distanceKm,
                etaMinutes = (place.distanceKm * 1.3f).toInt().coerceAtLeast(1),
                lockedByRider = "You (Road Captain)"
            )
            _uiState.update {
                it.copy(
                    sharedGoal = newGoal,
                    lastSystemBannerToast = "LOCKED GOAL: ${place.title}. Route synced across squad!"
                )
            }
        }
    }

    fun setSharedGoalLocation(point: GeoPointData) {
        val place = PlaceSearchResult(
            placeId = "goal-map-${System.currentTimeMillis()}",
            title = "Squad Destination Pin",
            subtitle = "Custom Goal set by Leader",
            category = PlaceCategory.VIEWPOINT,
            location = point,
            distanceKm = 0f
        )
        setSharedGoalDestination(place)
    }

    fun setSharedGoalByName(destinationName: String) {
        val existing = _uiState.value.placeResults.firstOrNull { it.title.contains(destinationName, ignoreCase = true) }
        if (existing != null) {
            setSharedGoalDestination(existing)
        } else {
            val fallbackPlace = PlaceSearchResult(
                placeId = "dest-custom-${destinationName.hashCode()}",
                title = destinationName,
                subtitle = "Jaipur Highway Corridor",
                category = PlaceCategory.VIEWPOINT,
                location = GeoPointData(26.9855, 75.8513),
                distanceKm = 5.2f
            )
            setSharedGoalDestination(fallbackPlace)
        }
    }

    // Voice AI & Copilot
    fun executeAiPrompt(promptText: String) {
        if (promptText.isBlank()) return

        val userMessage = CopilotMessage(
            id = "msg-${System.currentTimeMillis()}",
            sender = MessageSender.RIDER,
            text = promptText,
            timestampLabel = "Just now"
        )

        _uiState.update { it.copy(copilotMessages = it.copilotMessages + userMessage) }

        viewModelScope.launch {
            val executionResult = CopilotToolRegistry.parseAndExecutePrompt(promptText, _uiState.value)

            when (executionResult) {
                is CopilotToolRegistry.ToolExecutionResult.SearchPlaces -> {
                    val places = GeoMathAndFreeMapEngine.searchPlacesFree(
                        executionResult.query,
                        executionResult.category,
                        _uiState.value.telemetry.myLocation
                    )
                    val topPlace = places.firstOrNull()
                    val speakMessage = if (topPlace != null) {
                        "Nearest ${executionResult.query.lowercase()} station is ${topPlace.title}, ${topPlace.distanceKm} kilometers away on ${topPlace.subtitle}."
                    } else executionResult.replyText

                    _uiState.update { state ->
                        state.copy(
                            placeResults = places,
                            copilotMessages = state.copilotMessages + CopilotMessage(
                                id = "ai-${System.currentTimeMillis()}",
                                sender = MessageSender.AI_COPILOT,
                                text = speakMessage,
                                toolBadge = "SEARCH_PLACES",
                                suggestedPlaces = places.take(3),
                                timestampLabel = "Just now"
                            )
                        )
                    }
                    speakText(speakMessage)
                }
                is CopilotToolRegistry.ToolExecutionResult.SetGoal -> {
                    setSharedGoalByName(executionResult.destinationName)
                    _uiState.update { state ->
                        state.copy(
                            copilotMessages = state.copilotMessages + CopilotMessage(
                                id = "ai-${System.currentTimeMillis()}",
                                sender = MessageSender.AI_COPILOT,
                                text = executionResult.replyText,
                                toolBadge = "SET_SQUAD_GOAL",
                                timestampLabel = "Just now"
                            )
                        )
                    }
                }
                is CopilotToolRegistry.ToolExecutionResult.AddRider -> {
                    addCoRider(executionResult.callsign, executionResult.role)
                    _uiState.update { state ->
                        state.copy(
                            copilotMessages = state.copilotMessages + CopilotMessage(
                                id = "ai-${System.currentTimeMillis()}",
                                sender = MessageSender.AI_COPILOT,
                                text = executionResult.replyText,
                                toolBadge = "ADD_RIDER",
                                timestampLabel = "Just now"
                            )
                        )
                    }
                }
                is CopilotToolRegistry.ToolExecutionResult.RemoveRider -> {
                    removeCoRider(executionResult.callsign)
                    _uiState.update { state ->
                        state.copy(
                            copilotMessages = state.copilotMessages + CopilotMessage(
                                id = "ai-${System.currentTimeMillis()}",
                                sender = MessageSender.AI_COPILOT,
                                text = executionResult.replyText,
                                toolBadge = "REMOVE_RIDER",
                                timestampLabel = "Just now"
                            )
                        )
                    }
                }
                is CopilotToolRegistry.ToolExecutionResult.DeactivateGroup -> {
                    deactivateSquadGroup()
                    _uiState.update { state ->
                        state.copy(
                            copilotMessages = state.copilotMessages + CopilotMessage(
                                id = "ai-${System.currentTimeMillis()}",
                                sender = MessageSender.AI_COPILOT,
                                text = "Deactivated squad group session and unlatched WebRTC audio.",
                                toolBadge = "DEACTIVATE_GROUP",
                                timestampLabel = "Just now"
                            )
                        )
                    }
                }
                is CopilotToolRegistry.ToolExecutionResult.BroadcastHazard -> {
                    broadcastHazard(executionResult.type)
                    _uiState.update { state ->
                        state.copy(
                            copilotMessages = state.copilotMessages + CopilotMessage(
                                id = "ai-${System.currentTimeMillis()}",
                                sender = MessageSender.AI_COPILOT,
                                text = executionResult.replyText,
                                toolBadge = "ROAD_HAZARD",
                                timestampLabel = "Just now"
                            )
                        )
                    }
                }
                is CopilotToolRegistry.ToolExecutionResult.LaunchExternalNav -> {
                    val resultText = systemControlManager?.launchGoogleMapsNavigation(
                        executionResult.destinationName,
                        executionResult.lat,
                        executionResult.lon
                    ) ?: executionResult.replyText
                    _uiState.update { state ->
                        state.copy(
                            copilotMessages = state.copilotMessages + CopilotMessage(
                                id = "ai-${System.currentTimeMillis()}",
                                sender = MessageSender.AI_COPILOT,
                                text = resultText,
                                toolBadge = "EXTERNAL_NAV",
                                timestampLabel = "Just now"
                            )
                        )
                    }
                    speakText(resultText)
                }
                is CopilotToolRegistry.ToolExecutionResult.ControlMedia -> {
                    val resultText = systemControlManager?.controlMediaPlayback(executionResult.action) ?: executionResult.replyText
                    _uiState.update { state ->
                        state.copy(
                            copilotMessages = state.copilotMessages + CopilotMessage(
                                id = "ai-${System.currentTimeMillis()}",
                                sender = MessageSender.AI_COPILOT,
                                text = resultText,
                                toolBadge = "MEDIA_CONTROL",
                                timestampLabel = "Just now"
                            )
                        )
                    }
                    speakText(resultText)
                }
                is CopilotToolRegistry.ToolExecutionResult.AdjustVolume -> {
                    val resultText = systemControlManager?.adjustVolume(executionResult.increase, executionResult.isMax) ?: executionResult.replyText
                    _uiState.update { state ->
                        state.copy(
                            copilotMessages = state.copilotMessages + CopilotMessage(
                                id = "ai-${System.currentTimeMillis()}",
                                sender = MessageSender.AI_COPILOT,
                                text = resultText,
                                toolBadge = "VOLUME_CONTROL",
                                timestampLabel = "Just now"
                            )
                        )
                    }
                    speakText(resultText)
                }
                is CopilotToolRegistry.ToolExecutionResult.ToggleTorch -> {
                    val resultText = systemControlManager?.toggleFlashlight(executionResult.enable) ?: executionResult.replyText
                    _uiState.update { state ->
                        state.copy(
                            copilotMessages = state.copilotMessages + CopilotMessage(
                                id = "ai-${System.currentTimeMillis()}",
                                sender = MessageSender.AI_COPILOT,
                                text = resultText,
                                toolBadge = "TORCH_LIGHT",
                                timestampLabel = "Just now"
                            )
                        )
                    }
                    speakText(resultText)
                }
                is CopilotToolRegistry.ToolExecutionResult.MakeCall -> {
                    val resultText = if (executionResult.contactName.isNotBlank()) {
                        systemControlManager?.makePhoneCallByName(executionResult.contactName) ?: executionResult.replyText
                    } else {
                        systemControlManager?.makePhoneCall(executionResult.phoneNumber, executionResult.contactName) ?: executionResult.replyText
                    }
                    _uiState.update { state ->
                        state.copy(
                            copilotMessages = state.copilotMessages + CopilotMessage(
                                id = "ai-${System.currentTimeMillis()}",
                                sender = MessageSender.AI_COPILOT,
                                text = resultText,
                                toolBadge = "PHONE_CALL",
                                timestampLabel = "Just now"
                            )
                        )
                    }
                    speakText(resultText)
                }
                is CopilotToolRegistry.ToolExecutionResult.AnswerCall -> {
                    val resultText = systemControlManager?.answerIncomingCall() ?: executionResult.replyText
                    _uiState.update { state ->
                        state.copy(
                            copilotMessages = state.copilotMessages + CopilotMessage(
                                id = "ai-${System.currentTimeMillis()}",
                                sender = MessageSender.AI_COPILOT,
                                text = resultText,
                                toolBadge = "ANSWER_CALL",
                                timestampLabel = "Just now"
                            )
                        )
                    }
                    speakText(resultText)
                }
                is CopilotToolRegistry.ToolExecutionResult.DeclineCall -> {
                    val resultText = systemControlManager?.declineIncomingCall() ?: executionResult.replyText
                    _uiState.update { state ->
                        state.copy(
                            copilotMessages = state.copilotMessages + CopilotMessage(
                                id = "ai-${System.currentTimeMillis()}",
                                sender = MessageSender.AI_COPILOT,
                                text = resultText,
                                toolBadge = "DECLINE_CALL",
                                timestampLabel = "Just now"
                            )
                        )
                    }
                    speakText(resultText)
                }
                is CopilotToolRegistry.ToolExecutionResult.SendSMS -> {
                    val resultText = systemControlManager?.sendSms(executionResult.phoneNumber, executionResult.messageText) ?: executionResult.replyText
                    _uiState.update { state ->
                        state.copy(
                            copilotMessages = state.copilotMessages + CopilotMessage(
                                id = "ai-${System.currentTimeMillis()}",
                                sender = MessageSender.AI_COPILOT,
                                text = resultText,
                                toolBadge = "SEND_SMS",
                                timestampLabel = "Just now"
                            )
                        )
                    }
                    speakText(resultText)
                }
                is CopilotToolRegistry.ToolExecutionResult.InfoResponse -> {
                    _uiState.update { state ->
                        state.copy(
                            copilotMessages = state.copilotMessages + CopilotMessage(
                                id = "ai-${System.currentTimeMillis()}",
                                sender = MessageSender.AI_COPILOT,
                                text = executionResult.replyText,
                                toolBadge = executionResult.toolBadge,
                                timestampLabel = "Just now"
                            )
                        )
                    }
                }
            }
            _latestSpokenText.value = executionResult.replyText
        }
    }

    fun toggleQuickAiSheet(open: Boolean) {
        _uiState.update { it.copy(isQuickAiSheetOpen = open) }
    }

    fun updateVoiceListeningState(isListening: Boolean, partialTranscript: String) {
        _uiState.update {
            it.copy(
                isVoiceListening = isListening,
                voicePartialTranscript = partialTranscript
            )
        }
    }

    fun setSpeechRecognitionAvailable(available: Boolean) {
        _uiState.update { it.copy(isSpeechRecognitionAvailable = available) }
    }

    fun toggleMapRadarMode(radarMode: Boolean) {
        _uiState.update { it.copy(isMapInRadarMode = radarMode) }
    }

    // Bluetooth Telemetry & Audio Settings
    fun updateHeadsetBatteryFromHardware(headsetState: BluetoothHeadsetState) {
        _uiState.update { it.copy(headset = headsetState) }
    }

    fun setSimulatedHeadsetBattery(batteryPct: Int) {
        _uiState.update {
            it.copy(headset = it.headset.copy(batteryPct = batteryPct, hardwareSource = "Manual Telemetry Test Slider"))
        }
    }

    fun setWindSuppressionStage(stage: Int) {
        _uiState.update { it.copy(headset = it.headset.copy(windSuppressionStage = stage)) }
    }

    fun setVolumeLevels(intercom: Float, aiNav: Float, musicDucking: Float) {
        _uiState.update {
            it.copy(
                headset = it.headset.copy(
                    intercomVolume = intercom,
                    aiNavVolume = aiNav,
                    musicDuckingPct = musicDucking
                )
            )
        }
    }

    // Crash SOS
    fun triggerCrashSosTest() {
        startCrashSosCountdown(4.8f)
    }

    private fun startCrashSosCountdown(gForce: Float) {
        crashCountdownJob?.cancel()
        val loc = _uiState.value.telemetry.myLocation
        _uiState.update {
            it.copy(
                crashSos = CrashSosState(
                    isTriggered = true,
                    secondsRemaining = 10,
                    impactGForce = gForce,
                    broadcastDispatched = false
                )
            )
        }
        crashCountdownJob = viewModelScope.launch {
            for (sec in 10 downTo 0) {
                delay(1000)
                if (!_uiState.value.crashSos.isTriggered) break
                _uiState.update { state ->
                    val isDone = (sec == 0)
                    val locStr = "Lat: ${String.format("%.4f", loc.lat)}, Lon: ${String.format("%.4f", loc.lon)}"
                    val toastMsg = if (isDone) "🚨 SOS DISPATCHED to Squad Mesh! Last Location: $locStr" else null
                    state.copy(
                        crashSos = state.crashSos.copy(
                            secondsRemaining = sec,
                            broadcastDispatched = isDone
                        ),
                        lastSystemBannerToast = toastMsg ?: state.lastSystemBannerToast
                    )
                }
            }
        }
    }

    fun cancelCrashSos() {
        crashCountdownJob?.cancel()
        _uiState.update {
            it.copy(
                crashSos = CrashSosState(isTriggered = false),
                lastSystemBannerToast = "CRASH SOS CANCELLED — Rider confirmed OK."
            )
        }
    }

    fun clearToastBanner() {
        _uiState.update { it.copy(lastSystemBannerToast = null) }
    }

    fun setThemeMode(mode: com.ridervoice.cockpit.model.AppThemeMode) {
        _uiState.update {
            it.copy(
                themeMode = mode,
                lastSystemBannerToast = "COCKPIT THEME: ${mode.label}"
            )
        }
    }

    fun toggleThemeMode() {
        _uiState.update { state ->
            val nextMode = if (state.themeMode == com.ridervoice.cockpit.model.AppThemeMode.DARK) {
                com.ridervoice.cockpit.model.AppThemeMode.LIGHT
            } else {
                com.ridervoice.cockpit.model.AppThemeMode.DARK
            }
            state.copy(
                themeMode = nextMode,
                lastSystemBannerToast = "COCKPIT THEME: ${nextMode.label}"
            )
        }
    }

    fun updateRealHardwareTelemetry(
        speedKmh: Int,
        altitudeMeters: Int,
        location: GeoPointData,
        headingDeg: Int,
        leanAngleDeg: Float,
        peakLeft: Float,
        peakRight: Float
    ) {
        _uiState.update { state ->
            val currentPeakLeft = maxOf(state.telemetry.peakLeanLeftDeg, peakLeft)
            val currentPeakRight = maxOf(state.telemetry.peakLeanRightDeg, peakRight)
            val smoothedSpeed = if (state.telemetry.speedKmh == 0) speedKmh else ((state.telemetry.speedKmh * 0.8f) + (speedKmh * 0.2f)).toInt()
            state.copy(
                telemetry = state.telemetry.copy(
                    speedKmh = smoothedSpeed,
                    altitudeMeters = altitudeMeters,
                    myLocation = location,
                    compassHeadingDeg = headingDeg,
                    leanAngleDeg = leanAngleDeg,
                    peakLeanLeftDeg = currentPeakLeft,
                    peakLeanRightDeg = currentPeakRight
                )
            )
        }
    }

    fun triggerCrashSosFromImpact(gForce: Float) {
        startCrashSosCountdown(gForce)
    }
}
