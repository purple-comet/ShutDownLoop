package com.example.powermenuloop

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent

class PowerMenuService : AccessibilityService() {

    companion object {
        var instance: PowerMenuService? = null
        private const val TAG = "PowerMenuService"
        // 状態更新用のリスナー（MainActivityなどでセット）
        var onStatusUpdateListener: ((String) -> Unit)? = null
    }

    // ロジックを委譲するクラス
    private lateinit var appUsageMonitor: AppUsageMonitor

    override fun onCreate() {
        super.onCreate()
        initMonitor()
    }

    private fun initMonitor() {
        appUsageMonitor = AppUsageMonitor(
            onWarningThresholdReached = {
                showWarningOverlay()
            },
            onPowerThresholdReached = {
                showPowerMenu()
            },
            onStatusChanged = { status ->
                // 状態が変わったらリスナーに通知
                onStatusUpdateListener?.invoke(status)
            }
        )
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        
        // ウィンドウの状態が変わった（アプリが切り替わった）イベントを検知
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val packageName = event.packageName?.toString()
            // Log.d(TAG, "Window changed: $packageName")
            
            if (packageName != null) {
                appUsageMonitor.onPackageChanged(packageName)
            }
        }
    }

    override fun onInterrupt() {
        Log.d(TAG, "Service Interrupted")
        appUsageMonitor.stopMonitoring()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "Service Connected!")
        instance = this
        if (!::appUsageMonitor.isInitialized) {
             initMonitor()
        }
    }
    
    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        if (::appUsageMonitor.isInitialized) {
            appUsageMonitor.stopMonitoring()
        }
        return super.onUnbind(intent)
    }

    private fun showWarningOverlay() {
        OverlayHelper.showWarning(this)
    }

    fun showPowerMenu() {
        Log.d(TAG, "Showing Power Menu")
        performGlobalAction(GLOBAL_ACTION_POWER_DIALOG)
    }
}
