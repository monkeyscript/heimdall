package com.heimdall.app.util

import android.app.role.RoleManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Telephony
import android.util.Log

object SmsRoleHelper {
    private const val TAG = "SmsRoleHelper"

    /**
     * Checks if Heimdall is currently the designated Default SMS App on the device.
     */
    fun isDefaultSmsApp(context: Context): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val roleManager = context.getSystemService(RoleManager::class.java)
                if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_SMS)) {
                    return roleManager.isRoleHeld(RoleManager.ROLE_SMS)
                }
            }
            val defaultPackage = Telephony.Sms.getDefaultSmsPackage(context)
            defaultPackage == context.packageName
        } catch (e: Exception) {
            Log.e(TAG, "Error checking default SMS status", e)
            false
        }
    }

    /**
     * Creates an Intent to prompt the user to set Heimdall as the Default SMS App.
     */
    fun createDefaultSmsIntent(context: Context): Intent {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(RoleManager::class.java)
            if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_SMS)) {
                return roleManager.createRequestRoleIntent(RoleManager.ROLE_SMS)
            }
        }

        return Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT).apply {
            putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, context.packageName)
        }
    }

    /**
     * Writes verified clean SMS into Android's central Telephony provider.
     * This ensures other SMS apps (like Google Messages) have access to clean SMS
     * when opened, while spam is withheld from the system database.
     */
    fun writeCleanSmsToTelephonyProvider(
        context: Context,
        sender: String,
        body: String,
        timestamp: Long
    ): Uri? {
        return try {
            val values = ContentValues().apply {
                put(Telephony.Sms.ADDRESS, sender)
                put(Telephony.Sms.BODY, body)
                put(Telephony.Sms.DATE, timestamp)
                put(Telephony.Sms.DATE_SENT, timestamp)
                put(Telephony.Sms.READ, 0)
                put(Telephony.Sms.SEEN, 0)
                put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_INBOX)
            }
            context.contentResolver.insert(Telephony.Sms.Inbox.CONTENT_URI, values)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to insert clean SMS into central provider", e)
            null
        }
    }

    /**
     * Deletes a message from Android's central Telephony provider.
     * Allowed only when Heimdall is the active default SMS app.
     */
    fun deleteFromTelephonyProvider(context: Context, timestamp: Long): Int {
        if (!isDefaultSmsApp(context)) return 0
        return try {
            val minTime = timestamp - 1000L
            val maxTime = timestamp + 1000L
            context.contentResolver.delete(
                Telephony.Sms.CONTENT_URI,
                "${Telephony.Sms.DATE} >= ? AND ${Telephony.Sms.DATE} <= ?",
                arrayOf(minTime.toString(), maxTime.toString())
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete SMS from central provider", e)
            0
        }
    }

    /**
     * Reads existing messages from Android's central SMS inbox for one-time initial load.
     * Capped at limit (default 100) to keep memory footprint minimal.
     */
    fun readExistingInboxMessages(
        context: Context,
        keywords: Set<String> = emptySet(),
        limit: Int = 100
    ): List<com.heimdall.app.data.InspectedMessage> {
        val messages = mutableListOf<com.heimdall.app.data.InspectedMessage>()
        try {
            val projection = arrayOf(
                Telephony.Sms.ADDRESS,
                Telephony.Sms.BODY,
                Telephony.Sms.DATE,
                Telephony.Sms.READ
            )
            val cursor = context.contentResolver.query(
                Telephony.Sms.Inbox.CONTENT_URI,
                projection,
                null,
                null,
                "${Telephony.Sms.DATE} DESC"
            )
            cursor?.use {
                val addressIndex = it.getColumnIndex(Telephony.Sms.ADDRESS)
                val bodyIndex = it.getColumnIndex(Telephony.Sms.BODY)
                val dateIndex = it.getColumnIndex(Telephony.Sms.DATE)
                val readIndex = it.getColumnIndex(Telephony.Sms.READ)

                var count = 0
                while (it.moveToNext() && count < limit) {
                    val sender = if (addressIndex != -1) it.getString(addressIndex) ?: "Unknown" else "Unknown"
                    val body = if (bodyIndex != -1) it.getString(bodyIndex) ?: "" else ""
                    val timestamp = if (dateIndex != -1) it.getLong(dateIndex) else System.currentTimeMillis()
                    val isRead = if (readIndex != -1) it.getInt(readIndex) == 1 else true

                    val lowerBody = body.lowercase()
                    val matchedKeyword = keywords.firstOrNull { kw -> lowerBody.contains(kw) }
                    val isSpam = matchedKeyword != null

                    val category = CategoryHelper.detectCategory(sender, body, isSpam).name

                    messages.add(
                        com.heimdall.app.data.InspectedMessage(
                            timestamp = timestamp,
                            sender = sender,
                            body = body,
                            isSpam = isSpam,
                            matchedKeyword = matchedKeyword,
                            isRead = isRead,
                            category = category
                        )
                    )
                    count++
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read existing inbox messages", e)
        }
        return messages
    }
}
