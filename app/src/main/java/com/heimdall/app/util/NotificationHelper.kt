package com.heimdall.app.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.heimdall.app.MainActivity
import com.heimdall.app.receiver.CopyOtpReceiver

object NotificationHelper {

    private const val CHANNEL_ID_SPAM = "heimdall_spam_alerts"
    private const val CHANNEL_ID_CLEAN = "heimdall_clean_alerts"
    const val EXTRA_MESSAGE_TIMESTAMP = "extra_message_timestamp"

    private fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val spamChannel = NotificationChannel(
                CHANNEL_ID_SPAM,
                "Heimdall Spam Warnings",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "High priority warnings for intercepted spam SMS"
                enableVibration(true)
            }

            val cleanChannel = NotificationChannel(
                CHANNEL_ID_CLEAN,
                "Heimdall Clean Feed",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Subtle feed alerts for verified clean SMS"
                enableVibration(false)
            }

            notificationManager.createNotificationChannel(spamChannel)
            notificationManager.createNotificationChannel(cleanChannel)
        }
    }

    fun showInspectionNotification(
        context: Context,
        sender: String,
        body: String,
        isSpam: Boolean,
        matchedKeyword: String?,
        timestamp: Long = System.currentTimeMillis()
    ) {
        createNotificationChannels(context)

        val channelId = if (isSpam) CHANNEL_ID_SPAM else CHANNEL_ID_CLEAN
        val title = if (isSpam) {
            "⚠️ $sender"
        } else {
            "🛡️ $sender"
        }

        val content = if (isSpam && !matchedKeyword.isNullOrEmpty()) {
            "[SPAM: ${matchedKeyword.uppercase()}] $body"
        } else {
            body
        }

        // Tap on notification directly opens MainActivity and auto-opens this message's preview modal
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_MESSAGE_TIMESTAMP, timestamp)
        }
        val pendingOpenIntent = PendingIntent.getActivity(
            context,
            timestamp.toInt(),
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setPriority(if (isSpam) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .setContentIntent(pendingOpenIntent)

        // If an OTP is present, attach 1-Tap Copy Action Button directly on the notification
        val extractedOtp = OtpHelper.extractOtp(body)
        if (extractedOtp != null) {
            val copyIntent = Intent(context, CopyOtpReceiver::class.java).apply {
                putExtra(CopyOtpReceiver.EXTRA_OTP_CODE, extractedOtp)
            }
            val pendingCopyIntent = PendingIntent.getBroadcast(
                context,
                timestamp.toInt(),
                copyIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            builder.addAction(
                android.R.drawable.ic_menu_save,
                "Copy $extractedOtp",
                pendingCopyIntent
            )
        }

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(timestamp.toInt(), builder.build())
    }
}
