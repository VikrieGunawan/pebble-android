package com.pebble.player.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.Shapes
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.Colors
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
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
val ACCENTS: List<Pair<String, Int>> get() = listOf(
    "Pebble" to 0xFF7B61FF, t("Coral") to 0xFFE8502B, t("Rose") to 0xFFE5336D, t("Violet") to 0xFF7B5CFF,
    t("Ocean") to 0xFF1E88E5, t("Emerald") to 0xFF12A77A, t("Amber") to 0xFFD98200, t("Crimson") to 0xFFD32F2F,
    t("Scarlet") to 0xFFFF3B30, t("Tangerine") to 0xFFFF6D00, t("Sunflower") to 0xFFF5B700, t("Mustard") to 0xFFC9A227,
    t("Lime") to 0xFF7CB305, t("Forest") to 0xFF2E7D32, t("Mint") to 0xFF00BFA5, t("Teal") to 0xFF00897B,
    t("Cyan") to 0xFF00ACC1, t("Sky") to 0xFF29B6F6, t("Cobalt") to 0xFF2F5BEA, t("Indigo") to 0xFF3949AB,
    t("Grape") to 0xFF8E24AA, t("Orchid") to 0xFFBA68C8, t("Magenta") to 0xFFD81B60, t("Blush") to 0xFFF06292,
    t("Peach") to 0xFFFF8A65, t("Clay") to 0xFF8D6E63, t("Slate") to 0xFF546E7A, t("Steel") to 0xFF78909C,
    t("Graphite") to 0xFF455A64, t("Plum") to 0xFF6A1B9A,
).map { it.first to it.second.toInt() }

@Composable
fun PebbleTheme(mode: Int, dynamic: Boolean, accent: Color, amoled: Boolean = false, style: Int = 0, content: @Composable () -> Unit) {
    if (style == 1) {
        // Miuix (experimental): the real library theme; wallpaper colors when Material You is on, else the chosen accent
        val key = if (dynamic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) null else accent
        val ctl = remember(mode, key) {
            ThemeController(when (mode) { 1 -> ColorSchemeMode.MonetLight; 2 -> ColorSchemeMode.MonetDark; else -> ColorSchemeMode.MonetSystem }, keyColor = key)
        }
        MiuixTheme(controller = ctl) {
            val mx = MiuixTheme.colorScheme
            val dark = mx.surface.luminance() < 0.5f
            val base = miuixToMaterial(mx, dark)
            val scheme = if (dark && amoled) base.copy(
                background = Color.Black, surface = Color.Black, surfaceContainerLowest = Color.Black,
                surfaceContainerLow = Color(0xFF080808), surfaceContainer = Color(0xFF101010),
                surfaceContainerHigh = Color(0xFF181818), surfaceContainerHighest = Color(0xFF212121),
            ) else base
            MaterialExpressiveTheme(
                colorScheme = scheme, motionScheme = MotionScheme.standard(), shapes = MIUIX_SHAPES,
                typography = remember { miuixTypography() },
            ) { CompositionLocalProvider(LocalMiuix provides true, content = content) }
        }
        return
    }
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

/** Miuix look (experimental): the real Miuix library colors, mapped to the Material scheme so every screen follows them. */
private val MIUIX_SHAPES = Shapes(
    extraSmall = RoundedCornerShape(8.dp), small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(16.dp), extraLarge = RoundedCornerShape(20.dp),
)

/** True inside the Miuix style: shared pieces (cards, switches, sliders, buttons) draw the Miuix versions. */
val LocalMiuix = staticCompositionLocalOf { false }

private fun miuixToMaterial(mx: Colors, dark: Boolean): ColorScheme = if (dark) darkColorScheme(
    primary = mx.primary, onPrimary = mx.onPrimary, primaryContainer = mx.tertiaryContainer, onPrimaryContainer = mx.onTertiaryContainer,
    secondary = mx.onSurfaceVariantSummary, onSecondary = mx.surface, secondaryContainer = mx.secondaryContainer, onSecondaryContainer = mx.onSurface,
    tertiary = mx.primary, onTertiary = mx.onPrimary, tertiaryContainer = mx.tertiaryContainer, onTertiaryContainer = mx.onTertiaryContainer,
    background = mx.surface, onBackground = mx.onSurface, surface = mx.surface, onSurface = mx.onSurface,
    surfaceVariant = mx.secondaryContainer, onSurfaceVariant = mx.onSurfaceVariantSummary,
    surfaceContainerLowest = mx.surface, surfaceContainerLow = mx.surfaceContainer, surfaceContainer = mx.surfaceContainer,
    surfaceContainerHigh = mx.surfaceContainerHigh, surfaceContainerHighest = mx.surfaceContainerHigh,
    outline = mx.outline, outlineVariant = mx.dividerLine, error = mx.error, onError = mx.onError,
) else lightColorScheme(
    primary = mx.primary, onPrimary = mx.onPrimary, primaryContainer = mx.tertiaryContainer, onPrimaryContainer = mx.onTertiaryContainer,
    secondary = mx.onSurfaceVariantSummary, onSecondary = mx.surface, secondaryContainer = mx.secondaryContainer, onSecondaryContainer = mx.onSurface,
    tertiary = mx.primary, onTertiary = mx.onPrimary, tertiaryContainer = mx.tertiaryContainer, onTertiaryContainer = mx.onTertiaryContainer,
    background = mx.surface, onBackground = mx.onSurface, surface = mx.surface, onSurface = mx.onSurface,
    surfaceVariant = mx.secondaryContainer, onSurfaceVariant = mx.onSurfaceVariantSummary,
    surfaceContainerLowest = mx.surface, surfaceContainerLow = mx.surfaceContainer, surfaceContainer = mx.surfaceContainer,
    surfaceContainerHigh = mx.surfaceContainerHigh, surfaceContainerHighest = mx.surfaceContainerHigh,
    outline = mx.outline, outlineVariant = mx.dividerLine, error = mx.error, onError = mx.onError,
)

private fun miuixTypography(): Typography {
    val b = Typography()
    fun TextStyle.m() = copy(fontFamily = FontFamily.SansSerif, fontWeight = if (fontWeight == null || fontWeight == FontWeight.Normal) FontWeight.Medium else fontWeight)
    return Typography(
        displayLarge = b.displayLarge.m(), displayMedium = b.displayMedium.m(), displaySmall = b.displaySmall.m(),
        headlineLarge = b.headlineLarge.m(), headlineMedium = b.headlineMedium.m(), headlineSmall = b.headlineSmall.m(),
        titleLarge = b.titleLarge.m(), titleMedium = b.titleMedium.m(), titleSmall = b.titleSmall.m(),
        bodyLarge = b.bodyLarge.m(), bodyMedium = b.bodyMedium.m(), bodySmall = b.bodySmall.m(),
        labelLarge = b.labelLarge.m(), labelMedium = b.labelMedium.m(), labelSmall = b.labelSmall.m(),
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
