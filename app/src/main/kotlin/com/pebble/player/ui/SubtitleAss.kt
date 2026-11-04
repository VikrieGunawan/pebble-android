package com.pebble.player.ui

import android.graphics.Typeface
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LongState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.text.Cue
import kotlinx.coroutines.delay

internal fun isAssName(n: String) = n.endsWith(".ass", true) || n.endsWith(".ssa", true)

/** One subtitle line with everything the file says about how it looks and where it goes (all positions are fractions of the picture). */
internal class SubItem(
    val build: (px: Float, density: Density, colors: Boolean) -> AnnotatedString,
    val x: Float, val y: Float, val ax: Float, val ay: Float,
    val size: Float,
    val align: TextAlign, val maxW: Float,
    val fill: Color, val outline: Color, val outlinePx: Float, val box: Color?,
    val start: Long = 0L, val end: Long = Long.MAX_VALUE, val fadeIn: Long = 0L, val fadeOut: Long = 0L,
    val move: FloatArray? = null, val moveT: LongArray? = null,
    val fixed: Boolean = false,      // placed with \pos: never pushed away by other lines
    val bottomUp: Boolean = true,    // when two lines overlap, push up (bottom/middle) or down (top)
)

/** Turns a styled subtitle line (colors, bold, italic, underline...) into Compose text. */
private fun styled(cs: CharSequence, colors: Boolean): AnnotatedString = buildAnnotatedString {
    append(cs.toString())
    if (cs is Spanned) for (sp in cs.getSpans(0, cs.length, Any::class.java)) {
        val s = cs.getSpanStart(sp); val e = cs.getSpanEnd(sp)
        if (s < 0 || e <= s) continue
        val st: SpanStyle? = when (sp) {
            is ForegroundColorSpan -> if (colors) SpanStyle(color = Color(sp.foregroundColor)) else null
            is BackgroundColorSpan -> if (colors) SpanStyle(background = Color(sp.backgroundColor)) else null
            is UnderlineSpan -> SpanStyle(textDecoration = TextDecoration.Underline)
            is StrikethroughSpan -> SpanStyle(textDecoration = TextDecoration.LineThrough)
            is StyleSpan -> when (sp.style) {
                Typeface.BOLD -> SpanStyle(fontWeight = FontWeight.Bold)
                Typeface.ITALIC -> SpanStyle(fontStyle = FontStyle.Italic)
                Typeface.BOLD_ITALIC -> SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic)
                else -> null
            }
            else -> null
        }
        if (st != null) addStyle(st, s, e)
    }
}

/** Subtitle lines that come from the video itself (Media3 already read their style and position). */
internal fun cueItems(cues: List<Cue>): List<SubItem> = cues.mapNotNull { c ->
    val text = c.text ?: return@mapNotNull null
    val ln = if (c.line != Cue.DIMEN_UNSET && c.lineType == Cue.LINE_TYPE_FRACTION) c.line else 0.93f
    SubItem(
        build = { _, _, colors -> styled(text, colors) },
        x = if (c.position != Cue.DIMEN_UNSET) c.position else 0.5f, y = ln,
        ax = when (c.positionAnchor) { Cue.ANCHOR_TYPE_START -> 0f; Cue.ANCHOR_TYPE_END -> 1f; else -> 0.5f },
        ay = when (c.lineAnchor) { Cue.ANCHOR_TYPE_START -> 0f; Cue.ANCHOR_TYPE_MIDDLE -> 0.5f; else -> 1f },
        size = if ((c.textSizeType == Cue.TEXT_SIZE_TYPE_FRACTIONAL || c.textSizeType == Cue.TEXT_SIZE_TYPE_FRACTIONAL_IGNORE_PADDING) && c.textSize > 0f) c.textSize else 0.05f,
        align = when (c.textAlignment?.name) { "ALIGN_NORMAL" -> TextAlign.Start; "ALIGN_OPPOSITE" -> TextAlign.End; else -> TextAlign.Center },
        maxW = if (c.size != Cue.DIMEN_UNSET && c.size > 0f) c.size else 0.92f,
        fill = Color.White, outline = Color.Black, outlinePx = 0.004f, box = null,
        bottomUp = ln > 0.4f,
    )
}

private fun fadeAlpha(i: SubItem, t: Long): Float {
    var a = 1f
    if (i.fadeIn > 0) a = minOf(a, ((t - i.start).toFloat() / i.fadeIn).coerceIn(0f, 1f))
    if (i.fadeOut > 0) a = minOf(a, ((i.end - t).toFloat() / i.fadeOut).coerceIn(0f, 1f))
    return a
}

