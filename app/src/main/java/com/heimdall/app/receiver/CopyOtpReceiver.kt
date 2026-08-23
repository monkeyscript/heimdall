package com.heimdall.app.receiver

import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast

class CopyOtpReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_COPY_OTP = "com.heimdall.app.ACTION_COPY_OTP"
        const val EXTRA_OTP_CODE = "extra_otp_code"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        val otp = intent?.getStringExtra(EXTRA_OTP_CODE) ?: return
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("OTP Code", otp)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "Copied OTP: $otp", Toast.LENGTH_SHORT).show()
    }
}
