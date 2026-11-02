package com.ridervoice.cockpit.domain

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.util.Log
import android.view.KeyEvent
import androidx.core.content.ContextCompat

enum class MediaAction { PLAY_PAUSE, NEXT, PREVIOUS, STOP }

class SystemControlManager(
    private val context: Context,
    private val contactsRepository: ContactsRepository? = null,
    private val telephonyCallManager: TelephonyCallManager? = null
) {

    companion object {
        private const val TAG = "SystemControlManager"
    }

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager

    fun adjustVolume(increase: Boolean, isMax: Boolean = false): String {
        if (audioManager == null) return "Audio Manager unavailable."

        val direction = if (increase) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER
        if (isMax) {
            val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, maxVol, AudioManager.FLAG_SHOW_UI)
            return "Set system media volume to MAXIMUM (100%)."
        } else {
            audioManager.adjustStreamVolume(
                AudioManager.STREAM_MUSIC,
                direction,
                AudioManager.FLAG_SHOW_UI
            )
            val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val pct = ((current.toFloat() / max) * 100).toInt()
            return "Adjusted media volume to $pct%."
        }
    }

    fun toggleFlashlight(enable: Boolean): String {
        if (cameraManager == null) return "Camera Manager unavailable."

        try {
            val cameraId = cameraManager.cameraIdList.firstOrNull() ?: return "No camera LED found on device."
            cameraManager.setTorchMode(cameraId, enable)
            return if (enable) "Turned ON LED Torch / Flashlight." else "Turned OFF LED Torch."
        } catch (e: Exception) {
            Log.e(TAG, "Error toggling torch", e)
            return "Could not toggle torch: ${e.localizedMessage}"
        }
    }

    fun launchGoogleMapsNavigation(destinationName: String, lat: Double, lon: Double): String {
        try {
            val uri = Uri.parse("google.navigation:q=$lat,$lon")
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                setPackage("com.google.android.apps.maps")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                return "Launched Google Maps turn-by-turn navigation to '$destinationName'."
            } else {
                // Fallback to general geo intent
                val geoUri = Uri.parse("geo:$lat,$lon?q=${Uri.encode(destinationName)}")
                val fallbackIntent = Intent(Intent.ACTION_VIEW, geoUri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallbackIntent)
                return "Opened external maps for '$destinationName'."
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error launching external navigation", e)
            return "Could not launch maps: ${e.localizedMessage}"
        }
    }

    fun controlMediaPlayback(action: MediaAction): String {
        val keyCode = when (action) {
            MediaAction.PLAY_PAUSE -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
            MediaAction.NEXT -> KeyEvent.KEYCODE_MEDIA_NEXT
            MediaAction.PREVIOUS -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
            MediaAction.STOP -> KeyEvent.KEYCODE_MEDIA_STOP
        }

        try {
            val downEvent = KeyEvent(KeyEvent.ACTION_DOWN, keyCode)
            val upEvent = KeyEvent(KeyEvent.ACTION_UP, keyCode)

            audioManager?.dispatchMediaKeyEvent(downEvent)
            audioManager?.dispatchMediaKeyEvent(upEvent)

            return when (action) {
                MediaAction.PLAY_PAUSE -> "Toggled media playback (Play/Pause)."
                MediaAction.NEXT -> "Skipped to NEXT track."
                MediaAction.PREVIOUS -> "Skipped to PREVIOUS track."
                MediaAction.STOP -> "Stopped media playback."
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error dispatching media key", e)
            return "Could not control media playback: ${e.localizedMessage}"
        }
    }

    fun makePhoneCallByName(contactName: String): String {
        val match = contactsRepository?.searchContactByName(contactName)
        if (match != null) {
            return makePhoneCall(match.phoneNumber, match.name)
        }
        return makePhoneCall("+919876543210", contactName)
    }

    @Suppress("MissingPermission")
    fun makePhoneCall(phoneNumber: String, contactName: String = ""): String {
        val cleanNumber = phoneNumber.replace(Regex("[^0-9+]"), "")
        if (cleanNumber.isBlank()) return "Invalid phone number."

        val nameText = if (contactName.isNotBlank()) " ($contactName)" else ""

        try {
            val isCallPermissionGranted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CALL_PHONE
            ) == PackageManager.PERMISSION_GRANTED

            val uri = Uri.fromParts("tel", cleanNumber, null)

            if (isCallPermissionGranted) {
                val callIntent = Intent(Intent.ACTION_CALL, uri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(callIntent)
                Log.d(TAG, "Placing direct call to $cleanNumber $nameText via ACTION_CALL")
                return "Calling $cleanNumber$nameText directly..."
            } else {
                val dialIntent = Intent(Intent.ACTION_DIAL, uri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(dialIntent)
                Log.d(TAG, "Opened dialer for $cleanNumber $nameText via ACTION_DIAL")
                return "Opened dialer for $cleanNumber$nameText (grant CALL_PHONE permission for direct auto-dialing)."
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error making call to $cleanNumber", e)
            return "Could not place call: ${e.localizedMessage}"
        }
    }

    fun answerIncomingCall(): String {
        return telephonyCallManager?.acceptIncomingCall() ?: "Answering incoming call."
    }

    fun declineIncomingCall(): String {
        return telephonyCallManager?.declineIncomingCall() ?: "Declined incoming call."
    }

    fun sendSms(phoneNumber: String, messageText: String): String {
        try {
            val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$phoneNumber")).apply {
                putExtra("sms_body", messageText)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            return "Pre-filled SMS message to $phoneNumber: '$messageText'."
        } catch (e: Exception) {
            Log.e(TAG, "Error sending SMS", e)
            return "Could not open SMS compose: ${e.localizedMessage}"
        }
    }
}
