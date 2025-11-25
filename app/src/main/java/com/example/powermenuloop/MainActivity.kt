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
import kotlin.math.max


class MainActivity : AppCompatActivity() {

    private lateinit var statusTextView: TextView
    private lateinit var settingsTextView: TextView
    private lateinit var remainingTextView: TextView
    private lateinit var locationTextView: TextView
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
        remainingTextView = findViewById(R.id.tv_remaining)
        locationTextView = findViewById(R.id.tv_location)


        // 設定値を表示
        val warningSec = Constants.WARNING_THRESHOLD_MS / 1000
        val loopSec = Constants.LOOP_THRESHOLD_MS / 1000
        settingsTextView.text = "Settings: Warn ${warningSec}s / Loop ${loopSec}s"

        val button = findViewById<Button>(R.id.btn_accessibility_settings)
        button.setOnClickListener {
            openAccessibilitySettings()
        }

        val button2 = findViewById<Button>(R.id.btn_display_powermenu)
        button2.setOnClickListener {
            openPowerMenu()
        }

        // 位置情報権限のリクエスト
        checkAndRequestLocationPermission()
        
        // 初期状態の更新
        updateMonitoringStatus()
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
        var location = LocationHelper.getInstance(this).getCurrentLocation()
        locationTextView.text = "Location:\nLatitude: ${location?.latitude ?: "--"}, Longitude: ${location?.longitude ?: "--"}"

        updateMonitoringStatus()
    }

    private fun updateMonitoringStatus() {
        // SharedPreferencesから最新の状態を取得して表示（初期表示用、およびサービス未接続時用）
        val prefs = getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        val accumulatedUsage = prefs.getLong(Constants.KEY_ACCUMULATED_USAGE, 0)
        val lastSessionEndTime = prefs.getLong(Constants.KEY_LAST_SESSION_END, 0)

        // リセット判定（簡易的に実装、Service側とロジックを合わせる）
        val now = System.currentTimeMillis()
        var currentAccumulated = accumulatedUsage
        if (lastSessionEndTime > 0 && (now - lastSessionEndTime) > Constants.TIMER_MAINTAIN_DURATION_MS) {
            currentAccumulated = 0
        }

        val estimatedRemaining = max(0, Constants.LOOP_THRESHOLD_MS - currentAccumulated)
        remainingTextView.text = "Remaining: ${estimatedRemaining / 1000}s"

        if (isAccessibilityServiceEnabled(PowerMenuService::class.java)) {
            Log.d(TAG, "Accessibility Service is ON")
            statusTextView.text = "Monitoring Active"
            
            PowerMenuService.onStatusUpdateListener = { _, remaining ->
                runOnUiThread {
                    // ステータスと残り時間をそれぞれのTextViewに表示
                    // パッケージ名は削除
                    remainingTextView.text = "Remaining: ${remaining / 1000}s"
                }
            }
            
            val service = PowerMenuService.instance
            if (service != null) {
                service.requestStatusUpdate()
            } else {
                 Log.d(TAG, "Service instance is null")
            }
        } else {
            Log.d(TAG, "Accessibility Service is OFF")
            statusTextView.text = "Accessibility Service is OFF"
            // OFFのときもRemainingは表示したままにする（直近の保存値）
        }
    }
    
    override fun onPause() {
        super.onPause()
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
