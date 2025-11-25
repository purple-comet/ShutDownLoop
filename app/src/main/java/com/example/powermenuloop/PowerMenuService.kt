package com.example.powermenuloop

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Message
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.widget.TextView
import androidx.core.app.ActivityCompat

class PowerMenuService : AccessibilityService() {

    companion object {
        var instance: PowerMenuService? = null
        private const val TAG = "PowerMenuService"
        // 状態更新用のリスナー（引数を3つに変更）
        var onStatusUpdateListener: ((String, Long) -> Unit)? = null
    }

    private lateinit var appUsageMonitor: AppUsageMonitor

    override fun onCreate() {
        super.onCreate()
        initMonitor()
    }


    private fun initMonitor() {
        appUsageMonitor = AppUsageMonitor(
            context = this, // コンテキストを渡す（SharedPreferences用）
            isNearHome = { isNearHome() },
            onWarningThresholdReached = { message ->
                showWarningOverlay(message)
            },
            onPowerThresholdReached = {
                if (isNearHome()) {
                    showPowerMenu()
                } else {
                    Log.d(TAG, "Not near home, skipping power menu.")
                }
            },
            onStatusChanged = { pkg, remaining ->
                onStatusUpdateListener?.invoke(pkg, remaining)
            }
        )
    }

    // 現在のステータス更新をリクエストする（MainActivity用）
    fun requestStatusUpdate() {
        if (::appUsageMonitor.isInitialized) {
            appUsageMonitor.broadcastCurrentStatus()
        }
    }

    fun getCurrentLocation(): Location? {
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager

        // 権限チェック
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "Location permission not granted")
            return null
        }

        // 最後に取得した位置情報を確認（GPS優先、なければNetwork）
        val locGPS = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
        val locNet = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)

        // より新しい位置情報を採用するロジックなどが理想だが、簡易的にGPS優先で取得
        return locGPS ?: locNet
    }

    fun isNearHome(): Boolean {
        val location = getCurrentLocation()

        if (location == null) {
            Log.d(TAG, "Location not found")
            // 位置情報が取れない場合はどうするか？（とりあえず実行しない＝false）
            return false
        }

        val results = FloatArray(1)
        Location.distanceBetween(location.latitude, location.longitude, Constants.HOME_LATITUDE, Constants.HOME_LONGITUDE, results)
        val distance = results[0]

        Log.d(TAG, "Current Distance to Home: ${distance}m")

        return distance <= Constants.RADIUS_METERS
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val packageName = event.packageName?.toString()
            if (packageName != null) {
                appUsageMonitor.onPackageChanged(packageName)
            }
        }
    }

    override fun onInterrupt() {
        Log.d(TAG, "Service Interrupted")
        appUsageMonitor.stopMonitoring()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "Service Connected!")
        instance = this
        if (!::appUsageMonitor.isInitialized) {
             initMonitor()
        }
    }
    
    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        if (::appUsageMonitor.isInitialized) {
            appUsageMonitor.stopMonitoring()
        }
        return super.onUnbind(intent)
    }

    private fun showWarningOverlay(message: String = "長時間使用しています。\nそろそろ休憩しましょう。") {
        OverlayHelper.showWarning(this, message)
    }

    fun showPowerMenu() {
        Log.d(TAG, "Showing Power Menu")
        performGlobalAction(GLOBAL_ACTION_POWER_DIALOG)
    }
}
