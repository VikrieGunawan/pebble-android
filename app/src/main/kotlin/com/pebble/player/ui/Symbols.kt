package com.pebble.player.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.material3.MaterialTheme
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
    "battery_full" to 0xe1a5,
    "bluetooth" to 0xe1a7,
    "bolt" to 0xea0b,
    "brightness_high" to 0xe1ac,
    "brightness_low" to 0xe1ad,
    "brightness_medium" to 0xe1ae,
    "devices" to 0xe326,
    "memory" to 0xe322,
    "phone_android" to 0xf2db,
    "screenshot_monitor" to 0xec08,
    "speaker" to 0xe32d,
    "tablet_android" to 0xe330,
    "view_comfy" to 0xe42a,
    "view_compact" to 0xe42b,
    "volume_down" to 0xe04d,
    "zoom_in" to 0xe8ff,
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
    "contrast" to 0xeb37,
    "palette" to 0xe40a,
    "search" to 0xef7a,
    "shuffle" to 0xe043,
    "folder" to 0xe2c7,
    "schedule" to 0xefd6,
    "sort" to 0xe164,
    "movie" to 0xe404,
    "delete_sweep" to 0xe16c,
    "replay_10" to 0xe059,
    "forward_10" to 0xe056,
    "repeat" to 0xe040,
    "repeat_one" to 0xe041,
    "picture_in_picture_alt" to 0xe911,
    "graphic_eq" to 0xe1b8,
    "queue_music" to 0xe03d,
    "more_vert" to 0xe5d4,
    "share" to 0xe80d,
    "info" to 0xe88e,
    "bedtime" to 0xf159,
    "delete" to 0xe92e,
    "visibility_off" to 0xe8f5,
    "volume_up" to 0xe050,
    "brightness_6" to 0xe3ab,
    "smartphone" to 0xe7ba,
    "tune" to 0xe429,
    "database" to 0xf20e,
    "lock" to 0xe899,
    "add" to 0xe145,
    "folder_open" to 0xe2c8,
    "chevron_right" to 0xe5cc,
    "expand_more" to 0xe5cf,
    "cloud_done" to 0xe2bf,
    "slow_motion_video" to 0xe068,
    "edit_note" to 0xe745,
    "text_fields" to 0xe262,
    "audio_file" to 0xeb82,
    "label" to 0xe893,
    "hd" to 0xe052,
    "tonality" to 0xe427,
    "drag_handle" to 0xe25d,
    "upload_file" to 0xe9fc,
    "restart_alt" to 0xf053,
    "remove" to 0xe15b,
    "volume_off" to 0xe04f,
    "music_note" to 0xe405,
    "timer" to 0xe425,
    "equalizer" to 0xe01d,
    "subtitles" to 0xe048,
    "crop" to 0xe3be,
    "format_size" to 0xe245,
    "sort_by_alpha" to 0xe053,
    "hard_drive" to 0xf80e,
    "arrow_upward" to 0xe5d8,
    "edit" to 0xf097,
    "image" to 0xe3f4,
    "content_copy" to 0xe14d,
    "drive_file_move" to 0xe9a1,
    "heart_broken" to 0xeac2,
    "hide_image" to 0xf022,
    "cleaning_services" to 0xf0ff,
    "warning" to 0xf083,
    "refresh" to 0xe5d5,
    "system_update" to 0xf2cd,
    "download" to 0xf090,
    "error" to 0xf8b6,
    "smart_display" to 0xf06a,
    "link" to 0xe250,
    "content_paste" to 0xe14f,
    "dns" to 0xe875,
    "cloud_off" to 0xe2c1,
    "check_circle" to 0xf0be,
    "high_quality" to 0xe024,
    "open_in_new" to 0xe89e,
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

/** Material 3 switch with a check (on) / cross (off) inside the thumb. */
@Composable
fun PebbleSwitch(checked: Boolean, onChange: ((Boolean) -> Unit)?, modifier: Modifier = Modifier) {
    androidx.compose.material3.Switch(
        checked = checked, onCheckedChange = onChange, modifier = modifier,
        thumbContent = { Icon(if (checked) "check" else "close", null, Modifier.size(16.dp)) },
    )
}

/** Scope-free AnimatedVisibility: avoids the ColumnScope/RowScope extension being picked inside nested Box/Column. */
@Composable
fun PAnim(
    visible: Boolean,
    modifier: Modifier = Modifier,
    enter: androidx.compose.animation.EnterTransition = androidx.compose.animation.fadeIn() + androidx.compose.animation.expandIn(),
    exit: androidx.compose.animation.ExitTransition = androidx.compose.animation.fadeOut() + androidx.compose.animation.shrinkOut(),
    content: @Composable androidx.compose.animation.AnimatedVisibilityScope.() -> Unit,
) {
    androidx.compose.animation.AnimatedVisibility(visible, modifier, enter, exit, content = content)
}

/**
 * Slider wrapper. SliderState's API changed between Material3 1.5 pre-releases, so this keeps using the
 * value-based overload (still works, only deprecated) in ONE place; swap the body when 1.5 is stable.
 */
@Suppress("DEPRECATION")
@Composable
fun PebbleSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    onValueChangeFinished: (() -> Unit)? = null,
) {
    Slider(
        value = value, onValueChange = onValueChange, modifier = modifier,
        valueRange = valueRange, steps = steps, onValueChangeFinished = onValueChangeFinished,
    )
}
