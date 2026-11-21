package com.focusguard.app.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import com.focusguard.app.PermItem
import com.focusguard.app.Perms
import com.focusguard.app.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.util.Locale

@Composable
fun appLocale(): Locale = LocalConfiguration.current.locales[0]

fun NavController.goTab(route: String) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}

/** 1-second ticker that only runs while the screen is visible (STARTED), so it never costs battery in the background. */
@Composable
fun rememberNow(): State<Long> {
    val owner = LocalLifecycleOwner.current
    val st = remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(owner) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive) {
                st.longValue = System.currentTimeMillis()
                delay(1000)
            }
        }
    }
    return st
}

@Composable
fun SoftCard(
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.surfaceVariant,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier.clip(RoundedCornerShape(26.dp)).background(tint)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(16.dp),
        content = content
    )
}

@Composable
fun GradientHeader(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val c = MaterialTheme.colorScheme
    Box(
        modifier.fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 36.dp, bottomEnd = 36.dp))
            .background(Brush.linearGradient(listOf(c.primary, c.secondary)))
    ) {
        Canvas(Modifier.matchParentSize()) {
            drawCircle(Color.White.copy(alpha = 0.13f), radius = size.width * 0.34f, center = Offset(size.width * 0.92f, size.height * 0.05f))
            drawCircle(Color.White.copy(alpha = 0.10f), radius = size.width * 0.20f, center = Offset(size.width * 0.04f, size.height * 1.0f))
        }
        Column(Modifier.statusBarsPadding().padding(horizontal = 22.dp, vertical = 20.dp), content = content)
    }
}

@Composable
fun ProgressRing(progress: Float, size: Dp, content: @Composable BoxScope.() -> Unit) {
    val c = MaterialTheme.colorScheme
    val p by animateFloatAsState(progress.coerceIn(0f, 1f), label = "ring")
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val sw = 16.dp.toPx()
            val inset = sw / 2
            val arcSize = Size(this.size.width - sw, this.size.height - sw)
            drawArc(c.primaryContainer, -90f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(sw, cap = StrokeCap.Round))
            if (p > 0f) drawArc(
                Brush.sweepGradient(listOf(c.secondary, c.primary, c.secondary)), -90f, 360f * p, false,
                Offset(inset, inset), arcSize, style = Stroke(sw, cap = StrokeCap.Round)
            )
        }
        content()
    }
}

@Composable
fun BarChart(values: List<Float>, labels: List<String>, highlight: Int, modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme
    val max = (values.maxOrNull() ?: 0f).coerceAtLeast(0.0001f)
    Column(modifier) {
        Row(Modifier.fillMaxWidth().height(120.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Bottom) {
            values.forEachIndexed { i, v ->
                Box(
                    Modifier.weight(1f).fillMaxHeight((v / max).coerceIn(0.03f, 1f))
                        .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 3.dp, bottomEnd = 3.dp))
                        .background(if (i == highlight) c.secondary else c.primary.copy(alpha = 0.72f))
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            labels.forEach { Text(it, Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = c.onSurfaceVariant, maxLines = 1) }
        }
    }
}

@Composable
fun StatTile(value: String, label: String, modifier: Modifier = Modifier) {
    SoftCard(modifier) {
        Text(value, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary, maxLines = 1)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) =
    Text(text, modifier.padding(top = 6.dp), style = MaterialTheme.typography.titleMedium)

@Composable
fun PermissionRows(items: List<PermItem>) {
    val ctx = LocalContext.current
    var disclosure by remember { mutableStateOf(false) }
    val notif = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items.forEach { p ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(
                    if (p.ok) Icons.Rounded.CheckCircle else if (p.required) Icons.Rounded.ErrorOutline else Icons.Rounded.Info,
                    null, tint = if (p.ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Column(Modifier.weight(1f)) {
                    Text(stringResource(p.title), style = MaterialTheme.typography.bodyLarge)
                    Text(stringResource(p.desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (!p.ok) FilledTonalButton(onClick = {
                    when (p.id) {
                        "a11y" -> disclosure = true
                        "notif" -> if (Build.VERSION.SDK_INT >= 33) notif.launch(Manifest.permission.POST_NOTIFICATIONS)
                        else -> Perms.open(ctx, p.id)
                    }
                }) { Text(stringResource(R.string.grant)) }
            }
        }
    }
    if (disclosure) AlertDialog(
        onDismissRequest = { disclosure = false },
        title = { Text(stringResource(R.string.a11y_disc_title)) },
        text = { Text(stringResource(R.string.a11y_disc_body)) },
        confirmButton = { TextButton({ disclosure = false; Perms.open(ctx, "a11y") }) { Text(stringResource(R.string.agree_continue)) } },
        dismissButton = { TextButton({ disclosure = false }) { Text(stringResource(R.string.cancel)) } }
    )
}
