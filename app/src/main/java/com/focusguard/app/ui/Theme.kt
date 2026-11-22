package com.focusguard.app.ui

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.focusguard.app.R

private fun lt(p: Long, pc: Long, s: Long, bg: Long, sv: Long): ColorScheme = lightColorScheme(
    primary = Color(p), onPrimary = Color.White, primaryContainer = Color(pc), onPrimaryContainer = Color(0xFF2C2540),
    secondary = Color(s), onSecondary = Color.White, secondaryContainer = Color(sv), onSecondaryContainer = Color(0xFF2C2540),
    tertiary = Color(s), background = Color(bg), onBackground = Color(0xFF2A2433),
    surface = Color(bg), onSurface = Color(0xFF2A2433), surfaceVariant = Color(sv), onSurfaceVariant = Color(0xFF665E72),
    outline = Color(0xFFB9B0C6), outlineVariant = Color(0xFFE3DCEE), error = Color(0xFFD64B63)
)

private fun dk(p: Long, pc: Long, s: Long, bg: Long, sv: Long): ColorScheme = darkColorScheme(
    primary = Color(p), onPrimary = Color(0xFF1E1830), primaryContainer = Color(pc), onPrimaryContainer = Color(0xFFF1EAFF),
    secondary = Color(s), onSecondary = Color(0xFF1E1830), secondaryContainer = Color(sv), onSecondaryContainer = Color(0xFFF1EAFF),
    tertiary = Color(s), background = Color(bg), onBackground = Color(0xFFEDE7F6),
    surface = Color(bg), onSurface = Color(0xFFEDE7F6), surfaceVariant = Color(sv), onSurfaceVariant = Color(0xFFC3BACF),
    outline = Color(0xFF7D748A), outlineVariant = Color(0xFF3A3346), error = Color(0xFFFF8FA3)
)

/** Soft pastel palettes. girly = true ones are the pink / peach / lilac family. */
enum class Palette(val emoji: String, val girly: Boolean, val label: Int, val light: ColorScheme, val dark: ColorScheme) {
    LAVENDER("🪻", false, R.string.pal_lavender,
        lt(0xFF7C6BC4, 0xFFE7E0FF, 0xFFB48CD9, 0xFFF8F6FF, 0xFFEFEAFB),
        dk(0xFFB7A8F5, 0xFF3A3170, 0xFFC9A6E8, 0xFF16131F, 0xFF241F33)),
    OCEAN("🌊", false, R.string.pal_ocean,
        lt(0xFF4F8EDB, 0xFFDCEBFF, 0xFF6CB6E8, 0xFFF5F9FF, 0xFFE8F0FB),
        dk(0xFF8DB8F0, 0xFF1F3F6B, 0xFF8CCBF2, 0xFF0F151F, 0xFF1B2433)),
    MINT("🌿", false, R.string.pal_mint,
        lt(0xFF3FA796, 0xFFD9F5EE, 0xFF7CCDB7, 0xFFF5FCFA, 0xFFE6F6F2),
        dk(0xFF7FD6C3, 0xFF1B4A41, 0xFF9BE0CF, 0xFF0F1815, 0xFF1A2824)),
    ROSE("🌸", true, R.string.pal_rose,
        lt(0xFFD9658B, 0xFFFFE1EA, 0xFFF2A1BC, 0xFFFFF7F9, 0xFFFDEAF0),
        dk(0xFFF2A1BC, 0xFF5E2A3D, 0xFFF7B8CC, 0xFF1D1317, 0xFF2D1D24)),
    PEACH("🍑", true, R.string.pal_peach,
        lt(0xFFEE8A6B, 0xFFFFE6DB, 0xFFF7B68F, 0xFFFFF8F4, 0xFFFDEBE2),
        dk(0xFFF7B093, 0xFF5E3324, 0xFFF9C6A8, 0xFF1D1512, 0xFF2D211C)),
    LILAC("🦋", true, R.string.pal_lilac,
        lt(0xFFB06AD6, 0xFFF3E0FF, 0xFFE58BC7, 0xFFFCF7FF, 0xFFF5EAFB),
        dk(0xFFD4A0EE, 0xFF4A2A63, 0xFFF0AAD8, 0xFF1A1320, 0xFF2A1F33));

    companion object {
        fun of(name: String): Palette = entries.firstOrNull { it.name == name } ?: LAVENDER
    }
}

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp), small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp), large = RoundedCornerShape(28.dp), extraLarge = RoundedCornerShape(36.dp)
)

private val AppTypography = Typography().let {
    it.copy(
        headlineMedium = it.headlineMedium.copy(fontWeight = FontWeight.Bold),
        headlineSmall = it.headlineSmall.copy(fontWeight = FontWeight.Bold),
        titleLarge = it.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = it.titleMedium.copy(fontWeight = FontWeight.SemiBold)
    )
}

@Composable
fun AppTheme(mode: String, palette: Palette, content: @Composable () -> Unit) {
    val dark = when (mode) { "dark" -> true; "light" -> false; else -> isSystemInDarkTheme() }
    val view = LocalView.current
    SideEffect {
        val w = (view.context as? Activity)?.window ?: return@SideEffect
        WindowCompat.getInsetsController(w, view).apply {
            isAppearanceLightStatusBars = false // headers are saturated gradients -> light icons look right in both modes
            isAppearanceLightNavigationBars = !dark
        }
    }
    MaterialTheme(
        colorScheme = if (dark) palette.dark else palette.light,
        shapes = AppShapes, typography = AppTypography, content = content
    )
}
