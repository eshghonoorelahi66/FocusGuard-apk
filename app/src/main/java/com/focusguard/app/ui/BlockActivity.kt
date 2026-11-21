package com.focusguard.app.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focusguard.app.Apps
import com.focusguard.app.FocusApp
import com.focusguard.app.Fmt
import com.focusguard.app.R
import com.focusguard.app.engine.BlockEngine
import com.focusguard.app.engine.FocusService
import kotlinx.coroutines.delay

/** The screen shown instead of a blocked app. Soft, short and with a deliberate delay before any way out. */
class BlockActivity : AppCompatActivity() {
    private val pkg = mutableStateOf("")
    private val reason = mutableStateOf("SESSION")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        read(intent)
        val prefs = (application as FocusApp).container.prefs
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = goHome()
        })
        setContent {
            val mode by prefs.mode.collectAsStateWithLifecycle()
            val pal by prefs.palette.collectAsStateWithLifecycle()
            val palette = Palette.of(pal)
            AppTheme(mode, palette) {
                BlockScreen(pkg.value, reason.value, palette.emoji, onHome = { goHome() })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        read(intent)
    }

    private fun read(i: Intent) {
        pkg.value = i.getStringExtra("pkg") ?: ""
        reason.value = i.getStringExtra("reason") ?: "SESSION"
    }

    private fun goHome() {
        startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }
}

@Composable
private fun BlockScreen(pkg: String, reason: String, emoji: String, onHome: () -> Unit) {
    val ctx = LocalContext.current
    val loc = appLocale()
    val c = MaterialTheme.colorScheme
    val snap by BlockEngine.state.collectAsStateWithLifecycle()
    val now by rememberNow()
    val quote = remember { ctx.resources.getStringArray(R.array.quotes).random() }
    val label = remember(pkg) { Apps.label(ctx, pkg) }
    var wait by remember { mutableIntStateOf(-1) }
    LaunchedEffect(wait) { if (wait > 0) { delay(1000); wait-- } }

    val title = when (reason) { "LIMIT" -> R.string.b_limit_title; "PROTECTED" -> R.string.b_strict_title; else -> R.string.b_title }
    val msg = when (reason) { "LIMIT" -> R.string.b_limit_msg; "PROTECTED" -> R.string.b_strict_msg; else -> R.string.b_msg }

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(c.primary, c.secondary)))) {
        Column(
            Modifier.fillMaxSize().systemBarsPadding().padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
        ) {
            Text(emoji, fontSize = 72.sp)
            Spacer(Modifier.height(12.dp))
            Text(stringResource(title), style = MaterialTheme.typography.headlineMedium, color = c.onPrimary, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(stringResource(msg, label), color = c.onPrimary.copy(alpha = 0.92f), textAlign = TextAlign.Center)
            if (snap.active && reason != "LIMIT") {
                Spacer(Modifier.height(18.dp))
                Text(Fmt.clock(snap.sessionEnd - now, loc), fontSize = 44.sp, color = c.onPrimary)
            }
            Spacer(Modifier.height(18.dp))
            Text(quote, color = c.onPrimary.copy(alpha = 0.85f), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(28.dp))
            Button(
                onClick = onHome,
                colors = ButtonDefaults.buttonColors(containerColor = c.onPrimary, contentColor = c.primary),
                modifier = Modifier.fillMaxWidth().height(54.dp)
            ) { Text(stringResource(R.string.b_home)) }

            if (!snap.strict && snap.active && reason == "SESSION") {
                Spacer(Modifier.height(10.dp))
                when {
                    wait == -1 -> TextButton({ wait = 15 }) { Text(stringResource(R.string.b_end), color = c.onPrimary) }
                    wait > 0 -> Text(stringResource(R.string.b_wait, wait), color = c.onPrimary.copy(alpha = 0.85f))
                    else -> OutlinedButton({ FocusService.stop(ctx); onHome() }) { Text(stringResource(R.string.b_end_now), color = c.onPrimary) }
                }
            }
        }
    }
}
