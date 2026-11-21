package com.focusguard.app.engine

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.focusguard.app.FocusApp
import com.focusguard.app.Scheduler
import com.focusguard.app.SessionRec
import com.focusguard.app.Usage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Owns the focus session: phases (Pomodoro), alarms, notification and the UsageStats fallback watcher (method 2).
 * Battery design:
 *  - countdown = system chronometer in the notification (no per-second wakeups)
 *  - phase / end = one exact alarm at a time
 *  - watcher runs ONLY when: a session is active, the screen is on, and the Accessibility service is NOT connected
 */
class FocusService : Service() {
    companion object {
        const val ACTION_START = "com.focusguard.app.START"
        const val ACTION_STOP = "com.focusguard.app.STOP"
        const val ACTION_PHASE = "com.focusguard.app.PHASE"
        const val ACTION_SCHEDULED = "com.focusguard.app.SCHEDULED"
        const val ACTION_RESTORE = "com.focusguard.app.RESTORE"
        private const val NOTIF_ID = 11

        fun start(
            ctx: Context, profileId: Long, minutes: Int, strict: Boolean,
            pomodoro: Boolean, focusMin: Int, breakMin: Int, taskId: Long
        ) {
            ContextCompat.startForegroundService(
                ctx,
                Intent(ctx, FocusService::class.java).setAction(ACTION_START)
                    .putExtra("profileId", profileId).putExtra("minutes", minutes).putExtra("strict", strict)
                    .putExtra("pomodoro", pomodoro).putExtra("focusMin", focusMin).putExtra("breakMin", breakMin)
                    .putExtra("taskId", taskId)
            )
        }

        fun stop(ctx: Context) {
            ContextCompat.startForegroundService(ctx, Intent(ctx, FocusService::class.java).setAction(ACTION_STOP))
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val lock = Mutex()
    private var watch: Job? = null
    private val db get() = FocusApp.instance.container.db

    private val screenRx = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            if (i.action == Intent.ACTION_SCREEN_OFF) watch?.cancel()
            else if (BlockEngine.snap.active) startWatcher()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ContextCompat.registerReceiver(
            this, screenRx,
            IntentFilter().apply { addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_SCREEN_ON) },
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(screenRx) }
        scope.cancel()
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ServiceCompat.startForeground(
            this, NOTIF_ID, Notifs.focus(this, BlockEngine.snap),
            if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        )
        when (intent?.action) {
            ACTION_START -> scope.launch { lock.withLock { startSession(intent!!) } }
            ACTION_STOP -> scope.launch { lock.withLock { endSession(false) } }
            ACTION_PHASE -> scope.launch { lock.withLock { onPhase() } }
            ACTION_SCHEDULED -> scope.launch { lock.withLock { startScheduled(intent!!) } }
            else -> scope.launch { lock.withLock { restore() } }
        }
        return START_STICKY
    }

    // ---------- session lifecycle ----------

    private suspend fun startSession(i: Intent) {
        val cur = BlockEngine.snap
        if (cur.active && System.currentTimeMillis() < cur.sessionEnd) return
        val profile = db.profileDao().get(i.getLongExtra("profileId", 0)) ?: return idle()
        val now = System.currentTimeMillis()
        val total = i.getIntExtra("minutes", 25) * 60_000L
        val pomo = i.getBooleanExtra("pomodoro", false)
        val fMs = i.getIntExtra("focusMin", 25) * 60_000L
        val bMs = i.getIntExtra("breakMin", 5) * 60_000L
        val taskId = i.getLongExtra("taskId", 0)
        val end = now + total
        val sid = db.sessionDao().insert(SessionRec(profileId = profile.id, taskId = taskId, startAt = now, plannedMs = total))
        val phaseEnd = if (pomo) minOf(now + fMs, end) else end
        BlockEngine.set(
            this,
            BlockEngine.Snap(
                active = true, blocking = true, startAt = now, sessionEnd = end, phaseStart = now, phaseEnd = phaseEnd,
                strict = i.getBooleanExtra("strict", false) || profile.strict, allowMode = profile.allowMode,
                pkgs = profile.pkgSet(), sessionId = sid, profileName = profile.name, taskId = taskId,
                pomodoro = pomo, focusMs = fMs, breakMs = bMs
            )
        )
        refreshNotification()
        scheduleAlarm(phaseEnd)
        startWatcher()
    }

