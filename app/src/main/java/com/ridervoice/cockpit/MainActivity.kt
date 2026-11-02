package com.ridervoice.cockpit

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.ridervoice.cockpit.domain.BluetoothHelmetMonitor
import com.ridervoice.cockpit.domain.HybridSquadRouter
import com.ridervoice.cockpit.domain.LocalHotspotManager
import com.ridervoice.cockpit.domain.LocationRepository
import com.ridervoice.cockpit.domain.NearbySquadMeshManager
import com.ridervoice.cockpit.domain.RealRideSensorManager
import com.ridervoice.cockpit.domain.VoiceCopilotAudioController
import com.ridervoice.cockpit.service.RideTrackingService
import com.ridervoice.cockpit.model.CockpitTab
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import com.ridervoice.cockpit.ui.components.BottomGloveNavBar
import com.ridervoice.cockpit.ui.components.CrashSosModal
import com.ridervoice.cockpit.ui.components.QuickAiOverlaySheet
import com.ridervoice.cockpit.ui.components.TopPersistentStatusBar
import com.ridervoice.cockpit.ui.screens.AiCopilotPlacesScreen
import com.ridervoice.cockpit.ui.screens.AudioDspSettingsScreen
import com.ridervoice.cockpit.ui.screens.CockpitHudScreen
import com.ridervoice.cockpit.ui.screens.IntercomSquadScreen
import com.ridervoice.cockpit.ui.screens.RadarMapNavigationScreen
import com.ridervoice.cockpit.ui.theme.RiderVoiceTheme
import com.ridervoice.cockpit.viewmodel.RiderVoiceViewModel

class MainActivity : ComponentActivity() {

    private val container by lazy { (application as RiderVoiceApplication).container }
    private val viewModel: RiderVoiceViewModel by viewModels {
        RiderVoiceViewModel.Factory(container)
    }

    private lateinit var helmetMonitor: BluetoothHelmetMonitor
    private lateinit var realRideSensorManager: RealRideSensorManager
    private lateinit var voiceAudioController: VoiceCopilotAudioController

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            RideTrackingService.startService(this)
            startLocationUpdates()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enableEdgeToEdge()

