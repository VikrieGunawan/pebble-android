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
import androidx.compose.runtime.remember
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.pebble.player.R
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
fun PebbleTheme(mode: Int, dynamic: Boolean, accent: Color, amoled: Boolean = false, content: @Composable () -> Unit) {
    val dark = when (mode) { 1 -> false; 2 -> true; else -> isSystemInDarkTheme() }
    val ctx = LocalContext.current
    val base = if (dynamic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
    } else seedScheme(accent, dark)
    val scheme = if (dark && amoled) base.copy(
        background = Color.Black, surface = Color.Black, surfaceContainerLowest = Color.Black,
        surfaceContainerLow = Color(0xFF080808), surfaceContainer = Color(0xFF101010),
        surfaceContainerHigh = Color(0xFF181818), surfaceContainerHighest = Color(0xFF212121),
    ) else base
    MaterialExpressiveTheme(
        colorScheme = scheme,
        motionScheme = MotionScheme.expressive(),
        typography = remember { pebbleTypography() },
        content = content,
    )
}

private fun on(c: Color) = if (c.luminance() > 0.5f) Color(0xFF111111) else Color.White

/** Website palette (light/dark) tinted with the chosen accent, used when Material You is off. */
private fun seedScheme(seed: Color, dark: Boolean): ColorScheme {
    val bg = if (dark) Color(0xFF120F1E) else Color(0xFFF4F2FA)
    val surface = if (dark) Color(0xFF1C1830) else Color(0xFFFFFFFF)
    val s2 = if (dark) Color(0xFF292346) else Color(0xFFECE9F6)
    val ink = if (dark) Color(0xFFF2EFFF) else Color(0xFF1C1833)
    val muted = if (dark) Color(0xFFA09BC0) else Color(0xFF6B6785)
    val line = if (dark) Color(0xFF2F2951) else Color(0xFFDEDAEE)
    val p = if (!dark && seed.luminance() > 0.5f) lerp(seed, Color.Black, 0.25f) else seed
    val soft = if (dark) lerp(surface, seed, 0.16f) else lerp(Color.White, seed, 0.16f)
    val t = lerp(p, Color(0xFF7B61FF), 0.5f)
    val tc = if (dark) lerp(surface, t, 0.2f) else lerp(Color.White, t, 0.2f)
    return if (dark) darkColorScheme(
        primary = p, onPrimary = on(p), primaryContainer = soft, onPrimaryContainer = p,
        secondary = muted, onSecondary = bg, secondaryContainer = s2, onSecondaryContainer = ink,
        tertiary = t, onTertiary = on(t), tertiaryContainer = tc, onTertiaryContainer = ink,
        background = bg, onBackground = ink, surface = bg, onSurface = ink,
        surfaceVariant = s2, onSurfaceVariant = muted,
        surfaceContainerLowest = bg, surfaceContainerLow = surface, surfaceContainer = surface,
        surfaceContainerHigh = s2, surfaceContainerHighest = lerp(s2, ink, 0.06f),
        outline = muted, outlineVariant = line,
    ) else lightColorScheme(
        primary = p, onPrimary = on(p), primaryContainer = soft, onPrimaryContainer = p,
        secondary = muted, onSecondary = Color.White, secondaryContainer = s2, onSecondaryContainer = ink,
        tertiary = t, onTertiary = on(t), tertiaryContainer = tc, onTertiaryContainer = ink,
        background = bg, onBackground = ink, surface = bg, onSurface = ink,
        surfaceVariant = s2, onSurfaceVariant = muted,
        surfaceContainerLowest = Color.White, surfaceContainerLow = surface, surfaceContainer = surface,
        surfaceContainerHigh = s2, surfaceContainerHighest = lerp(s2, ink, 0.06f),
        outline = muted, outlineVariant = line,
    )
}

// Same fonts as the website: Nunito for text, Fredoka for headings.
private fun fam(res: Int, ws: List<Int>) = FontFamily(ws.map { w -> Font(res, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w))) })
private val NUNITO by lazy { fam(R.font.nunito, listOf(400, 500, 600, 700, 800)) }
private val FREDOKA by lazy { fam(R.font.fredoka, listOf(500, 600, 700)) }

private fun pebbleTypography(): Typography {
    val b = Typography()
    fun TextStyle.d() = copy(fontFamily = FREDOKA)
    fun TextStyle.n() = copy(fontFamily = NUNITO, fontWeight = if (fontWeight == null || fontWeight == FontWeight.Normal) FontWeight.SemiBold else fontWeight)
    return Typography(
        displayLarge = b.displayLarge.d(), displayMedium = b.displayMedium.d(), displaySmall = b.displaySmall.d(),
        headlineLarge = b.headlineLarge.d(), headlineMedium = b.headlineMedium.d(), headlineSmall = b.headlineSmall.d(),
        titleLarge = b.titleLarge.d(), titleMedium = b.titleMedium.n(), titleSmall = b.titleSmall.n(),
        bodyLarge = b.bodyLarge.n(), bodyMedium = b.bodyMedium.n(), bodySmall = b.bodySmall.n(),
        labelLarge = b.labelLarge.n(), labelMedium = b.labelMedium.n(), labelSmall = b.labelSmall.n(),
    )
}
