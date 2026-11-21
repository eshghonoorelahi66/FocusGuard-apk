package com.focusguard.app.engine

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent

/**
 * Method 1: event-driven. Android calls us only when a window changes, so there is no polling at all.
 * canRetrieveWindowContent=false: we never read screen content, only the package name of the window in front.
 */
class AppBlockerService : AccessibilityService() {
    companion object {
        @Volatile var connected = false
    }

    override fun onServiceConnected() {
        connected = true
        BlockEngine.refreshLimits(this)
    }

    override fun onAccessibilityEvent(e: AccessibilityEvent?) {
        if (e == null || e.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = e.packageName?.toString() ?: return
        Blocker.onForeground(this, pkg, this)
    }

    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean {
        connected = false
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        connected = false
        super.onDestroy()
    }
}
