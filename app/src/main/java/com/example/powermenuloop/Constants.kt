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

}