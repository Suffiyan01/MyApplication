package com.ridervoice.cockpit.di

import android.content.Context
import com.ridervoice.cockpit.domain.BluetoothHelmetMonitor
import com.ridervoice.cockpit.domain.ContactsRepository
import com.ridervoice.cockpit.domain.HybridSquadRouter
import com.ridervoice.cockpit.domain.LocalHotspotManager
import com.ridervoice.cockpit.domain.LocationRepository
import com.ridervoice.cockpit.domain.NearbySquadMeshManager
import com.ridervoice.cockpit.domain.RealRideSensorManager
import com.ridervoice.cockpit.domain.SystemControlManager
import com.ridervoice.cockpit.domain.TelephonyCallManager
import com.ridervoice.cockpit.domain.VoiceCopilotAudioController
import com.ridervoice.cockpit.model.GeoPointData

interface AppContainer {
    val locationRepository: LocationRepository
    val localHotspotManager: LocalHotspotManager
    val nearbyMeshManager: NearbySquadMeshManager
    val hybridSquadRouter: HybridSquadRouter
    val contactsRepository: ContactsRepository
    val systemControlManager: SystemControlManager
    val telephonyCallManager: (onIncomingCallAnnounced: (String, String) -> Unit) -> TelephonyCallManager
    val bluetoothHelmetMonitor: (onUpdate: (com.ridervoice.cockpit.model.BluetoothHeadsetState) -> Unit) -> BluetoothHelmetMonitor
    val realSensorManager: (
        onTelemetryUpdate: (Int, Int, GeoPointData, Int, Float, Float, Float) -> Unit,
        onHighGImpactDetected: (Float) -> Unit
    ) -> RealRideSensorManager
    val voiceAudioController: (onSpokenCommand: (String) -> Unit) -> VoiceCopilotAudioController
}

class AppContainerImpl(private val context: Context) : AppContainer {

    override val locationRepository: LocationRepository by lazy {
        LocationRepository(context)
    }

    override val localHotspotManager: LocalHotspotManager by lazy {
        LocalHotspotManager(context)
    }

    override val nearbyMeshManager: NearbySquadMeshManager by lazy {
        NearbySquadMeshManager(context, {}, {}, { _, _ -> })
    }

    override val hybridSquadRouter: HybridSquadRouter by lazy {
        HybridSquadRouter(context, nearbyMeshManager) { _, _, _ -> }
    }

    override val contactsRepository: ContactsRepository by lazy {
        ContactsRepository(context)
    }

    override val systemControlManager: SystemControlManager by lazy {
        SystemControlManager(context, contactsRepository)
    }

    override val telephonyCallManager: (onIncomingCallAnnounced: (String, String) -> Unit) -> TelephonyCallManager = { onAnnounce ->
        TelephonyCallManager(context, contactsRepository, onAnnounce)
    }

    override val bluetoothHelmetMonitor: (onUpdate: (com.ridervoice.cockpit.model.BluetoothHeadsetState) -> Unit) -> BluetoothHelmetMonitor = { onUpdate ->
        BluetoothHelmetMonitor(context, onUpdate)
    }

    override val realSensorManager: (
        onTelemetryUpdate: (Int, Int, GeoPointData, Int, Float, Float, Float) -> Unit,
        onHighGImpactDetected: (Float) -> Unit
    ) -> RealRideSensorManager = { onTelemetryUpdate, onHighGImpactDetected ->
        RealRideSensorManager(context, onTelemetryUpdate, onHighGImpactDetected)
    }

    override val voiceAudioController: (onSpokenCommand: (String) -> Unit) -> VoiceCopilotAudioController = { onSpokenCommand ->
        VoiceCopilotAudioController(context, onSpokenCommand)
    }
}
