package com.pebble.player.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kashif_e.backdrop.Backdrop
import com.kashif_e.backdrop.backdrops.layerBackdrop
import com.kashif_e.backdrop.backdrops.rememberLayerBackdrop
import com.kashif_e.backdrop.drawBackdrop
import com.kashif_e.backdrop.effects.blur
import com.kashif_e.backdrop.effects.lens
import com.kashif_e.backdrop.effects.vibrancy

/** The layer all Liquid Glass surfaces look through (null inside separate windows such as sheets and dialogs). */
val LocalBackdrop = compositionLocalOf<Backdrop?> { null }

/**
 * Liquid Glass surface (Kyant0's Backdrop library): blurs, saturates and refracts what lies behind it.
 * Without a backdrop (dialogs, sheets, over video) it falls back to a plain frosted panel.
 * [lens] adds the refraction at the edges; keep it off for large cards so lists stay smooth.
 */
@Composable
fun Modifier.glass(radius: Dp = 24.dp, lens: Boolean = true, tint: Color = Color.Unspecified): Modifier {
    val bd = LocalBackdrop.current
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(radius)
    val fill = (if (tint == Color.Unspecified) cs.surfaceContainerHigh else tint).copy(alpha = 0.42f)
    return if (bd == null) this.clip(shape).background(cs.surfaceContainer) else this.drawBackdrop(
        backdrop = bd,
        shape = { shape },
        effects = {
            vibrancy()
            blur(10.dp.toPx())
            if (lens && radius > 0.dp) lens(minOf(12.dp.toPx(), radius.toPx()), minOf(24.dp.toPx(), radius.toPx() * 2f))
        },
        onDrawSurface = { drawRect(fill) },
    )
}

/** Soft colored backdrop that the glass shows through; everything glass is drawn on top of it. */
@Composable
fun GlassScene(content: @Composable () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val bd = rememberLayerBackdrop()
    androidx.compose.runtime.CompositionLocalProvider(LocalBackdrop provides bd) {
        Box(Modifier.fillMaxSize()) {
            Canvas(Modifier.fillMaxSize().background(cs.background).layerBackdrop(bd)) {
                val w = size.width
                val h = size.height
                fun blob(c: Color, x: Float, y: Float, r: Float) =
                    drawCircle(Brush.radialGradient(listOf(c, Color.Transparent), Offset(x, y), r), r, Offset(x, y))
                blob(cs.primary.copy(alpha = 0.40f), w * 0.05f, h * 0.08f, maxOf(w, h) * 0.55f)
                blob(cs.tertiary.copy(alpha = 0.34f), w * 0.98f, h * 0.45f, maxOf(w, h) * 0.5f)
                blob(cs.secondary.copy(alpha = 0.30f), w * 0.2f, h * 0.95f, maxOf(w, h) * 0.5f)
            }
            content()
        }
    }
}
