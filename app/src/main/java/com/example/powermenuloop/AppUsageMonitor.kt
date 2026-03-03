package com.example.powermenuloop

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlin.math.max
import kotlin.ranges.random

class AppUsageMonitor(
    private val context: Context, // データの保存（SharedPreferences）のためにContextを受け取る
    private val locationHelper: LocationHelper,
    private val onWarningThresholdReached: (message: String) -> Unit,
    private val onPowerThresholdReached: () -> Unit,
    // ステータス更新時のコールバック： (PackageName, RemainingTime)
    private val onStatusChanged: (String, Long) -> Unit = { _, _ -> }
) {

    companion object {
        private const val TAG = "AppUsageMonitor"
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
    private var currentMonitoredPackage: String? = null

    private var initialWarningMs = 0
    
    // タイマー関連の変数
    private var accumulatedUsage: Long = 0 // 過去のセッションの累積時間
    private var currentSessionStartTime: Long = 0 // 現在のセッションの開始時間
    private var lastSessionEndTime: Long = 0 // 最後にアプリを閉じた時間

    private var hasWarningShown: Boolean = false
    private var isLooping: Boolean = false
    private val handler = Handler(Looper.getMainLooper())

    init {
        // 初期化時に保存されたデータを読み込む
        accumulatedUsage = prefs.getLong(Constants.KEY_ACCUMULATED_USAGE, 0)
        lastSessionEndTime = prefs.getLong(Constants.KEY_LAST_SESSION_END, 0)
        isLooping = prefs.getBoolean(Constants.KEY_IS_LOOPING, false)
        Log.d(TAG, "Loaded accumulated usage: ${accumulatedUsage / 1000}s, isLooping: $isLooping")
    }

    private val checkTimeRunnable = object : Runnable {
        override fun run() {
            if (isLooping) return
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

            if (initialWarningMs < currentSessionDuration && locationHelper.isNearHome()) {
                Log.d(TAG, "totalElapsed: $totalElapsed ,initialWarning: $initialWarningMs")
                onWarningThresholdReached("開発するかゲームするか外に出るか。\n何か行動しませんか？")

                initialWarningMs += (Constants.INITIAL_WARNING_START_MS + 10 * 1000..Constants.INITIAL_WARNING_END_MS + 10 * 1000).random()
            }
            when(totalElapsed) {
                in 0..Constants.WARNING_THRESHOLD_MS -> false
                in Constants.WARNING_THRESHOLD_MS..Constants.LOOP_THRESHOLD_MS -> {
                    if (!hasWarningShown) {
                        Log.d(TAG, "Warning threshold reset for $currentPackage")
                        onWarningThresholdReached("長時間使用しています。\nそろそろ休憩しましょう。")
                        hasWarningShown = true
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
            if (locationHelper.isNearHome()) {
                onPowerThresholdReached()
            } else {
                // 自宅から離れている場合はループモードを解除
                Log.d(TAG, "Not near home, resetting loop mode")
                isLooping = false
                saveUsageData()
            }
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
            isLooping = false
            saveUsageData()
            Log.d(TAG, "Timer reset due to inactivity (> 30 mins)")
        } else {
            if (accumulatedUsage > 0) {
                Log.d(TAG, "Resuming timer. Accumulated: ${accumulatedUsage / 1000}s, isLooping: $isLooping")
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

    fun isExtensionAvailable(): Boolean {
        val lastUsed = prefs.getLong(Constants.KEY_LAST_EXTENSION_USED, 0)
        if (lastUsed == 0L) return true
        return System.currentTimeMillis() >= Constants.getNextExtensionResetTime(lastUsed)
    }

    fun extendTime(): Boolean {
        if (!isExtensionAvailable()) return false
        accumulatedUsage = max(0, accumulatedUsage - Constants.EXTENSION_DURATION_MS)
        if (isLooping) {
            isLooping = false
        }
        hasWarningShown = false
        saveUsageData()
        val now = System.currentTimeMillis()
        prefs.edit().putLong(Constants.KEY_LAST_EXTENSION_USED, now).apply()
        incrementExtensionStats(now)
        Log.d(TAG, "Time extended by 10 minutes. Accumulated: ${accumulatedUsage / 1000}s")
        return true
    }

    /** 統計カウンターをインクリメントし、必要に応じて週次・月次をリセットする */
    private fun incrementExtensionStats(now: Long) {
        val latestWeeklyReset = Constants.getLatestWeeklyResetTime(now)
        val latestMonthlyReset = Constants.getLatestMonthlyResetTime(now)

        val storedWeeklyReset = prefs.getLong(Constants.KEY_EXTENSION_WEEKLY_RESET_TIME, 0L)
        val storedMonthlyReset = prefs.getLong(Constants.KEY_EXTENSION_MONTHLY_RESET_TIME, 0L)

        val weeklyCount = if (latestWeeklyReset > storedWeeklyReset) 0 else prefs.getInt(Constants.KEY_EXTENSION_COUNT_WEEKLY, 0)
        val monthlyCount = if (latestMonthlyReset > storedMonthlyReset) 0 else prefs.getInt(Constants.KEY_EXTENSION_COUNT_MONTHLY, 0)
        val totalCount = prefs.getInt(Constants.KEY_EXTENSION_COUNT_TOTAL, 0)

        prefs.edit()
            .putInt(Constants.KEY_EXTENSION_COUNT_WEEKLY, weeklyCount + 1)
            .putInt(Constants.KEY_EXTENSION_COUNT_MONTHLY, monthlyCount + 1)
            .putInt(Constants.KEY_EXTENSION_COUNT_TOTAL, totalCount + 1)
            .putLong(Constants.KEY_EXTENSION_WEEKLY_RESET_TIME, latestWeeklyReset)
            .putLong(Constants.KEY_EXTENSION_MONTHLY_RESET_TIME, latestMonthlyReset)
            .apply()
    }

    /**
     * 統計情報を取得する。リセット判定を行い Triple(週次, 月次, 全期間) を返す。
     * 各期間のリセット条件：
     *  - 週次: 毎週月曜日午前4時
     *  - 月次: 毎月1日午前4時
     */
    fun getExtensionStats(): Triple<Int, Int, Int> = Constants.readExtensionStats(prefs)

    private fun getNextResetTime(fromTime: Long): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = fromTime
        val cal4AM = (cal.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 4); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val cal4PM = (cal.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 16); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        return when {
            cal4AM.timeInMillis > fromTime -> cal4AM.timeInMillis
            cal4PM.timeInMillis > fromTime -> cal4PM.timeInMillis
            else -> cal4AM.apply { add(Calendar.DAY_OF_MONTH, 1) }.timeInMillis
        }
    }

    private fun saveUsageData() {
        prefs.edit()
            .putLong(Constants.KEY_ACCUMULATED_USAGE, accumulatedUsage)
            .putLong(Constants.KEY_LAST_SESSION_END, lastSessionEndTime)
            .putBoolean(Constants.KEY_IS_LOOPING, isLooping)
            .apply()
    }
}
