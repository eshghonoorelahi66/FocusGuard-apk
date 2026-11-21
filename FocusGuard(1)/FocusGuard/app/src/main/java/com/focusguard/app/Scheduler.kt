package com.focusguard.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.focusguard.app.engine.FocusService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

/** Keeps exactly one alarm armed: the next planned block that has a profile attached. */
object Scheduler {
    const val RC_SCHEDULE = 7
    const val RC_PHASE = 8

    fun applies(b: PlanBlock, d: LocalDate): Boolean =
        if (b.repeatMask == 0) d.toEpochDay() == b.epochDay
        else d.toEpochDay() >= b.epochDay && (b.repeatMask and (1 shl d.dayOfWeek.value)) != 0

    fun startMs(b: PlanBlock, d: LocalDate): Long =
        d.atStartOfDay(ZoneId.systemDefault()).plusMinutes(b.startMin.toLong()).toInstant().toEpochMilli()

    fun rescheduleAll(ctx: Context) {
        val app = ctx.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            val blocks = FocusApp.instance.container.db.planDao().allOnce().filter { it.profileId != 0L }
            val now = System.currentTimeMillis()
            val today = LocalDate.now()
            var best: Pair<Long, PlanBlock>? = null
            for (b in blocks) {
                for (o in 0..7) {
                    val d = today.plusDays(o.toLong())
                    if (!applies(b, d)) continue
                    val t = startMs(b, d)
                    if (t > now + 1000) {
                        if (best == null || t < best.first) best = t to b
                        break
                    }
                }
            }
            val i = Intent(app, FocusService::class.java).setAction(FocusService.ACTION_SCHEDULED)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            val am = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val chosen = best
            if (chosen == null) {
                am.cancel(PendingIntent.getForegroundService(app, RC_SCHEDULE, i, flags))
                return@launch
            }
            i.putExtra("blockId", chosen.second.id).putExtra("startAt", chosen.first)
            setAlarm(app, chosen.first, PendingIntent.getForegroundService(app, RC_SCHEDULE, i, flags))
        }
    }

    fun setAlarm(ctx: Context, at: Long, pi: PendingIntent) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (Perms.exactOk(ctx)) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
    }
}
