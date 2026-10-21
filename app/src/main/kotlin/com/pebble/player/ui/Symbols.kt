package com.pebble.player.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pebble.player.R

/** Same icon font as the web version: Material Symbols Rounded (FILL 1, wght 400, opsz 24). */
private fun family(fill: Float) = FontFamily(
    Font(R.font.material_symbols, variationSettings = FontVariation.Settings(
        FontVariation.Setting("FILL", fill), FontVariation.Setting("GRAD", 0f),
        FontVariation.Setting("opsz", 24f), FontVariation.weight(400))),
)
private val FILLED = family(1f)
private val OUTLINE = family(0f)

private val CP = mapOf(
    "arrow_back" to 0xe5c4,
    "arrow_forward" to 0xe5c8,
    "aspect_ratio" to 0xe85b,
    "brightness_auto" to 0xe1ab,
    "blur_on" to 0xe3a5,
    "check" to 0xe668,
    "dark_mode" to 0xe51c,
    "favorite" to 0xe87e,
    "fullscreen" to 0xe5d0,
    "grid_view" to 0xe9b0,
    "headphones" to 0xf01f,
    "history" to 0xe8b3,
    "home" to 0xe9b2,
    "light_mode" to 0xe518,
    "pause" to 0xe034,
    "photo_camera" to 0xe412,
    "play_arrow" to 0xe037,
    "play_circle" to 0xe1c4,
    "sync" to 0xe627,
    "screen_rotation" to 0xf2d5,
    "settings" to 0xe8b8,
    "skip_next" to 0xe044,
    "skip_previous" to 0xe045,
    "speed" to 0xe9e4,
    "closed_caption" to 0xe996,
    "video_library" to 0xe04a,
    "view_list" to 0xe8ef,
    "close" to 0xe5cd,
)

@Composable
fun Icon(name: String, contentDescription: String?, modifier: Modifier = Modifier, tint: Color = LocalContentColor.current, fill: Boolean = true) {
    val glyph = remember(name) { CP[name]?.let { String(Character.toChars(it)) } ?: "" }
    BoxWithConstraints(modifier.semantics { if (contentDescription != null) this.contentDescription = contentDescription }) {
        val side = if (constraints.minWidth == constraints.maxWidth && constraints.maxWidth > 0) maxWidth else 24.dp
        val sp = with(LocalDensity.current) { side.toSp() }
        Text(
            glyph, color = tint, maxLines = 1, softWrap = false, textAlign = TextAlign.Center,
            style = TextStyle(
                fontFamily = if (fill) FILLED else OUTLINE, fontSize = sp, lineHeight = sp,
                lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
            ),
            modifier = Modifier.size(side).wrapContentSize(Alignment.Center, unbounded = true),
        )
    }
}

/** Material 3 Expressive switch with a check (on) / cross (off) inside the thumb. */
@Composable
fun PebbleSwitch(checked: Boolean, onChange: ((Boolean) -> Unit)?, modifier: Modifier = Modifier) {
    Switch(checked, onChange, modifier, thumbContent = {
        Icon(if (checked) "check" else "close", null, Modifier.size(SwitchDefaults.IconSize))
    })
}
