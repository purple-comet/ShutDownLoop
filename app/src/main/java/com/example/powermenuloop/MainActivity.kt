package com.example.powermenuloop

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.util.Log


class MainActivity : AppCompatActivity() {

    private lateinit var statusTextView: TextView
    private lateinit var settingsTextView: TextView
    private lateinit var remainingTextView: TextView // 追加
    companion object {
        private const val TAG = "PowerMenuLoopMain"
        private const val PERMISSION_REQUEST_LOCATION = 1001
    }

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
        settingsTextView = findViewById(R.id.tv_thresholds)
        remainingTextView = findViewById(R.id.tv_remaining) // 紐付け

        // 設定値を表示
        val warningSec = AppUsageMonitor.WARNING_THRESHOLD_MS / 1000
        val loopSec = AppUsageMonitor.LOOP_THRESHOLD_MS / 1000
        settingsTextView.text = "Settings: Warn ${warningSec}s / Loop ${loopSec}s"

        val button = findViewById<Button>(R.id.btn_accessibility_settings)
        button.setOnClickListener {
            openAccessibilitySettings()
        }

        val button2 = findViewById<Button>(R.id.btn_display_powermenu)
        button2.setOnClickListener {
            openPowerMenu()
        }

        // 位置情報権限のリクエストボタンを追加したいが、レイアウト変更が必要。
        // とりあえず起動時にチェックしてリクエストする
        checkAndRequestLocationPermission()
    }

    private fun checkAndRequestLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), PERMISSION_REQUEST_LOCATION)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_LOCATION) {
            if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                Toast.makeText(this, "Location Permission Granted", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Location Permission Denied", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume()")
        if (isAccessibilityServiceEnabled(PowerMenuService::class.java)) {
            Log.d(TAG, "Accessibility Service is ON")
            PowerMenuService.onStatusUpdateListener = { pkg, total, remaining ->
                runOnUiThread {
                    // ステータスと残り時間をそれぞれのTextViewに表示
                    statusTextView.text = "Monitoring: $pkg\nTotal: ${total / 1000}s"
                    remainingTextView.text = "Remaining: ${remaining / 1000}s"
                }
            }
        } else {
            Log.d(TAG, "Accessibility Service is OFF")
            statusTextView.text = "Accessibility Service is OFF"
            remainingTextView.text = "Remaining: --"
        }
    }
    
    override fun onPause() {
        super.onPause()
        Log.d(TAG, "onPause()")
//        PowerMenuService.onStatusUpdateListener = null
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
