package com.pebble.player.ui

import android.graphics.Typeface
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.layout
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
import androidx.compose.ui.unit.dp
import androidx.media3.common.VideoSize
import androidx.media3.common.text.Cue

/** Turns a styled subtitle line (colors, bold, italic, underline...) into Compose text. */
private fun styled(cs: CharSequence): AnnotatedString = buildAnnotatedString {
    append(cs.toString())
    if (cs is Spanned) for (sp in cs.getSpans(0, cs.length, Any::class.java)) {
        val s = cs.getSpanStart(sp); val e = cs.getSpanEnd(sp)
        if (s < 0 || e <= s) continue
        val st: SpanStyle? = when (sp) {
            is ForegroundColorSpan -> SpanStyle(color = Color(sp.foregroundColor))
            is BackgroundColorSpan -> SpanStyle(background = Color(sp.backgroundColor))
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

/**
 * Draws .ass / .ssa subtitles the way the file asks: its own colors, sizes, bold/italic, alignment (\an) and
 * position (\pos, margins), placed over the picture. The app's own subtitle settings are not applied.
 */
@Composable
internal fun AssCues(cues: List<Cue>, vSize: VideoSize, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    BoxWithConstraints(modifier.fillMaxSize()) {
        val boxW = constraints.maxWidth.toFloat(); val boxH = constraints.maxHeight.toFloat()
        // the area the picture covers (letterboxed), so positions line up with the video
        val vw = vSize.width * (if (vSize.pixelWidthHeightRatio > 0f) vSize.pixelWidthHeightRatio else 1f); val vh = vSize.height.toFloat()
        val k = if (vw > 0f && vh > 0f) minOf(boxW / vw, boxH / vh) else 1f
        val w = if (vw > 0f) vw * k else boxW; val h = if (vh > 0f) vh * k else boxH
        Box(Modifier.align(Alignment.Center).size(with(density) { w.toDp() }, with(density) { h.toDp() })) {
            cues.forEach { c ->
                val text = c.text ?: return@forEach
                val px = if (c.position != Cue.DIMEN_UNSET) c.position else 0.5f
                val ln = if (c.line != Cue.DIMEN_UNSET && c.lineType == Cue.LINE_TYPE_FRACTION) c.line else 0.93f
                val ax = when (c.positionAnchor) { Cue.ANCHOR_TYPE_START -> 0f; Cue.ANCHOR_TYPE_END -> 1f; else -> 0.5f }
                val ay = when (c.lineAnchor) { Cue.ANCHOR_TYPE_START -> 0f; Cue.ANCHOR_TYPE_MIDDLE -> 0.5f; else -> 1f }
                val fontPx = when (c.textSizeType) {
                    Cue.TEXT_SIZE_TYPE_FRACTIONAL, Cue.TEXT_SIZE_TYPE_FRACTIONAL_IGNORE_PADDING -> if (c.textSize > 0f) c.textSize * h else 0.05f * h
                    Cue.TEXT_SIZE_TYPE_ABSOLUTE -> if (c.textSize > 0f) c.textSize else 0.05f * h
                    else -> 0.05f * h
                }
                val align = when (c.textAlignment?.name) { "ALIGN_NORMAL" -> TextAlign.Start; "ALIGN_OPPOSITE" -> TextAlign.End; else -> TextAlign.Center }
                val maxW = (w * (if (c.size != Cue.DIMEN_UNSET && c.size > 0f) c.size else 0.92f)).toInt().coerceAtLeast(1)
                Text(
                    styled(text), color = Color.White, textAlign = align,
                    style = TextStyle(
                        fontSize = with(density) { fontPx.toSp() }, lineHeight = with(density) { (fontPx * 1.2f).toSp() },
                        fontWeight = FontWeight.Medium, shadow = Shadow(Color.Black, Offset(0f, 0f), 8f),
                    ),
                    modifier = Modifier.layout { m, cons ->
                        val p = m.measure(Constraints(maxWidth = minOf(maxW, cons.maxWidth)))
                        layout(cons.maxWidth, cons.maxHeight) {
                            p.place((px * cons.maxWidth - ax * p.width).toInt().coerceIn(0, (cons.maxWidth - p.width).coerceAtLeast(0)), (ln * cons.maxHeight - ay * p.height).toInt().coerceIn(0, (cons.maxHeight - p.height).coerceAtLeast(0)))
                        }
                    },
                )
            }
        }
    }
}
