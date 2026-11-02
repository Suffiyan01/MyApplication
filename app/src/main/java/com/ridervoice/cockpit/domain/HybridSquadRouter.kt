package com.ridervoice.cockpit.domain

import android.content.Context
import android.util.Log
import com.ridervoice.cockpit.model.CoRider
import com.ridervoice.cockpit.model.ConnectionType
import com.ridervoice.cockpit.model.GeoPointData
import com.ridervoice.cockpit.model.RiderRole
import com.ridervoice.cockpit.model.RoadHazard
import com.ridervoice.cockpit.model.SharedDestination
import java.util.concurrent.ConcurrentHashMap

class HybridSquadRouter(
    private val context: Context,
    private val localMeshManager: NearbySquadMeshManager?,
    private val onPeerConnectionTypeChanged: (peerId: String, connectionType: ConnectionType, connectionLabel: String) -> Unit
) {

    companion object {
        private const val TAG = "HybridSquadRouter"
        private const val LOCAL_MESH_TIMEOUT_MS = 5000L
    }

    private val peerLastSeenLocalMesh = ConcurrentHashMap<String, Long>()
    private val peerConnectionTypes = ConcurrentHashMap<String, ConnectionType>()

    fun recordLocalMeshHeartbeat(peerId: String) {
        val now = System.currentTimeMillis()
        peerLastSeenLocalMesh[peerId] = now

        val previousType = peerConnectionTypes[peerId]
        if (previousType != ConnectionType.LOCAL_MESH) {
            peerConnectionTypes[peerId] = ConnectionType.LOCAL_MESH
            Log.d(TAG, "Peer $peerId switched to LOCAL_MESH")
            onPeerConnectionTypeChanged(peerId, ConnectionType.LOCAL_MESH, "P2P Mesh")
        }
    }

    fun evaluatePeerConnections() {
        val now = System.currentTimeMillis()
        peerLastSeenLocalMesh.forEach { (peerId, lastSeen) ->
            val elapsed = now - lastSeen
            val currentType = peerConnectionTypes[peerId] ?: ConnectionType.LOCAL_MESH

            if (elapsed > LOCAL_MESH_TIMEOUT_MS && currentType == ConnectionType.LOCAL_MESH) {
                // Out of local Wi-Fi range -> Failover to Cellular Cloud
                peerConnectionTypes[peerId] = ConnectionType.CELLULAR_CLOUD
                Log.d(TAG, "Peer $peerId out of local range. Failover to CELLULAR_CLOUD")
                onPeerConnectionTypeChanged(peerId, ConnectionType.CELLULAR_CLOUD, "Cellular 4G")
            }
        }
    }

    fun broadcastTelemetry(
        myCallsign: String,
        role: RiderRole,
        location: GeoPointData,
        speedKmh: Int,
        headingDeg: Float,
        batteryPct: Int,
        isSpeaking: Boolean,
        sharedGoal: SharedDestination?,
        latestHazard: RoadHazard?
    ) {
        // 1. Always attempt local P2P broadcast (0 mobile data usage)
        localMeshManager?.broadcastTelemetryPacket(
            myCallsign = myCallsign,
            role = role,
            location = location,
            speedKmh = speedKmh,
            headingDeg = headingDeg,
            batteryPct = batteryPct,
            isSpeaking = isSpeaking,
            sharedGoal = sharedGoal,
            latestHazard = latestHazard
        )

        // 2. Evaluate connection health and route via Cellular Cloud if needed
        evaluatePeerConnections()
    }

    fun sendPttAudio(isTransmitting: Boolean) {
        if (isTransmitting) {
            localMeshManager?.startPttStream()
        } else {
            localMeshManager?.stopPttStream()
        }
    }
}
