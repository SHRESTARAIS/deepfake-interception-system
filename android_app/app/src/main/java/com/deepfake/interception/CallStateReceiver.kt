package com.deepfake.interception

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager

class CallStateReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == TelephonyManager.ACTION_PHONE_STATE_CHANGED) {
            val stateStr = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
            if (stateStr == TelephonyManager.EXTRA_STATE_OFFHOOK) {
                // Phone Call Answered -> Start Interception Foreground Service
                val serviceIntent = Intent(context, CallOverlayService::class.java)
                context.startForegroundService(serviceIntent)
            } else if (stateStr == TelephonyManager.EXTRA_STATE_IDLE) {
                // Phone Call Ended -> Stop Interception Service
                val serviceIntent = Intent(context, CallOverlayService::class.java)
                context.stopService(serviceIntent)
            }
        }
    }
}
