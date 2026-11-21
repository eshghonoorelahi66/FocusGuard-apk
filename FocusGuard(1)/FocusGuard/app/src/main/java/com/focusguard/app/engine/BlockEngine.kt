package com.focusguard.app.engine

import android.content.Context
import com.focusguard.app.Essentials
import com.focusguard.app.FocusApp
import com.focusguard.app.Usage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/** Single source of truth for "is something blocked right now". Read by both blocking methods. */
object BlockEngine {
    data class Snap(
        val active: Boolean = false,
        val blocking: Boolean = false,       // false during a Pomodoro break
        val startAt: Long = 0,
        val sessionEnd: Long = 0,
        val phaseStart: Long = 0,
        val phaseEnd: Long = 0,
        val focusAcc: Long = 0,
        val strict: Boolean = false,
        val allowMode: Boolean = false,
        val pkgs: Set<String> = emptySet(),
        val sessionId: Long = 0,
        val profileName: String = "",
        val taskId: Long = 0,
        val pomodoro: Boolean = false,
        val focusMs: Long = 0,
        val breakMs: Long = 0
    )

    enum class Reason { SESSION, PROTECTED, LIMIT }

    val state = MutableStateFlow(Snap())
    val snap: Snap get() = state.value

    @Volatile var limits: Map<String, Long> = emptyMap()
    @Volatile private var essentials: Set<String> = emptySet()
    @Volatile private var launchers: Set<String> = emptySet()

    private val ALWAYS = setOf("com.android.systemui", "android")

    /** Apps that can uninstall us or switch the blocker off; locked only in Strict mode. */
    private val PROTECTED = setOf(
        "com.android.settings", "com.android.packageinstaller", "com.google.android.packageinstaller",
        "com.samsung.android.packageinstaller", "com.miui.packageinstaller"
    )

    fun boot(ctx: Context) {
        state.value = load(ctx)
        launchers = Essentials.launchers(ctx)
        essentials = Essentials.compute(ctx)
        refreshLimits(ctx)
    }

    fun set(ctx: Context, s: Snap) {
        state.value = s
        save(ctx, s)
        if (s.allowMode) essentials = Essentials.compute(ctx)
    }

    fun refreshLimits(ctx: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            limits = FocusApp.instance.container.db.limitDao().allOnce().associate { it.pkg to it.limitMs }
        }
    }

    fun ignorable(ctx: Context, pkg: String) = pkg == ctx.packageName || pkg in ALWAYS || pkg in launchers

    fun sessionReason(pkg: String): Reason? {
        val s = snap
        if (!s.active || System.currentTimeMillis() >= s.sessionEnd) return null
        if (s.strict && pkg in PROTECTED) return Reason.PROTECTED
        if (!s.blocking) return null
        val hit = if (s.allowMode) pkg !in s.pkgs && pkg !in essentials else pkg in s.pkgs
        return if (hit) Reason.SESSION else null
    }

    /** Remaining daily allowance in ms, or null when the app has no limit. */
    fun limitLeft(ctx: Context, pkg: String): Long? {
        val l = limits[pkg] ?: return null
        return l - Usage.todayMs(ctx, pkg)
    }

    private fun save(ctx: Context, s: Snap) {
        val j = JSONObject()
            .put("active", s.active).put("blocking", s.blocking).put("startAt", s.startAt)
            .put("sessionEnd", s.sessionEnd).put("phaseStart", s.phaseStart).put("phaseEnd", s.phaseEnd)
            .put("focusAcc", s.focusAcc).put("strict", s.strict).put("allowMode", s.allowMode)
            .put("pkgs", JSONArray(s.pkgs.toList())).put("sessionId", s.sessionId)
            .put("profileName", s.profileName).put("taskId", s.taskId).put("pomodoro", s.pomodoro)
            .put("focusMs", s.focusMs).put("breakMs", s.breakMs)
        ctx.applicationContext.getSharedPreferences("engine", Context.MODE_PRIVATE).edit().putString("snap", j.toString()).apply()
    }

    private fun load(ctx: Context): Snap {
        val str = ctx.applicationContext.getSharedPreferences("engine", Context.MODE_PRIVATE).getString("snap", null) ?: return Snap()
        return runCatching {
            val j = JSONObject(str)
            val arr = j.getJSONArray("pkgs")
            Snap(
                active = j.getBoolean("active"), blocking = j.getBoolean("blocking"), startAt = j.getLong("startAt"),
                sessionEnd = j.getLong("sessionEnd"), phaseStart = j.getLong("phaseStart"), phaseEnd = j.getLong("phaseEnd"),
                focusAcc = j.getLong("focusAcc"), strict = j.getBoolean("strict"), allowMode = j.getBoolean("allowMode"),
                pkgs = (0 until arr.length()).map { arr.getString(it) }.toSet(), sessionId = j.getLong("sessionId"),
                profileName = j.getString("profileName"), taskId = j.getLong("taskId"), pomodoro = j.getBoolean("pomodoro"),
                focusMs = j.getLong("focusMs"), breakMs = j.getLong("breakMs")
            )
        }.getOrDefault(Snap())
    }
}
