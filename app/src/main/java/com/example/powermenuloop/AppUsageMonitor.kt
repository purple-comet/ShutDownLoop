package com.example.powermenuloop

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlin.math.max
import kotlin.ranges.random
import java.util.Calendar

class AppUsageMonitor(
    private val context: Context, // データの保存（SharedPreferences）のためにContextを受け取る
    private val locationHelper: LocationHelper,
    private val onWarningThresholdReached: (message: String) -> Unit,
    private val onPowerThresholdReached: () -> Unit,
    // ステータス更新時のコールバック： (PackageName, NormalRemaining, ExtensionRemaining?)
    // ExtensionRemaining が null でない場合は延長モード中
    private val onStatusChanged: (String, Long, Long?) -> Unit = { _, _, _ -> }
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

    // 延長タイマー関連の変数
    private var isInExtensionMode: Boolean = false // 延長モード中か
    private var extensionStartTime: Long = 0 // 延長タイマー開始時刻

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

            if (isInExtensionMode) {
                // 延長タイマーモード
                val now = System.currentTimeMillis()
                val extensionElapsed = now - extensionStartTime
                val extensionRemaining = max(0, Constants.EXTENSION_DURATION_MS - extensionElapsed)

                Log.d(TAG, "Extension mode: Elapsed ${extensionElapsed / 1000}s (Remaining: ${extensionRemaining / 1000}s)")

                // ステータス変更を通知（延長残り時間を表示）
                // 通常タイマーの残り時間も同時に送る（表示用）
                val normalRemaining = max(0, Constants.LOOP_THRESHOLD_MS - accumulatedUsage)
                onStatusChanged(currentPackage, normalRemaining, extensionRemaining)

                if (extensionElapsed >= Constants.EXTENSION_DURATION_MS) {
                    // 延長タイマー終了 → 通常タイマー再開
                    isInExtensionMode = false
                    currentSessionStartTime = now // セッション開始を現在時刻にリセット
                    Log.d(TAG, "Extension mode ended, resuming normal timer. Accumulated: ${accumulatedUsage / 1000}s")
                    // この時点でaccumulatedUsageが既にLOOP_THRESHOLD超えていれば、次のcheckでループモード発動する
                } else {
                    // 延長中: 外出時と同じ挙動（警告オーバーレイのみ、電源メニューなし）
                    when(extensionElapsed) {
                        in 0..Constants.WARNING_THRESHOLD_MS -> false
                        in Constants.WARNING_THRESHOLD_MS..Constants.LOOP_THRESHOLD_MS -> {
                            if (!hasWarningShown) {
                                Log.d(TAG, "Warning threshold reset for $currentPackage")
                                onWarningThresholdReached("長時間使用しています。\nそろそろ休憩しましょう。")
                                hasWarningShown = true
                            }
                        }
                    }
                }
            } else {
                // 通常タイマーモード
                val currentSessionDuration = System.currentTimeMillis() - currentSessionStartTime
                val totalElapsed = accumulatedUsage + currentSessionDuration

                // 残り時間を計算（負の場合は0）
                val remaining = max(0, Constants.LOOP_THRESHOLD_MS - totalElapsed)

                // ログで経過時間を確認 (秒単位)
                Log.d(TAG, "Monitoring $currentPackage: Total ${totalElapsed / 1000}s (Remaining: ${remaining / 1000}s)")

                // ステータス変更を通知（通常モード：延長残り時間はnull）
                onStatusChanged(currentPackage, remaining, null)

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
            }

            // 1秒ごとにチェック
            handler.postDelayed(this, 1000)
        }
    }

    fun onPackageChanged(newPackage: String) {
        Log.d(TAG, newPackage)
        // ループモード中の処理
        if (isLooping) {
            // 延長モード中はループ処理をスキップ
            if (isInExtensionMode) return
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
            isInExtensionMode = false
            saveUsageData()
            Log.d(TAG, "Timer reset due to inactivity (> 30 mins)")
        } else {
            if (accumulatedUsage > 0) {
                Log.d(TAG, "Resuming timer. Accumulated: ${accumulatedUsage / 1000}s, isLooping: $isLooping, isInExtensionMode: $isInExtensionMode")
            }
        }

        currentMonitoredPackage = packageName
        currentSessionStartTime = now


        Log.d(TAG, "Started monitoring $packageName")

        handler.post(checkTimeRunnable)
    }

    fun stopMonitoring() {
        if (currentMonitoredPackage != null) {
//            // 延長モードを解除
//            isInExtensionMode = false

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
        val currentSessionDuration = if (currentMonitoredPackage != null && !isInExtensionMode) {
            System.currentTimeMillis() - currentSessionStartTime
        } else {
            0L
        }
        val totalElapsed = accumulatedUsage + currentSessionDuration
        val normalRemaining = max(0, Constants.LOOP_THRESHOLD_MS - totalElapsed)

        // 延長モード中は延長残り時間も算出して通知
        val extensionElapsed = System.currentTimeMillis() - extensionStartTime
        val extensionRemaining = max(0, Constants.EXTENSION_DURATION_MS - extensionElapsed)

        onStatusChanged(currentPackage, normalRemaining, extensionRemaining)
    }

    fun isExtensionAvailable(): Boolean {
        val lastUsed = prefs.getLong(Constants.KEY_LAST_EXTENSION_USED, 0)
        if (lastUsed == 0L) return true

        return System.currentTimeMillis() >= Constants.getNextExtensionResetTime(lastUsed)
    }

    fun extendTime(): Boolean {
        if (!isExtensionAvailable()) return false
        // accumulatedUsage は変更しない（元のタイマーをそのまま保持）
        isLooping = false
        hasWarningShown = false
        isInExtensionMode = true
        extensionStartTime = System.currentTimeMillis()
        saveUsageData()
        val now = System.currentTimeMillis()
        prefs.edit().putLong(Constants.KEY_LAST_EXTENSION_USED, now).apply()
        incrementExtensionStats(now)
        Log.d(TAG, "Extension mode started. Accumulated preserved: ${accumulatedUsage / 1000}s")
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
