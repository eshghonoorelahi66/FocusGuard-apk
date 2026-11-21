@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.focusguard.app.ui

import android.app.TimePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focusguard.app.Fmt
import com.focusguard.app.PlanBlock
import com.focusguard.app.R
import com.focusguard.app.Scheduler
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle

@Composable
fun PlannerScreen(vm: MainVm) {
    val plans by vm.plans.collectAsStateWithLifecycle()
    val profiles by vm.profiles.collectAsStateWithLifecycle()
    var day by rememberSaveable { mutableLongStateOf(LocalDate.now().toEpochDay()) }
    var edit by remember { mutableStateOf<PlanBlock?>(null) }
    val loc = appLocale()
    val c = MaterialTheme.colorScheme
    val date = LocalDate.ofEpochDay(day)
    val blocks = plans.filter { Scheduler.applies(it, date) }.sortedBy { it.startMin }
    val rtl = androidx.compose.ui.platform.LocalLayoutDirection.current == androidx.compose.ui.unit.LayoutDirection.Rtl

    Box(Modifier.fillMaxSize()) {
        LazyColumn(contentPadding = PaddingValues(bottom = 100.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                GradientHeader {
                    Text(stringResource(R.string.tab_plan), style = MaterialTheme.typography.headlineMedium, color = c.onPrimary)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton({ day -= 1 }) { Icon(if (rtl) Icons.Rounded.ChevronRight else Icons.Rounded.ChevronLeft, null, tint = c.onPrimary) }
                        Text(
                            Fmt.date(date, loc) + "  ·  " + date.dayOfWeek.getDisplayName(TextStyle.FULL, loc),
                            Modifier.weight(1f), color = c.onPrimary
                        )
                        IconButton({ day += 1 }) { Icon(if (rtl) Icons.Rounded.ChevronLeft else Icons.Rounded.ChevronRight, null, tint = c.onPrimary) }
                    }
                    if (day != LocalDate.now().toEpochDay()) TextButton({ day = LocalDate.now().toEpochDay() }) {
                        Text(stringResource(R.string.today), color = c.onPrimary)
                    }
                }
            }
            if (blocks.isEmpty()) item {
                Text(stringResource(R.string.plan_empty), Modifier.padding(24.dp), color = c.onSurfaceVariant)
            }
            items(blocks, key = { it.id }) { b ->
                val prof = profiles.firstOrNull { it.id == b.profileId }
                SoftCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth(), onClick = { edit = b }) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(Fmt.hhmm(b.startMin, loc), style = MaterialTheme.typography.titleMedium, color = c.primary)
                            Text(Fmt.hhmm(b.endMin, loc), style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant)
                        }
                        Column(Modifier.weight(1f)) {
                            Text(b.title, style = MaterialTheme.typography.titleMedium)
                            val tags = buildList {
                                if (prof != null) add("${prof.emoji} ${prof.name}")
                                if (b.repeatMask != 0) add(stringResource(R.string.repeats))
                            }
                            if (tags.isNotEmpty()) Text(tags.joinToString("  ·  "), style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = { edit = PlanBlock(title = "", epochDay = day, startMin = 9 * 60, endMin = 10 * 60) },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            icon = { Icon(Icons.Rounded.Add, null) }, text = { Text(stringResource(R.string.add_block)) }
        )
    }
    edit?.let { b ->
        BlockDialog(
            b, vm, onSave = { vm.upsertPlan(it); edit = null },
            onDelete = { vm.deletePlan(b); edit = null }, onDismiss = { edit = null }
        )
    }
}

@Composable
private fun BlockDialog(b: PlanBlock, vm: MainVm, onSave: (PlanBlock) -> Unit, onDelete: () -> Unit, onDismiss: () -> Unit) {
    val profiles by vm.profiles.collectAsStateWithLifecycle()
    var title by remember { mutableStateOf(b.title) }
    var start by remember { mutableIntStateOf(b.startMin) }
    var end by remember { mutableIntStateOf(b.endMin) }
    var mask by remember { mutableIntStateOf(b.repeatMask) }
    var prof by remember { mutableLongStateOf(b.profileId) }
    val ctx = LocalContext.current
    val loc = appLocale()
    // week order: Saturday first for Persian, Monday first otherwise
    val order = if (loc.language == "fa") listOf(6, 7, 1, 2, 3, 4, 5) else listOf(1, 2, 3, 4, 5, 6, 7)

    fun pick(cur: Int, set: (Int) -> Unit) {
        TimePickerDialog(ctx, { _, h, m -> set(h * 60 + m) }, cur / 60, cur % 60, true).show()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (b.id == 0L) R.string.add_block else R.string.edit_block)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(title, { title = it }, singleLine = true, label = { Text(stringResource(R.string.block_title)) })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton({ pick(start) { start = it } }) { Text(stringResource(R.string.from_t, Fmt.hhmm(start, loc))) }
                    OutlinedButton({ pick(end) { end = it } }) { Text(stringResource(R.string.to_t, Fmt.hhmm(end, loc))) }
                }
                if (end <= start) Text(stringResource(R.string.err_time), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.repeat_days), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    order.forEach { d ->
                        FilterChip(
                            selected = (mask and (1 shl d)) != 0,
                            onClick = { mask = mask xor (1 shl d) },
                            label = { Text(DayOfWeek.of(d).getDisplayName(TextStyle.SHORT, loc)) }
                        )
                    }
                }
                Text(stringResource(R.string.auto_focus), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = prof == 0L, onClick = { prof = 0 }, label = { Text(stringResource(R.string.none)) })
                    profiles.forEach { p ->
                        FilterChip(selected = prof == p.id, onClick = { prof = p.id }, label = { Text("${p.emoji} ${p.name}") })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = title.isNotBlank() && end > start,
                onClick = { onSave(b.copy(title = title.trim(), startMin = start, endMin = end, repeatMask = mask, profileId = prof)) }
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            Row {
                if (b.id != 0L) TextButton(onDelete) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
                TextButton(onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        }
    )
}
