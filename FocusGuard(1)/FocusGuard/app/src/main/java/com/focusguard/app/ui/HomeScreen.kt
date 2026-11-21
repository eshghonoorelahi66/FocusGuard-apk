@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.focusguard.app.ui

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.focusguard.app.Fmt
import com.focusguard.app.Perms
import com.focusguard.app.R
import com.focusguard.app.engine.BlockEngine
import com.focusguard.app.engine.FocusService
import java.time.LocalDate
import kotlin.math.roundToInt

@Composable
fun HomeScreen(vm: MainVm, nav: NavController) {
    val ctx = LocalContext.current
    val loc = appLocale()
    val c = MaterialTheme.colorScheme
    val snap by BlockEngine.state.collectAsStateWithLifecycle()
    val profiles by vm.profiles.collectAsStateWithLifecycle()
    val tasks by vm.tasks.collectAsStateWithLifecycle()
    val focusTaskId by vm.focusTask.collectAsStateWithLifecycle()
    val palName by vm.prefs.palette.collectAsStateWithLifecycle()
    val pal = remember(palName) { Palette.of(palName) }

    var tick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { tick++; vm.syncUsage() }
    val perms = remember(tick) { Perms.items(ctx) }
    val today = remember(tick) { LocalDate.now() }
    val t0 = Fmt.dayStart(today)
    val usage by remember(today) { vm.usage(today, today) }.collectAsStateWithLifecycle(emptyList())
    val sessions by remember(today) { vm.sessions(t0, t0 + 86_400_000L) }.collectAsStateWithLifecycle(emptyList())
    val attempts by remember(today) { vm.attempts(t0, t0 + 86_400_000L) }.collectAsStateWithLifecycle(emptyList())

    var selId by rememberSaveable { mutableLongStateOf(0L) }
    val profile = profiles.firstOrNull { it.id == selId } ?: profiles.firstOrNull()
    var minutes by rememberSaveable { mutableIntStateOf(45) }
    var pomodoro by rememberSaveable { mutableStateOf(false) }
    var combo by rememberSaveable { mutableIntStateOf(0) }
    var strict by rememberSaveable(profile?.id) { mutableStateOf(profile?.strict ?: false) }
    var confirmStop by remember { mutableStateOf(false) }
    val combos = listOf(25 to 5, 45 to 10, 50 to 10)
    val quotes = stringArrayResource(R.array.quotes)

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        GradientHeader {
            Text(stringResource(R.string.hello, pal.emoji), style = MaterialTheme.typography.headlineMedium, color = c.onPrimary)
            Text(Fmt.date(today, loc), color = c.onPrimary.copy(alpha = 0.85f))
            Spacer(Modifier.height(10.dp))
            Text(quotes[today.dayOfYear % quotes.size], style = MaterialTheme.typography.bodyMedium, color = c.onPrimary.copy(alpha = 0.92f))
        }

        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            val missing = perms.filter { !it.ok }
            if (missing.any { it.id in setOf("a11y", "usage", "overlay", "notif") }) {
                SoftCard(Modifier.fillMaxWidth(), tint = c.primaryContainer) {
                    Text(stringResource(R.string.setup_needed), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.setup_needed_d), style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant)
                    Spacer(Modifier.height(10.dp))
                    PermissionRows(missing)
                }
            }

            if (snap.active) {
                val now by rememberNow()
                val total = (snap.sessionEnd - snap.startAt).coerceAtLeast(1)
                val left = (snap.sessionEnd - now).coerceAtLeast(0)
                SoftCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        ProgressRing(left.toFloat() / total, 250.dp) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(Fmt.clock(left, loc), fontSize = 44.sp, fontWeight = FontWeight.Bold, color = c.onSurface)
                                Text(
                                    stringResource(if (snap.blocking) R.string.phase_focus else R.string.phase_break),
                                    color = c.primary, style = MaterialTheme.typography.titleMedium
                                )
                                if (snap.pomodoro) Text(
                                    stringResource(R.string.next_in, Fmt.clock(snap.phaseEnd - now, loc)),
                                    style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant
                                )
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(snap.profileName, style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(10.dp))
                        if (snap.strict) Text(stringResource(R.string.strict_on), color = c.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                        else OutlinedButton(onClick = { confirmStop = true }) { Text(stringResource(R.string.stop_session)) }
                    }
                }
            } else {
                SoftCard(Modifier.fillMaxWidth()) {
                    if (profiles.isEmpty()) {
                        Text(stringResource(R.string.no_profile), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.no_profile_d), style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant)
                        Spacer(Modifier.height(10.dp))
                        Button(onClick = { nav.navigate("profiles") }) { Text(stringResource(R.string.create_profile)) }
                    } else {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.choose_profile), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                            TextButton(onClick = { nav.navigate("profiles") }) { Text(stringResource(R.string.manage)) }
                        }
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(profiles, key = { it.id }) { p ->
                                FilterChip(selected = p.id == profile?.id, onClick = { selId = p.id }, label = { Text("${p.emoji} ${p.name}") })
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                        Text(stringResource(R.string.duration), style = MaterialTheme.typography.titleMedium)
                        Text(Fmt.dur(ctx, minutes * 60_000L), style = MaterialTheme.typography.headlineSmall, color = c.primary)
                        Slider(value = minutes.toFloat(), onValueChange = { minutes = (it / 5).roundToInt() * 5 }, valueRange = 5f..240f)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(listOf(15, 25, 45, 60, 90, 120)) { m ->
                                FilterChip(selected = minutes == m, onClick = { minutes = m }, label = { Text(stringResource(R.string.dur_m, m)) })
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(R.string.pomodoro), style = MaterialTheme.typography.titleMedium)
                                Text(stringResource(R.string.pomodoro_d), style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant)
                            }
                            Switch(checked = pomodoro, onCheckedChange = { pomodoro = it })
                        }
                        if (pomodoro) LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(combos.indices.toList()) { i ->
                                FilterChip(
                                    selected = combo == i, onClick = { combo = i },
                                    label = { Text(stringResource(R.string.combo, combos[i].first, combos[i].second)) }
                                )
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(R.string.strict), style = MaterialTheme.typography.titleMedium)
                                Text(stringResource(R.string.strict_d), style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant)
                            }
                            Switch(checked = strict, onCheckedChange = { strict = it })
                        }
                        val linked = tasks.firstOrNull { it.id == focusTaskId }
                        if (linked != null) {
                            Spacer(Modifier.height(6.dp))
                            AssistChip(
                                onClick = { vm.focusTask.value = 0 }, label = { Text("🎯 ${linked.title}") },
                                trailingIcon = { Icon(Icons.Rounded.Close, null, Modifier.size(16.dp)) }
                            )
                        }
                        Spacer(Modifier.height(14.dp))
                        Button(
                            onClick = {
                                val p = profile
                                if (p != null) {
                                    if (!Perms.canBlock(ctx)) Toast.makeText(ctx, R.string.err_no_perm, Toast.LENGTH_LONG).show()
                                    else if (!p.allowMode && p.pkgSet().isEmpty()) Toast.makeText(ctx, R.string.err_no_apps, Toast.LENGTH_LONG).show()
                                    else FocusService.start(ctx, p.id, minutes, strict, pomodoro, combos[combo].first, combos[combo].second, focusTaskId)
                                }
                            },
                            enabled = profile != null,
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.fillMaxWidth().height(56.dp)
                        ) {
                            Icon(Icons.Rounded.PlayArrow, null)
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.start_focus), style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile(Fmt.dur(ctx, sessions.sumOf { it.focusedMs }), stringResource(R.string.stat_focus), Modifier.weight(1f))
                StatTile(String.format(loc, "%d", attempts.size), stringResource(R.string.stat_blocked), Modifier.weight(1f))
                StatTile(Fmt.dur(ctx, usage.sumOf { it.ms }), stringResource(R.string.stat_screen), Modifier.weight(1f))
            }
        }
    }

    if (confirmStop) AlertDialog(
        onDismissRequest = { confirmStop = false },
        title = { Text(stringResource(R.string.stop_confirm_title)) },
        text = { Text(stringResource(R.string.stop_confirm_body)) },
        confirmButton = { TextButton({ confirmStop = false; FocusService.stop(ctx) }) { Text(stringResource(R.string.stop_session)) } },
        dismissButton = { TextButton({ confirmStop = false }) { Text(stringResource(R.string.keep_going)) } }
    )
}
