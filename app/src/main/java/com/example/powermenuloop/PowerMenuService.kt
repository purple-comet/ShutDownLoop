package com.example.powermenuloop

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent

class PowerMenuService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // イベントを受け取ったときの処理。
        // 現時点では特に何もしないが、TODOを残すとクラッシュするため空にしておくかログを出す。
    }

    override fun onInterrupt() {
        // サービスが中断されたときの処理
        Log.d("PowerMenuService", "Service Interrupted")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d("PowerMenuService", "Service Connected!")
        // ここで performGlobalAction(GLOBAL_ACTION_POWER_DIALOG) などを呼ぶことができます
    }
}