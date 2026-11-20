package com.willian.tvaberta.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.willian.tvaberta.data.AppSettings

val AccentPalette: List<Pair<String, Color>> = listOf(
    "Azul" to Color(0xFF2F6FED),
    "Verde" to Color(0xFF1FA463),
    "Vermelho" to Color(0xFFE5484D),
    "Laranja" to Color(0xFFE8801A),
    "Roxo" to Color(0xFF7C5CFF),
    "Rosa" to Color(0xFFE5559F),
    "Turquesa" to Color(0xFF0E9AA7),
)

@Composable
fun AppTheme(s: AppSettings, content: @Composable () -> Unit) {
    val dark = when (s.theme) {
        1 -> false
        2 -> true
        else -> isSystemInDarkTheme()
    }
    val accent = AccentPalette[s.accent.coerceIn(0, AccentPalette.lastIndex)].second

    val scheme = if (dark) {
        val bg = if (s.pureBlack) Color.Black else Color(0xFF0D1117)
        val card = if (s.pureBlack) Color(0xFF121212) else Color(0xFF171C24)
        darkColorScheme(
            primary = accent,
            onPrimary = Color.White,
            primaryContainer = lerp(accent, bg, 0.72f),
            onPrimaryContainer = Color.White,
            secondary = accent,
            onSecondary = Color.White,
            secondaryContainer = lerp(accent, bg, 0.80f),
            onSecondaryContainer = Color.White,
            background = bg,
            onBackground = Color(0xFFE8ECF2),
            surface = bg,
            onSurface = Color(0xFFE8ECF2),
            surfaceVariant = card,
            onSurfaceVariant = Color(0xFFA7B0BE),
            surfaceContainerLowest = bg,
            surfaceContainerLow = card,
            surfaceContainer = card,
            surfaceContainerHigh = lerp(card, Color.White, 0.06f),
            surfaceContainerHighest = lerp(card, Color.White, 0.10f),
            outline = Color(0xFF3A4350),
            outlineVariant = Color(0xFF262D38),
            error = Color(0xFFFF6B6B),
        )
    } else {
        val bg = Color(0xFFF4F6FA)
        lightColorScheme(
            primary = accent,
            onPrimary = Color.White,
            primaryContainer = lerp(accent, Color.White, 0.82f),
            onPrimaryContainer = lerp(accent, Color.Black, 0.45f),
            secondary = accent,
            onSecondary = Color.White,
            secondaryContainer = lerp(accent, Color.White, 0.88f),
            onSecondaryContainer = lerp(accent, Color.Black, 0.45f),
            background = bg,
            onBackground = Color(0xFF141922),
            surface = bg,
            onSurface = Color(0xFF141922),
            surfaceVariant = Color.White,
            onSurfaceVariant = Color(0xFF5B6575),
            surfaceContainerLowest = Color.White,
            surfaceContainerLow = Color.White,
            surfaceContainer = Color.White,
            surfaceContainerHigh = Color(0xFFF0F2F6),
            surfaceContainerHighest = Color(0xFFE8ECF2),
            outline = Color(0xFFCBD2DC),
            outlineVariant = Color(0xFFE2E7EE),
            error = Color(0xFFD93636),
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = view.context.findActivity()?.window
            if (window != null) {
                val c = WindowCompat.getInsetsController(window, view)
                c.isAppearanceLightStatusBars = !dark
                c.isAppearanceLightNavigationBars = !dark
            }
        }
    }

    MaterialTheme(colorScheme = scheme, content = content)
}
