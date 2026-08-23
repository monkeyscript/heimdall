package com.heimdall.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.heimdall.app.data.InspectedMessage
import com.heimdall.app.data.PreferencesManager
import com.heimdall.app.util.NotificationHelper

class SmsReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "HeimdallSmsReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) return

        val prefsManager = PreferencesManager(context)
        val isShieldOn = prefsManager.isShieldEnabled()
        val keywords = prefsManager.getKeywords()

        // Combine multi-part messages if needed
        val sender = messages[0].displayOriginatingAddress ?: "Unknown"
        val fullBodyBuilder = StringBuilder()
        for (sms in messages) {
            fullBodyBuilder.append(sms.displayMessageBody)
        }
        val fullBody = fullBodyBuilder.toString()
        val normalizedBody = fullBody.lowercase()

        Log.d(TAG, "Heimdall intercepted SMS from $sender: $fullBody")

        // Keyword evaluation
        var matchedKeyword: String? = null
        var isSpam = false

        if (isShieldOn) {
            for (kw in keywords) {
                if (normalizedBody.contains(kw.lowercase())) {
                    isSpam = true
                    matchedKeyword = kw
                    break
                }
            }
        }

        if (isSpam) {
            prefsManager.incrementBlockedCount()
        }

        // Log the inspection
        val inspected = InspectedMessage(
            timestamp = System.currentTimeMillis(),
            sender = sender,
            body = fullBody,
            isSpam = isSpam,
            matchedKeyword = matchedKeyword
        )
        prefsManager.addInspectedMessage(inspected)

        // Show Heimdall Companion Notification
        NotificationHelper.showInspectionNotification(
            context = context,
            sender = sender,
            body = fullBody,
            isSpam = isSpam,
            matchedKeyword = matchedKeyword
        )
    }
}