@Composable
private fun SubText(i: SubItem, h: Float, density: Density, time: LongState) {
    val px = i.size * h
    val sp = with(density) { px.toSp() }
    val style = TextStyle(fontSize = sp, lineHeight = sp * 1.15f, textAlign = i.align)
    val fades = i.fadeIn > 0 || i.fadeOut > 0
    Box(
        Modifier.graphicsLayer { alpha = if (fades) fadeAlpha(i, time.longValue) else 1f }
            .then(if (i.box != null) Modifier.background(i.box) else Modifier),
    ) {
        if (i.outlinePx > 0f) Text(i.build(px, density, false), style = style.copy(color = i.outline, drawStyle = Stroke(width = i.outlinePx * h * 2f, join = StrokeJoin.Round)))
        Text(i.build(px, density, true), style = style.copy(color = i.fill))
    }
}

/**
 * Draws subtitle lines over the picture, where the file wants them: its colors, sizes, outline, alignment, \pos / \move, fades,
 * and lines that would overlap are stacked instead of printed on top of each other.
 */
@Composable
internal fun SubLayer(items: List<SubItem>, vSize: VideoSize, time: LongState, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    BoxWithConstraints(modifier.fillMaxSize()) {
        val boxW = constraints.maxWidth.toFloat(); val boxH = constraints.maxHeight.toFloat()
        // the area the picture covers (letterboxed), so positions line up with the video
        val vw = vSize.width * (if (vSize.pixelWidthHeightRatio > 0f) vSize.pixelWidthHeightRatio else 1f); val vh = vSize.height.toFloat()
        val k = if (vw > 0f && vh > 0f) minOf(boxW / vw, boxH / vh) else 1f
        val w = if (vw > 0f) vw * k else boxW; val h = if (vh > 0f) vh * k else boxH
        Layout(
            content = { items.forEach { key(it) { SubText(it, h, density, time) } } },
            modifier = Modifier.align(Alignment.Center).size(with(density) { w.toDp() }, with(density) { h.toDp() }),
        ) { ms, c ->
            val ww = c.maxWidth; val hh = c.maxHeight
            val ps = ms.mapIndexed { n, m -> m.measure(Constraints(maxWidth = (ww * items[n].maxW).toInt().coerceAtLeast(1))) }
            layout(ww, hh) {
                val flow = ArrayList<IntArray>() // left, top, right, bottom of lines already placed in the normal flow
                items.forEachIndexed { n, it ->
                    val p = ps[n]
                    var fx = it.x; var fy = it.y
                    if (it.move != null && it.moveT != null) {
                        val t = time.longValue - it.start
                        val t1 = it.moveT[0]; val t2 = it.moveT[1]
                        val f = if (t2 <= t1) 1f else ((t - t1).toFloat() / (t2 - t1)).coerceIn(0f, 1f)
                        fx = it.move[0] + (it.move[2] - it.move[0]) * f; fy = it.move[1] + (it.move[3] - it.move[1]) * f
                    }
                    var x = (fx * ww - it.ax * p.width).toInt()
                    var y = (fy * hh - it.ay * p.height).toInt()
                    if (!it.fixed && it.move == null) {
                        var guard = 0
                        while (guard++ < 12) {
                            val hit = flow.firstOrNull { r -> x < r[2] && x + p.width > r[0] && y < r[3] && y + p.height > r[1] } ?: break
                            y = if (it.bottomUp) hit[1] - p.height else hit[3]
                        }
                    }
                    x = x.coerceIn(0, (ww - p.width).coerceAtLeast(0)); y = y.coerceIn(0, (hh - p.height).coerceAtLeast(0))
                    if (!it.fixed) flow += intArrayOf(x, y, x + p.width, y + p.height)
                    p.place(x, y)
                }
            }
        }
    }
}

/** A loaded .ass / .ssa file played in sync with the video. */
@Composable
internal fun AssFile(script: AssScript, player: Player, vSize: VideoSize, modifier: Modifier = Modifier) {
    val time = remember { mutableLongStateOf(0L) }
    var active by remember { mutableStateOf<List<SubItem>>(emptyList()) }
    LaunchedEffect(script, player) {
        while (true) {
            val t = player.currentPosition
            time.longValue = t
            val now = script.items.filter { t >= it.start && t < it.end }
            if (now != active) active = now // same line objects = same list: nothing to redraw
            if (player.isPlaying) withFrameNanos { } else delay(80)
        }
    }
    SubLayer(active, vSize, time, modifier)
}