    private suspend fun startScheduled(i: Intent) {
        val b = db.planDao().get(i.getLongExtra("blockId", 0))
        val startAt = i.getLongExtra("startAt", System.currentTimeMillis())
        if (b == null || b.profileId == 0L) { idle(); Scheduler.rescheduleAll(this); return }
        val remaining = (b.endMin - b.startMin) * 60_000L - (System.currentTimeMillis() - startAt)
        val cur = BlockEngine.snap
        if (remaining > 60_000L && !(cur.active && System.currentTimeMillis() < cur.sessionEnd)) {
            val minutes = (remaining / 60_000L).toInt().coerceAtLeast(1)
            startSession(
                Intent().putExtra("profileId", b.profileId).putExtra("minutes", minutes)
                    .putExtra("strict", false).putExtra("pomodoro", false)
            )
        } else if (!BlockEngine.snap.active) idle()
        Scheduler.rescheduleAll(this)
    }

    private suspend fun onPhase() {
        val s = BlockEngine.snap
        if (!s.active) return idle()
        val now = System.currentTimeMillis()
        if (now >= s.sessionEnd - 1000) return endSession(true)
        if (!s.pomodoro) { scheduleAlarm(s.phaseEnd); return }
        val n = if (s.blocking)
            s.copy(blocking = false, focusAcc = s.focusAcc + (now - s.phaseStart), phaseStart = now, phaseEnd = minOf(now + s.breakMs, s.sessionEnd))
        else
            s.copy(blocking = true, phaseStart = now, phaseEnd = minOf(now + s.focusMs, s.sessionEnd))
        BlockEngine.set(this, n)
        refreshNotification()
        scheduleAlarm(n.phaseEnd)
    }

    private suspend fun restore() {
        val s = BlockEngine.snap
        val now = System.currentTimeMillis()
        if (!s.active) return idle()
        if (now >= s.sessionEnd) return endSession(true)
        if (now >= s.phaseEnd) onPhase() else { refreshNotification(); scheduleAlarm(s.phaseEnd) }
        startWatcher()
    }

    private suspend fun endSession(completed: Boolean) {
        val s = BlockEngine.snap
        if (!s.active) return idle()
        val now = minOf(System.currentTimeMillis(), s.sessionEnd)
        val focused = s.focusAcc + if (s.blocking) (now - s.phaseStart).coerceAtLeast(0) else 0L
        withContext(Dispatchers.IO) {
            db.sessionDao().finish(s.sessionId, now, focused, completed)
            if (s.taskId != 0L) db.taskDao().addFocus(s.taskId, focused)
        }
        BlockEngine.set(this, BlockEngine.Snap())
        cancelAlarm()
        watch?.cancel()
        if (completed) Notifs.done(this, s.profileName)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun idle() {
        watch?.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun refreshNotification() {
        (getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager)
            .notify(NOTIF_ID, Notifs.focus(this, BlockEngine.snap))
    }

    // ---------- alarms ----------

    private fun phasePi(): PendingIntent = PendingIntent.getForegroundService(
        this, Scheduler.RC_PHASE, Intent(this, FocusService::class.java).setAction(ACTION_PHASE),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun scheduleAlarm(at: Long) = Scheduler.setAlarm(this, at, phasePi())

    private fun cancelAlarm() = (getSystemService(ALARM_SERVICE) as AlarmManager).cancel(phasePi())

    // ---------- method 2: UsageStats fallback watcher ----------

    private fun startWatcher() {
        watch?.cancel()
        watch = scope.launch {
            var lastTs = System.currentTimeMillis()
            while (isActive) {
                if (AppBlockerService.connected) {
                    delay(30_000) // Accessibility is doing the work; just wake rarely to notice if it stops
                    lastTs = System.currentTimeMillis()
                    continue
                }
                val events = Usage.foregroundSince(this@FocusService, lastTs)
                if (events.isNotEmpty()) {
                    lastTs = events.last().second + 1
                    withContext(Dispatchers.Main) {
                        events.forEach { Blocker.onForeground(this@FocusService, it.first, null) }
                    }
                }
                delay(1500)
            }
        }
    }
}
