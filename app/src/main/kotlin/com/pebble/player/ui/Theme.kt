package com.pebble.player.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext

/** 30 accent colors (name to ARGB). */
val ACCENTS: List<Pair<String, Int>> = listOf(
    "Pebble" to 0xFF7B61FF, "Coral" to 0xFFE8502B, "Rose" to 0xFFE5336D, "Violet" to 0xFF7B5CFF,
    "Ocean" to 0xFF1E88E5, "Emerald" to 0xFF12A77A, "Amber" to 0xFFD98200, "Crimson" to 0xFFD32F2F,
    "Scarlet" to 0xFFFF3B30, "Tangerine" to 0xFFFF6D00, "Sunflower" to 0xFFF5B700, "Mustard" to 0xFFC9A227,
    "Lime" to 0xFF7CB305, "Forest" to 0xFF2E7D32, "Mint" to 0xFF00BFA5, "Teal" to 0xFF00897B,
    "Cyan" to 0xFF00ACC1, "Sky" to 0xFF29B6F6, "Cobalt" to 0xFF2F5BEA, "Indigo" to 0xFF3949AB,
    "Grape" to 0xFF8E24AA, "Orchid" to 0xFFBA68C8, "Magenta" to 0xFFD81B60, "Blush" to 0xFFF06292,
    "Peach" to 0xFFFF8A65, "Clay" to 0xFF8D6E63, "Slate" to 0xFF546E7A, "Steel" to 0xFF78909C,
    "Graphite" to 0xFF455A64, "Plum" to 0xFF6A1B9A,
).map { it.first to it.second.toInt() }

@Composable
fun PebbleTheme(mode: Int, dynamic: Boolean, accent: Color, content: @Composable () -> Unit) {
    val dark = when (mode) { 1 -> false; 2 -> true; else -> isSystemInDarkTheme() }
    val ctx = LocalContext.current
    val scheme = if (dynamic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
    } else seedScheme(accent, dark)
    MaterialExpressiveTheme(
        colorScheme = scheme,
        motionScheme = MotionScheme.expressive(),
        typography = Typography(),
        content = content,
    )
}

private fun on(c: Color) = if (c.luminance() > 0.5f) Color(0xFF111111) else Color.White

/** Builds a Material-like tonal scheme from a single seed color (used when Material You is off). */
private fun seedScheme(seed: Color, dark: Boolean): ColorScheme {
    val neutral = if (dark) Color(0xFF16131C) else Color(0xFFFBF8FF)
    val ink = if (dark) Color(0xFFE8E2EE) else Color(0xFF1C1A22)
    val p = if (dark) lerp(seed, Color.White, 0.35f) else seed
    val pc = if (dark) lerp(seed, Color.Black, 0.55f) else lerp(seed, Color.White, 0.80f)
    val opc = if (dark) lerp(seed, Color.White, 0.85f) else lerp(seed, Color.Black, 0.65f)
    val s = lerp(p, if (dark) Color(0xFFB0AAC0) else Color(0xFF6B6785), 0.6f)
    val sc = if (dark) lerp(s, Color.Black, 0.6f) else lerp(s, Color.White, 0.8f)
    val t = lerp(p, Color(0xFFE5336D), 0.5f)
    val tc = if (dark) lerp(t, Color.Black, 0.6f) else lerp(t, Color.White, 0.8f)
    fun tint(a: Float) = lerp(neutral, seed, a)
    val variant = if (dark) tint(0.16f) else tint(0.10f)
    return if (dark) darkColorScheme(
        primary = p, onPrimary = on(p), primaryContainer = pc, onPrimaryContainer = opc,
        secondary = s, onSecondary = on(s), secondaryContainer = sc, onSecondaryContainer = ink,
        tertiary = t, onTertiary = on(t), tertiaryContainer = tc, onTertiaryContainer = ink,
        background = tint(0.05f), onBackground = ink, surface = tint(0.05f), onSurface = ink,
        surfaceVariant = variant, onSurfaceVariant = Color(0xFFC9C3D3),
        surfaceContainerLowest = tint(0.03f), surfaceContainerLow = tint(0.07f),
        surfaceContainer = tint(0.09f), surfaceContainerHigh = tint(0.12f), surfaceContainerHighest = tint(0.15f),
        outline = Color(0xFF948E9E), outlineVariant = tint(0.22f),
    ) else lightColorScheme(
        primary = p, onPrimary = on(p), primaryContainer = pc, onPrimaryContainer = opc,
        secondary = s, onSecondary = on(s), secondaryContainer = sc, onSecondaryContainer = ink,
        tertiary = t, onTertiary = on(t), tertiaryContainer = tc, onTertiaryContainer = ink,
        background = tint(0.02f), onBackground = ink, surface = tint(0.02f), onSurface = ink,
        surfaceVariant = variant, onSurfaceVariant = Color(0xFF48454F),
        surfaceContainerLowest = Color.White, surfaceContainerLow = tint(0.04f),
        surfaceContainer = tint(0.07f), surfaceContainerHigh = tint(0.10f), surfaceContainerHighest = tint(0.13f),
        outline = Color(0xFF79747F), outlineVariant = tint(0.20f),
    )
}
