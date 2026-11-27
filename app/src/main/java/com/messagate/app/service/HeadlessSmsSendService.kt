package com.messagate.app.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log

/**
 * Required by Android OS for default SMS app eligibility.
 * Handles quick response messages when user rejects incoming phone calls with SMS.
 */
class HeadlessSmsSendService : Service() {
    companion object {
        private const val TAG = "HeadlessSmsSendService"
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Respond via message intent received: ${intent?.action}")
        stopSelf(startId)
        return START_NOT_STICKY
    }
}
