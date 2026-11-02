package com.ridervoice.cockpit.domain

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import com.ridervoice.cockpit.model.BluetoothHeadsetState
import java.util.UUID

class BluetoothHelmetMonitor(
    private val context: Context,
    private val onBatteryOrConnectionChanged: (BluetoothHeadsetState) -> Unit
) {

    companion object {
        val BATTERY_SERVICE_UUID: UUID = UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb")
        val BATTERY_LEVEL_CHAR_UUID: UUID = UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb")
        const val ACTION_BATTERY_LEVEL_CHANGED = "android.bluetooth.device.action.BATTERY_LEVEL_CHANGED"
        const val EXTRA_BATTERY_LEVEL = "android.bluetooth.device.extra.BATTERY_LEVEL"
    }

    private var currentHeadsetState = BluetoothHeadsetState()
    private var isRegistered = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            if (intent == null) return
            when (intent.action) {
                ACTION_BATTERY_LEVEL_CHANGED -> {
                    val level = intent.getIntExtra(EXTRA_BATTERY_LEVEL, -1)
                    if (level in 0..100) {
                        currentHeadsetState = currentHeadsetState.copy(
                            batteryPct = level,
                            isConnected = true,
                            hardwareSource = "BLE GATT 0x180F + HFP Broadcast"
                        )
                        onBatteryOrConnectionChanged(currentHeadsetState)
                    }
                }
                "android.bluetooth.device.action.ACL_CONNECTED" -> {
                    val deviceName = try {
                        intent.getParcelableExtra<android.bluetooth.BluetoothDevice>("android.bluetooth.device.extra.DEVICE")?.name ?: "Cardo Packtalk Edge"
                    } catch (e: SecurityException) {
                        "Cardo Packtalk Edge"
                    }
                    currentHeadsetState = currentHeadsetState.copy(
                        isConnected = true,
                        deviceName = deviceName
                    )
                    onBatteryOrConnectionChanged(currentHeadsetState)
                }
                "android.bluetooth.device.action.ACL_DISCONNECTED" -> {
                    currentHeadsetState = currentHeadsetState.copy(
                        isConnected = false
                    )
                    onBatteryOrConnectionChanged(currentHeadsetState)
                }
            }
        }
    }

    fun startMonitoring() {
        if (isRegistered) return
        val filter = IntentFilter().apply {
            addAction(ACTION_BATTERY_LEVEL_CHANGED)
            addAction("android.bluetooth.device.action.ACL_CONNECTED")
            addAction("android.bluetooth.device.action.ACL_DISCONNECTED")
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                context.registerReceiver(receiver, filter)
            }
            isRegistered = true
        } catch (e: Exception) {
            // Permission or system limitation fallback
        }
        setupAudioCommunicationDevice()
    }

    fun stopMonitoring() {
        if (!isRegistered) return
        try {
            context.unregisterReceiver(receiver)
            isRegistered = false
        } catch (e: Exception) {
            // Already unregistered
        }
    }

    fun setupAudioCommunicationDevice(): Boolean {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return false
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val devices = audioManager.availableCommunicationDevices
                val btDevice = devices.firstOrNull {
                    it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                            it.type == AudioDeviceInfo.TYPE_BLE_HEADSET
                }
                if (btDevice != null) {
                    return audioManager.setCommunicationDevice(btDevice)
                }
            } else {
                @Suppress("DEPRECATION")
                audioManager.startBluetoothSco()
                @Suppress("DEPRECATION")
                audioManager.isBluetoothScoOn = true
                return true
            }
        } catch (e: SecurityException) {
            // Fallback to internal audio without crash
        }
        return false
    }
}
