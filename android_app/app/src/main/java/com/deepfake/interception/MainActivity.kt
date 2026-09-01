package com.deepfake.interception

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private val PERMISSION_REQUEST_CODE = 101
    private lateinit var statusTextView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(64, 128, 64, 64)
            gravity = android.view.Gravity.CENTER_HORIZONTAL
        }

        val titleView = TextView(this).apply {
            text = "🛡️ Deepfake Voice Interceptor"
            textSize = 24f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(android.graphics.Color.parseColor("#1565C0"))
            setPadding(0, 0, 0, 16)
        }

        val descView = TextView(this).apply {
            text = "Real-Time AI Interception System\n99.54% Accuracy | 0.39% EER"
            textSize = 14f
            gravity = android.view.Gravity.CENTER
            setPadding(0, 0, 0, 48)
        }

        statusTextView = TextView(this).apply {
            text = "Status: Inactive"
            textSize = 16f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(android.graphics.Color.parseColor("#757575"))
            gravity = android.view.Gravity.CENTER
            setPadding(0, 0, 0, 48)
        }

        val startBtn = Button(this).apply {
            text = "▶ Start Real-Time Interception"
            textSize = 16f
            setBackgroundColor(android.graphics.Color.parseColor("#2E7D32"))
            setTextColor(android.graphics.Color.WHITE)
            setPadding(32, 24, 32, 24)
            setOnClickListener { checkPermissionsAndStart() }
        }

        val permBtn = Button(this).apply {
            text = "⚙️ Open App Permissions Settings"
            textSize = 14f
            setBackgroundColor(android.graphics.Color.parseColor("#1565C0"))
            setTextColor(android.graphics.Color.WHITE)
            setPadding(32, 16, 32, 16)
            setOnClickListener { openAppSettings() }
        }

        val stopBtn = Button(this).apply {
            text = "⏹ Stop Interception"
            textSize = 14f
            setBackgroundColor(android.graphics.Color.parseColor("#C62828"))
            setTextColor(android.graphics.Color.WHITE)
            setOnClickListener { stopInterceptionService() }
        }

        layout.addView(titleView)
        layout.addView(descView)
        layout.addView(statusTextView)
        layout.addView(startBtn)
        layout.addView(permBtn)
        layout.addView(stopBtn)

        setContentView(layout)
    }

    override fun onResume() {
        super.onResume()
        updatePermissionStatus()
    }

    private fun updatePermissionStatus() {
        val hasMic = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val hasPhone = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
        val hasOverlay = Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)

        if (hasMic && hasPhone && hasOverlay) {
            statusTextView.text = "Status: Ready to Intercept ✅"
            statusTextView.setTextColor(android.graphics.Color.parseColor("#2E7D32"))
        } else {
            statusTextView.text = "Status: Permissions Required ⚠️\n(Mic: ${if(hasMic) "OK" else "Missing"}, Phone: ${if(hasPhone) "OK" else "Missing"}, Overlay: ${if(hasOverlay) "OK" else "Missing"})"
            statusTextView.setTextColor(android.graphics.Color.parseColor("#D32F2F"))
        }
    }

    private fun checkPermissionsAndStart() {
        val permissionsNeeded = mutableListOf<String>()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.RECORD_AUDIO)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.READ_PHONE_STATE)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (permissionsNeeded.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsNeeded.toTypedArray(), PERMISSION_REQUEST_CODE)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            startActivity(intent)
            Toast.makeText(this, "Please enable 'Display over other apps' toggle for Deepfake Interceptor", Toast.LENGTH_LONG).show()
        } else {
            startInterceptionService()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        updatePermissionStatus()
        if (requestCode == PERMISSION_REQUEST_CODE) {
            var allGranted = true
            for (result in grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false
                    break
                }
            }
            if (allGranted) {
                checkPermissionsAndStart()
            } else {
                Toast.makeText(this, "Permissions denied by system. Tap 'Open App Permissions Settings' to enable manually.", Toast.LENGTH_LONG).show()
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
            statusTextView.text = "Status: Interception Active 🛡️"
            statusTextView.setTextColor(android.graphics.Color.parseColor("#1565C0"))
            Toast.makeText(this, "🛡️ Real-Time Interception Active!", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Error starting service: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun stopInterceptionService() {
        val intent = Intent(this, CallOverlayService::class.java)
        stopService(intent)
        statusTextView.text = "Status: Stopped"
        statusTextView.setTextColor(android.graphics.Color.parseColor("#757575"))
        Toast.makeText(this, "Interception Stopped", Toast.LENGTH_SHORT).show()
    }
}
