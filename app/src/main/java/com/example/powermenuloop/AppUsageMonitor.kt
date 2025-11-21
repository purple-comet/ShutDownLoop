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

    // 現在のステータスを強制的に通知する（UI表示用）
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
        
        // 前回の終了から一定時間（30分）経過していなければ、タイマーを維持する
        if (lastSessionEndTime > 0 && (now - lastSessionEndTime) > TIMER_MAINTAIN_DURATION_MS) {
            accumulatedUsage = 0
            saveUsageData() // リセットも保存
            Log.d(TAG, "Timer reset due to inactivity (> 30 mins)")
            // 警告フラグなどもリセット
            hasWarningShown = false
            isLooping = false
        } else {
            if (accumulatedUsage > 0) {
                Log.d(TAG, "Resuming timer. Accumulated: ${accumulatedUsage / 1000}s")
            }
        }

        currentMonitoredPackage = packageName
        currentSessionStartTime = now
        
        // 既に警告済みの時間が経過している場合はフラグを立て直す必要があるが、
        // ここではシンプルに継続監視とする。

        Log.d(TAG, "Started monitoring $packageName")
        // 開始時も通知（経過時間はaccumulatedUsage）
        broadcastCurrentStatus()
        
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

    private fun saveUsageData() {
        prefs.edit()
            .putLong(KEY_ACCUMULATED_USAGE, accumulatedUsage)
            .putLong(KEY_LAST_SESSION_END, lastSessionEndTime)
            .apply()
    }
}
