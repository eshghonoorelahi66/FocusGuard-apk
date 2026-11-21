package com.focusguard.app

import android.content.Context
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

object Fmt {
    private val JALALI_MONTHS = listOf(
        "فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
        "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند"
    )

    /** Gregorian -> Jalali (Solar Hijri). */
    fun toJalali(d: LocalDate): Triple<Int, Int, Int> {
        val gy = d.year; val gm = d.monthValue; val gd = d.dayOfMonth
        val gdm = intArrayOf(0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334)
        val gy2 = if (gm > 2) gy + 1 else gy
        var days = 355666 + (365 * gy) + ((gy2 + 3) / 4) - ((gy2 + 99) / 100) + ((gy2 + 399) / 400) + gd + gdm[gm - 1]
        var jy = -1595 + (33 * (days / 12053))
        days %= 12053
        jy += 4 * (days / 1461)
        days %= 1461
        if (days > 365) { jy += (days - 1) / 365; days = (days - 1) % 365 }
        val jm: Int; val jd: Int
        if (days < 186) { jm = 1 + days / 31; jd = 1 + days % 31 }
        else { jm = 7 + (days - 186) / 30; jd = 1 + (days - 186) % 30 }
        return Triple(jy, jm, jd)
    }

    fun date(d: LocalDate, loc: Locale): String =
        if (loc.language == "fa") {
            val (y, m, day) = toJalali(d)
            String.format(loc, "%d %s %d", day, JALALI_MONTHS[m - 1], y)
        } else d.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(loc))

    fun shortDate(d: LocalDate, loc: Locale): String =
        if (loc.language == "fa") {
            val (_, m, day) = toJalali(d)
            String.format(loc, "%d %s", day, JALALI_MONTHS[m - 1])
        } else d.format(DateTimeFormatter.ofPattern("MMM d", loc))

    fun dayNum(d: LocalDate, loc: Locale): String =
        if (loc.language == "fa") String.format(loc, "%d", toJalali(d).third)
        else String.format(loc, "%d", d.dayOfMonth)

    fun hhmm(min: Int, loc: Locale): String = String.format(loc, "%02d:%02d", min / 60, min % 60)

    fun clock(ms: Long, loc: Locale): String {
        val s = (ms / 1000).coerceAtLeast(0)
        val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60
        return if (h > 0) String.format(loc, "%d:%02d:%02d", h, m, sec) else String.format(loc, "%02d:%02d", m, sec)
    }

    fun dur(ctx: Context, ms: Long): String {
        val min = (ms / 60_000).toInt()
        val h = min / 60; val m = min % 60
        return when {
            h > 0 && m > 0 -> ctx.getString(R.string.dur_hm, h, m)
            h > 0 -> ctx.getString(R.string.dur_h, h)
            else -> ctx.getString(R.string.dur_m, m)
        }
    }

    fun dayStart(d: LocalDate): Long = d.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
}
