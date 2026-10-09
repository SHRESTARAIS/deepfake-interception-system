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
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.telecom.TelecomManager
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat

class CallOverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var overlayContainer: LinearLayout? = null
    private var statusTextView: TextView? = null
    private var subTextView: TextView? = null
    private var muteBtn: Button? = null
    private var dropBtn: Button? = null

    private lateinit var classifier: DeepfakeClassifier
    private lateinit var audioProcessor: AudioProcessor
    private var audioManager: AudioManager? = null

    // Track whether the call audio is muted by our app
    private var callMuted = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Log.i("DeepfakeInterceptor", "CallOverlayService created")
        startForegroundServiceNotification()

        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        // Enable speakerphone so AudioRecord can capture the caller's voice
        // (Android blocks earpiece audio from AudioRecord â€” speakerphone routes it through mic)
        audioManager?.isSpeakerphoneOn = true
        audioManager?.mode = AudioManager.MODE_IN_CALL
        Log.i("DeepfakeInterceptor", "Speakerphone enabled for audio capture")

        try {
            classifier = DeepfakeClassifier(this)
            setupOverlayWindow()

            var lastVerdictTime = 0L

            audioProcessor = AudioProcessor(classifier) { result ->
                val currentTime = System.currentTimeMillis()

                if (result == null) {
                    // Revert to MONITORING after 2.0s of complete silence
                    if (currentTime - lastVerdictTime > 2000L) {
                        updateOverlayUI(state = OverlayState.MONITORING)
                        Log.i("DeepfakeInterceptor", "[USB_CABLE_STREAM] STATUS:MONITORING:Monitoring Voice Audio...")
                    }
                } else {
                    lastVerdictTime = currentTime
                    val probFake = result.probFake
                    val percentage = (probFake * 100).toInt()

                    if (probFake > 0.50f) {
                        updateOverlayUI(state = OverlayState.THREAT, confidence = percentage)
                        triggerSensoryAlerts(this)
                        Log.i("DeepfakeInterceptor", "[USB_CABLE_STREAM] ALERT:DEEPFAKE:$percentage")
                    } else {
                        val realPct = 100 - percentage
                        updateOverlayUI(state = OverlayState.SAFE, confidence = realPct)
                        Log.i("DeepfakeInterceptor", "[USB_CABLE_STREAM] ALERT:REAL:$realPct")
                    }
                }
            }

            audioProcessor.startListening()
        } catch (t: Throwable) {
            Log.e("DeepfakeInterceptor", "Error initializing CallOverlayService: ${t.message}")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    // MULTI-SENSORY ALERT SYSTEM
    // Tier 1: Haptic cheek alert (Morse-code vibration pattern against face)
    // Tier 2: In-ear ToneGenerator beep via STREAM_VOICE_CALL earpiece channel
    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    private fun triggerSensoryAlerts(context: Context) {
        try {
            // TIER 1 â€” Haptic cheek alert
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                val vibrator = vibratorManager.defaultVibrator
                val timings = longArrayOf(0, 500, 200, 500, 200, 500)
                val amplitudes = intArrayOf(0, VibrationEffect.DEFAULT_AMPLITUDE, 0, VibrationEffect.DEFAULT_AMPLITUDE, 0, VibrationEffect.DEFAULT_AMPLITUDE)
                vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                val timings = longArrayOf(0, 500, 200, 500, 200, 500)
                val amplitudes = intArrayOf(0, VibrationEffect.DEFAULT_AMPLITUDE, 0, VibrationEffect.DEFAULT_AMPLITUDE, 0, VibrationEffect.DEFAULT_AMPLITUDE)
                vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                @Suppress("DEPRECATION")
                vibrator.vibrate(longArrayOf(0, 500, 200, 500, 200, 500), -1)
            }
        } catch (e: Exception) {
            Log.e("DeepfakeInterceptor", "Vibration error: ${e.message}")
        }

        try {
            // TIER 2 â€” In-ear double beep directly into earpiece (STREAM_VOICE_CALL)
            val toneGen = ToneGenerator(AudioManager.STREAM_VOICE_CALL, 80)
            toneGen.startTone(ToneGenerator.TONE_PROP_BEEP2, 400)
        } catch (e: Exception) {
            Log.e("DeepfakeInterceptor", "ToneGenerator error: ${e.message}")
        }
    }

    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    // OVERLAY STATES
    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    private enum class OverlayState { MONITORING, SAFE, THREAT }

    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    // OVERLAY WINDOW SETUP â€” Pill-shaped frosted glass banner
    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
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
            y = 60
        }

        // Outer pill container â€” deep translucent charcoal, pill-shaped
        overlayContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(20), dp(14), dp(20), dp(14))
            background = buildPillBackground(
                fillColor = Color.parseColor("#CC1A1A2E"),
                strokeColor = Color.parseColor("#3D5AFE"),
                strokeWidth = dp(2).toFloat()
            )
        }

        // Primary status text â€” large bold white
        statusTextView = TextView(this).apply {
            text = "Aegis Voice â€” Monitoring"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }

        // Sub-text line â€” smaller, dim
        subTextView = TextView(this).apply {
            text = "AI audio analysis active"
            setTextColor(Color.parseColor("#B0BEC5"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            gravity = Gravity.CENTER
            setPadding(0, dp(2), 0, dp(6))
        }

        // Action button row
        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        muteBtn = Button(this).apply {
            text = "Mute Call"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setTextColor(Color.WHITE)
            background = buildPillBackground(
                fillColor = Color.parseColor("#E65100"),
                strokeColor = Color.parseColor("#FF6D00"),
                strokeWidth = dp(1).toFloat()
            )
            val lp = LinearLayout.LayoutParams(dp(100), dp(34))
            lp.marginEnd = dp(10)
            layoutParams = lp
            visibility = View.GONE
            setOnClickListener { toggleMuteCall() }
        }

        dropBtn = Button(this).apply {
            text = "Drop Call"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setTextColor(Color.WHITE)
            background = buildPillBackground(
                fillColor = Color.parseColor("#B71C1C"),
                strokeColor = Color.parseColor("#FF1744"),
                strokeWidth = dp(1).toFloat()
            )
            layoutParams = LinearLayout.LayoutParams(dp(100), dp(34))
            visibility = View.GONE
            setOnClickListener { dropCall() }
        }

        btnRow.addView(muteBtn)
        btnRow.addView(dropBtn)

        overlayContainer?.addView(statusTextView)
        overlayContainer?.addView(subTextView)
        overlayContainer?.addView(btnRow)

        try {
            windowManager?.addView(overlayContainer, params)
        } catch (e: Exception) {
            Log.e("DeepfakeInterceptor", "Failed to add overlay window: ${e.message}")
        }
    }

    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    // UPDATE OVERLAY UI â€” Switches between MONITORING / SAFE / THREAT states
    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    private fun updateOverlayUI(state: OverlayState, confidence: Int = 0) {
        overlayContainer?.post {
            overlayContainer?.visibility = View.VISIBLE
            when (state) {
                OverlayState.MONITORING -> {
                    statusTextView?.text = "Aegis Voice â€” Monitoring"
                    subTextView?.text = "AI audio analysis active"
                    overlayContainer?.background = buildPillBackground(
                        fillColor = Color.parseColor("#CC1A1A2E"),
                        strokeColor = Color.parseColor("#3D5AFE"),
                        strokeWidth = dp(2).toFloat()
                    )
                    muteBtn?.visibility = View.GONE
                    dropBtn?.visibility = View.GONE
                    Log.i("DeepfakeInterceptor", "[USB_CABLE_STREAM] STATUS:MONITORING:Monitoring Voice Audio...")
                }
                OverlayState.SAFE -> {
                    statusTextView?.text = "Voice Verified â€” Real Human"
                    subTextView?.text = "Confidence: $confidence% authentic"
                    overlayContainer?.background = buildPillBackground(
                        fillColor = Color.parseColor("#CC0D2B0D"),
                        strokeColor = Color.parseColor("#00E676"),
                        strokeWidth = dp(2).toFloat()
                    )
                    muteBtn?.visibility = View.GONE
                    dropBtn?.visibility = View.GONE
                }
                OverlayState.THREAT -> {
                    statusTextView?.text = "UNVERIFIED AI SYNTHETIC VOICE SUSPECTED"
                    subTextView?.text = "Fake probability: $confidence%"
                    overlayContainer?.background = buildPillBackground(
                        fillColor = Color.parseColor("#CC2D0000"),
                        strokeColor = Color.parseColor("#FF1744"),
                        strokeWidth = dp(3).toFloat()
                    )
                    muteBtn?.visibility = View.VISIBLE
                    dropBtn?.visibility = View.VISIBLE
                }
            }
        }
    }

    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    // INTERACTIVE BUTTONS â€” Mute / Drop Call
    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    private fun toggleMuteCall() {
        callMuted = !callMuted
        audioManager?.isMicrophoneMute = callMuted
        muteBtn?.text = if (callMuted) "Unmute" else "Mute Call"
        Log.i("DeepfakeInterceptor", "Call mic muted: $callMuted")
    }

    private fun dropCall() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val telecom = getSystemService(Context.TELECOM_SERVICE) as TelecomManager
                telecom.endCall()
                Log.i("DeepfakeInterceptor", "Call ended via TelecomManager")
            } else {
                @Suppress("DEPRECATION")
                audioManager?.let {
                    it.mode = AudioManager.MODE_NORMAL
                }
                Log.w("DeepfakeInterceptor", "Drop call: API < 28, used AudioManager fallback")
            }
        } catch (e: Exception) {
            Log.e("DeepfakeInterceptor", "Drop call error: ${e.message}")
        }
    }

    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    // HELPER â€” Build pill-shaped GradientDrawable background
    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    private fun buildPillBackground(fillColor: Int, strokeColor: Int, strokeWidth: Float): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(50).toFloat()
            setColor(fillColor)
            setStroke(strokeWidth.toInt(), strokeColor)
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density + 0.5f).toInt()

    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    // FOREGROUND SERVICE NOTIFICATION
    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    private fun startForegroundServiceNotification() {
        val channelId = "aegis_voice_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Real-Time Audio Deepfake Interception",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Real-Time Audio Deepfake Interception Active")
            .setContentText("Real-time deepfake interception running â€” 1D-LCNN engine online")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1001, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(1001, notification)
        }
    }

    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    // CLEANUP
    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    override fun onDestroy() {
        super.onDestroy()
        Log.i("DeepfakeInterceptor", "CallOverlayService destroyed")

        // Restore normal audio mode
        audioManager?.isSpeakerphoneOn = false
        audioManager?.mode = AudioManager.MODE_NORMAL
        audioManager?.isMicrophoneMute = false

        try { audioProcessor.stopListening() } catch (e: Exception) {}

        if (overlayContainer != null) {
            try { windowManager?.removeView(overlayContainer) } catch (e: Exception) {}
        }
    }
}

