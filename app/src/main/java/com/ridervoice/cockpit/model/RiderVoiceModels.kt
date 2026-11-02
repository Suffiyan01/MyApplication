package com.ridervoice.cockpit.model

enum class CockpitTab(val label: String) {
    HUD("HUD"),
    SQUAD("Squad"),
    MAP_RADAR("Live Map"),
    AI_PLACES("AI & Places"),
    AUDIO_SOS("Helmet & SOS")
}

enum class RiderRole(val badge: String, val colorHex: Long) {
    ROAD_CAPTAIN("LEAD", 0xFF06B6D4),
    PACK_RIDER("PACK", 0xFF10B981),
    TAIL_GUNNER("SWEEP", 0xFFF59E0B)
}

enum class IntercomChannel(val title: String, val freqLabel: String) {
    SQUAD_MAIN("Squad Open Mesh", "CH-01 • FULL PACK"),
    LEAD_WHISPER("Road Captain Direct", "CH-02 • LEAD ONLY"),
    OFFGRID_P2P("Nearby P2P Relay", "CH-03 • MESH HOP"),
    EMERGENCY_19("Highway SOS Channel", "CH-19 • DISTRESS")
}

enum class ConnectionType(val badge: String, val isLocal: Boolean) {
    LOCAL_MESH("P2P MESH", true),
    CELLULAR_CLOUD("CELLULAR 4G", false),
    OFFLINE("OFFLINE", false)
}

enum class TransportMode(val label: String, val subLabel: String) {
    HYBRID_MESH_CELLULAR("Hybrid Mesh + Cellular", "Local Hotspot P2P + 4G Fallback"),
    WEBRTC_CLOUD("LiveKit Cloud WebRTC", "4G/5G Opus Wideband • Unlimited Range"),
    NEARBY_MESH("Nearby P2P Cluster", "Wi-Fi Direct / BLE Mesh • Off-Grid Relay")
}

enum class AudioCodec(val label: String, val bitrateKbps: Int) {
    BLE_LC3("BLE Audio LC3 Ultra-Low Latency", 32),
    HFP_MSBC("Bluetooth HFP Wideband mSBC", 24),
    A2DP_AAC("A2DP + SCO Hybrid", 64)
}

enum class PlaceCategory(val label: String, val osmTag: String) {
    ALL("All Stops", "amenity"),
    FUEL("Fuel & EV", "fuel"),
    BIKER_CAFE("Biker Cafe", "cafe"),
    MECHANIC("Tyre & Repair", "motorcycle_repair"),
    VIEWPOINT("Scenic Point", "viewpoint"),
    MEDICAL("First Aid / Hospital", "hospital")
}

enum class HazardType(val label: String, val warningSpeech: String, val colorHex: Long) {
    POTHOLE("Deep Pothole", "Caution: Deep pothole reported ahead", 0xFFF59E0B),
    SPEED_TRAP("Radar / Speed Trap", "Heads up: Speed camera reported ahead", 0xFF06B6D4),
    OIL_GRAVEL("Oil / Loose Gravel", "Danger: Slippery gravel or oil in lane", 0xFFF43F5E),
    ROADBLOCK("Lane Blocked", "Alert: Roadblock or stalled vehicle ahead", 0xFFF43F5E)
}

enum class MessageSender { RIDER, AI_COPILOT, SYSTEM_TOOL }

data class GeoPointData(
    val lat: Double = 26.9124,
    val lon: Double = 75.7873
)

data class TelemetryState(
    val speedKmh: Int = 0,
    val speedLimitKmh: Int = 100,
    val rpm: Int = 0,
    val gear: Int = 0,
    val leanAngleDeg: Float = 0f,
    val peakLeanLeftDeg: Float = 0f,
    val peakLeanRightDeg: Float = 0f,
    val compassHeadingDeg: Int = 0,
    val compassCardinal: String = "N",
    val altitudeMeters: Int = 0,
    val tripDistanceKm: Float = 0.0f,
    val tripDurationMinutes: Int = 0,
    val myLocation: GeoPointData = GeoPointData(26.9124, 75.7873)
)

data class SquadGroupState(
    val groupId: String = "GRP-JAIPUR-01",
    val groupName: String = "Aravalli Dawn Canyon Squad",
    val joinCode: String = "RV-8492",
    val isActive: Boolean = true,
    val isCurrentUserLeader: Boolean = true,
    val createdByCallsign: String = "You (Captain)",
    val transportMode: TransportMode = TransportMode.HYBRID_MESH_CELLULAR,
    val hotspotSsid: String? = "RiderSquad-Hotspot",
    val hotspotPassword: String? = "RiderMeshPass123",
    val meshHopCount: Int = 2,
    val activeChannel: IntercomChannel = IntercomChannel.SQUAD_MAIN
)

