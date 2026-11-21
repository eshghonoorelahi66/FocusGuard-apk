package com.focusguard.app

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.os.SystemClock
import android.telecom.TelecomManager
import android.util.LruCache
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/** Reads usage events and turns them into per-app foreground time. */
object Usage {
    class Stat { var ms = 0L; var launches = 0 }

    /** Foreground time per package between [from] and [to] (epoch ms), built from activity resume/pause events. */
    fun collect(ctx: Context, from: Long, to: Long): Map<String, Stat> {
        val usm = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val ev = usm.queryEvents(from, to) ?: return emptyMap()
        val e = UsageEvents.Event()
        val out = HashMap<String, Stat>()
        val acts = HashMap<String, MutableSet<String>>()
        val start = HashMap<String, Long>()
        fun close(pkg: String, at: Long) {
            val s = start.remove(pkg) ?: return
            out.getOrPut(pkg) { Stat() }.ms += (at - s).coerceAtLeast(0)
        }
        while (ev.hasNextEvent()) {
            ev.getNextEvent(e)
            val pkg = e.packageName ?: continue
            when (e.eventType) {
                UsageEvents.Event.MOVE_TO_FOREGROUND -> {
                    val set = acts.getOrPut(pkg) { HashSet() }
                    if (set.isEmpty()) { start[pkg] = e.timeStamp; out.getOrPut(pkg) { Stat() }.launches++ }
                    set.add(e.className ?: "")
                }
                UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                    val set = acts[pkg] ?: continue
                    set.remove(e.className ?: "")
                    if (set.isEmpty()) close(pkg, e.timeStamp)
                }
                16, 17 -> { // SCREEN_NON_INTERACTIVE / KEYGUARD_SHOWN: everything is in the background
                    start.keys.toList().forEach { close(it, e.timeStamp) }
                    acts.clear()
                }
            }
        }
        start.keys.toList().forEach { close(it, to) }
        return out
    }

    /** (package, timestamp) of every foreground event after [from]; used by the lightweight fallback watcher. */
    fun foregroundSince(ctx: Context, from: Long): List<Pair<String, Long>> {
        val usm = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        val ev = usm.queryEvents(maxOf(from, now - 60_000), now) ?: return emptyList()
        val e = UsageEvents.Event()
        val out = ArrayList<Pair<String, Long>>()
        while (ev.hasNextEvent()) {
            ev.getNextEvent(e)
            if (e.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND && e.timeStamp >= from) out += e.packageName to e.timeStamp
        }
        return out
    }

    private val cache = HashMap<String, Pair<Long, Long>>()
    fun todayMs(ctx: Context, pkg: String): Long {
        val t = SystemClock.elapsedRealtime()
        cache[pkg]?.let { if (t - it.first < 15_000) return it.second }
        val v = collect(ctx, Fmt.dayStart(LocalDate.now()), System.currentTimeMillis())[pkg]?.ms ?: 0L
        cache[pkg] = t to v
        return v
    }
}

object UsageSync {
    /** Stores per-day totals in Room so history survives the ~7-10 days Android keeps events for. */
    suspend fun sync(ctx: Context, days: Int) = withContext(Dispatchers.IO) {
        if (!Perms.usageGranted(ctx)) return@withContext
        val dao = FocusApp.instance.container.db.usageDao()
        val pm = ctx.packageManager
        val launchers = Essentials.launchers(ctx)
        val keepCache = HashMap<String, Boolean>()
        fun keep(p: String) = keepCache.getOrPut(p) {
            p != ctx.packageName && p !in launchers && pm.getLaunchIntentForPackage(p) != null
        }
        val today = LocalDate.now()
        val now = System.currentTimeMillis()
        for (i in 0 until days) {
            val d = today.minusDays(i.toLong())
            val key = d.toString()
            if (i >= 2 && dao.countDay(key) > 0) continue
            val from = Fmt.dayStart(d)
            val to = if (i == 0) now else Fmt.dayStart(d.plusDays(1))
            val rows = Usage.collect(ctx, from, to)
                .filter { it.value.ms >= 1000 && keep(it.key) }
                .map { DailyUsage(key, it.key, it.value.ms, it.value.launches) }
            dao.replaceDay(key, rows)
        }
    }
}

class UsageSyncWorker(c: Context, p: WorkerParameters) : CoroutineWorker(c, p) {
    override suspend fun doWork(): Result {
        UsageSync.sync(applicationContext, 10)
        return Result.success()
    }