        requestRequiredPermissionsSafely()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            RideTrackingService.startService(this)
            startLocationUpdates()
        }

        helmetMonitor = container.bluetoothHelmetMonitor { updatedHeadset ->
            viewModel.updateHeadsetBatteryFromHardware(updatedHeadset)
        }

        realRideSensorManager = container.realSensorManager(
            { speed, alt, loc, heading, lean, peakL, peakR ->
                viewModel.updateRealHardwareTelemetry(speed, alt, loc, heading, lean, peakL, peakR)
            },
            { gForce ->
                viewModel.triggerCrashSosFromImpact(gForce)
            }
        )
        viewModel.setRealRideSensorManager(realRideSensorManager)

        voiceAudioController = container.voiceAudioController { spokenCommand ->
            viewModel.executeAiPrompt(spokenCommand)
        }
        viewModel.setSpeechRecognitionAvailable(voiceAudioController.isSpeechAvailable())
        viewModel.setNearbyMeshManager(container.nearbyMeshManager)
        viewModel.setLocalHotspotManager(container.localHotspotManager)
        viewModel.setHybridSquadRouter(container.hybridSquadRouter)
        viewModel.setSystemControlManager(container.systemControlManager)

        val telephonyCallManager = container.telephonyCallManager { callerName, number ->
            viewModel.speakText("Incoming call from $callerName. Say Answer or Decline.")
        }

        val whatsappReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == com.ridervoice.cockpit.service.RiderNotificationListenerService.ACTION_WHATSAPP_NOTIFICATION) {
                    val sender = intent.getStringExtra(com.ridervoice.cockpit.service.RiderNotificationListenerService.EXTRA_SENDER) ?: "Someone"
                    val message = intent.getStringExtra(com.ridervoice.cockpit.service.RiderNotificationListenerService.EXTRA_MESSAGE) ?: ""
                    viewModel.speakText("WhatsApp from $sender: $message")
                }
            }
        }
        val filter = IntentFilter(com.ridervoice.cockpit.service.RiderNotificationListenerService.ACTION_WHATSAPP_NOTIFICATION)
        ContextCompat.registerReceiver(this, whatsappReceiver, filter, ContextCompat.RECEIVER_EXPORTED)

        handleSquadIntents(intent)

        setContent {
            val uiState by viewModel.uiState.collectAsState()
            RiderVoiceTheme(themeMode = uiState.themeMode) {
                val spokenText by viewModel.latestSpokenText.collectAsState()
                val isListening by voiceAudioController.isListening.collectAsState()
                val partialTranscript by voiceAudioController.partialTranscript.collectAsState()

                LaunchedEffect(isListening, partialTranscript) {
                    viewModel.updateVoiceListeningState(isListening, partialTranscript)
                }

                LaunchedEffect(spokenText) {
                    spokenText?.let { text ->
                        voiceAudioController.speak(text, viewModel.uiState.value.headset.aiNavVolume)
                        viewModel.clearSpokenText()
                    }
                }

                DisposableEffect(Unit) {
                    helmetMonitor.startMonitoring()
                    realRideSensorManager.startListening()
                    telephonyCallManager.startListening()
                    voiceAudioController.startContinuousHotword()
                    onDispose {
                        helmetMonitor.stopMonitoring()
                        realRideSensorManager.stopListening()
                        telephonyCallManager.stopListening()
                        voiceAudioController.stopContinuousHotword()
                        voiceAudioController.destroy()
                        container.nearbyMeshManager.stopGroupSession()
                        container.localHotspotManager.stopHotspot()
                        container.localHotspotManager.disconnectFromHotspot()
                    }
                }

                RiderVoiceAppShell(
                    viewModel = viewModel,
                    onStartVoiceListening = { voiceAudioController.startListening() },
                    onStopVoiceListening = { voiceAudioController.stopListening() }
                )
            }
        }
    }

    private fun requestRequiredPermissionsSafely() {
        val permissionsToRequest = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.CALL_PHONE
        )

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            permissionsToRequest.add(Manifest.permission.ANSWER_PHONE_CALLS)
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            permissionsToRequest.add(Manifest.permission.BLUETOOTH_CONNECT)
            permissionsToRequest.add(Manifest.permission.BLUETOOTH_SCAN)
            permissionsToRequest.add(Manifest.permission.BLUETOOTH_ADVERTISE)
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            permissionsToRequest.add(Manifest.permission.NEARBY_WIFI_DEVICES)
            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val ungranted = permissionsToRequest.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (ungranted.isNotEmpty()) {
            try {
                permissionLauncher.launch(ungranted.toTypedArray())
            } catch (e: SecurityException) {
                // Defensive fallback to live simulation
            }
        }
    }

    private fun startLocationUpdates() {
        lifecycleScope.launch {
            container.locationRepository.getLocationFlow()
                .catch { /* Permission denied or error handled gracefully */ }
                .collect { location ->
                    viewModel.updateRealGpsLocation(location)
                }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleSquadIntents(intent)
    }

    private fun handleSquadIntents(intent: android.content.Intent) {
        val selectTabParam = intent.getStringExtra("select_tab")
        val createSquadParam = intent.getStringExtra("create_squad")
        val joinCodeParam = intent.getStringExtra("join_code")
        val toggleThemeParam = intent.getStringExtra("toggle_theme")

        if (!selectTabParam.isNullOrBlank()) {
            when (selectTabParam.uppercase()) {
                "SQUAD" -> viewModel.selectTab(CockpitTab.SQUAD)
                "MAP" -> viewModel.selectTab(CockpitTab.MAP_RADAR)
                "PLACES" -> viewModel.selectTab(CockpitTab.AI_PLACES)
                "AUDIO", "SETTINGS" -> viewModel.selectTab(CockpitTab.AUDIO_SOS)
                else -> viewModel.selectTab(CockpitTab.HUD)
            }
            intent.removeExtra("create_squad")
            intent.removeExtra("join_code")
            intent.removeExtra("select_tab")
        } else if (!createSquadParam.isNullOrBlank()) {
            viewModel.createSquadGroup(createSquadParam)
            viewModel.selectTab(CockpitTab.SQUAD)
            intent.removeExtra("create_squad")
        } else if (!joinCodeParam.isNullOrBlank()) {
            viewModel.joinSquadGroup(joinCodeParam)
            viewModel.selectTab(CockpitTab.SQUAD)
            intent.removeExtra("join_code")
        }

        if (toggleThemeParam == "true") {
            viewModel.toggleThemeMode()
            intent.removeExtra("toggle_theme")
        }
    }
}

