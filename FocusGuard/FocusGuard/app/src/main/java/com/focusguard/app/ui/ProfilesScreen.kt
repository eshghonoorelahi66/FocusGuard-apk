@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.focusguard.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.focusguard.app.AppEntry
import com.focusguard.app.AppIcon
import com.focusguard.app.Apps
import com.focusguard.app.Profile
import com.focusguard.app.R

@Composable
fun ProfilesScreen(vm: MainVm, nav: NavController) {
    val profiles by vm.profiles.collectAsStateWithLifecycle()
    val apps by vm.apps.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<Profile?>(null) }
    val c = MaterialTheme.colorScheme

    Box(Modifier.fillMaxSize()) {
        LazyColumn(contentPadding = PaddingValues(bottom = 100.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                GradientHeader {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.Rounded.ArrowBack, null, tint = c.onPrimary) }
                        Column {
                            Text(stringResource(R.string.profiles), style = MaterialTheme.typography.headlineMedium, color = c.onPrimary)
                            Text(stringResource(R.string.profiles_sub), color = c.onPrimary.copy(alpha = 0.88f))
                        }
                    }
                }
            }
            items(profiles, key = { it.id }) { p ->
                SoftCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth(), onClick = { editing = p }) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(p.emoji, style = MaterialTheme.typography.headlineMedium)
                        Column(Modifier.weight(1f)) {
                            Text(p.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                stringResource(if (p.allowMode) R.string.mode_allow_n else R.string.mode_block_n, p.pkgSet().size) +
                                    if (p.strict) " · " + stringResource(R.string.strict) else "",
                                style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = { editing = Profile(name = "") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            icon = { Icon(Icons.Rounded.Add, null) }, text = { Text(stringResource(R.string.new_profile)) }
        )
    }
    editing?.let { p ->
        ProfileEditor(
            p, apps,
            onSave = { vm.upsertProfile(it); editing = null },
            onDelete = { vm.deleteProfile(p); editing = null },
            onDismiss = { editing = null }
        )
    }
}

@Composable
private fun ProfileEditor(p: Profile, apps: List<AppEntry>, onSave: (Profile) -> Unit, onDelete: () -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(p.name) }
    var emoji by remember { mutableStateOf(p.emoji) }
    var allow by remember { mutableStateOf(p.allowMode) }
    var strict by remember { mutableStateOf(p.strict) }
    var sel by remember { mutableStateOf(p.pkgSet()) }
    var query by remember { mutableStateOf("") }
    val c = MaterialTheme.colorScheme
    val filtered = remember(query, apps) { apps.filter { query.isBlank() || it.label.contains(query, ignoreCase = true) } }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = c.background) {
            Column(Modifier.statusBarsPadding().padding(horizontal = 16.dp)) {
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                    Spacer(Modifier.weight(1f))
                    if (p.id != 0L) IconButton(onClick = onDelete) { Icon(Icons.Rounded.Delete, null, tint = c.error) }
                    Button(
                        enabled = name.isNotBlank(),
                        onClick = { onSave(p.copy(name = name.trim(), emoji = emoji, allowMode = allow, strict = strict, packages = sel.joinToString(","))) }
                    ) { Text(stringResource(R.string.save)) }
                }
                OutlinedTextField(
                    value = name, onValueChange = { name = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.profile_name)) }, shape = MaterialTheme.shapes.medium
                )
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("📚", "💼", "🌙", "🌸", "🎯", "🧘", "✨", "🦋", "☕", "🍑").forEach {
                        FilterChip(selected = emoji == it, onClick = { emoji = it }, label = { Text(it) })
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !allow, onClick = { allow = false }, label = { Text(stringResource(R.string.mode_block)) })
                    FilterChip(selected = allow, onClick = { allow = true }, label = { Text(stringResource(R.string.mode_allow)) })
                }
                Text(
                    stringResource(if (allow) R.string.mode_allow_d else R.string.mode_block_d),
                    style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.strict_default), Modifier.weight(1f))
                    Switch(checked = strict, onCheckedChange = { strict = it })
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = query, onValueChange = { query = it }, singleLine = true, modifier = Modifier.weight(1f),
                        placeholder = { Text(stringResource(R.string.search_apps)) }, shape = MaterialTheme.shapes.medium
                    )
                    AssistChip(
                        onClick = {
                            sel = sel + apps.filter { it.category == 0 || it.category == 2 || it.category == 4 || it.pkg in Apps.KNOWN_DISTRACTIONS }.map { it.pkg }
                        },
                        label = { Text(stringResource(R.string.preset_social)) }
                    )
                }
                Text(
                    stringResource(R.string.selected_n, sel.size),
                    style = MaterialTheme.typography.labelMedium, color = c.primary, modifier = Modifier.padding(vertical = 4.dp)
                )
                LazyColumn(Modifier.weight(1f)) {
                    items(filtered, key = { it.pkg }) { a ->
                        Row(
                            Modifier.fillMaxWidth().clickable { sel = if (a.pkg in sel) sel - a.pkg else sel + a.pkg }.padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            AppIcon(a.pkg, 40.dp)
                            Column(Modifier.weight(1f)) {
                                Text(a.label, maxLines = 1)
                                Text(stringResource(Apps.categoryRes(a.category)), style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant)
                            }
                            Checkbox(checked = a.pkg in sel, onCheckedChange = { sel = if (it) sel + a.pkg else sel - a.pkg })
                        }
                    }
                }
            }
        }
    }
}
