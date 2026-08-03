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
import android.view.Gravity
import android.view.LayoutInflater
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
        startForeground(1001, createNotification("Monitoring active call..."))

        classifier = DeepfakeClassifier(this)
        setupOverlayWindow()

        audioProcessor = AudioProcessor(classifier) { probFake ->
            val percentage = (probFake * 100).toInt()
            if (probFake > 0.5f) {
                updateOverlayUI(
                    text = "🚨 WARNING: SUSPECTED DEEPFAKE VOICE ($percentage%)",
                    backgroundColor = Color.parseColor("#D32F2F") // RED
                )
            } else {
                updateOverlayUI(
                    text = "🛡️ REAL VOICE VERIFIED (${100 - percentage}%)",
                    backgroundColor = Color.parseColor("#388E3C") // GREEN
                )
            }
        }

        audioProcessor.startListening()
    }

    private fun setupOverlayWindow() {
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
            text = "🛡️ Initializing Deepfake Interceptor..."
            setTextColor(Color.WHITE)
            textSize = 16f
            setPadding(32, 24, 32, 24)
            setBackgroundColor(Color.parseColor("#1976D2")) // BLUE
            gravity = Gravity.CENTER
        }

        statusTextView = textView
        overlayView = textView
        windowManager.addView(overlayView, params)
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
