package com.example.powermenuloop

object Constants {
    const val PACKAGE_YOUTUBE = "com.google.android.youtube"
    const val PACKAGE_X = "com.twitter.android"

    // 連続起動時間の監視用 (ミリ秒) - 外部（MainActivity）から参照できるようにpublicにする
    const val INITIAL_WARNING_START_MS = 5 * 1000 // 5秒
    const val INITIAL_WARNING_END_MS = 10 * 1000 // 10秒
    const val WARNING_THRESHOLD_MS = 5 * 60 * 1000L // 5分
    const val LOOP_THRESHOLD_MS = 10 * 60 * 1000L   // 10分

    // アプリを閉じた後、タイマーを維持する時間（30分）
    const val TIMER_MAINTAIN_DURATION_MS = 30 * 60 * 1000L

    // 自宅の位置情報
    const val HOME_LATITUDE = 35.79378925
    const val HOME_LONGITUDE = 139.96975516
    const val RADIUS_METERS = 100f // 100m以内

    // 10分延長機能
    const val EXTENSION_DURATION_MS = 10 * 60 * 1000L // 10分
    const val RESET_HOUR_MORNING = 4  // 午前4時にボタンをリセット
    const val RESET_HOUR_AFTERNOON = 16 // 午後4時にボタンをリセット

    fun getNextExtensionResetTime(fromTime: Long): Long {
        val cal = java.util.Calendar.getInstance()
        cal.timeInMillis = fromTime
        val cal4AM = (cal.clone() as java.util.Calendar).apply {
            set(java.util.Calendar.HOUR_OF_DAY, RESET_HOUR_MORNING); set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
        }
        val cal4PM = (cal.clone() as java.util.Calendar).apply {
            set(java.util.Calendar.HOUR_OF_DAY, RESET_HOUR_AFTERNOON); set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
        }
        return when {
            cal4AM.timeInMillis > fromTime -> cal4AM.timeInMillis
            cal4PM.timeInMillis > fromTime -> cal4PM.timeInMillis
            else -> cal4AM.apply { add(java.util.Calendar.DAY_OF_MONTH, 1) }.timeInMillis
        }
    }

    // SharedPreferences keys
    const val PREFS_NAME = "AppUsageMonitorPrefs"
    const val KEY_ACCUMULATED_USAGE = "accumulated_usage"
    const val KEY_LAST_SESSION_END = "last_session_end"
    const val KEY_IS_LOOPING = "is_looping"
    const val KEY_LAST_EXTENSION_USED = "last_extension_used"

    // 10分延長ボタン 統計情報
    const val KEY_EXTENSION_COUNT_WEEKLY = "extension_count_weekly"
    const val KEY_EXTENSION_COUNT_MONTHLY = "extension_count_monthly"
    const val KEY_EXTENSION_COUNT_TOTAL = "extension_count_total"
    const val KEY_EXTENSION_WEEKLY_RESET_TIME = "extension_weekly_reset_time"
    const val KEY_EXTENSION_MONTHLY_RESET_TIME = "extension_monthly_reset_time"

    /** 指定時刻以前の直近の月曜日午前4時のタイムスタンプを返す。指定時刻がその週の月曜日4時より前なら前週月曜日4時を返す */
    fun getLatestWeeklyResetTime(now: Long): Long {
        val cal = java.util.Calendar.getInstance()
        cal.timeInMillis = now
        // 今週月曜日の日付に合わせる
        val dayOfWeek = cal.get(java.util.Calendar.DAY_OF_WEEK) // Sun=1, Mon=2
        val daysFromMonday = (dayOfWeek - java.util.Calendar.MONDAY + 7) % 7
        cal.add(java.util.Calendar.DAY_OF_MONTH, -daysFromMonday)
        cal.set(java.util.Calendar.HOUR_OF_DAY, RESET_HOUR_MORNING)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        // もし計算結果が未来なら1週間前に戻す
        if (cal.timeInMillis > now) {
            cal.add(java.util.Calendar.WEEK_OF_YEAR, -1)
        }
        return cal.timeInMillis
    }

    /** 指定時刻以前の直近の毎月1日午前4時のタイムスタンプを返す */
    fun getLatestMonthlyResetTime(now: Long): Long {
        val cal = java.util.Calendar.getInstance()
        cal.timeInMillis = now
        cal.set(java.util.Calendar.DAY_OF_MONTH, 1)
        cal.set(java.util.Calendar.HOUR_OF_DAY, RESET_HOUR_MORNING)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        // もし計算結果が未来なら1ヶ月前に戻す
        if (cal.timeInMillis > now) {
            cal.add(java.util.Calendar.MONTH, -1)
        }
        return cal.timeInMillis
    }

    /** SharedPreferencesから統計情報を読み取る。必要に応じて週次・月次のリセット判定を行い (週次, 月次, 全期間) を返す */
    fun readExtensionStats(prefs: android.content.SharedPreferences): Triple<Int, Int, Int> {
        val now = System.currentTimeMillis()
        val latestWeeklyReset = getLatestWeeklyResetTime(now)
        val latestMonthlyReset = getLatestMonthlyResetTime(now)
        val storedWeeklyReset = prefs.getLong(KEY_EXTENSION_WEEKLY_RESET_TIME, 0L)
        val storedMonthlyReset = prefs.getLong(KEY_EXTENSION_MONTHLY_RESET_TIME, 0L)
        val weekly = if (latestWeeklyReset > storedWeeklyReset) 0 else prefs.getInt(KEY_EXTENSION_COUNT_WEEKLY, 0)
        val monthly = if (latestMonthlyReset > storedMonthlyReset) 0 else prefs.getInt(KEY_EXTENSION_COUNT_MONTHLY, 0)
        val total = prefs.getInt(KEY_EXTENSION_COUNT_TOTAL, 0)
        return Triple(weekly, monthly, total)
    }
}