@Composable
fun RiderVoiceAppShell(
    viewModel: RiderVoiceViewModel,
    onStartVoiceListening: () -> Unit = {},
    onStopVoiceListening: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()

    // Handle Toast Banners
    LaunchedEffect(uiState.lastSystemBannerToast) {
        uiState.lastSystemBannerToast?.let { toastText ->
            // System toast banner for quick rider feedback
            viewModel.clearToastBanner()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopPersistentStatusBar(
                headset = uiState.headset,
                intercom = uiState.intercom,
                squadGroup = uiState.squadGroup,
                coRiderCount = uiState.coRiders.size,
                themeMode = uiState.themeMode,
                onToggleThemeMode = { viewModel.toggleThemeMode() },
                onTabSelect = { viewModel.selectTab(it) }
            )
        },
        bottomBar = {
            BottomGloveNavBar(
                currentTab = uiState.currentTab,
                onTabSelect = { viewModel.selectTab(it) },
                onSosTrigger = { viewModel.triggerCrashSosTest() }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (uiState.currentTab) {
                CockpitTab.HUD -> {
                    CockpitHudScreen(
                        uiState = uiState,
                        onTabSelect = { viewModel.selectTab(it) },
                        onTogglePttLatch = { viewModel.togglePttLatch() },
                        onOpenAiSheet = {
                            onStartVoiceListening()
                            viewModel.toggleQuickAiSheet(true)
                        },
                        onBroadcastHazard = { viewModel.broadcastHazard(it) },
                        onTriggerRegroupPing = { viewModel.triggerRegroupPing() }
                    )
                }

                CockpitTab.SQUAD -> {
                    IntercomSquadScreen(
                        uiState = uiState,
                        onTogglePttLatch = { viewModel.togglePttLatch() },
                        onSetPttHeld = { viewModel.setPttHeld(it) },
                        onToggleVox = { viewModel.toggleVox(it) },
                        onSetVoxThreshold = { viewModel.setVoxThreshold(it) },
                        onSelectChannel = { viewModel.selectIntercomChannel(it) },
                        onCreateGroup = { viewModel.createSquadGroup(it) },
                        onJoinGroup = { viewModel.joinSquadGroup(it) },
                        onDeactivateGroup = { viewModel.deactivateSquadGroup() },
                        onAddCoRider = { callsign, role -> viewModel.addCoRider(callsign, role) },
                        onRemoveCoRider = { viewModel.removeCoRider(it) },
                        onAssignLead = { viewModel.assignRiderAsLead(it) },
                        onToggleMuteCoRider = { viewModel.toggleMuteCoRider(it) },
                        onToggleSoloWhisperCoRider = { viewModel.toggleSoloWhisperCoRider(it) }
                    )
                }

                CockpitTab.MAP_RADAR -> {
                    RadarMapNavigationScreen(
                        uiState = uiState,
                        onToggleRadarMode = { viewModel.toggleMapRadarMode(it) },
                        onBroadcastHazard = { viewModel.broadcastHazard(it) },
                        onSetSharedGoalLocation = { viewModel.setSharedGoalLocation(it) }
                    )
                }

                CockpitTab.AI_PLACES -> {
                    AiCopilotPlacesScreen(
                        uiState = uiState,
                        onExecutePrompt = { viewModel.executeAiPrompt(it) },
                        onSearchPlaces = { query, category -> viewModel.searchPlaces(query, category) },
                        onSetSharedGoal = { viewModel.setSharedGoalDestination(it) },
                        onStartVoiceListening = onStartVoiceListening,
                        onStopVoiceListening = onStopVoiceListening
                    )
                }

                CockpitTab.AUDIO_SOS -> {
                    AudioDspSettingsScreen(
                        uiState = uiState,
                        onSetWindStage = { viewModel.setWindSuppressionStage(it) },
                        onSetVolumeLevels = { intercom, ai, music -> viewModel.setVolumeLevels(intercom, ai, music) },
                        onSetThemeMode = { viewModel.setThemeMode(it) },
                        onTriggerCrashSosTest = { viewModel.triggerCrashSosTest() }
                    )
                }
            }

            // Quick AI Overlay Bottom Sheet
            if (uiState.isQuickAiSheetOpen) {
                QuickAiOverlaySheet(
                    isListening = uiState.isVoiceListening,
                    partialTranscript = uiState.voicePartialTranscript,
                    onStartListening = onStartVoiceListening,
                    onStopListening = onStopVoiceListening,
                    onDismiss = { viewModel.toggleQuickAiSheet(false) },
                    onSendPrompt = { viewModel.executeAiPrompt(it) }
                )
            }

            // Crash SOS Modal
            CrashSosModal(
                crashSos = uiState.crashSos,
                onCancelSos = { viewModel.cancelCrashSos() }
            )
        }
    }
}
