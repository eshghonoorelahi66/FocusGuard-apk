@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.focusguard.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focusguard.app.*
import java.time.LocalDate

@Composable
fun ReportsScreen(vm: MainVm) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var range by rememberSaveable { mutableIntStateOf(1) } // 0 today, 1 week, 2 month
    val days = listOf(1, 7, 30)[range]
    val today = LocalDate.now()
    val from = today.minusDays((days - 1).toLong())
    val c = MaterialTheme.colorScheme

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        GradientHeader {
            Text(stringResource(R.string.tab_reports), style = MaterialTheme.typography.headlineMedium, color = c.onPrimary)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(R.string.r_today, R.string.r_week, R.string.r_month).forEachIndexed { i, r ->
                    FilterChip(selected = range == i, onClick = { range = i }, label = { Text(stringResource(r)) })
                }
            }
        }
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            TabRow(selectedTabIndex = tab, containerColor = c.background) {
                listOf(R.string.rt_usage, R.string.rt_focus, R.string.rt_plan).forEachIndexed { i, r ->
                    Tab(selected = tab == i, onClick = { tab = i }, text = { Text(stringResource(r)) })
                }
            }
            when (tab) {
                0 -> UsageTab(vm, from, today, days)
                1 -> FocusTab(vm, from, today, days)
                else -> PlanTab(vm, from, today)
            }
        }
    }
}

private fun dayLabels(n: Int, today: LocalDate, loc: java.util.Locale): List<String> =
    (n - 1 downTo 0).map { off ->
        val d = today.minusDays(off.toLong())
        if (n <= 7 || off % 5 == 0) Fmt.dayNum(d, loc) else ""
    }

@Composable
private fun UsageTab(vm: MainVm, from: LocalDate, today: LocalDate, days: Int) {
    val ctx = LocalContext.current
    val loc = appLocale()
    val c = MaterialTheme.colorScheme
    val limits by vm.limits.collectAsStateWithLifecycle()
    val rows by remember(from) { vm.usage(from, today) }.collectAsStateWithLifecycle(emptyList())
    val chartDays = if (days == 1) 7 else days
    val chartFrom = today.minusDays((chartDays - 1).toLong())
    val chartRows by remember(chartFrom) { vm.usage(chartFrom, today) }.collectAsStateWithLifecycle(emptyList())
    var limitFor by remember { mutableStateOf<String?>(null) }

    if (!Perms.usageGranted(ctx)) {
        SoftCard(Modifier.fillMaxWidth(), tint = c.primaryContainer) {
            Text(stringResource(R.string.usage_need_perm), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            Button({ Perms.open(ctx, "usage") }) { Text(stringResource(R.string.grant)) }
        }
        return
    }

    val perApp = remember(rows) {
        rows.groupBy { it.pkg }.map { (p, l) -> Triple(p, l.sumOf { it.ms }, l.sumOf { it.launches }) }.sortedByDescending { it.second }
    }
    val total = perApp.sumOf { it.second }
    val perDay = chartRows.groupBy { it.day }.mapValues { e -> e.value.sumOf { it.ms } }
    val values = (chartDays - 1 downTo 0).map { off -> (perDay[today.minusDays(off.toLong()).toString()] ?: 0L).toFloat() }

    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StatTile(Fmt.dur(ctx, total), stringResource(R.string.total_screen), Modifier.weight(1f))
        StatTile(
            Fmt.dur(ctx, total / days), stringResource(R.string.daily_avg), Modifier.weight(1f)
        )
    }
    SoftCard(Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.last_days, chartDays), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(10.dp))
        BarChart(values, dayLabels(chartDays, today, loc), values.lastIndex)
    }

    val cats = remember(perApp) {
        perApp.groupBy { Apps.categoryRes(Apps.category(ctx, it.first)) }.map { (r, l) -> r to l.sumOf { it.second } }.sortedByDescending { it.second }
    }
    if (cats.isNotEmpty()) SoftCard(Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.by_category), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        cats.take(6).forEach { (r, ms) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(r), Modifier.width(110.dp), style = MaterialTheme.typography.bodyMedium)
                LinearProgressIndicator(progress = { if (total > 0) ms.toFloat() / total else 0f }, Modifier.weight(1f).height(8.dp), trackColor = c.primaryContainer)
                Text(Fmt.dur(ctx, ms), Modifier.padding(start = 10.dp), style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(6.dp))
        }
    }

    SectionTitle(stringResource(R.string.top_apps))
    if (perApp.isEmpty()) Text(stringResource(R.string.no_usage_yet), color = c.onSurfaceVariant)
    val maxMs = perApp.maxOfOrNull { it.second } ?: 1L
    perApp.take(25).forEach { (pkg, ms, launches) ->
        SoftCard(Modifier.fillMaxWidth(), onClick = { limitFor = pkg }) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AppIcon(pkg, 42.dp)
                Column(Modifier.weight(1f)) {
                    Text(remember(pkg) { Apps.label(ctx, pkg) }, maxLines = 1, style = MaterialTheme.typography.titleSmall)
                    LinearProgressIndicator(progress = { ms.toFloat() / maxMs }, Modifier.fillMaxWidth().padding(vertical = 5.dp).height(6.dp), trackColor = c.primaryContainer)
                    Text(
                        stringResource(R.string.opens_n, launches) + (limits[pkg]?.let { "  ·  ⏱ " + Fmt.dur(ctx, it) } ?: ""),
                        style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant
                    )
                }
                Text(Fmt.dur(ctx, ms), style = MaterialTheme.typography.titleSmall, color = c.primary)
            }
        }
    }

    limitFor?.let { pkg ->
        AlertDialog(
            onDismissRequest = { limitFor = null },
            title = { Text(stringResource(R.string.daily_limit_for, Apps.label(ctx, pkg))) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.limit_note), style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(15, 30, 45, 60, 90, 120, 180).forEach { m ->
                            FilterChip(
                                selected = limits[pkg] == m * 60_000L,
                                onClick = { vm.setLimit(pkg, m * 60_000L); limitFor = null },
                                label = { Text(stringResource(R.string.dur_m, m)) }
                            )
                        }
                    }
                }
            },
            confirmButton = { TextButton({ limitFor = null }) { Text(stringResource(R.string.close)) } },
            dismissButton = { if (limits[pkg] != null) TextButton({ vm.setLimit(pkg, 0); limitFor = null }) { Text(stringResource(R.string.remove_limit)) } }
        )
    }
}

