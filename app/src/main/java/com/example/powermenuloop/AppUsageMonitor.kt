package com.example.powermenuloop

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlin.math.max

class AppUsageMonitor(
    private val context: Context, // データの保存（SharedPreferences）のためにContextを受け取る
    private val isNearHome: Boolean,
    private val onWarningThresholdReached: (message: String) -> Unit,
    private val onPowerThresholdReached: () -> Unit,
    // ステータス更新時のコールバック： (PackageName, RemainingTime)
    private val onStatusChanged: (String, Long) -> Unit = { _, _ -> }
) {

    companion object {
        private const val TAG = "AppUsageMonitor"
        
        private const val PREFS_NAME = "AppUsageMonitorPrefs"
        private const val KEY_ACCUMULATED_USAGE = "accumulated_usage"
        private const val KEY_LAST_SESSION_END = "last_session_end"
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private var currentMonitoredPackage: String? = null

    private var initialWarningMs = 0
    
    // タイマー関連の変数
    private var accumulatedUsage: Long = 0 // 過去のセッションの累積時間
    private var currentSessionStartTime: Long = 0 // 現在のセッションの開始時間
    private var lastSessionEndTime: Long = 0 // 最後にアプリを閉じた時間

    private var hasShownInitialWarn: Boolean = false
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
            val remaining = max(0, Constants.LOOP_THRESHOLD_MS - totalElapsed)
            
            // ログで経過時間を確認 (秒単位)
            Log.d(TAG, "Monitoring $currentPackage: Total ${totalElapsed / 1000}s (Remaining: ${remaining / 1000}s)")
            
            // ステータス変更を通知
            onStatusChanged(currentPackage, remaining)

            when(totalElapsed) {
                in 0..initialWarningMs -> false
                in initialWarningMs..Constants.WARNING_THRESHOLD_MS -> {
                    if (isNearHome && !hasShownInitialWarn && initialWarningMs != 0) {
                        Log.d(TAG, "totalElapsed: $totalElapsed ,initialWarning: $initialWarningMs")
                        onWarningThresholdReached("開発するかゲームするか外に出るか。\n何か行動しませんか？")
                        hasShownInitialWarn = true
                    }
                }
                in Constants.WARNING_THRESHOLD_MS..Constants.LOOP_THRESHOLD_MS -> {
                    if (hasWarningShown) {
                        Log.d(TAG, "Warning threshold reset for $currentPackage")
                        onWarningThresholdReached("長時間使用しています。\nそろそろ休憩しましょう。")
                        hasWarningShown = false
                    }
                }
                in Constants.LOOP_THRESHOLD_MS..Long.MAX_VALUE -> {
                    if (!isLooping) {
                        Log.d(TAG, "Loop threshold reached for $currentPackage")
                        isLooping = true
                        onPowerThresholdReached()
                    }
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
        if (newPackage == Constants.PACKAGE_YOUTUBE || newPackage == Constants.PACKAGE_X) {
            startMonitoring(newPackage)
        }
    }

    private fun startMonitoring(packageName: String) {
        val now = System.currentTimeMillis()
        initialWarningMs = (Constants.INITIAL_WARNING_START_MS..Constants.INITIAL_WARNING_END_MS).random()
        
        // 前回の終了から一定時間（30分）以上経過しているなら、タイマーリセット
        if (lastSessionEndTime > 0 && (now - lastSessionEndTime) > Constants.TIMER_MAINTAIN_DURATION_MS) {
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
        val remaining = max(0, Constants.LOOP_THRESHOLD_MS - totalElapsed)

        onStatusChanged(currentPackage, remaining)
    }

    private fun saveUsageData() {
        prefs.edit()
            .putLong(KEY_ACCUMULATED_USAGE, accumulatedUsage)
            .putLong(KEY_LAST_SESSION_END, lastSessionEndTime)
            .apply()
    }
}
