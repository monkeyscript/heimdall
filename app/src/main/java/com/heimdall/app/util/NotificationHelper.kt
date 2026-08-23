package com.heimdall.app.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.heimdall.app.MainActivity
import com.heimdall.app.R
import com.heimdall.app.receiver.CopyOtpReceiver

object NotificationHelper {
    private const val CHANNEL_ID = "heimdall_alerts"
    private const val CHANNEL_NAME = "Heimdall SMS Alerts"

    fun showInspectionNotification(
        context: Context,
        sender: String,
        body: String,
        isSpam: Boolean,
        matchedKeyword: String?
    ) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Live SMS Alerts from Heimdall"
                enableVibration(true)
            }
            manager.createNotificationChannel(channel)
        }

        // Clean format: Emoji + Sender
        val title = if (isSpam) "⚠️ $sender" else "🛡️ $sender"
        val content = body

        // Open MainActivity when notification is clicked
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notificationId = (System.currentTimeMillis() % 100000).toInt()

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_heimdall_eye)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        // Check if message contains an OTP code
        val extractedOtp = OtpHelper.extractOtp(body)
        if (extractedOtp != null) {
            val copyIntent = Intent(context, CopyOtpReceiver::class.java).apply {
                action = CopyOtpReceiver.ACTION_COPY_OTP
                putExtra(CopyOtpReceiver.EXTRA_OTP_CODE, extractedOtp)
            }
            val copyPendingIntent = PendingIntent.getBroadcast(
                context,
                notificationId,
                copyIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            // Add 1-tap "Copy <OTP>" action directly on the notification!
            builder.addAction(
                R.drawable.ic_heimdall_eye,
                "Copy $extractedOtp",
                copyPendingIntent
            )
        }

        manager.notify(notificationId, builder.build())
    }
}
