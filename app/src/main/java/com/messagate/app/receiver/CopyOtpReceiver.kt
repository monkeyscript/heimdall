package com.messagate.app.receiver

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.messagate.app.data.PreferencesManager

class CopyOtpReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_COPY_OTP = "com.messagate.app.ACTION_COPY_OTP"
        const val EXTRA_OTP_CODE = "extra_otp_code"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
        const val EXTRA_MESSAGE_TIMESTAMP = "extra_message_timestamp"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        val otp = intent?.getStringExtra(EXTRA_OTP_CODE) ?: return
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("OTP Code", otp)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "Copied OTP: $otp", Toast.LENGTH_SHORT).show()

        // 1. Dismiss Notification
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)
        if (notificationId != -1) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(notificationId)
        }

        // 2. Mark message as read
        val timestamp = intent.getLongExtra(EXTRA_MESSAGE_TIMESTAMP, -1L)
        if (timestamp != -1L) {
            val prefsManager = PreferencesManager(context)
            prefsManager.markMessageAsRead(timestamp)
        }
    }
}
