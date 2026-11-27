package com.messagate.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.messagate.app.core.engine.DetectionResult
import com.messagate.app.core.engine.DetectionService
import com.messagate.app.data.InspectedMessage
import com.messagate.app.data.PreferencesManager
import com.messagate.app.util.CategoryHelper
import com.messagate.app.util.NotificationHelper
import com.messagate.app.util.SmsRoleHelper

class SmsReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "MessagateSmsReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Telephony.Sms.Intents.SMS_DELIVER_ACTION && action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            return
        }

        val isDefault = SmsRoleHelper.isDefaultSmsApp(context)

        // When Messagate is the default SMS app, the system delivers SMS_DELIVER_ACTION directly to us.
        // It also broadcasts SMS_RECEIVED_ACTION to all receivers. Drop SMS_RECEIVED_ACTION to prevent double-processing.
        if (action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION && isDefault) {
            Log.d(TAG, "Dropping secondary SMS_RECEIVED broadcast because Messagate is the default SMS app.")
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
        val timestamp = System.currentTimeMillis()

        Log.d(TAG, "Messagate ($action, isDefault=$isDefault) received SMS broadcast.")

        // If Master Switch is OFF:
        if (!isMasterActive) {
            Log.d(TAG, "Messagate is Master Inactive.")
            if (isDefault) {
                // If default SMS app, persist to central telephony provider, log to Messagate inbox, and notify
                SmsRoleHelper.writeCleanSmsToTelephonyProvider(context, sender, fullBody, timestamp)
                val category = CategoryHelper.detectCategory(
                    sender = sender,
                    body = fullBody,
                    isSpam = false
                )
                val cleanMessage = InspectedMessage(
                    timestamp = timestamp,
                    sender = sender,
                    body = fullBody,
                    isSpam = false,
                    matchedKeyword = null,
                    isRead = false,
                    category = category.name
                )
                prefsManager.addInspectedMessage(cleanMessage)

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

        // 1. Ordered rule evaluation (ALLOW first, then priority order)
        val detection = if (isFilterEnabled) {
            DetectionService(context).detect(sender, fullBody)
        } else {
            DetectionResult.NONE
        }
        val isSpam = detection.isSpam
        val matchedKeyword = detection.matchedText
        val matchedRuleName = detection.matchedRuleName
        val isMuted = detection.muted

        if (isSpam) {
            prefsManager.incrementBlockedCount()
        }

        // 2. Pre-compute category ONCE on arrival in the background thread
        val category = CategoryHelper.detectCategory(
            sender = sender,
            body = fullBody,
            isSpam = isSpam,
            tagCategoryId = detection.tagCategoryId
        )

        // 3. Log the inspection in Messagate local storage
        val inspected = InspectedMessage(
            timestamp = timestamp,
            sender = sender,
            body = fullBody,
            isSpam = isSpam,
            matchedKeyword = matchedKeyword,
            isRead = false,
            category = category.name,
            matchedRuleName = matchedRuleName
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
                // - Show notification with 1-Tap OTP Copy if present (unless a MUTE rule matched)
                if (!isMuted) {
                    NotificationHelper.showInspectionNotification(
                        context = context,
                        sender = sender,
                        body = fullBody,
                        isSpam = false,
                        matchedKeyword = null,
                        timestamp = timestamp
                    )
                }
            }
        } else {
            // Not Default SMS App: Post standard companion notification
            if (!isMuted) {
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
}
