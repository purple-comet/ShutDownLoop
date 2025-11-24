package com.example.powermenuloop

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlin.math.max

class AppUsageMonitor(
    private val context: Context, // データの保存（SharedPreferences）のためにContextを受け取る
    private val onWarningThresholdReached: () -> Unit,
    private val onPowerThresholdReached: () -> Unit,
    // ステータス更新時のコールバック： (PackageName, TotalElapsed, RemainingTime)
    private val onStatusChanged: (String, Long, Long) -> Unit = { _, _, _ -> }
) {

    companion object {
        private const val TAG = "AppUsageMonitor"
        private const val PACKAGE_YOUTUBE = "com.google.android.youtube"
        private const val PACKAGE_X = "com.twitter.android"
        
        // 連続起動時間の監視用 (ミリ秒) - 外部（MainActivity）から参照できるようにpublicにする
        const val WARNING_THRESHOLD_MS = 5 * 60 * 1000L // 5分
        const val LOOP_THRESHOLD_MS = 10 * 60 * 1000L   // 10分

        // アプリを閉じた後、タイマーを維持する時間（30分）
        private const val TIMER_MAINTAIN_DURATION_MS = 30 * 60 * 1000L
        
        private const val PREFS_NAME = "AppUsageMonitorPrefs"
        private const val KEY_ACCUMULATED_USAGE = "accumulated_usage"
        private const val KEY_LAST_SESSION_END = "last_session_end"
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private var currentMonitoredPackage: String? = null
    
    // タイマー関連の変数
    private var accumulatedUsage: Long = 0 // 過去のセッションの累積時間
    private var currentSessionStartTime: Long = 0 // 現在のセッションの開始時間
    private var lastSessionEndTime: Long = 0 // 最後にアプリを閉じた時間

    private var hasWarningShown: Boolean = false
    private var isLooping: Boolean = false
    private val handler = Handler(Looper.getMainLooper())

    init {
        // 初期化時に保存されたデータを読み込む
        accumulatedUsage = prefs.getLong(KEY_ACCUMULATED_USAGE, 0)
        lastSessionEndTime = prefs.getLong(KEY_LAST_SESSION_END, 0)
        Log.d(TAG, "Loaded accumulated usage: ${accumulatedUsage / 1000}s")
    }

    private val checkTimeRunnable = object : Runnable {
        override fun run() {
            val currentPackage = currentMonitoredPackage ?: return
            
            // 現在のセッション時間を計算
            val currentSessionDuration = System.currentTimeMillis() - currentSessionStartTime
            // 累積時間と合計する
            val totalElapsed = accumulatedUsage + currentSessionDuration
            
            // 残り時間を計算（負の場合は0）
            val remaining = max(0, LOOP_THRESHOLD_MS - totalElapsed)
            
            // ログで経過時間を確認 (秒単位)
            Log.d(TAG, "Monitoring $currentPackage: Total ${totalElapsed / 1000}s (Remaining: ${remaining / 1000}s)")
            
            // ステータス変更を通知
            onStatusChanged(currentPackage, totalElapsed, remaining)

            if (totalElapsed >= LOOP_THRESHOLD_MS) {
                if (!isLooping) {
                    Log.d(TAG, "Loop threshold reached for $currentPackage")
                    isLooping = true
                    onPowerThresholdReached()
                }
            } else if (totalElapsed >= WARNING_THRESHOLD_MS) {
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
        // ループモード中の処理
        if (isLooping) {
            Log.d(TAG, "Loop Triggered: Showing Power Menu again")
            onPowerThresholdReached()
            return
        }
        // 中断時のオーバーレイでも監視を続ける
        if (newPackage == "com.example.powermenuloop") return

        if (currentMonitoredPackage == newPackage) return

        // 監視ストップ
        if (currentMonitoredPackage != null) {
             val duration = System.currentTimeMillis() - currentSessionStartTime
             Log.d(TAG, "Stopped monitoring $currentMonitoredPackage. Session Duration: ${duration/1000}s")
             stopMonitoring()
        }

        // YouTube または X の場合、監視を開始
        if (newPackage == PACKAGE_YOUTUBE || newPackage == PACKAGE_X) {
            startMonitoring(newPackage)
        }
    }

    private fun startMonitoring(packageName: String) {
        val now = System.currentTimeMillis()
        
        // 前回の終了から一定時間（30分）以上経過しているなら、タイマーリセット
        if (lastSessionEndTime > 0 && (now - lastSessionEndTime) > TIMER_MAINTAIN_DURATION_MS) {
            accumulatedUsage = 0
            saveUsageData()
            Log.d(TAG, "Timer reset due to inactivity (> 30 mins)")
            isLooping = false
        } else {
            if (accumulatedUsage > 0) {
                Log.d(TAG, "Resuming timer. Accumulated: ${accumulatedUsage / 1000}s")
            }
        }

        currentMonitoredPackage = packageName
        currentSessionStartTime = now


        Log.d(TAG, "Started monitoring $packageName")
        
        handler.post(checkTimeRunnable)
    }

    fun stopMonitoring() {
        if (currentMonitoredPackage != null) {
            // 累積時間に加算して保存
            val now = System.currentTimeMillis()
            accumulatedUsage += (now - currentSessionStartTime)
            lastSessionEndTime = now
            
            saveUsageData() // 永続化
            // 停止時通知
            broadcastCurrentStatus()
            Log.d(TAG, "Monitoring stopped (Saved: ${accumulatedUsage/1000}s)")
        }
        
        currentMonitoredPackage = null
        isLooping = false // 監視停止時はループフラグをリセット（再開時に再判定）
        handler.removeCallbacks(checkTimeRunnable)
    }
    fun broadcastCurrentStatus() {
        val currentPackage = currentMonitoredPackage ?: "None"

        // 実行中でなければ累積時間のみ、実行中なら＋セッション時間
        val currentSessionDuration = if (currentMonitoredPackage != null) {
            System.currentTimeMillis() - currentSessionStartTime
        } else {
            0L
        }
        val totalElapsed = accumulatedUsage + currentSessionDuration
        val remaining = max(0, LOOP_THRESHOLD_MS - totalElapsed)

        onStatusChanged(currentPackage, totalElapsed, remaining)
    }

    private fun saveUsageData() {
        prefs.edit()
            .putLong(KEY_ACCUMULATED_USAGE, accumulatedUsage)
            .putLong(KEY_LAST_SESSION_END, lastSessionEndTime)
            .apply()
    }
}
