package com.focusguard.app.engine

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.focusguard.app.AttemptRec
import com.focusguard.app.FocusApp
import com.focusguard.app.ui.BlockActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Shared decision + action used by BOTH detection methods:
 *  1. AppBlockerService (Accessibility, event driven, zero polling)
 *  2. the UsageStats fallback watcher inside FocusService
 * Always call on the main thread.
 */
object Blocker {
    private val main = Handler(Looper.getMainLooper())
    private var lastPkg = ""
    private var lastAt = 0L
    private var currentPkg = ""
    private var timer: Runnable? = null

    fun onForeground(ctx: Context, pkg: String, svc: AccessibilityService?) {
        val app = ctx.applicationContext
        if (BlockEngine.ignorable(app, pkg)) return
        currentPkg = pkg
        timer?.let { main.removeCallbacks(it) }
        timer = null

        var reason = BlockEngine.sessionReason(pkg)
        if (reason == null) {
            val left = BlockEngine.limitLeft(app, pkg)
            if (left != null) {
                if (left <= 0) reason = BlockEngine.Reason.LIMIT
                else {
                    // re-check exactly when the allowance runs out, only while that app is still in front
                    val r = Runnable { if (currentPkg == pkg) onForeground(app, pkg, svc) }
                    timer = r
                    main.postDelayed(r, left + 800)
                }
            }
        }
        if (reason == null) return

        val t = SystemClock.elapsedRealtime()
        if (pkg == lastPkg && t - lastAt < 1200) return
        lastPkg = pkg; lastAt = t
        currentPkg = ""

        svc?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
        val i = Intent(app, BlockActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NO_ANIMATION)
            .putExtra("pkg", pkg).putExtra("reason", reason.name)
        runCatching { app.startActivity(i) }

        val sid = BlockEngine.snap.sessionId
        val r = reason.name
        CoroutineScope(Dispatchers.IO).launch {
            FocusApp.instance.container.db.attemptDao().insert(AttemptRec(sessionId = sid, pkg = pkg, ts = System.currentTimeMillis(), reason = r))
        }
    }
}
