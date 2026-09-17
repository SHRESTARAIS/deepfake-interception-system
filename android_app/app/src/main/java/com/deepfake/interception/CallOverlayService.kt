package com.deepfake.interception

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat

class CallOverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var overlayContainer: LinearLayout? = null
    private var statusTextView: TextView? = null

    private lateinit var classifier: DeepfakeClassifier
    private lateinit var audioProcessor: AudioProcessor

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Log.i("DeepfakeInterceptor", "CallOverlayService created")
        startForegroundServiceNotification()

        try {
            classifier = DeepfakeClassifier(this)
            setupOverlayWindow()

            var lastVerdictTime = 0L

            audioProcessor = AudioProcessor(classifier) { result ->
                val currentTime = System.currentTimeMillis()

                if (result == null) {
                    // Revert to MONITORING after 2.5s of complete silence
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

                    // Clean 50% threshold on 3-frame median filtered probability
                    if (probFake > 0.50f) {
                        val alertMsg = "🚨 WARNING: SUSPECTED DEEPFAKE VOICE ($percentage%)"
                        updateOverlayUI(text = alertMsg, backgroundColor = Color.parseColor("#D32F2F")) // RED
                        Log.i("DeepfakeInterceptor", "[USB_CABLE_STREAM] ALERT:DEEPFAKE:$percentage:$alertMsg")
                    } else {
                        val realPct = 100 - percentage
                        val okMsg = "🛡️ REAL HUMAN VOICE VERIFIED ($realPct%)"
                        updateOverlayUI(text = okMsg, backgroundColor = Color.parseColor("#388E3C")) // GREEN
                        Log.i("DeepfakeInterceptor", "[USB_CABLE_STREAM] ALERT:REAL:$realPct:$okMsg")
                    }
                }
            }

            audioProcessor.startListening()
        } catch (t: Throwable) {
            Log.e("DeepfakeInterceptor", "Error initializing CallOverlayService: ${t.message}")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    private fun startForegroundServiceNotification() {
        val channelId = "deepfake_interceptor_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Deepfake Call Protection",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Deepfake Voice Interceptor Active")
            .setContentText("Monitoring live in-call audio for deepfake interception...")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        if (Build.VERSION.SDK_INT >= 34) { // Android 14
            startForeground(1001, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(1001, notification)
        }
    }

    private fun setupOverlayWindow() {
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = 80 // Position near top of screen over call header
        }

        // Create overlay container programmatically with rounded feel
        overlayContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(32, 24, 32, 24)
            setBackgroundColor(Color.parseColor("#1976D2")) // Default BLUE
        }

        statusTextView = TextView(this).apply {
            text = "🛡️ Monitoring Voice Audio..."
            setTextColor(Color.WHITE)
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }

        overlayContainer?.addView(statusTextView)

        try {
            windowManager?.addView(overlayContainer, params)
        } catch (e: Exception) {
            Log.e("DeepfakeInterceptor", "Failed to add overlay window: ${e.message}")
        }
    }

    private fun updateOverlayUI(text: String, backgroundColor: Int) {
        statusTextView?.post {
            statusTextView?.text = text
            overlayContainer?.setBackgroundColor(backgroundColor)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i("DeepfakeInterceptor", "CallOverlayService destroyed")
        try {
            audioProcessor.stopListening()
        } catch (e: Exception) {}

        if (overlayContainer != null) {
            try {
                windowManager?.removeView(overlayContainer)
            } catch (e: Exception) {}
        }
    }
}