@Composable
private fun FocusTab(vm: MainVm, from: LocalDate, today: LocalDate, days: Int) {
    val ctx = LocalContext.current
    val loc = appLocale()
    val c = MaterialTheme.colorScheme
    val fromMs = Fmt.dayStart(from)
    val toMs = Fmt.dayStart(today.plusDays(1))
    val sessions by remember(from) { vm.sessions(fromMs, toMs) }.collectAsStateWithLifecycle(emptyList())
    val attempts by remember(from) { vm.attempts(fromMs, toMs) }.collectAsStateWithLifecycle(emptyList())
    val longFrom = Fmt.dayStart(today.minusDays(60))
    val longSessions by remember(today) { vm.sessions(longFrom, toMs) }.collectAsStateWithLifecycle(emptyList())

    val chartDays = if (days == 1) 7 else days
    val chartFrom = Fmt.dayStart(today.minusDays((chartDays - 1).toLong()))
    val perDay = longSessions.filter { it.startAt >= chartFrom }
        .groupBy { java.time.Instant.ofEpochMilli(it.startAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate() }
        .mapValues { e -> e.value.sumOf { it.focusedMs } }
    val values = (chartDays - 1 downTo 0).map { off -> (perDay[today.minusDays(off.toLong())] ?: 0L).toFloat() }

    val doneDays = longSessions.filter { it.completed }
        .map { java.time.Instant.ofEpochMilli(it.startAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate() }.toSet()
    var streak = 0
    var d = if (today in doneDays) today else today.minusDays(1)
    while (d in doneDays) { streak++; d = d.minusDays(1) }

    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StatTile(Fmt.dur(ctx, sessions.sumOf { it.focusedMs }), stringResource(R.string.stat_focus), Modifier.weight(1f))
        StatTile(String.format(loc, "%d / %d", sessions.count { it.completed }, sessions.size), stringResource(R.string.sessions_done), Modifier.weight(1f))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StatTile(String.format(loc, "%d", attempts.size), stringResource(R.string.stat_blocked), Modifier.weight(1f))
        StatTile("🔥 " + String.format(loc, "%d", streak), stringResource(R.string.streak), Modifier.weight(1f))
    }
    SoftCard(Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.focus_per_day), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(10.dp))
        BarChart(values, dayLabels(chartDays, today, loc), values.lastIndex)
    }
    val top = attempts.groupBy { it.pkg }.map { it.key to it.value.size }.sortedByDescending { it.second }.take(5)
    if (top.isNotEmpty()) SoftCard(Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.most_tempting), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        top.forEach { (pkg, n) ->
            Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AppIcon(pkg, 34.dp)
                Text(remember(pkg) { Apps.label(ctx, pkg) }, Modifier.weight(1f), maxLines = 1)
                Text(stringResource(R.string.times_n, n), color = c.primary)
            }
        }
    }
}

