package com.focusguard.app

import android.Manifest
import android.app.AlarmManager
import android.app.AppOpsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.focusguard.app.engine.AppBlockerService

data class PermItem(val id: String, val title: Int, val desc: Int, val ok: Boolean, val required: Boolean)

object Perms {
    fun usageGranted(c: Context): Boolean {
        val ops = c.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val uid = android.os.Process.myUid()
        val mode = if (Build.VERSION.SDK_INT >= 29)
            ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, uid, c.packageName)
        else @Suppress("DEPRECATION") ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, uid, c.packageName)
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun overlayGranted(c: Context) = Settings.canDrawOverlays(c)

    fun accessibilityOn(c: Context): Boolean {
        val me = ComponentName(c, AppBlockerService::class.java)
        val s = Settings.Secure.getString(c.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        return s.split(':').any { ComponentName.unflattenFromString(it) == me }
    }

    fun notifGranted(c: Context) = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(c, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun exactOk(c: Context) = Build.VERSION.SDK_INT < 31 ||
        (c.getSystemService(Context.ALARM_SERVICE) as AlarmManager).canScheduleExactAlarms()

    fun batteryOk(c: Context) =
        (c.getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(c.packageName)

    /** Blocking works if either method is available. */
    fun canBlock(c: Context) = accessibilityOn(c) || (usageGranted(c) && overlayGranted(c))

    fun items(c: Context): List<PermItem> = listOf(
        PermItem("a11y", R.string.p_a11y, R.string.p_a11y_d, accessibilityOn(c), false),
        PermItem("usage", R.string.p_usage, R.string.p_usage_d, usageGranted(c), true),
        PermItem("overlay", R.string.p_overlay, R.string.p_overlay_d, overlayGranted(c), true),
        PermItem("notif", R.string.p_notif, R.string.p_notif_d, notifGranted(c), true),
        PermItem("exact", R.string.p_exact, R.string.p_exact_d, exactOk(c), false),
        PermItem("battery", R.string.p_battery, R.string.p_battery_d, batteryOk(c), false)
    )

    fun open(c: Context, id: String) {
        val pkg = Uri.parse("package:${c.packageName}")
        val i = when (id) {
            "a11y" -> Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            "usage" -> Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
            "overlay" -> Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, pkg)
            "exact" -> if (Build.VERSION.SDK_INT >= 31) Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, pkg) else return
            "battery" -> Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkg)
            else -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg)
        }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { c.startActivity(i) }.onFailure {
            runCatching { c.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
    }
}
