package com.ridervoice.cockpit.domain

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSpecifier
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi

class LocalHotspotManager(
    private val context: Context
) {

    companion object {
        private const val TAG = "LocalHotspotManager"
    }

    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    private val connectivityManager = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    private var reservation: WifiManager.LocalOnlyHotspotReservation? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    var activeSsid: String? = null
        private set
    var activePassphrase: String? = null
        private set
    var isHotspotActive: Boolean = false
        private set

    fun startHotspot(
        squadCode: String,
        onHotspotStarted: (ssid: String, password: String, qrPayload: String) -> Unit,
        onHotspotFailed: (reason: String) -> Unit
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            onHotspotFailed("LocalOnlyHotspot requires Android 8.0 (API 26) or higher.")
            return
        }

        if (wifiManager == null) {
            onHotspotFailed("Wi-Fi Manager unavailable on device.")
            return
        }

        try {
            wifiManager.startLocalOnlyHotspot(object : WifiManager.LocalOnlyHotspotCallback() {
                override fun onStarted(res: WifiManager.LocalOnlyHotspotReservation) {
                    super.onStarted(res)
                    reservation = res
                    val config = res.wifiConfiguration
                    activeSsid = config?.SSID ?: "RiderSquad-Hotspot"
                    activePassphrase = config?.preSharedKey ?: "RiderMeshPass123"
                    isHotspotActive = true

                    val qrPayload = buildQrPayload(activeSsid!!, activePassphrase!!, squadCode)
                    Log.d(TAG, "LocalOnlyHotspot started. SSID: $activeSsid, Password: $activePassphrase")
                    onHotspotStarted(activeSsid!!, activePassphrase!!, qrPayload)
                }

                override fun onStopped() {
                    super.onStopped()
                    isHotspotActive = false
                    reservation = null
                    Log.d(TAG, "LocalOnlyHotspot stopped.")
                }

                override fun onFailed(reason: Int) {
                    super.onFailed(reason)
                    isHotspotActive = false
                    reservation = null
                    Log.e(TAG, "LocalOnlyHotspot failed with reason code: $reason")
                    onHotspotFailed("Failed to create Local-Only Hotspot (Error code: $reason)")
                }
            }, null)
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException starting hotspot", e)
            onHotspotFailed("Wi-Fi / Nearby permission required to start Local Hotspot.")
        } catch (e: Exception) {
            Log.e(TAG, "Exception starting hotspot", e)
            onHotspotFailed("Could not start Local Hotspot: ${e.localizedMessage}")
        }
    }

    fun stopHotspot() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                reservation?.close()
            }
            reservation = null
            isHotspotActive = false
            activeSsid = null
            activePassphrase = null
            Log.d(TAG, "Closed LocalOnlyHotspot reservation.")
        } catch (e: Exception) {
            Log.e(TAG, "Error closing hotspot reservation", e)
        }
    }

    fun connectToHotspot(
        ssid: String,
        passphrase: String,
        onConnected: () -> Unit,
        onFailed: (String) -> Unit
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            // Pre-Android 10 fallback
            onConnected()
            return
        }

        if (connectivityManager == null) {
            onFailed("ConnectivityManager unavailable.")
            return
        }

        try {
            disconnectFromHotspot()

            val specifier = WifiNetworkSpecifier.Builder()
                .setSsid(ssid)
                .setWpa2Passphrase(passphrase)
                .build()

            val request = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .setNetworkSpecifier(specifier)
                .build()

            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    super.onAvailable(network)
                    connectivityManager.bindProcessToNetwork(network)
                    Log.d(TAG, "Bound process to Local Hotspot network: $ssid")
                    onConnected()
                }

                override fun onUnavailable() {
                    super.onUnavailable()
                    Log.e(TAG, "Local Hotspot unavailable: $ssid")
                    onFailed("Could not connect to Squad Local Hotspot '$ssid'")
                }

                override fun onLost(network: Network) {
                    super.onLost(network)
                    connectivityManager.bindProcessToNetwork(null)
                    Log.d(TAG, "Lost connection to Local Hotspot network.")
                }
            }

            networkCallback = callback
            connectivityManager.requestNetwork(request, callback)

        } catch (e: Exception) {
            Log.e(TAG, "Error requesting network for hotspot connection", e)
            onFailed("Connection error: ${e.localizedMessage}")
        }
    }

    fun disconnectFromHotspot() {
        try {
            networkCallback?.let {
                connectivityManager?.unregisterNetworkCallback(it)
                connectivityManager?.bindProcessToNetwork(null)
            }
            networkCallback = null
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering network callback", e)
        }
    }

    fun buildQrPayload(ssid: String, passphrase: String, squadCode: String): String {
        return "WIFI:S:$ssid;T:WPA;P:$passphrase;;JOIN:$squadCode"
    }

    fun parseQrPayload(raw: String): HotspotQrData? {
        val trimmed = raw.trim()
        if (!trimmed.contains("WIFI:", ignoreCase = true) && !trimmed.contains("JOIN:", ignoreCase = true)) {
            return null
        }

        var ssid: String? = null
        var password: String? = null
        var squadCode: String? = null

        val parts = trimmed.split(";")
        for (part in parts) {
            val p = part.trim()
            when {
                p.startsWith("WIFI:S:", ignoreCase = true) -> ssid = p.substring(7)
                p.startsWith("S:", ignoreCase = true) -> ssid = p.substring(2)
                p.startsWith("P:", ignoreCase = true) -> password = p.substring(2)
                p.startsWith("JOIN:", ignoreCase = true) -> squadCode = p.substring(5)
            }
        }

        if (squadCode == null) {
            val rvIdx = trimmed.indexOf("RV-", ignoreCase = true)
            if (rvIdx >= 0) {
                squadCode = trimmed.substring(rvIdx).take(7).uppercase()
            }
        }

        return HotspotQrData(
            ssid = ssid ?: "",
            password = password ?: "",
            squadCode = squadCode ?: "RV-8492"
        )
    }
}

data class HotspotQrData(
    val ssid: String,
    val password: String,
    val squadCode: String
)
