package com.messagate.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Required by Android OS for default SMS app eligibility.
 * Handles incoming WAP Push MMS payloads.
 */
class MmsReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "MessagateMmsReceiver"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        Log.d(TAG, "MMS WAP Push deliver received: action=${intent?.action}")
        // Acknowledge broadcast receipt for compliance
    }
}
