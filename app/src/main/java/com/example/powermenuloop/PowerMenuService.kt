package com.example.powermenuloop

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent

class PowerMenuService : AccessibilityService() {

    companion object {
        var instance: PowerMenuService? = null
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
    }

    override fun onInterrupt() {
        Log.d("PowerMenuService", "Service Interrupted")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d("PowerMenuService", "Service Connected!")
        instance = this
    }
    
    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    fun showPowerMenu() {
        performGlobalAction(GLOBAL_ACTION_POWER_DIALOG)
    }
}