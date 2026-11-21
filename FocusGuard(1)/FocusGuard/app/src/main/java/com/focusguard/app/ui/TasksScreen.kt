@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.focusguard.app.ui

import android.app.DatePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.focusguard.app.Fmt
import com.focusguard.app.R
import com.focusguard.app.TaskRec
import java.time.LocalDate

@Composable
fun TasksScreen(vm: MainVm, nav: NavController) {
    val tasks by vm.tasks.collectAsStateWithLifecycle()
    var edit by remember { mutableStateOf<TaskRec?>(null) }
    val loc = appLocale()
    val ctx = LocalContext.current
    val c = MaterialTheme.colorScheme
    val open = tasks.filter { !it.done }
    val done = tasks.filter { it.done }
    val today = LocalDate.now().toEpochDay()

    Box(Modifier.fillMaxSize()) {
        LazyColumn(contentPadding = PaddingValues(bottom = 100.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                GradientHeader {
                    Text(stringResource(R.string.tab_tasks), style = MaterialTheme.typography.headlineMedium, color = c.onPrimary)
                    Text(
                        stringResource(R.string.tasks_sub, open.size, done.size),
                        color = c.onPrimary.copy(alpha = 0.88f)
                    )
                }
            }
            if (tasks.isEmpty()) item {
                Text(
                    stringResource(R.string.tasks_empty), Modifier.padding(24.dp),
                    color = c.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge
                )
            }
            items(open, key = { it.id }) { t -> TaskRow(t, vm, nav, today, loc, ctx) { edit = t } }
            if (done.isNotEmpty()) {
                item { SectionTitle(stringResource(R.string.done_section), Modifier.padding(horizontal = 20.dp)) }
                items(done, key = { it.id }) { t -> TaskRow(t, vm, nav, today, loc, ctx) { edit = t } }
            }
        }
        ExtendedFloatingActionButton(
            onClick = { edit = TaskRec(title = "") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            icon = { Icon(Icons.Rounded.Add, null) }, text = { Text(stringResource(R.string.add_task)) }
        )
    }
    edit?.let { t ->
        TaskDialog(
            t, onSave = { vm.upsertTask(it); edit = null },
            onDelete = { vm.deleteTask(t); edit = null }, onDismiss = { edit = null }
        )
    }
}

@Composable
private fun TaskRow(
    t: TaskRec, vm: MainVm, nav: NavController, today: Long, loc: java.util.Locale,
    ctx: android.content.Context, onEdit: () -> Unit
) {
    val c = MaterialTheme.colorScheme
    val prio = listOf(R.string.prio_low, R.string.prio_med, R.string.prio_high)[t.priority.coerceIn(0, 2)]
    SoftCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth(), onClick = onEdit) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = t.done, onCheckedChange = { vm.toggleTask(t) })
            Column(Modifier.weight(1f)) {
                Text(
                    t.title, style = MaterialTheme.typography.titleMedium,
                    textDecoration = if (t.done) TextDecoration.LineThrough else null,
                    color = if (t.done) c.onSurfaceVariant else c.onSurface
                )
                val parts = buildList {
                    add(stringResource(prio))
                    if (t.due != 0L) add(Fmt.shortDate(LocalDate.ofEpochDay(t.due), loc))
                    if (t.focusMs > 0) add("🎯 " + Fmt.dur(ctx, t.focusMs))
                }
                Text(
                    parts.joinToString("  ·  "), style = MaterialTheme.typography.bodySmall,
                    color = if (!t.done && t.due != 0L && t.due < today) c.error else c.onSurfaceVariant
                )
            }
            if (!t.done) IconButton(onClick = { vm.focusTask.value = t.id; nav.goTab("home") }) {
                Icon(Icons.Rounded.PlayArrow, stringResource(R.string.focus_on_task), tint = c.primary)
            }
        }
    }
}

@Composable
private fun TaskDialog(t: TaskRec, onSave: (TaskRec) -> Unit, onDelete: () -> Unit, onDismiss: () -> Unit) {
    var title by remember { mutableStateOf(t.title) }
    var note by remember { mutableStateOf(t.note) }
    var prio by remember { mutableIntStateOf(t.priority) }
    var due by remember { mutableLongStateOf(t.due) }
    val ctx = LocalContext.current
    val loc = appLocale()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (t.id == 0L) R.string.add_task else R.string.edit_task)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(title, { title = it }, singleLine = true, label = { Text(stringResource(R.string.task_title)) })
                OutlinedTextField(note, { note = it }, label = { Text(stringResource(R.string.task_note)) }, maxLines = 3)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(R.string.prio_low, R.string.prio_med, R.string.prio_high).forEachIndexed { i, r ->
                        FilterChip(selected = prio == i, onClick = { prio = i }, label = { Text(stringResource(r)) })
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        val d = if (due != 0L) LocalDate.ofEpochDay(due) else LocalDate.now()
                        DatePickerDialog(ctx, { _, y, m, day -> due = LocalDate.of(y, m + 1, day).toEpochDay() }, d.year, d.monthValue - 1, d.dayOfMonth).show()
                    }) {
                        Text(if (due == 0L) stringResource(R.string.due_none) else Fmt.date(LocalDate.ofEpochDay(due), loc))
                    }
                    if (due != 0L) TextButton({ due = 0 }) { Text(stringResource(R.string.clear)) }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = title.isNotBlank(), onClick = { onSave(t.copy(title = title.trim(), note = note.trim(), priority = prio, due = due)) }) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            Row {
                if (t.id != 0L) TextButton(onDelete) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
                TextButton(onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        }
    )
}
