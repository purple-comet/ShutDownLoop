package com.example.powermenuloop

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent

class PowerMenuService : AccessibilityService() {

    companion object {
        var instance: PowerMenuService? = null
        private const val TAG = "PowerMenuService"
        // 状態更新用のリスナー： (PackageName, NormalRemaining, ExtensionRemaining?)
        var onStatusUpdateListener: ((String, Long, Long?) -> Unit)? = null
    }

    private lateinit var appUsageMonitor: AppUsageMonitor
    private lateinit var locationHelper: LocationHelper

    override fun onCreate() {
        super.onCreate()
        locationHelper = LocationHelper.getInstance(this)
        initMonitor()
    }

    private fun initMonitor() {
        appUsageMonitor = AppUsageMonitor(
            context = this, // コンテキストを渡す（SharedPreferences用）
            locationHelper,
            onWarningThresholdReached = { message ->
                showWarningOverlay(message)
            },
            onPowerThresholdReached = {
                if (locationHelper.isNearHome()) {
                    showPowerMenu()
                } else {
                    Log.d(TAG, "Not near home, skipping power menu.")
                    showWarningOverlay("使いすぎには気をつけてくださいね？")
                }
            },
            onStatusChanged = { pkg, normalRemaining, extensionRemaining ->
                onStatusUpdateListener?.invoke(pkg, normalRemaining, extensionRemaining)
            }
        )
    }

    // 現在のステータス更新をリクエストする（MainActivity用）
    fun requestStatusUpdate() {
        if (::appUsageMonitor.isInitialized) {
            appUsageMonitor.broadcastCurrentStatus()
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val packageName = event.packageName?.toString()
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

    fun isExtensionAvailable(): Boolean {
        return if (::appUsageMonitor.isInitialized) appUsageMonitor.isExtensionAvailable() else false
    }

    fun extendTime(): Boolean {
        return if (::appUsageMonitor.isInitialized) appUsageMonitor.extendTime() else false
    }

    fun getExtensionStats(): Triple<Int, Int, Int> {
        return if (::appUsageMonitor.isInitialized) appUsageMonitor.getExtensionStats()
        else Triple(0, 0, 0)
    }

    private fun showWarningOverlay(message: String) {
        OverlayHelper.showWarning(this, message)
    }

    fun showPowerMenu() {
        Log.d(TAG, "Showing Power Menu")
        performGlobalAction(GLOBAL_ACTION_HOME)
        performGlobalAction(GLOBAL_ACTION_POWER_DIALOG)
    }
}
