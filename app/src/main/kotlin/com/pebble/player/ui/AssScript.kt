package com.pebble.player.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration

/** A parsed .ass / .ssa file: every dialogue line, ready to draw with the file's own styles. */
internal class AssScript(val items: List<SubItem>)

private class AssStyle(
    var fs: Float = 20f, var primary: Color = Color.White, var outline: Color = Color.Black, var back: Color = Color(0x80000000),
    var bold: Boolean = false, var italic: Boolean = false, var underline: Boolean = false, var strike: Boolean = false,
    var borderStyle: Int = 1, var outlineW: Float = 2f, var align: Int = 2, var mL: Float = 10f, var mR: Float = 10f, var mV: Float = 10f,
)

private class Run(val text: String, val bold: Boolean, val italic: Boolean, val underline: Boolean, val strike: Boolean, val color: Color?, val scale: Float)

/** "&HAABBGGRR" (style) or "&HBBGGRR&" (tag) -> Color. In ASS alpha 00 is opaque. */
private fun assColor(raw: String, keepAlpha: Boolean = true): Color? {
    val t = raw.trim().removePrefix("&H").removePrefix("&h").removeSuffix("&")
    val v = t.toLongOrNull(16) ?: return null
    val a = if (t.length > 6) 255 - ((v shr 24) and 0xFF).toInt() else 255
    val b = ((v shr 16) and 0xFF).toInt(); val g = ((v shr 8) and 0xFF).toInt(); val r = (v and 0xFF).toInt()
    return Color(r, g, b, if (keepAlpha) a else 255)
}

private fun assTime(s: String): Long {
    val p = s.trim().split(':', '.')
    if (p.size < 4) return 0L
    return ((p[0].toLong() * 3600 + p[1].toLong() * 60 + p[2].toLong()) * 1000) + p[3].padEnd(3, '0').take(3).toLong()
}

private fun num(s: String) = s.trim().toFloatOrNull()

/** Legacy SSA alignment (1-3 bottom, 5-7 top, 9-11 middle) to the numpad layout (1-9). */
private fun legacyAlign(a: Int) = when (a) { in 1..3 -> a; in 5..7 -> a - 5 + 7; in 9..11 -> a - 9 + 4; else -> 2 }

internal fun parseAss(text: String): AssScript {
    var resX = 384f; var resY = 288f
    var legacy = false
    val styles = HashMap<String, AssStyle>()
    var section = ""
    var styleFmt = emptyList<String>(); var evFmt = emptyList<String>()
    val items = ArrayList<Pair<Int, SubItem>>()

    fun style(name: String) = styles[name] ?: styles["Default"] ?: AssStyle()

    for (rawLine in text.lineSequence()) {
        val line = rawLine.trim().removePrefix("﻿")
        if (line.isEmpty() || line.startsWith(";") || line.startsWith("!:")) continue
        if (line.startsWith("[")) { section = line.lowercase(); legacy = legacy || section.startsWith("[v4 styles]"); continue }
        val key = line.substringBefore(':', "").trim().lowercase(); val value = line.substringAfter(':', "").trim()
        when {
            section.startsWith("[script info]") -> when (key) { "playresx" -> num(value)?.let { resX = it }; "playresy" -> num(value)?.let { resY = it } }
            section.contains("styles") -> if (key == "format") styleFmt = value.split(',').map { it.trim().lowercase() } else if (key == "style") {
                val f = value.split(',', limit = styleFmt.size.coerceAtLeast(1)).map { it.trim() }
                fun g(n: String) = styleFmt.indexOf(n).let { if (it in f.indices) f[it] else "" }
                val st = AssStyle()
                num(g("fontsize"))?.let { st.fs = it }
                assColor(g("primarycolour"))?.let { st.primary = it }
                assColor(g("outlinecolour"))?.let { st.outline = it }
                assColor(g("backcolour"))?.let { st.back = it }
                st.bold = g("bold").let { it == "-1" || it == "1" }; st.italic = g("italic").let { it == "-1" || it == "1" }
                st.underline = g("underline").let { it == "-1" || it == "1" }; st.strike = g("strikeout").let { it == "-1" || it == "1" }
                g("borderstyle").toIntOrNull()?.let { st.borderStyle = it }
                num(g("outline"))?.let { st.outlineW = it }
                g("alignment").toIntOrNull()?.let { st.align = if (legacy) legacyAlign(it) else it }
                num(g("marginl"))?.let { st.mL = it }; num(g("marginr"))?.let { st.mR = it }; num(g("marginv"))?.let { st.mV = it }
                styles[g("name")] = st
            }
            section.startsWith("[events]") -> if (key == "format") evFmt = value.split(',').map { it.trim().lowercase() } else if (key == "dialogue") {
                val f = value.split(',', limit = evFmt.size.coerceAtLeast(1))
                fun g(n: String) = evFmt.indexOf(n).let { if (it in f.indices) f[it].trim() else "" }
                val rawText = evFmt.indexOf("text").let { if (it in f.indices) f[it] else "" }
                buildItem(rawText, assTime(g("start")), assTime(g("end")), style(g("style")), g("marginl").toFloatOrNull() ?: 0f, g("marginr").toFloatOrNull() ?: 0f, g("marginv").toFloatOrNull() ?: 0f, resX, resY)
                    ?.let { items += (g("layer").toIntOrNull() ?: 0) to it }
            }
        }
    }
    return AssScript(items.sortedWith(compareBy({ it.first }, { it.second.start })).map { it.second })
}

