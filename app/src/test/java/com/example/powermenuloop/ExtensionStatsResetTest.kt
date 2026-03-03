package com.example.powermenuloop

import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar

class ExtensionStatsResetTest {

    /** タイムスタンプを作るヘルパー */
    private fun calOf(year: Int, month: Int, day: Int, hour: Int, minute: Int = 0): Long {
        return Calendar.getInstance().apply {
            set(year, month - 1, day, hour, minute, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    // --- getLatestWeeklyResetTime ---

    @Test
    fun weeklyReset_onMondayAfter4AM_returnsSameDayAt4AM() {
        // 2026-03-02 (月) 10:00
        val now = calOf(2026, 3, 2, 10)
        val reset = Constants.getLatestWeeklyResetTime(now)
        val expected = calOf(2026, 3, 2, 4)
        assertEquals(expected, reset)
    }

    @Test
    fun weeklyReset_onMondayBefore4AM_returnsPreviousWeekMondayAt4AM() {
        // 2026-03-02 (月) 01:00 → 前週月曜 2026-02-23 04:00
        val now = calOf(2026, 3, 2, 1)
        val reset = Constants.getLatestWeeklyResetTime(now)
        val expected = calOf(2026, 2, 23, 4)
        assertEquals(expected, reset)
    }

    @Test
    fun weeklyReset_onWednesdayMidWeek_returnsMostRecentMondayAt4AM() {
        // 2026-03-04 (水) 15:00 → 2026-03-02 (月) 04:00
        val now = calOf(2026, 3, 4, 15)
        val reset = Constants.getLatestWeeklyResetTime(now)
        val expected = calOf(2026, 3, 2, 4)
        assertEquals(expected, reset)
    }

    @Test
    fun weeklyReset_onSundayBeforeMidnight_returnsPreviousWeekMondayAt4AM() {
        // 2026-03-08 (日) 23:59 → 2026-03-02 (月) 04:00
        val now = calOf(2026, 3, 8, 23, 59)
        val reset = Constants.getLatestWeeklyResetTime(now)
        val expected = calOf(2026, 3, 2, 4)
        assertEquals(expected, reset)
    }

    @Test
    fun weeklyReset_isNeverInFuture() {
        val now = System.currentTimeMillis()
        val reset = Constants.getLatestWeeklyResetTime(now)
        assertTrue("週次リセット時刻は現在時刻以前でなければならない", reset <= now)
    }

    // --- getLatestMonthlyResetTime ---

    @Test
    fun monthlyReset_onFirstOfMonthAfter4AM_returnsSameDayAt4AM() {
        // 2026-03-01 10:00 → 2026-03-01 04:00
        val now = calOf(2026, 3, 1, 10)
        val reset = Constants.getLatestMonthlyResetTime(now)
        val expected = calOf(2026, 3, 1, 4)
        assertEquals(expected, reset)
    }

    @Test
    fun monthlyReset_onFirstOfMonthBefore4AM_returnsPreviousMonthFirstAt4AM() {
        // 2026-03-01 01:00 → 2026-02-01 04:00
        val now = calOf(2026, 3, 1, 1)
        val reset = Constants.getLatestMonthlyResetTime(now)
        val expected = calOf(2026, 2, 1, 4)
        assertEquals(expected, reset)
    }

    @Test
    fun monthlyReset_midMonth_returnsFirstOfCurrentMonthAt4AM() {
        // 2026-03-15 12:00 → 2026-03-01 04:00
        val now = calOf(2026, 3, 15, 12)
        val reset = Constants.getLatestMonthlyResetTime(now)
        val expected = calOf(2026, 3, 1, 4)
        assertEquals(expected, reset)
    }

    @Test
    fun monthlyReset_isNeverInFuture() {
        val now = System.currentTimeMillis()
        val reset = Constants.getLatestMonthlyResetTime(now)
        assertTrue("月次リセット時刻は現在時刻以前でなければならない", reset <= now)
    }
}
