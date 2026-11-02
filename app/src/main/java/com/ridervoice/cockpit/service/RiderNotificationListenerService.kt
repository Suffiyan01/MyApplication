package com.ridervoice.cockpit.service

import android.app.Notification
import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

class RiderNotificationListenerService : NotificationListenerService() {

    companion object {
        private const val TAG = "RiderNotificationListener"
        const val ACTION_WHATSAPP_NOTIFICATION = "com.ridervoice.cockpit.WHATSAPP_NOTIFICATION"
        const val EXTRA_SENDER = "extra_sender"
        const val EXTRA_MESSAGE = "extra_message"

        var activeInstance: RiderNotificationListenerService? = null
            private set
    }

    override fun onCreate() {
        super.onCreate()
        activeInstance = this
        Log.d(TAG, "RiderNotificationListenerService created.")
    }

    override fun onDestroy() {
        super.onDestroy()
        if (activeInstance == this) {
            activeInstance = null
        }
        Log.d(TAG, "RiderNotificationListenerService destroyed.")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        val pkg = sbn.packageName
        val extras = sbn.notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: "Unknown"
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""

        when (pkg) {
            "com.whatsapp" -> {
                if (text.isNotBlank() && !title.contains("new messages", ignoreCase = true)) {
                    Log.d(TAG, "Intercepted WhatsApp from $title: $text")
                    val intent = Intent(ACTION_WHATSAPP_NOTIFICATION).apply {
                        putExtra(EXTRA_SENDER, title)
                        putExtra(EXTRA_MESSAGE, text)
                        setPackage(packageName)
                    }
                    sendBroadcast(intent)
                }
            }
            "com.spotify.music" -> {
                if (title.isNotBlank() && text.isNotBlank()) {
                    Log.d(TAG, "Intercepted Spotify: $title by $text")
                }
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
    }
}
