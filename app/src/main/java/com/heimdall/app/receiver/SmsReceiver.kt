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
import com.heimdall.app.util.SmsRoleHelper

class SmsReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "HeimdallSmsReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Telephony.Sms.Intents.SMS_DELIVER_ACTION && action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            return
        }

        val isDefault = SmsRoleHelper.isDefaultSmsApp(context)

        // When Heimdall is the default SMS app, the system delivers SMS_DELIVER_ACTION directly to us.
        // It also broadcasts SMS_RECEIVED_ACTION to all receivers. Drop SMS_RECEIVED_ACTION to prevent double-processing.
        if (action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION && isDefault) {
            Log.d(TAG, "Dropping secondary SMS_RECEIVED broadcast because Heimdall is the default SMS app.")
            return
        }

        val prefsManager = PreferencesManager(context)
        val isMasterActive = prefsManager.isMasterActive()

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) return

        val sender = messages[0].displayOriginatingAddress ?: "Unknown"
        val fullBodyBuilder = StringBuilder()
        for (sms in messages) {
            fullBodyBuilder.append(sms.displayMessageBody)
        }
        val fullBody = fullBodyBuilder.toString()
        val normalizedBody = fullBody.lowercase()
        val timestamp = System.currentTimeMillis()

        Log.d(TAG, "Heimdall ($action, isDefault=$isDefault) received SMS broadcast.")

        // If Master Switch is OFF:
        if (!isMasterActive) {
            Log.d(TAG, "Heimdall is Master Inactive.")
            if (isDefault) {
                // If default SMS app, we must still persist incoming messages to central telephony provider so SMS is not lost
                SmsRoleHelper.writeCleanSmsToTelephonyProvider(context, sender, fullBody, timestamp)
                NotificationHelper.showInspectionNotification(
                    context = context,
                    sender = sender,
                    body = fullBody,
                    isSpam = false,
                    matchedKeyword = null,
                    timestamp = timestamp
                )
            }
            return
        }

        val isFilterEnabled = prefsManager.isFilterEnabled()
        val keywords = prefsManager.getKeywords()

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

        // 3. Log the inspection in Heimdall local storage
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

        // 4. Default SMS App Handling vs Companion Handling
        if (isDefault) {
            if (isSpam) {
                // Intercept silently:
                // - Do NOT write to Android's central SMS database (Telephony.Sms.Inbox).
                //   Google Messages will never see or store it.
                // - Do NOT post any notification (silence all vibrations/sounds/popups).
                Log.d(TAG, "Default SMS App: Intercepted SPAM silently. Withheld from system database.")
            } else {
                // Verified Clean SMS:
                // - Write to central Telephony provider so Android & fallback SMS apps have access
                SmsRoleHelper.writeCleanSmsToTelephonyProvider(context, sender, fullBody, timestamp)
                // - Show notification with 1-Tap OTP Copy if present
                NotificationHelper.showInspectionNotification(
                    context = context,
                    sender = sender,
                    body = fullBody,
                    isSpam = false,
                    matchedKeyword = null,
                    timestamp = timestamp
                )
            }
        } else {
            // Not Default SMS App: Post standard companion notification
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
}
