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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate()

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
            setPadding(0, 0, 0, 32)
        }

        val descView = TextView(this).apply {
            text = "Real-Time AI Interception System\n99.54% Accuracy | 0.39% EER"
            textSize = 14f
            gravity = android.view.Gravity.CENTER
            setPadding(0, 0, 0, 64)
        }

        val startBtn = Button(this).apply {
            text = "▶ Start Real-Time Interception Service"
            setBackgroundColor(android.graphics.Color.parseColor("#2E7D32"))
            setTextColor(android.graphics.Color.WHITE)
            setOnClickListener { checkPermissionsAndStart() }
        }

        val stopBtn = Button(this).apply {
            text = "⏹ Stop Service"
            setBackgroundColor(android.graphics.Color.parseColor("#C62828"))
            setTextColor(android.graphics.Color.WHITE)
            setOnClickListener { stopInterceptionService() }
        }

        layout.addView(titleView)
        layout.addView(descView)
        layout.addView(startBtn)
        layout.addView(stopBtn)

        setContentView(layout)
    }

    private fun checkPermissionsAndStart() {
        val permissionsNeeded = mutableListOf<String>()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.RECORD_AUDIO)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.READ_PHONE_STATE)
        }

        if (permissionsNeeded.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsNeeded.toTypedArray(), PERMISSION_REQUEST_CODE)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            startActivity(intent)
            Toast.makeText(this, "Please grant Overlay Permission to display call alerts", Toast.LENGTH_LONG).show()
        } else {
            startInterceptionService()
        }
    }

    private fun startInterceptionService() {
        val intent = Intent(this, CallOverlayService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        Toast.makeText(this, "Real-Time Interception Active!", Toast.LENGTH_SHORT).show()
    }

    private fun stopInterceptionService() {
        val intent = Intent(this, CallOverlayService::class.java)
        stopService(intent)
        Toast.makeText(this, "Interception Stopped", Toast.LENGTH_SHORT).show()
    }
}
