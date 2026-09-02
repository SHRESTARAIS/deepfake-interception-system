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
        startForegroundNotification("🛡️ Monitoring Voice Audio...")

        try {
            classifier = DeepfakeClassifier(this)
            setupOverlayWindow()

            var lastVerdictTime = 0L
            var lastText = "🛡️ Monitoring Voice Audio..."
            var lastBgColor = Color.parseColor("#1976D2")

            audioProcessor = AudioProcessor(classifier) { result ->
                val currentTime = System.currentTimeMillis()

                if (result == null) {
                    // Hold last Green/Red verdict for 2.5s during pauses between words to prevent flickering
                    if (currentTime - lastVerdictTime > 2500L) {
                        updateOverlayUI(
                            text = "🛡️ Monitoring Voice Audio...",
                            backgroundColor = Color.parseColor("#1976D2") // BLUE
                        )
                        Log.i("DeepfakeInterceptor", "[USB_CABLE_STREAM] STATUS:MONITORING:Monitoring Voice Audio...")
                    }
                } else {
                    lastVerdictTime = currentTime
                    val probFake = result.probFake
                    val percentage = (probFake * 100).toInt()

                    if (probFake > 0.50f) {
                        val alertMsg = "🚨 WARNING: SUSPECTED DEEPFAKE VOICE ($percentage%)"
                        lastText = alertMsg
                        lastBgColor = Color.parseColor("#D32F2F") // RED
                        updateOverlayUI(text = alertMsg, backgroundColor = lastBgColor)
                        Log.i("DeepfakeInterceptor", "[USB_CABLE_STREAM] ALERT:DEEPFAKE:$percentage:$alertMsg")
                    } else {
                        val realPct = 100 - percentage
                        val okMsg = "🛡️ REAL HUMAN VOICE VERIFIED ($realPct%)"
                        lastText = okMsg
                        lastBgColor = Color.parseColor("#388E3C") // GREEN
                        updateOverlayUI(text = okMsg, backgroundColor = lastBgColor)
                        Log.i("DeepfakeInterceptor", "[USB_CABLE_STREAM] ALERT:REAL:$realPct:$okMsg")
                    }
                }
            }

            audioProcessor.startListening()
        } catch (t: Throwable) {
            Log.e("DeepfakeInterceptor", "CallOverlayService onCreate Exception: ${t.message}")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundNotification("🛡️ Active In-Call Deepfake Interception")
        if (overlayView == null) {
            setupOverlayWindow()
        }
        return START_STICKY
    }

    private fun startForegroundNotification(text: String) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                startForeground(
                    1001,
                    createNotification(text),
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                )
            } else {
                startForeground(1001, createNotification(text))
            }
        } catch (e: Exception) {
            Log.e("DeepfakeInterceptor", "startForeground Exception: ${e.message}")
        }
    }

    private fun setupOverlayWindow() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
                Log.w("DeepfakeInterceptor", "Overlay permission missing, skipping addView to avoid crash")
                return
            }

            windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

            val windowType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE

            val flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                windowType,
                flags,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                y = 80 // Positioned at top of screen over WhatsApp/Phone call header
            }

            val textView = TextView(this).apply {
                text = "🛡️ Monitoring Voice Audio..."
                setTextColor(Color.WHITE)
                textSize = 16f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setPadding(36, 28, 36, 28)
                setBackgroundColor(Color.parseColor("#1976D2")) // BLUE
                gravity = Gravity.CENTER
            }

            statusTextView = textView
            overlayView = textView
            windowManager.addView(overlayView, params)
            Log.i("DeepfakeInterceptor", "Overlay window added successfully to screen top!")
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
            try {
                windowManager.removeView(overlayView)
            } catch (e: Exception) {}
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
