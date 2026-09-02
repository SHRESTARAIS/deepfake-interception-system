package com.deepfake.interception

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.core.app.NotificationCompat

class CallOverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private var overlayView: View? = null
    private var statusTextView: TextView? = null

    private lateinit var classifier: DeepfakeClassifier
    private lateinit var audioProcessor: AudioProcessor

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                startForeground(
                    1001,
                    createNotification("Monitoring active call..."),
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                )
            } else {
                startForeground(1001, createNotification("Monitoring active call..."))
            }
        } catch (e: Exception) {
            Log.e("DeepfakeInterceptor", "startForeground Exception caught safely: ${e.message}")
        }

        try {
            classifier = DeepfakeClassifier(this)
            setupOverlayWindow()

            audioProcessor = AudioProcessor(classifier) { result ->
                if (result == null) {
                    updateOverlayUI(
                        text = "🛡️ Monitoring Voice Audio...",
                        backgroundColor = Color.parseColor("#1976D2") // BLUE
                    )
                    Log.d("DeepfakeInterceptor", "[USB_CABLE_STREAM] STATUS:MONITORING:Monitoring Voice Audio...")
                } else {
                    val probFake = result.probFake
                    val percentage = (probFake * 100).toInt()

                    if (probFake > 0.50f) {
                        val alertMsg = "🚨 WARNING: SUSPECTED DEEPFAKE VOICE ($percentage%)"
                        updateOverlayUI(
                            text = alertMsg,
                            backgroundColor = Color.parseColor("#D32F2F") // RED
                        )
                        Log.d("DeepfakeInterceptor", "[USB_CABLE_STREAM] ALERT:DEEPFAKE:$percentage:$alertMsg")
                    } else {
                        val realPct = 100 - percentage
                        val okMsg = "🛡️ REAL VOICE VERIFIED ($realPct%)"
                        updateOverlayUI(
                            text = okMsg,
                            backgroundColor = Color.parseColor("#388E3C") // GREEN
                        )
                        Log.d("DeepfakeInterceptor", "[USB_CABLE_STREAM] ALERT:REAL:$realPct:$okMsg")
                    }
                }
            }

            audioProcessor.startListening()
        } catch (t: Throwable) {
            Log.e("DeepfakeInterceptor", "CallOverlayService onCreate Exception: ${t.message}")
        }
    }

    private fun setupOverlayWindow() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
                Log.w("DeepfakeInterceptor", "Overlay permission missing, skipping addView to avoid crash")
                return
            }

            windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                else
                    WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                y = 100
            }

            val textView = TextView(this).apply {
                text = "🛡️ Monitoring Voice Audio..."
                setTextColor(Color.WHITE)
                textSize = 16f
                setPadding(32, 24, 32, 24)
                setBackgroundColor(Color.parseColor("#1976D2")) // BLUE
                gravity = Gravity.CENTER
            }

            statusTextView = textView
            overlayView = textView
            windowManager.addView(overlayView, params)
        } catch (e: Exception) {
            Log.e("DeepfakeInterceptor", "Error adding overlay window: ${e.message}")
        }
    }

    private fun updateOverlayUI(text: String, backgroundColor: Int) {
        statusTextView?.post {
            statusTextView?.text = text
            statusTextView?.setBackgroundColor(backgroundColor)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        audioProcessor.stopListening()
        classifier.close()
        if (overlayView != null) {
            windowManager.removeView(overlayView)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "DEEPFAKE_CHANNEL",
                "Deepfake Interception",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(content: String): Notification {
        return NotificationCompat.Builder(this, "DEEPFAKE_CHANNEL")
            .setContentTitle("Deepfake Interceptor Active")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
