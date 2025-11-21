package com.example.powermenuloop

import android.os.Handler
import android.os.Looper
import android.util.Log

class AppUsageMonitor(
    private val onWarningThresholdReached: () -> Unit,
    private val onPowerThresholdReached: () -> Unit,
    private val onStatusChanged: (String) -> Unit = {}
) {

    companion object {
        private const val TAG = "AppUsageMonitor"
        private const val PACKAGE_YOUTUBE = "com.google.android.youtube"
        private const val PACKAGE_X = "com.twitter.android"
        
        // 連続起動時間の監視用 (ミリ秒)
        private const val WARNING_THRESHOLD_MS = 5 * 60 * 1000L // 5分
        private const val LOOP_THRESHOLD_MS = 10 * 60 * 1000L   // 10分
    }

    private var currentMonitoredPackage: String? = null
    private var startTime: Long = 0
    private var hasWarningShown: Boolean = false
    private val handler = Handler(Looper.getMainLooper())

    private val checkTimeRunnable = object : Runnable {
        override fun run() {
            val currentPackage = currentMonitoredPackage ?: return
            val elapsed = System.currentTimeMillis() - startTime
            
            // ログで経過時間を確認 (秒単位)
            Log.d(TAG, "Monitoring $currentPackage: ${elapsed / 1000}s")
            
            val statusText = "Monitoring $currentPackage: ${elapsed / 1000}s"
            onStatusChanged(statusText)

            if (elapsed >= LOOP_THRESHOLD_MS) {
                Log.d(TAG, "Loop threshold reached for $currentPackage")
                onPowerThresholdReached()
            } else if (elapsed >= WARNING_THRESHOLD_MS) {
                 if (!hasWarningShown) {
                     Log.d(TAG, "Warning threshold reached for $currentPackage")
                     onWarningThresholdReached()
                     hasWarningShown = true
                 }
            }

            // 1秒ごとにチェック
            handler.postDelayed(this, 1000)
        }
    }

    fun onPackageChanged(newPackage: String) {
        Log.d(TAG, newPackage)
        
        // 自分のアプリ（オーバーレイ通知など）が表示された場合は、監視を中断せずに無視する
        if (newPackage == "com.example.powermenuloop") return

        // 同じパッケージ内の画面遷移などは無視
        if (currentMonitoredPackage == newPackage) return

        // 監視中のアプリから別のアプリに移動した場合、監視を停止
        if (currentMonitoredPackage != null) {
             Log.d(TAG, "Stopped monitoring $currentMonitoredPackage. Duration: ${(System.currentTimeMillis() - startTime)/1000}s")
             stopMonitoring()
        }

        // YouTube または X の場合、監視を開始
        if (newPackage == PACKAGE_YOUTUBE || newPackage == PACKAGE_X) {
            startMonitoring(newPackage)
        }
    }

    private fun startMonitoring(packageName: String) {
        currentMonitoredPackage = packageName
        startTime = System.currentTimeMillis()
        hasWarningShown = false
        Log.d(TAG, "Started monitoring $packageName")
        onStatusChanged("Started monitoring $packageName")
        handler.post(checkTimeRunnable)
    }

    fun stopMonitoring() {
        if (currentMonitoredPackage != null) {
            onStatusChanged("Monitoring stopped")
        }
        currentMonitoredPackage = null
        handler.removeCallbacks(checkTimeRunnable)
    }
}
