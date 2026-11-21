@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.focusguard.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focusguard.app.Perms
import com.focusguard.app.R

@Composable
fun SettingsScreen(vm: MainVm) {
    val ctx = LocalContext.current
    val c = MaterialTheme.colorScheme
    val lang by vm.prefs.language.collectAsStateWithLifecycle()
    val mode by vm.prefs.mode.collectAsStateWithLifecycle()
    val palName by vm.prefs.palette.collectAsStateWithLifecycle()
    var tick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { tick++ }
    val perms = remember(tick) { Perms.items(ctx) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        GradientHeader {
            Text(stringResource(R.string.tab_settings), style = MaterialTheme.typography.headlineMedium, color = c.onPrimary)
        }
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            SoftCard(Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.language), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = lang == "fa", onClick = { vm.prefs.setLanguage("fa") }, label = { Text("فارسی") })
                    FilterChip(selected = lang == "en", onClick = { vm.prefs.setLanguage("en") }, label = { Text("English") })
                    FilterChip(selected = lang == "system", onClick = { vm.prefs.setLanguage("system") }, label = { Text(stringResource(R.string.lang_system)) })
                }
            }
            SoftCard(Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.appearance), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = mode == "system", onClick = { vm.prefs.setMode("system") }, label = { Text(stringResource(R.string.mode_auto)) })
                    FilterChip(selected = mode == "light", onClick = { vm.prefs.setMode("light") }, label = { Text("☀️ " + stringResource(R.string.mode_light)) })
                    FilterChip(selected = mode == "dark", onClick = { vm.prefs.setMode("dark") }, label = { Text("🌙 " + stringResource(R.string.mode_dark)) })
                }
                Spacer(Modifier.height(14.dp))
                listOf(true, false).forEach { girly ->
                    Text(
                        stringResource(if (girly) R.string.pal_girly else R.string.pal_classic),
                        style = MaterialTheme.typography.labelLarge, color = c.onSurfaceVariant
                    )
                    Spacer(Modifier.height(6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Palette.entries.filter { it.girly == girly }.forEach { p ->
                            val on = p.name == palName
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { vm.prefs.setPalette(p.name) }) {
                                Box(
                                    Modifier.size(56.dp).clip(CircleShape)
                                        .background(Brush.linearGradient(listOf(p.light.primary, p.light.secondary)))
                                        .then(if (on) Modifier.border(3.dp, c.onSurface, CircleShape) else Modifier),
                                    contentAlignment = Alignment.Center
                                ) { Text(p.emoji, style = MaterialTheme.typography.titleLarge) }
                                Text(stringResource(p.label), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }
            }
            SoftCard(Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.permissions), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(10.dp))
                PermissionRows(perms)
            }
            SoftCard(Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.battery_title), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Text(stringResource(R.string.battery_body), style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant)
            }
            Text(
                stringResource(R.string.about_line), Modifier.fillMaxWidth().padding(8.dp),
                style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant
            )
        }
    }
}
