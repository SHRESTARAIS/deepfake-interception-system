package com.deepfake.interception

import android.Manifest
import android.animation.ValueAnimator
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlin.math.sin

class MainActivity : AppCompatActivity() {

    private val PERMISSION_REQUEST_CODE = 101

    // Status card views we'll update at runtime
    private lateinit var micStatusDot: View
    private lateinit var micStatusLabel: TextView
    private lateinit var phoneStatusDot: View
    private lateinit var phoneStatusLabel: TextView
    private lateinit var overlayStatusDot: View
    private lateinit var overlayStatusLabel: TextView
    private lateinit var serviceStatusCard: LinearLayout
    private lateinit var serviceStatusText: TextView
    private lateinit var waveView: WaveformView

    // Colour constants for the dark theme
    private val colBg = Color.parseColor("#0F0F14")         // deep space black
    private val colSurface = Color.parseColor("#1A1A2E")    // dark navy card
    private val colBorder = Color.parseColor("#2A2A45")     // subtle border
    private val colAccent = Color.parseColor("#3D5AFE")     // indigo accent
    private val colGreen = Color.parseColor("#00E676")      // safe neon green
    private val colAmber = Color.parseColor("#FFB300")      // warning amber
    private val colRed = Color.parseColor("#FF1744")        // threat red
    private val colText = Color.parseColor("#ECEFF1")       // near-white text
    private val colSubText = Color.parseColor("#78909C")    // muted text

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // â”€â”€ Root scroll container â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        val scrollView = ScrollView(this).apply {
            setBackgroundColor(colBg)
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(48), dp(20), dp(32))
        }

        // â”€â”€ Top header â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(28))
        }

        val shieldIcon = TextView(this).apply {
            text = "â¬¡"                    // hexagon as shield stand-in
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 38f)
            setTextColor(colAccent)
            setPadding(0, 0, dp(14), 0)
        }

        val titleBlock = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val appTitle = TextView(this).apply {
            text = "Real-Time Audio Deepfake Interception"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f)
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(colText)
        }

        val appSub = TextView(this).apply {
            text = "Real-Time Deepfake Interception"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(colSubText)
            letterSpacing = 0.08f
        }

        titleBlock.addView(appTitle)
        titleBlock.addView(appSub)
        headerRow.addView(shieldIcon)
        headerRow.addView(titleBlock)
        root.addView(headerRow)

        // â”€â”€ Shield Status Card (pulsing ring with "Active Protection") â”€â”€â”€â”€â”€â”€â”€â”€
        val shieldCard = buildCard()
        shieldCard.gravity = Gravity.CENTER
        shieldCard.setPadding(dp(20), dp(24), dp(20), dp(24))

        // Animated ring text indicator
        val shieldRingLabel = TextView(this).apply {
            text = "â—‰  ACTIVE PROTECTION"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(colGreen)
            gravity = Gravity.CENTER
            letterSpacing = 0.12f
        }

        val shieldSubLabel = TextView(this).apply {
            text = "1D-LCNN Core Engine Online\nOn-Device ONNX Runtime - 100% Offline"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(colSubText)
            gravity = Gravity.CENTER
            setPadding(0, dp(6), 0, 0)
        }

        // Pulse animation on the ring label
        val pulseAnimator = ValueAnimator.ofFloat(0.4f, 1f).apply {
            duration = 1800
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = LinearInterpolator()
            addUpdateListener { shieldRingLabel.alpha = it.animatedValue as Float }
            start()
        }

        shieldCard.addView(shieldRingLabel)
        shieldCard.addView(shieldSubLabel)
        root.addView(shieldCard)
        root.addView(spacer(dp(16)))

        // â”€â”€ Permission Status Micro-Cards â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        val permSectionLabel = makeSectionLabel("System Status")
        root.addView(permSectionLabel)
        root.addView(spacer(dp(10)))

        val (micCard, micDot, micLbl) = buildStatusMicroCard("Microphone", "RECORD_AUDIO")
        val (phoneCard, phoneDot, phoneLbl) = buildStatusMicroCard("Phone State", "READ_PHONE_STATE")
        val (overlayCard, overlayDot, overlayLbl) = buildStatusMicroCard("Overlay Permission", "SYSTEM_ALERT_WINDOW")

        micStatusDot = micDot; micStatusLabel = micLbl
        phoneStatusDot = phoneDot; phoneStatusLabel = phoneLbl
        overlayStatusDot = overlayDot; overlayStatusLabel = overlayLbl

        root.addView(micCard)
        root.addView(spacer(dp(8)))
        root.addView(phoneCard)
        root.addView(spacer(dp(8)))
        root.addView(overlayCard)
        root.addView(spacer(dp(16)))

        // â”€â”€ Service Status Card â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        val engineLabel = makeSectionLabel("Interception Engine")
        root.addView(engineLabel)
        root.addView(spacer(dp(10)))

        serviceStatusCard = buildCard()
        serviceStatusCard.orientation = LinearLayout.HORIZONTAL
        serviceStatusCard.gravity = Gravity.CENTER_VERTICAL
        serviceStatusCard.setPadding(dp(18), dp(16), dp(18), dp(16))

        val engineIcon = TextView(this).apply {
            text = "â—ˆ"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            setTextColor(colAccent)
            setPadding(0, 0, dp(14), 0)
        }

        val engineTextBlock = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        serviceStatusText = TextView(this).apply {
            text = "Inactive â€” tap Start to arm"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(colSubText)
        }

        val engineSubText = TextView(this).apply {
            text = "Core Engine: 1D-LCNN Â· ONNX Runtime"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setTextColor(colSubText)
            setPadding(0, dp(3), 0, 0)
        }

        engineTextBlock.addView(serviceStatusText)
        engineTextBlock.addView(engineSubText)
        serviceStatusCard.addView(engineIcon)
        serviceStatusCard.addView(engineTextBlock)
        root.addView(serviceStatusCard)
        root.addView(spacer(dp(24)))

        // â”€â”€ Action Buttons â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        val startBtn = buildActionButton(
            label = "Start Real-Time Interception",
            fillColor = Color.parseColor("#1B5E20"),
            strokeColor = colGreen
        )
        startBtn.setOnClickListener { checkPermissionsAndStart() }

        val permBtn = buildActionButton(
            label = "Open App Permission Settings",
            fillColor = Color.parseColor("#0D1C3F"),
            strokeColor = colAccent
        )
        permBtn.setOnClickListener { openAppSettings() }

        val stopBtn = buildActionButton(
            label = "Stop Interception",
            fillColor = Color.parseColor("#3B0000"),
            strokeColor = colRed
        )
        stopBtn.setOnClickListener { stopInterceptionService() }

        root.addView(startBtn)
        root.addView(spacer(dp(10)))
        root.addView(permBtn)
        root.addView(spacer(dp(10)))
        root.addView(stopBtn)
        root.addView(spacer(dp(28)))

        // â”€â”€ Live Oscilloscope / Waveform Strip â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        val waveSectionLabel = makeSectionLabel("Live Audio Monitor")
        root.addView(waveSectionLabel)
        root.addView(spacer(dp(10)))

        val waveCard = buildCard()
        waveCard.setPadding(dp(12), dp(12), dp(12), dp(12))

        waveView = WaveformView(this)
        waveView.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(70)
        )
        waveCard.addView(waveView)
        root.addView(waveCard)
        root.addView(spacer(dp(12)))

        val waveFooter = TextView(this).apply {
            text = "Offline Â· Background Audio Interception Active"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            setTextColor(colSubText)
            gravity = Gravity.CENTER
            letterSpacing = 0.06f
        }
        root.addView(waveFooter)

        scrollView.addView(root)
        setContentView(scrollView)
    }

    override fun onResume() {
        super.onResume()
        updatePermissionStatus()
    }

    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    // PERMISSION HELPERS
    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    private fun updatePermissionStatus() {
        val hasMic = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val hasPhone = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
        val hasOverlay = Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)

        setDotStatus(micStatusDot, micStatusLabel, hasMic, "Microphone")
        setDotStatus(phoneStatusDot, phoneStatusLabel, hasPhone, "Phone State")
        setDotStatus(overlayStatusDot, overlayStatusLabel, hasOverlay, "Overlay")
    }

    private fun setDotStatus(dot: View, label: TextView, granted: Boolean, name: String) {
        dot.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(if (granted) colGreen else colAmber)
        }
        label.text = "$name: ${if (granted) "Granted" else "Required"}"
        label.setTextColor(if (granted) colText else colAmber)
    }

    private fun checkPermissionsAndStart() {
        val permissionsNeeded = mutableListOf<String>()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
            permissionsNeeded.add(Manifest.permission.RECORD_AUDIO)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED)
            permissionsNeeded.add(Manifest.permission.READ_PHONE_STATE)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED)
            permissionsNeeded.add(Manifest.permission.READ_CALL_LOG)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                permissionsNeeded.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        if (permissionsNeeded.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsNeeded.toTypedArray(), PERMISSION_REQUEST_CODE)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            startActivity(intent)
            Toast.makeText(this, "Enable 'Display over other apps' for Deepfake Interceptor", Toast.LENGTH_LONG).show()
        } else {
            startInterceptionService()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        updatePermissionStatus()
        if (requestCode == PERMISSION_REQUEST_CODE) {
            val allGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            if (allGranted) {
                checkPermissionsAndStart()
            } else {
                Toast.makeText(this, "Some permissions denied. Tap 'Open App Permission Settings' to enable manually.", Toast.LENGTH_LONG).show()
                openAppSettings()
            }
        }
    }

    private fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
        }
        startActivity(intent)
    }

    private fun startInterceptionService() {
        try {
            val intent = Intent(this, CallOverlayService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            serviceStatusText.text = "Active â€” interception armed"
            serviceStatusText.setTextColor(colGreen)
            waveView.start()
            Toast.makeText(this, "Real-Time Interception Active", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Error starting service: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun stopInterceptionService() {
        val intent = Intent(this, CallOverlayService::class.java)
        stopService(intent)
        serviceStatusText.text = "Inactive â€” tap Start to arm"
        serviceStatusText.setTextColor(colSubText)
        waveView.stop()
        Toast.makeText(this, "Interception Stopped", Toast.LENGTH_SHORT).show()
    }

    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    // UI BUILDER HELPERS
    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    /** Builds a dark navy rounded card LinearLayout */
    private fun buildCard(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(16).toFloat()
            setColor(colSurface)
            setStroke(dp(1), colBorder)
        }
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
    }

    /**
     * Builds a permission status micro-card with a dot + label.
     * Returns Triple(card, dotView, labelView).
     */
    private fun buildStatusMicroCard(name: String, permDesc: String): Triple<LinearLayout, View, TextView> {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(18), dp(13), dp(18), dp(13))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(12).toFloat()
                setColor(colSurface)
                setStroke(dp(1), colBorder)
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val dot = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(10), dp(10)).also {
                it.marginEnd = dp(14)
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(colSubText)
            }
        }

        val textBlock = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val nameLabel = TextView(this).apply {
            text = name
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(colText)
        }

        val permLabel = TextView(this).apply {
            text = "Checking..."
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setTextColor(colSubText)
        }

        textBlock.addView(nameLabel)
        textBlock.addView(permLabel)
        card.addView(dot)
        card.addView(textBlock)

        return Triple(card, dot, permLabel)
    }

    /** Builds a pill-shaped, bordered action button */
    private fun buildActionButton(label: String, fillColor: Int, strokeColor: Int): Button =
        Button(this).apply {
            text = label
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(colText)
            typeface = Typeface.DEFAULT_BOLD
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(50).toFloat()
                setColor(fillColor)
                setStroke(dp(1), strokeColor)
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(52)
            )
            elevation = 0f
            stateListAnimator = null
            isAllCaps = false
        }

    private fun makeSectionLabel(text: String): TextView = TextView(this).apply {
        this.text = text.uppercase()
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
        setTextColor(colSubText)
        letterSpacing = 0.16f
        setPadding(dp(4), 0, 0, 0)
    }

    private fun spacer(height: Int): View = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, height)
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density + 0.5f).toInt()

    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    // INNER CLASS â€” Live oscilloscope waveform animation (Canvas-based)
    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    inner class WaveformView(context: android.content.Context) : View(context) {

        private val wavePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#3D5AFE")
            strokeWidth = 3f
            style = Paint.Style.STROKE
        }
        private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#403D5AFE")
            strokeWidth = 8f
            style = Paint.Style.STROKE
        }

        private var phase = 0f
        private var running = false
        private val animator = ValueAnimator.ofFloat(0f, (2 * Math.PI).toFloat()).apply {
            duration = 1200
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                phase = it.animatedValue as Float
                invalidate()
            }
        }

        fun start() { running = true; animator.start() }
        fun stop() { running = false; animator.cancel(); invalidate() }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            if (!running) {
                // Flat line when not active
                val midY = height / 2f
                canvas.drawLine(0f, midY, width.toFloat(), midY, wavePaint)
                return
            }
            val midY = height / 2f
            val amplitude = height / 3f
            val path = android.graphics.Path()
            var first = true
            for (x in 0..width) {
                // Two overlapping sine waves at different frequencies for organic look
                val y = midY +
                        amplitude * 0.65f * sin((x.toFloat() / width * 4 * Math.PI + phase).toFloat()).toFloat() +
                        amplitude * 0.35f * sin((x.toFloat() / width * 9 * Math.PI + phase * 1.7f).toFloat()).toFloat()
                if (first) { path.moveTo(x.toFloat(), y); first = false }
                else path.lineTo(x.toFloat(), y)
            }
            canvas.drawPath(path, glowPaint)
            canvas.drawPath(path, wavePaint)
        }
    }
}

