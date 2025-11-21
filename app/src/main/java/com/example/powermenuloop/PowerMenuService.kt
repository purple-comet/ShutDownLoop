package com.example.powermenuloop

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import androidx.core.app.ActivityCompat

class PowerMenuService : AccessibilityService() {

    companion object {
        var instance: PowerMenuService? = null
        private const val TAG = "PowerMenuService"
        // 状態更新用のリスナー（引数を3つに変更）
        var onStatusUpdateListener: ((String, Long, Long) -> Unit)? = null

        // 自宅の位置情報
        private const val HOME_LATITUDE = 35.79378925
        private const val HOME_LONGITUDE = 139.96975516
        private const val RADIUS_METERS = 100f // 100m以内
    }

    private lateinit var appUsageMonitor: AppUsageMonitor

    override fun onCreate() {
        super.onCreate()
        initMonitor()
    }

    private fun initMonitor() {
        appUsageMonitor = AppUsageMonitor(
            context = this, // コンテキストを渡す（SharedPreferences用）
            onWarningThresholdReached = {
                if (isNearHome()) {
                    showWarningOverlay()
                } else {
                    // 現在の緯度経度をログに出力（デバッグ用）
                    val location = getCurrentLocation()
                    if (location != null) {
                        Log.d(TAG, "Current Location: lat=${location.latitude}, lng=${location.longitude}")
                    }
                    Log.d(TAG, "Not near home, skipping warning.")
                }
            },
            onPowerThresholdReached = {
                // ループ中は位置情報チェックをスキップするかどうか？
                // 一度ループに入ったら家にいなくても出られないのは困るので、ここでもチェックする
                if (isNearHome()) {
                    showPowerMenu()
                } else {
                    Log.d(TAG, "Not near home, skipping power menu.")
                }
            },
            onStatusChanged = { pkg, total, remaining ->
                onStatusUpdateListener?.invoke(pkg, total, remaining)
            }
        )
    }

    // 現在のステータス更新をリクエストする（MainActivity用）
    fun requestStatusUpdate() {
        if (::appUsageMonitor.isInitialized) {
            appUsageMonitor.broadcastCurrentStatus()
        }
    }

    private fun getCurrentLocation(): Location? {
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

    private fun isNearHome(): Boolean {
        val location = getCurrentLocation()

        if (location == null) {
            Log.d(TAG, "Location not found")
            // 位置情報が取れない場合はどうするか？（とりあえず実行しない＝false）
            return false
        }

        val results = FloatArray(1)
        Location.distanceBetween(location.latitude, location.longitude, HOME_LATITUDE, HOME_LONGITUDE, results)
        val distance = results[0]

        Log.d(TAG, "Current Distance to Home: ${distance}m")

        return distance <= RADIUS_METERS
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

    private fun showWarningOverlay() {
        OverlayHelper.showWarning(this, "長時間使用しています。\nそろそろ休憩しましょう。")
    }

    fun showPowerMenu() {
        Log.d(TAG, "Showing Power Menu")
        performGlobalAction(GLOBAL_ACTION_POWER_DIALOG)
    }
}
