package com.focusguard.app.engine

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.focusguard.app.Scheduler

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        Scheduler.rescheduleAll(c)
        val s = BlockEngine.snap
        if (s.active && System.currentTimeMillis() < s.sessionEnd) {
            ContextCompat.startForegroundService(c, Intent(c, FocusService::class.java).setAction(FocusService.ACTION_RESTORE))
        }
    }
}
