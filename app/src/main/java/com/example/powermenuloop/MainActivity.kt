package com.example.powermenuloop

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class MainActivity : AppCompatActivity() {
    
    private lateinit var statusTextView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        statusTextView = findViewById(R.id.tv_status)

        val button = findViewById<Button>(R.id.btn_accessibility_settings)
        button.setOnClickListener {
            openAccessibilitySettings()
        }

        val button2 = findViewById<Button>(R.id.btn_display_powermenu)
        button2.setOnClickListener {
            openPowerMenu()
        }
    }

    override fun onResume() {
        super.onResume()
        if (isAccessibilityServiceEnabled(PowerMenuService::class.java)) {
            // Toast.makeText(this, "Accessibility Service is Enabled!", Toast.LENGTH_SHORT).show()
            
            // サービスからのステータス更新を受け取る
            PowerMenuService.onStatusUpdateListener = { status ->
                runOnUiThread {
                    statusTextView.text = status
                }
            }
        } else {
            statusTextView.text = "Accessibility Service is OFF"
        }
    }
    
    override fun onPause() {
        super.onPause()
        // メモリリーク防止のためリスナーを解除
        PowerMenuService.onStatusUpdateListener = null
    }

    private fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        startActivity(intent)
    }

    private fun openPowerMenu() {
        val service = PowerMenuService.instance
        if (service != null) {
            service.showPowerMenu()
        } else {
            Toast.makeText(this, "Service is not connected", Toast.LENGTH_SHORT).show()
        }
    }

    private fun isAccessibilityServiceEnabled(service: Class<out Any>): Boolean {
        val am = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        for (enabledService in enabledServices) {
            val serviceInfo = enabledService.resolveInfo.serviceInfo
            if (serviceInfo.packageName == packageName && serviceInfo.name == service.name) {
                return true
            }
        }
        return false
    }
}
