package com.deepfake.interception

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager

class CallStateReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        try {
            if (intent.action == TelephonyManager.ACTION_PHONE_STATE_CHANGED) {
                val stateStr = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
                if (stateStr == TelephonyManager.EXTRA_STATE_OFFHOOK) {
                    // Phone Call Answered -> Start Interception Foreground Service
                    val serviceIntent = Intent(context, CallOverlayService::class.java)
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        context.startForegroundService(serviceIntent)
                    } else {
                        context.startService(serviceIntent)
                    }
                } else if (stateStr == TelephonyManager.EXTRA_STATE_IDLE) {
                    // Phone Call Ended -> Stop Interception Service
                    val serviceIntent = Intent(context, CallOverlayService::class.java)
                    context.stopService(serviceIntent)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("DeepfakeInterceptor", "CallStateReceiver Exception caught safely: ${e.message}")
        }
    }
}
