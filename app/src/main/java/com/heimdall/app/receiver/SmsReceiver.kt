package com.heimdall.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.heimdall.app.data.InspectedMessage
import com.heimdall.app.data.PreferencesManager
import com.heimdall.app.util.CategoryHelper
import com.heimdall.app.util.NotificationHelper

class SmsReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "HeimdallSmsReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val prefsManager = PreferencesManager(context)

        // Master Switch Check: If Master is OFF, completely sleep
        if (!prefsManager.isMasterActive()) {
            Log.d(TAG, "Heimdall is Master Inactive. Skipping SMS.")
            return
        }

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) return

        val isFilterEnabled = prefsManager.isFilterEnabled()
        val keywords = prefsManager.getKeywords()

        val sender = messages[0].displayOriginatingAddress ?: "Unknown"
        val fullBodyBuilder = StringBuilder()
        for (sms in messages) {
            fullBodyBuilder.append(sms.displayMessageBody)
        }
        val fullBody = fullBodyBuilder.toString()
        val normalizedBody = fullBody.lowercase()
        val timestamp = System.currentTimeMillis()

        Log.d(TAG, "Heimdall intercepted SMS from $sender: $fullBody")

        // 1. Keyword evaluation (Only if Spam Filter toggle is ON)
        var matchedKeyword: String? = null
        var isSpam = false

        if (isFilterEnabled) {
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

        // 2. Pre-compute category ONCE on arrival in the background thread
        val category = CategoryHelper.detectCategory(
            sender = sender,
            body = fullBody,
            isSpam = isSpam
        )

        // 3. Log the inspection with pre-computed category and isRead = false
        val inspected = InspectedMessage(
            timestamp = timestamp,
            sender = sender,
            body = fullBody,
            isSpam = isSpam,
            matchedKeyword = matchedKeyword,
            isRead = false,
            category = category.name
        )
        prefsManager.addInspectedMessage(inspected)

        // 4. Show Heimdall Companion Notification
        NotificationHelper.showInspectionNotification(
            context = context,
            sender = sender,
            body = fullBody,
            isSpam = isSpam,
            matchedKeyword = matchedKeyword,
            timestamp = timestamp
        )
    }
}