    companion object {
        fun schedule(ctx: Context) {
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(
                "usage_sync", ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<UsageSyncWorker>(6, TimeUnit.HOURS).build()
            )
        }
    }
}

data class AppEntry(val pkg: String, val label: String, val category: Int)

object Apps {
    val KNOWN_DISTRACTIONS = setOf(
        "org.telegram.messenger", "org.telegram.messenger.web", "org.telegram.plus", "org.thunderdog.challegram",
        "com.instagram.android", "com.whatsapp", "com.whatsapp.w4b", "com.google.android.youtube",
        "com.zhiliaoapp.musically", "com.ss.android.ugc.trill", "com.twitter.android", "com.facebook.katana",
        "com.snapchat.android", "com.reddit.frontpage", "com.pinterest", "com.discord",
        "ir.eitaa.messenger", "ir.resaneh1.iptv", "ir.nasim", "app.rbmain.a", "com.aparat", "ir.aparat.android"
    )

    fun load(ctx: Context): List<AppEntry> {
        val pm = ctx.packageManager
        val launchers = Essentials.launchers(ctx)
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, 0)
            .map { it.activityInfo.packageName }.distinct()
            .filter { it != ctx.packageName && it !in launchers }
            .mapNotNull { p ->
                runCatching {
                    val ai = pm.getApplicationInfo(p, 0)
                    AppEntry(p, pm.getApplicationLabel(ai).toString(), ai.category)
                }.getOrNull()
            }
            .sortedBy { it.label.lowercase() }
    }

    private val labels = HashMap<String, String>()
    fun label(ctx: Context, pkg: String): String = labels.getOrPut(pkg) {
        runCatching { ctx.packageManager.getApplicationLabel(ctx.packageManager.getApplicationInfo(pkg, 0)).toString() }
            .getOrDefault(pkg)
    }

    fun category(ctx: Context, pkg: String): Int =
        runCatching { ctx.packageManager.getApplicationInfo(pkg, 0).category }.getOrDefault(ApplicationInfo.CATEGORY_UNDEFINED)

    fun categoryRes(c: Int): Int = when (c) {
        0 -> R.string.cat_games
        1 -> R.string.cat_audio
        2 -> R.string.cat_video
        3 -> R.string.cat_photo
        4 -> R.string.cat_social
        5 -> R.string.cat_news
        6 -> R.string.cat_maps
        7 -> R.string.cat_productivity
        else -> R.string.cat_other
    }
}

/** Packages that must stay usable no matter what (launcher, keyboard, dialer, system UI...). */
object Essentials {
    fun launchers(ctx: Context): Set<String> =
        ctx.packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)
            .map { it.activityInfo.packageName }.toSet()

    fun compute(ctx: Context): Set<String> {
        val s = HashSet<String>(launchers(ctx))
        s += ctx.packageName
        s += setOf(
            "com.android.systemui", "android", "com.android.phone", "com.android.server.telecom",
            "com.google.android.dialer", "com.android.dialer", "com.samsung.android.dialer",
            "com.samsung.android.incallui", "com.android.settings"
        )
        runCatching { (ctx.getSystemService(Context.TELECOM_SERVICE) as TelecomManager).defaultDialerPackage?.let { s += it } }
        runCatching {
            (ctx.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                .enabledInputMethodList.forEach { s += it.packageName }
        }
        return s
    }
}

private object IconCache {
    private val cache = LruCache<String, ImageBitmap>(150)
    fun peek(pkg: String): ImageBitmap? = cache.get(pkg)
    fun get(ctx: Context, pkg: String): ImageBitmap? {
        cache.get(pkg)?.let { return it }
        return runCatching {
            ctx.packageManager.getApplicationIcon(pkg).toBitmap(96, 96, Bitmap.Config.ARGB_8888).asImageBitmap()
        }.getOrNull()?.also { cache.put(pkg, it) }
    }
}

@Composable
fun AppIcon(pkg: String, size: Dp = 40.dp) {
    val ctx = LocalContext.current
    val bmp by produceState<ImageBitmap?>(IconCache.peek(pkg), pkg) {
        value = withContext(Dispatchers.Default) { IconCache.get(ctx, pkg) }
    }
    val b = bmp
    if (b != null) {
        Image(bitmap = b, contentDescription = null, modifier = Modifier.size(size).clip(RoundedCornerShape(26)))
    } else {
        Box(Modifier.size(size).clip(RoundedCornerShape(26)).background(MaterialTheme.colorScheme.primaryContainer))
    }
}
