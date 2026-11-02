package com.ridervoice.cockpit.domain

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.ParcelFileDescriptor
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsClient
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import com.ridervoice.cockpit.model.CoRider
import com.ridervoice.cockpit.model.GeoPointData
import com.ridervoice.cockpit.model.HazardType
import com.ridervoice.cockpit.model.RiderRole
import com.ridervoice.cockpit.model.RoadHazard
import com.ridervoice.cockpit.model.SharedDestination
import org.json.JSONObject
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread

class NearbySquadMeshManager(
    private val context: Context,
    private val onCoRiderDiscoveredOrUpdated: (CoRider) -> Unit,
    private val onCoRiderDisconnected: (String) -> Unit,
    private val onGoalOrHazardReceived: (SharedDestination?, RoadHazard?) -> Unit
) {

    companion object {
        private const val SERVICE_ID = "com.ridervoice.cockpit.mesh"
        private const val SAMPLE_RATE = 16000
    }

    private val connectionsClient: ConnectionsClient = Nearby.getConnectionsClient(context)
    private val connectedEndpoints = ConcurrentHashMap<String, String>() // endpointId -> callsign

    private var isAdvertising = false
    private var isDiscovering = false

    private var audioRecord: AudioRecord? = null
    private var isTransmittingPtt = false
    private var pttThread: Thread? = null

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            when (payload.type) {
                Payload.Type.BYTES -> {
                    val bytes = payload.asBytes() ?: return
                    val jsonString = String(bytes, Charsets.UTF_8)
                    parseAndProcessPeerTelemetry(endpointId, jsonString)
                }
                Payload.Type.STREAM -> {
                    val inputStream = payload.asStream()?.asInputStream() ?: return
                    playIncomingPeerAudioStream(inputStream)
                }
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {}
    }

    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            // Auto-accept P2P mesh connection
            connectionsClient.acceptConnection(endpointId, payloadCallback)
            connectedEndpoints[endpointId] = info.endpointName
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            if (!result.status.isSuccess) {
                connectedEndpoints.remove(endpointId)
                onCoRiderDisconnected(endpointId)
            }
        }

        override fun onDisconnected(endpointId: String) {
            connectedEndpoints.remove(endpointId)
            onCoRiderDisconnected(endpointId)
        }
    }

    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            // Request connection to discovered squad endpoint
            connectionsClient.requestConnection(
                "Rider-${System.currentTimeMillis() % 1000}",
                endpointId,
                connectionLifecycleCallback
            )
        }

        override fun onEndpointLost(endpointId: String) {
            connectedEndpoints.remove(endpointId)
            onCoRiderDisconnected(endpointId)
        }
    }

    fun startAdvertisingGroup(joinCode: String, myCallsign: String) {
        if (isAdvertising) return
        val options = AdvertisingOptions.Builder().setStrategy(Strategy.P2P_CLUSTER).build()
        connectionsClient.startAdvertising(
            "$myCallsign|$joinCode",
            SERVICE_ID,
            connectionLifecycleCallback,
            options
        ).addOnSuccessListener {
            isAdvertising = true
        }.addOnFailureListener {
            isAdvertising = false
        }
    }

    fun startJoiningGroup(joinCode: String) {
        if (isDiscovering) return
        val options = DiscoveryOptions.Builder().setStrategy(Strategy.P2P_CLUSTER).build()
        connectionsClient.startDiscovery(
            SERVICE_ID,
            endpointDiscoveryCallback,
            options
        ).addOnSuccessListener {
            isDiscovering = true
        }.addOnFailureListener {
            isDiscovering = false
        }
    }

    fun stopGroupSession() {
        try {
            if (isAdvertising) connectionsClient.stopAdvertising()
            if (isDiscovering) connectionsClient.stopDiscovery()
            connectionsClient.stopAllEndpoints()
            connectedEndpoints.clear()
            isAdvertising = false
            isDiscovering = false
            stopPttStream()
        } catch (e: Exception) {
            // Defensive cleanup
        }
    }

    fun broadcastTelemetryPacket(
        myCallsign: String,
        role: RiderRole,
        location: GeoPointData,
        speedKmh: Int,
        headingDeg: Float,
        batteryPct: Int,
        isSpeaking: Boolean,
        sharedGoal: SharedDestination? = null,
        latestHazard: RoadHazard? = null
    ) {
        if (connectedEndpoints.isEmpty()) return

        val json = JSONObject().apply {
            put("callsign", myCallsign)
            put("role", role.name)
            put("lat", location.lat)
            put("lon", location.lon)
            put("speed", speedKmh)
            put("heading", headingDeg)
            put("battery", batteryPct)
            put("speaking", isSpeaking)

            if (sharedGoal != null) {
                put("sharedGoal", JSONObject().apply {
                    put("name", sharedGoal.name)
                    put("address", sharedGoal.address)
                    put("targetLat", sharedGoal.targetPoint.lat)
                    put("targetLon", sharedGoal.targetPoint.lon)
                    put("distanceKm", sharedGoal.distanceRemainingKm)
                    put("etaMin", sharedGoal.etaMinutes)
                    put("instruction", sharedGoal.nextManeuverInstruction)
                })
            }

            if (latestHazard != null) {
                put("hazard", JSONObject().apply {
                    put("id", latestHazard.id)
                    put("type", latestHazard.type.name)
                    put("lat", latestHazard.location.lat)
                    put("lon", latestHazard.location.lon)
                    put("reporter", latestHazard.reportedByCallsign)
                })
            }
        }

        val payload = Payload.fromBytes(json.toString().toByteArray(Charsets.UTF_8))
        connectionsClient.sendPayload(connectedEndpoints.keys.toList(), payload)
    }

    fun startPttStream() {
        if (isTransmittingPtt || connectedEndpoints.isEmpty()) return
        isTransmittingPtt = true

        try {
            val pipes = ParcelFileDescriptor.createPipe()
            val readSide = pipes[0]
            val writeSide = pipes[1]

            val streamPayload = Payload.fromStream(readSide)
            connectionsClient.sendPayload(connectedEndpoints.keys.toList(), streamPayload)

            val outputStream = ParcelFileDescriptor.AutoCloseOutputStream(writeSide)

            pttThread = thread(start = true) {
                val minBufferSize = AudioRecord.getMinBufferSize(
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )
                try {
                    audioRecord = AudioRecord(
                        MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                        SAMPLE_RATE,
                        AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        minBufferSize * 2
                    )
                    audioRecord?.startRecording()
                    val buffer = ByteArray(minBufferSize)

                    while (isTransmittingPtt) {
                        val readBytes = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                        if (readBytes > 0) {
                            outputStream.write(buffer, 0, readBytes)
                            outputStream.flush()
                        }
                    }
                } catch (e: SecurityException) {
                    // Permission check handled safely
                } catch (e: Exception) {
                    // Pipe closed or interrupted
                } finally {
                    try {
                        outputStream.close()
                    } catch (e: Exception) {}
                }
            }
        } catch (e: Exception) {
            isTransmittingPtt = false
        }
    }

    fun stopPttStream() {
        isTransmittingPtt = false
        try {
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
        } catch (e: Exception) {}
    }

    private fun parseAndProcessPeerTelemetry(endpointId: String, jsonStr: String) {
        try {
            val json = JSONObject(jsonStr)
            val callsign = json.optString("callsign", "Co-Rider")
            val roleName = json.optString("role", RiderRole.PACK_RIDER.name)
            val role = try { RiderRole.valueOf(roleName) } catch (e: Exception) { RiderRole.PACK_RIDER }
            val lat = json.optDouble("lat", 26.9520)
            val lon = json.optDouble("lon", 75.8320)
            val speed = json.optInt("speed", 75)
            val heading = json.optDouble("heading", 42.0).toFloat()
            val battery = json.optInt("battery", 85)
            val speaking = json.optBoolean("speaking", false)

            val updatedCoRider = CoRider(
                id = endpointId,
                name = callsign,
                callsign = callsign,
                role = role,
                location = GeoPointData(lat, lon),
                speedKmh = speed,
                headingDeg = heading,
                distanceMeters = 0,
                bearingDeg = 0f,
                isAhead = true,
                isSpeaking = speaking,
                helmetBatteryPct = battery
            )

            onCoRiderDiscoveredOrUpdated(updatedCoRider)

            // Parse Goal if present
            var rxGoal: SharedDestination? = null
            if (json.has("sharedGoal")) {
                val goalObj = json.getJSONObject("sharedGoal")
                rxGoal = SharedDestination(
                    placeId = "dest-p2p-sync",
                    name = goalObj.optString("name", "Pack Destination"),
                    address = goalObj.optString("address", "Squad Route Destination"),
                    targetPoint = GeoPointData(goalObj.optDouble("targetLat", 26.9855), goalObj.optDouble("targetLon", 75.8513)),
                    distanceRemainingKm = goalObj.optDouble("distanceKm", 5.2).toFloat(),
                    etaMinutes = goalObj.optInt("etaMin", 8),
                    lockedByRider = callsign,
                    nextManeuverInstruction = goalObj.optString("instruction", "Follow Squad Captain")
                )
            }

            // Parse Hazard if present
            var rxHazard: RoadHazard? = null
            if (json.has("hazard")) {
                val hzObj = json.getJSONObject("hazard")
                val typeName = hzObj.optString("type", HazardType.POTHOLE.name)
                val hzType = try { HazardType.valueOf(typeName) } catch (e: Exception) { HazardType.POTHOLE }
                rxHazard = RoadHazard(
                    id = hzObj.optString("id", "hz-${System.currentTimeMillis()}"),
                    type = hzType,
                    location = GeoPointData(hzObj.optDouble("lat", lat), hzObj.optDouble("lon", lon)),
                    distanceMeters = 0,
                    bearingDeg = 0f,
                    reportedByCallsign = hzObj.optString("reporter", callsign)
                )
            }

            if (rxGoal != null || rxHazard != null) {
                onGoalOrHazardReceived(rxGoal, rxHazard)
            }

        } catch (e: Exception) {
            // Malformed JSON handled
        }
    }

    private fun playIncomingPeerAudioStream(inputStream: InputStream) {
        thread(start = true) {
            val minBufferSize = AudioTrack.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )

            val audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(minBufferSize * 2)
                .build()

            try {
                audioTrack.play()
                val buffer = ByteArray(minBufferSize)
                var bytesRead: Int
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    if (bytesRead > 0) {
                        audioTrack.write(buffer, 0, bytesRead)
                    }
                }
            } catch (e: Exception) {
                // Stream closed
            } finally {
                try {
                    audioTrack.stop()
                    audioTrack.release()
                    inputStream.close()
                } catch (e: Exception) {}
            }
        }
    }
}