@Composable
private fun PlanTab(vm: MainVm, from: LocalDate, today: LocalDate) {
    val ctx = LocalContext.current
    val loc = appLocale()
    val c = MaterialTheme.colorScheme
    val plans by vm.plans.collectAsStateWithLifecycle()
    val tasks by vm.tasks.collectAsStateWithLifecycle()
    val fromMs = Fmt.dayStart(from)
    val toMs = Fmt.dayStart(today.plusDays(1))
    val sessions by remember(from) { vm.sessions(fromMs - 86_400_000L, toMs) }.collectAsStateWithLifecycle(emptyList())
    val now = System.currentTimeMillis()

    // planned vs actual per day: actual = overlap between each block window and real sessions
    class Row3(val d: LocalDate, val planned: Long, val actual: Long)
    val rows = remember(plans, sessions, from) {
        generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(today) }.map { d ->
            var planned = 0L; var actual = 0L
            plans.filter { Scheduler.applies(it, d) }.forEach { b ->
                val ws = Fmt.dayStart(d) + b.startMin * 60_000L
                val we = Fmt.dayStart(d) + b.endMin * 60_000L
                planned += we - ws
                sessions.forEach { s ->
                    val se = if (s.endAt == 0L) now else s.endAt
                    actual += (minOf(we, se) - maxOf(ws, s.startAt)).coerceAtLeast(0)
                }
            }
            Row3(d, planned, minOf(actual, planned))
        }.toList()
    }
    val planned = rows.sumOf { it.planned }
    val actual = rows.sumOf { it.actual }
    val pct = if (planned > 0) actual.toFloat() / planned else 0f

    val doneIn = tasks.count { it.done && it.doneAt in fromMs until toMs }
    val openNow = tasks.count { !it.done }
    val overdue = tasks.count { !it.done && it.due != 0L && it.due < today.toEpochDay() }

    SoftCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            ProgressRing(pct, 110.dp) { Text(String.format(loc, "%d%%", (pct * 100).toInt()), style = MaterialTheme.typography.titleLarge) }
            Column {
                Text(stringResource(R.string.plan_vs_actual), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.planned_x, Fmt.dur(ctx, planned)), color = c.onSurfaceVariant)
                Text(stringResource(R.string.actual_x, Fmt.dur(ctx, actual)), color = c.primary)
            }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StatTile(String.format(loc, "%d", doneIn), stringResource(R.string.tasks_done), Modifier.weight(1f))
        StatTile(String.format(loc, "%d", openNow), stringResource(R.string.tasks_open), Modifier.weight(1f))
        StatTile(String.format(loc, "%d", overdue), stringResource(R.string.tasks_overdue), Modifier.weight(1f))
    }
    if (planned == 0L) Text(stringResource(R.string.no_plan_yet), color = c.onSurfaceVariant)
    rows.filter { it.planned > 0 }.asReversed().take(14).forEach { r ->
        SoftCard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(Fmt.shortDate(r.d, loc), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                Text(Fmt.dur(ctx, r.actual) + " / " + Fmt.dur(ctx, r.planned), style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant)
            }
            LinearProgressIndicator(
                progress = { if (r.planned > 0) r.actual.toFloat() / r.planned else 0f },
                Modifier.fillMaxWidth().padding(top = 8.dp).height(8.dp), trackColor = c.primaryContainer
            )
        }
    }
}