private fun buildItem(raw: String, start: Long, end: Long, st: AssStyle, evL: Float, evR: Float, evV: Float, resX: Float, resY: Float): SubItem? {
    var align = st.align
    var px: Float? = null; var py: Float? = null
    var move: FloatArray? = null; var moveT: LongArray? = null
    var fadeIn = 0L; var fadeOut = 0L
    var outlineColor = st.outline; var outlineW = st.outlineW; var scaleY = 1f
    var bold = st.bold; var italic = st.italic; var underline = st.underline; var strike = st.strike
    var color: Color? = null; var scale = 1f
    var drawing = false
    val runs = ArrayList<Run>()
    val sb = StringBuilder()
    fun flush() { if (sb.isNotEmpty()) { runs += Run(sb.toString(), bold, italic, underline, strike, color, scale); sb.setLength(0) } }

    var i = 0
    while (i < raw.length) {
        val c = raw[i]
        if (c == '{') {
            val close = raw.indexOf('}', i)
            if (close < 0) break
            flush()
            for (tag in raw.substring(i + 1, close).split('\\').drop(1)) {
                val t = tag.trim()
                fun args(): List<String> = t.substringAfter('(', "").substringBefore(')').split(',').map { it.trim() }
                when {
                    t.startsWith("alpha") -> Unit
                    t.startsWith("an") -> t.drop(2).toIntOrNull()?.let { if (it in 1..9) align = it }
                    t.startsWith("pos(") -> args().let { a -> if (a.size >= 2) { px = num(a[0]); py = num(a[1]) } }
                    t.startsWith("move(") -> args().let { a ->
                        if (a.size >= 4) { move = floatArrayOf(num(a[0]) ?: 0f, num(a[1]) ?: 0f, num(a[2]) ?: 0f, num(a[3]) ?: 0f); moveT = if (a.size >= 6) longArrayOf(a[4].toLongOrNull() ?: 0L, a[5].toLongOrNull() ?: 0L) else longArrayOf(0L, end - start) }
                    }
                    t.startsWith("fade(") -> args().let { a -> if (a.size >= 7) { fadeIn = ((a[4].toLongOrNull() ?: 0L) - (a[3].toLongOrNull() ?: 0L)).coerceAtLeast(0L); fadeOut = ((a[6].toLongOrNull() ?: 0L) - (a[5].toLongOrNull() ?: 0L)).coerceAtLeast(0L) } }
                    t.startsWith("fad(") -> args().let { a -> if (a.size >= 2) { fadeIn = a[0].toLongOrNull() ?: 0L; fadeOut = a[1].toLongOrNull() ?: 0L } }
                    t.startsWith("a") && t.drop(1).toIntOrNull() != null -> align = legacyAlign(t.drop(1).toInt())
                    t.startsWith("fscx") -> Unit
                    t.startsWith("fscy") -> num(t.drop(4))?.let { scaleY = it / 100f }
                    t.startsWith("fsp") || t.startsWith("fn") || t.startsWith("fe") -> Unit
                    t.startsWith("fs") -> num(t.drop(2))?.let { if (st.fs > 0f) scale = it / st.fs }
                    t.startsWith("1c") -> color = assColor(t.drop(2), false) ?: color
                    t.startsWith("3c") -> assColor(t.drop(2), false)?.let { outlineColor = it }
                    t.startsWith("c") && !t.startsWith("clip") -> color = assColor(t.drop(1), false) ?: color
                    t.startsWith("bord") -> num(t.drop(4))?.let { outlineW = it }
                    t.startsWith("blur") || t.startsWith("be") -> Unit
                    t.startsWith("b") -> t.drop(1).toIntOrNull()?.let { bold = it > 0 }
                    t.startsWith("i") -> t.drop(1).toIntOrNull()?.let { italic = it > 0 }
                    t.startsWith("u") -> t.drop(1).toIntOrNull()?.let { underline = it > 0 }
                    t.startsWith("s") && t.drop(1).toIntOrNull() != null -> strike = t.drop(1).toInt() > 0
                    t.startsWith("p") && t.drop(1).toIntOrNull() != null -> drawing = t.drop(1).toInt() > 0
                    t == "r" || t.startsWith("r") -> { bold = st.bold; italic = st.italic; underline = st.underline; strike = st.strike; color = null; scale = 1f }
                }
            }
            i = close + 1
        } else if (c == '\\' && i + 1 < raw.length && (raw[i + 1] == 'N' || raw[i + 1] == 'n')) { sb.append('\n'); i += 2 }
        else if (c == '\\' && i + 1 < raw.length && raw[i + 1] == 'h') { sb.append(' '); i += 2 }
        else { sb.append(c); i++ }
    }
    flush()
    if (drawing || runs.all { it.text.isBlank() }) return null

    val mL = if (evL > 0f) evL else st.mL; val mR = if (evR > 0f) evR else st.mR; val mV = if (evV > 0f) evV else st.mV
    val col = (align - 1) % 3; val row = (align - 1) / 3   // col 0/1/2 = left/center/right, row 0/1/2 = bottom/middle/top
    val fixed = px != null && py != null
    val x = if (fixed) px!! / resX else when (col) { 0 -> mL / resX; 1 -> (mL + (resX - mR)) / 2f / resX; else -> 1f - mR / resX }
    val y = if (fixed) py!! / resY else when (row) { 0 -> 1f - mV / resY; 1 -> 0.5f; else -> mV / resY }
    val box = if (st.borderStyle == 3) st.back else null
    val fill = st.primary
    val frozen = runs.toList()

    return SubItem(
        build = { px2, density, colors ->
            buildAnnotatedString {
                for (r in frozen) {
                    val start0 = length
                    append(r.text)
                    addStyle(
                        SpanStyle(
                            fontWeight = if (r.bold) FontWeight.Bold else FontWeight.Normal, fontStyle = if (r.italic) FontStyle.Italic else FontStyle.Normal,
                            textDecoration = when { r.underline && r.strike -> TextDecoration.combine(listOf(TextDecoration.Underline, TextDecoration.LineThrough)); r.underline -> TextDecoration.Underline; r.strike -> TextDecoration.LineThrough; else -> null },
                            color = if (colors && r.color != null) r.color else Color.Unspecified,
                            fontSize = if (r.scale != 1f) with(density) { (px2 * r.scale).toSp() } else androidx.compose.ui.unit.TextUnit.Unspecified,
                        ),
                        start0, length,
                    )
                }
            }
        },
        x = x, y = y, ax = col / 2f, ay = when (row) { 0 -> 1f; 1 -> 0.5f; else -> 0f },
        size = st.fs * scaleY / resY,
        align = when (col) { 0 -> TextAlign.Start; 1 -> TextAlign.Center; else -> TextAlign.End },
        maxW = if (fixed) 0.95f else ((resX - mL - mR) / resX).coerceIn(0.2f, 1f),
        fill = fill, outline = outlineColor, outlinePx = if (box != null) 0f else outlineW / resY, box = box,
        start = start, end = end, fadeIn = fadeIn, fadeOut = fadeOut,
        move = move?.let { floatArrayOf(it[0] / resX, it[1] / resY, it[2] / resX, it[3] / resY) }, moveT = moveT,
        fixed = fixed, bottomUp = row != 2,
    )
}