data class CoRider(
    val id: String = "",
    val name: String = "",
    val callsign: String = "",
    val role: RiderRole = RiderRole.PACK_RIDER,
    val location: GeoPointData = GeoPointData(),
    val speedKmh: Int = 76,
    val headingDeg: Float = 42f,
    val distanceMeters: Int = 180,
    val bearingDeg: Float = 15f,
    val isAhead: Boolean = true,
    val isSpeaking: Boolean = false,
    val isMutedByMe: Boolean = false,
    val isSoloWhisper: Boolean = false,
    val helmetDeviceName: String = "Cardo Packtalk Edge",
    val helmetBatteryPct: Int = 82,
    val signalBars: Int = 4,
    val isInternetRelay: Boolean = false,
    val connectionType: ConnectionType = ConnectionType.LOCAL_MESH,
    val connectionLabel: String = "P2P Mesh"
)

data class BluetoothHeadsetState(
    val isConnected: Boolean = true,
    val deviceName: String = "Cardo Packtalk Edge",
    val macAddress: String = "A4:C1:38:9F:12:0E",
    val batteryPct: Int = 84,
    val isCharging: Boolean = false,
    val codec: AudioCodec = AudioCodec.BLE_LC3,
    val estimatedTalkHoursLeft: Float = 10.5f,
    val hardwareSource: String = "BLE GATT 0x180F + HFP Broadcast",
    val windSuppressionStage: Int = 2, // 0=Off, 1=Low, 2=Highway, 3=Extreme Storm
    val intercomVolume: Float = 0.90f,
    val aiNavVolume: Float = 0.85f,
    val musicDuckingPct: Float = 0.70f
)

data class IntercomState(
    val isPttLatched: Boolean = false,
    val isPttHeld: Boolean = false,
    val isVoxEnabled: Boolean = true,
    val voxThresholdDb: Float = -28f,
    val currentInputGainDb: Float = -42f,
    val activeSpeakerCallsign: String? = "Aarav [LEAD]"
) {
    val isTransmitting: Boolean get() = isPttLatched || isPttHeld
}

data class SharedDestination(
    val placeId: String = "dest-amber-fort",
    val name: String = "Amber Fort Viewpoint & Riders Point",
    val address: String = "Devisinghpura, Amer, Jaipur, NH-48",
    val category: PlaceCategory = PlaceCategory.VIEWPOINT,
    val targetPoint: GeoPointData = GeoPointData(26.9855, 75.8513),
    val routePolyline: List<GeoPointData> = emptyList(),
    val distanceRemainingKm: Float = 6.4f,
    val etaMinutes: Int = 9,
    val lockedByRider: String = "You (Road Captain)",
    val nextManeuverInstruction: String = "In 650m, Keep Left onto Amer Fort Ghat Road",
    val nextManeuverIcon: String = "KEEP_LEFT",
    val isSyncedWithPack: Boolean = true
)

data class PlaceSearchResult(
    val placeId: String = "",
    val title: String = "",
    val subtitle: String = "",
    val category: PlaceCategory = PlaceCategory.FUEL,
    val location: GeoPointData = GeoPointData(),
    val distanceKm: Float = 2.4f,
    val detourMinutes: Int = 2,
    val isOpenNow: Boolean = true,
    val rating: Float = 4.7f
)

data class RoadHazard(
    val id: String = "",
    val type: HazardType = HazardType.POTHOLE,
    val location: GeoPointData = GeoPointData(),
    val distanceMeters: Int = 320,
    val bearingDeg: Float = 12f,
    val reportedByCallsign: String = "Vikram",
    val reportedAgoSec: Int = 25
)

data class CopilotMessage(
    val id: String = "",
    val sender: MessageSender = MessageSender.AI_COPILOT,
    val text: String = "",
    val toolBadge: String? = null,
    val suggestedPlaces: List<PlaceSearchResult> = emptyList(),
    val timestampLabel: String = "Just now"
)

data class CrashSosState(
    val isTriggered: Boolean = false,
    val secondsRemaining: Int = 10,
    val impactGForce: Float = 4.8f,
    val broadcastDispatched: Boolean = false
)

enum class AppThemeMode(val label: String, val isDark: Boolean) {
    DARK("Tactical Dark (Night)", true),
    LIGHT("Daylight High-Contrast", false)
}

data class RiderVoiceUiState(
    val currentTab: CockpitTab = CockpitTab.HUD,
    val themeMode: AppThemeMode = AppThemeMode.DARK,
    val telemetry: TelemetryState = TelemetryState(),
    val squadGroup: SquadGroupState = SquadGroupState(),
    val coRiders: List<CoRider> = emptyList(),
    val headset: BluetoothHeadsetState = BluetoothHeadsetState(),
    val intercom: IntercomState = IntercomState(),
    val sharedGoal: SharedDestination? = SharedDestination(),
    val placeResults: List<PlaceSearchResult> = emptyList(),
    val selectedPlaceCategory: PlaceCategory = PlaceCategory.ALL,
    val placeSearchQuery: String = "",
    val hazards: List<RoadHazard> = emptyList(),
    val copilotMessages: List<CopilotMessage> = emptyList(),
    val isQuickAiSheetOpen: Boolean = false,
    val isMapInRadarMode: Boolean = false,
    val crashSos: CrashSosState = CrashSosState(),
    val lastSystemBannerToast: String? = null,
    val isVoiceListening: Boolean = false,
    val voicePartialTranscript: String = "",
    val isSpeechRecognitionAvailable: Boolean = true
)
