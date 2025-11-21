package com.example.powermenuloop

import android.content.Context
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
        private const val WARNING_THRESHOLD_MS = 2 * 1000L // 5分
        private const val LOOP_THRESHOLD_MS = 10 * 1000L   // 10分
    }

    private var currentMonitoredPackage: String? = null
    private var startTime: Long = 0
    private var hasWarningShown: Boolean = false
    private var isLooping: Boolean = false
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
                if (!isLooping) {
                    Log.d(TAG, "Loop threshold reached for $currentPackage")
                    isLooping = true
                    onPowerThresholdReached()
                }
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

        // システムUI（電源メニューや通知シェードなど）への遷移は監視を継続する
        if (newPackage == "com.android.systemui" || newPackage == "android") return

        // ループモード中の処理
        if (isLooping) {
            // 監視中のアプリに戻ろうとした場合（キャンセルや背景タップなど）、再度電源メニューを表示する
            if (newPackage == currentMonitoredPackage) {
                Log.d(TAG, "Loop Triggered: Showing Power Menu again")
                onPowerThresholdReached()
                return // 監視を停止せずに継続
            }
            // ホーム画面や緊急通報など、他のアプリへ遷移した場合は通常通り監視を終了（ループ脱出）
        }

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
        isLooping = false
        Log.d(TAG, "Started monitoring $packageName")
        onStatusChanged("Started monitoring $packageName")
        handler.post(checkTimeRunnable)
    }

    fun stopMonitoring() {
        if (currentMonitoredPackage != null) {
            onStatusChanged("Monitoring stopped")
        }
        currentMonitoredPackage = null
        isLooping = false
        handler.removeCallbacks(checkTimeRunnable)
    }
}
