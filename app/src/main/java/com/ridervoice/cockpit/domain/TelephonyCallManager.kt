package com.ridervoice.cockpit.domain

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Build
import android.telecom.TelecomManager
import android.telephony.TelephonyManager
import android.util.Log

class TelephonyCallManager(
    private val context: Context,
    private val contactsRepository: ContactsRepository,
    private val onIncomingCallAnnounced: (callerName: String, phoneNumber: String) -> Unit
) {

    companion object {
        private const val TAG = "TelephonyCallManager"
    }

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager

    var currentRingingCallerName: String? = null
        private set
    var currentRingingNumber: String? = null
        private set

    private var isRegistered = false

    private val phoneStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == TelephonyManager.ACTION_PHONE_STATE_CHANGED) {
                val stateStr = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
                val number = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)

                Log.d(TAG, "PhoneStateReceiver received state: $stateStr, number: $number")

                val state = when (stateStr) {
                    TelephonyManager.EXTRA_STATE_RINGING -> TelephonyManager.CALL_STATE_RINGING
                    TelephonyManager.EXTRA_STATE_OFFHOOK -> TelephonyManager.CALL_STATE_OFFHOOK
                    else -> TelephonyManager.CALL_STATE_IDLE
                }
                handleCallStateChanged(state, number)
            }
        }
    }

    fun startListening() {
        if (isRegistered) return
        try {
            val filter = IntentFilter(TelephonyManager.ACTION_PHONE_STATE_CHANGED)
            context.registerReceiver(phoneStateReceiver, filter)
            isRegistered = true
            Log.d(TAG, "Registered PhoneStateReceiver for incoming call interception.")
        } catch (e: Exception) {
            Log.e(TAG, "Error registering phone state receiver", e)
        }
    }

    fun stopListening() {
        if (!isRegistered) return
        try {
            context.unregisterReceiver(phoneStateReceiver)
            isRegistered = false
            Log.d(TAG, "Unregistered PhoneStateReceiver.")
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering receiver", e)
        }
    }

    fun handleCallStateChanged(state: Int, incomingNumber: String?) {
        when (state) {
            TelephonyManager.CALL_STATE_RINGING -> {
                val number = incomingNumber ?: "+919876543210"
                currentRingingNumber = number
                val callerName = contactsRepository.getContactNameByPhoneNumber(number)
                currentRingingCallerName = callerName

                Log.d(TAG, "Incoming call ringing from $callerName ($number). Silencing ringer & announcing...")
                silenceRinger()
                onIncomingCallAnnounced(callerName, number)
            }
            TelephonyManager.CALL_STATE_OFFHOOK, TelephonyManager.CALL_STATE_IDLE -> {
                currentRingingCallerName = null
                currentRingingNumber = null
            }
        }
    }

    @Suppress("MissingPermission")
    fun silenceRinger() {
        try {
            telecomManager?.silenceRinger()
            audioManager?.ringerMode = AudioManager.RINGER_MODE_SILENT
            Log.d(TAG, "Silenced device ringer for incoming call.")
        } catch (e: Exception) {
            Log.e(TAG, "Error silencing ringer", e)
        }
    }

    @Suppress("MissingPermission")
    fun acceptIncomingCall(): String {
        val caller = currentRingingCallerName ?: "incoming call"
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                telecomManager?.acceptRingingCall()
                return "Answered call from $caller."
            } else {
                return "Call answering requires Android 8.0 (API 26) or higher."
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "ANSWER_PHONE_CALLS permission required", e)
            return "ANSWER_PHONE_CALLS permission required to accept call."
        } catch (e: Exception) {
            Log.e(TAG, "Error accepting ringing call", e)
            return "Could not accept call: ${e.localizedMessage}"
        }
    }

    @Suppress("MissingPermission")
    fun declineIncomingCall(): String {
        val caller = currentRingingCallerName ?: "incoming call"
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val ended = telecomManager?.endCall()
                return if (ended == true) "Declined call from $caller." else "Ended call."
            } else {
                return "Call ending requires Android 9.0 (API 28) or higher."
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "ANSWER_PHONE_CALLS permission required", e)
            return "ANSWER_PHONE_CALLS permission required to decline call."
        } catch (e: Exception) {
            Log.e(TAG, "Error declining call", e)
            return "Could not decline call: ${e.localizedMessage}"
        }
    }
}
