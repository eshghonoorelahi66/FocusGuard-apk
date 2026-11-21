package com.focusguard.app.engine

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.focusguard.app.R
import com.focusguard.app.localized
import com.focusguard.app.ui.MainActivity

object Notifs {
    const val CH_FOCUS = "focus"
    const val CH_DONE = "done"

    fun ensureChannels(ctx: Context) {
        val c = ctx.localized()
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel(CH_FOCUS, c.getString(R.string.ch_focus), NotificationManager.IMPORTANCE_LOW))
        nm.createNotificationChannel(NotificationChannel(CH_DONE, c.getString(R.string.ch_done), NotificationManager.IMPORTANCE_DEFAULT))
    }

    fun focus(ctx: Context, s: BlockEngine.Snap): Notification {
        val c = ctx.localized()
        val open = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE
        )
        val b = NotificationCompat.Builder(ctx, CH_FOCUS)
            .setSmallIcon(R.drawable.ic_stat_focus)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        if (s.active) {
            // countdown is drawn by the system itself -> the app wakes up zero times per second
            b.setContentTitle(s.profileName)
                .setContentText(c.getString(if (s.blocking) R.string.n_focus else R.string.n_break))
                .setWhen(s.phaseEnd).setShowWhen(true).setUsesChronometer(true).setChronometerCountDown(true)
            if (!s.strict) {
                b.addAction(
                    0, c.getString(R.string.stop),
                    PendingIntent.getForegroundService(
                        ctx, 1, Intent(ctx, FocusService::class.java).setAction(FocusService.ACTION_STOP), PendingIntent.FLAG_IMMUTABLE
                    )
                )
            }
        } else {
            b.setContentTitle(c.getString(R.string.app_name))
        }
        return b.build()
    }

    fun done(ctx: Context, name: String) {
        val c = ctx.localized()
        val n = NotificationCompat.Builder(ctx, CH_DONE)
            .setSmallIcon(R.drawable.ic_stat_focus)
            .setContentTitle(c.getString(R.string.n_done_title))
            .setContentText(c.getString(R.string.n_done_text, name))
            .setAutoCancel(true)
            .setContentIntent(PendingIntent.getActivity(ctx, 2, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE))
            .build()
        runCatching { (ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(22, n) }
    }
}
