package com.pebble.player.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas as AndroidCanvas
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import android.util.LruCache
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.pebble.player.PebbleApp
import com.pebble.player.data.Profile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.text.style.TextOverflow
import java.io.File
import java.util.Calendar

val LocalApp = staticCompositionLocalOf<PebbleApp> { error("PebbleApp not provided") }

object ThumbCache {
    private val c = LruCache<Long, ImageBitmap>(120)
    fun get(id: Long): ImageBitmap? = c.get(id)
    fun put(id: Long, b: ImageBitmap) { c.put(id, b) }
    fun clear() = c.evictAll()
    /** Bumped whenever a cover image changes, so thumbnails reload. */
    val ver = androidx.compose.runtime.mutableIntStateOf(0)
    fun invalidate(id: Long) { c.remove(id); ver.intValue++ }
}

fun fmt(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    val h = s / 3600
    val m = s % 3600 / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}

fun greeting(): String {
    val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return when { h < 5 -> "Good night"; h < 12 -> "Good morning"; h < 18 -> "Good afternoon"; else -> "Good evening" }
}

val EMOJIS = listOf(
    "", "😀", "😎", "🤓", "🥳", "😺", "🐶", "🦊", "🐼", "🐧",
    "🦄", "🌈", "🎬", "🍿", "🚀", "🎧", "🔥", "⭐", "🌙", "🍀",
)

@Composable
fun Avatar(p: Profile, size: Dp, modifier: Modifier = Modifier) {
    val bg = if (p.color != 0) Color(p.color) else MaterialTheme.colorScheme.primary
    val bmp by produceState<ImageBitmap?>(null, p.photo) {
        value = if (p.photo.isNotEmpty()) withContext(Dispatchers.IO) {
            BitmapFactory.decodeFile(p.photo)?.asImageBitmap()
        } else null
    }
    Box(modifier.size(size).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) {
        val b = bmp
        when {
            b != null -> Image(b, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            p.emoji.isNotEmpty() -> Text(p.emoji, fontSize = (size.value * 0.5f).sp)
            else -> Text(
                (p.name.firstOrNull() ?: 'P').uppercase(),
                color = if (bg.luminance() > 0.5f) Color.Black else Color.White,
                fontSize = (size.value * 0.45f).sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/** Avatar with a camera badge: tap to choose a photo, then adjust it in a crop dialog. */
@Composable
fun PhotoAvatar(p: Profile, size: Dp, onPhoto: (String) -> Unit) {
    val ctx = LocalContext.current
    var picked by remember { mutableStateOf<Uri?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { picked = it }
    Box(Modifier.size(size)) {
        Avatar(p, size, Modifier.clickable {
            launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        })
        Box(
            Modifier.align(Alignment.BottomEnd).size(size * 0.3f).clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondaryContainer)
                .border(3.dp, MaterialTheme.colorScheme.surface, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon("photo_camera", null, Modifier.size(size * 0.16f), tint = MaterialTheme.colorScheme.onSecondaryContainer)
        }
    }
    picked?.let { u ->
        CropDialog(u, onCancel = { picked = null }, onDone = { bmp ->
            onPhoto(saveProfilePhoto(ctx, bmp)); picked = null
        })
    }
}

fun saveProfilePhoto(ctx: Context, bmp: Bitmap): String {
    val f = File(ctx.filesDir, "profile_${System.currentTimeMillis()}.jpg")
    f.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 92, it) }
    ctx.filesDir.listFiles { x -> x.name.startsWith("profile_") && x != f }?.forEach { it.delete() }
    return f.absolutePath
}

@Composable
private fun PickCard(title: String, content: @Composable () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(cs.surfaceContainer).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = cs.primary)
        content()
    }
}

@Composable
fun EmojiColorPicker(p: Profile, onChange: (Profile) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        PickCard("Emoji") {
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), maxItemsInEachRow = 5) {
                EMOJIS.forEach { e ->
                    val sel = p.photo.isEmpty() && p.emoji == e
                    val rRaw by androidx.compose.animation.core.animateDpAsState(if (sel) 26.dp else 16.dp, MaterialTheme.motionScheme.fastSpatialSpec(), label = "r")
                    val r = rRaw.coerceAtLeast(0.dp)
                    Box(
                        Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(r))
                            .background(if (sel) cs.primaryContainer else cs.surfaceContainerHigh)
                            .border(if (sel) 2.dp else 0.dp, cs.primary, RoundedCornerShape(r))
                            .clickable { onChange(p.copy(emoji = e, photo = "")) },
                        contentAlignment = Alignment.Center,
                    ) { if (e.isEmpty()) Text("Aa", fontWeight = FontWeight.Bold) else Text(e, fontSize = 24.sp) }
                }
            }
        }
        SwatchGrid(selected = p.color, autoOption = true, onPick = { onChange(p.copy(color = it)) })
    }
}

/** Named color picker: a hero card with the chosen color + a labelled grid of swatches. */
@Composable
fun SwatchGrid(selected: Int, autoOption: Boolean, enabled: Boolean = true, onPick: (Int) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val list = (if (autoOption) listOf("Auto" to 0) else emptyList()) + ACCENTS.drop(if (autoOption) 1 else 0)
    val cur = list.firstOrNull { it.second == selected } ?: list.first()
    fun col(c: Int) = if (c == 0) cs.primary else Color(c)
    fun onCol(c: Color) = if (c.luminance() > 0.55f) Color(0xFF111111) else Color.White
    val hero by androidx.compose.animation.animateColorAsState(col(cur.second), MaterialTheme.motionScheme.defaultEffectsSpec(), label = "hero")
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(hero).padding(horizontal = 20.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Selected color", style = MaterialTheme.typography.labelMedium, color = onCol(hero).copy(alpha = 0.75f))
                androidx.compose.animation.AnimatedContent(cur.first, label = "cname") { n ->
                    Text(n, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = onCol(hero))
                }
            }
            Box(Modifier.clip(RoundedCornerShape(50)).background(onCol(hero).copy(alpha = 0.18f)).padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text("Aa", fontWeight = FontWeight.Bold, color = onCol(hero))
            }
            Box(Modifier.size(34.dp).clip(CircleShape).background(onCol(hero)), contentAlignment = Alignment.Center) {
                Icon("check", null, Modifier.size(20.dp), tint = hero)
            }
        }
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(cs.surfaceContainer).padding(14.dp)) {
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(10.dp), maxItemsInEachRow = 5) {
                list.forEach { (name, c) ->
                    val sel = c == selected
                    val ringRaw by androidx.compose.animation.core.animateDpAsState(if (sel) 3.dp else 0.dp, MaterialTheme.motionScheme.fastSpatialSpec(), label = "ring")
                    val ring = ringRaw.coerceAtLeast(0.dp) // spring overshoot must never go negative
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).clickable(enabled = enabled) { onPick(c) }.padding(vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Box(Modifier.fillMaxWidth(0.86f).aspectRatio(1f).clip(CircleShape).border(ring, col(c), CircleShape).padding(ring * 2), contentAlignment = Alignment.Center) {
                            Box(Modifier.fillMaxSize().clip(CircleShape).background(col(c)), contentAlignment = Alignment.Center) {
                                PAnim(sel, enter = androidx.compose.animation.scaleIn() + androidx.compose.animation.fadeIn(), exit = androidx.compose.animation.scaleOut() + androidx.compose.animation.fadeOut()) {
                                    Icon("check", name, Modifier.size(20.dp), tint = onCol(col(c)))
                                }
                            }
                        }
                        Text(name, fontSize = 10.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal, color = if (sel) cs.onSurface else cs.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

// ---------- photo crop ----------

private fun maxOff(w: Int, h: Int, view: Float, zoom: Float): Offset {
    val s = view / minOf(w, h) * zoom
    return Offset(maxOf(0f, (w * s - view) / 2f), maxOf(0f, (h * s - view) / 2f))
}

private fun cropMatrix(w: Int, h: Int, out: Float, view: Float, zoom: Float, off: Offset): Matrix {
    val s = out / minOf(w, h) * zoom
    val k = out / view
    val mx = maxOf(0f, (w * s - out) / 2f)
    val my = maxOf(0f, (h * s - out) / 2f)
    val m = Matrix()
    m.postTranslate(-w / 2f, -h / 2f)
    m.postScale(s, s)
    m.postTranslate(out / 2f + (off.x * k).coerceIn(-mx, mx), out / 2f + (off.y * k).coerceIn(-my, my))
    return m
}

@Composable
fun CropDialog(uri: Uri, onCancel: () -> Unit, onDone: (Bitmap) -> Unit) {
    val ctx = LocalContext.current
    val src by produceState<Bitmap?>(null, uri) {
        value = withContext(Dispatchers.IO) {
            try {
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(ctx.contentResolver, uri)) { d, info, _ ->
                    val m = maxOf(info.size.width, info.size.height)
                    if (m > 2048) d.setTargetSampleSize(m / 2048 + 1)
                    d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }
            } catch (e: Exception) { null }
        }
    }
    var zoom by remember { mutableFloatStateOf(1f) }
    var off by remember { mutableStateOf(Offset.Zero) }
    val view = 280.dp
    val viewPx = with(LocalDensity.current) { view.toPx() }
    Dialog(onDismissRequest = onCancel) {
        Surface(shape = MaterialTheme.shapes.extraLarge, tonalElevation = 6.dp) {
            Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Adjust photo", style = MaterialTheme.typography.titleLarge)
                Text("Drag to move, pinch or slide to zoom", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val bmp = src
                Box(
                    Modifier.size(view).clip(RoundedCornerShape(28.dp)).background(Color.Black)
                        .pointerInput(bmp) {
                            detectTransformGestures { _, pan, z, _ ->
                                if (bmp == null) return@detectTransformGestures
                                zoom = (zoom * z).coerceIn(1f, 5f)
                                val mo = maxOff(bmp.width, bmp.height, viewPx, zoom)
                                off = Offset((off.x + pan.x).coerceIn(-mo.x, mo.x), (off.y + pan.y).coerceIn(-mo.y, mo.y))
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    if (bmp != null) {
                        Canvas(Modifier.fillMaxSize()) {
                            drawIntoCanvas { it.nativeCanvas.drawBitmap(bmp, cropMatrix(bmp.width, bmp.height, size.width, size.width, zoom, off), null) }
                            drawCircle(Color.White.copy(alpha = 0.9f), radius = size.minDimension / 2f - 1.5.dp.toPx(), style = Stroke(3.dp.toPx()))
                        }
                    } else LoadingIndicator()
                }
                PebbleSlider(value = zoom, onValueChange = {
                    zoom = it
                    bmp?.let { b -> val mo = maxOff(b.width, b.height, viewPx, zoom); off = Offset(off.x.coerceIn(-mo.x, mo.x), off.y.coerceIn(-mo.y, mo.y)) }
                }, valueRange = 1f..5f)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onCancel) { Text("Cancel") }
                    Button(
                        onClick = {
                            bmp?.let { b ->
                                val out = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
                                AndroidCanvas(out).drawBitmap(b, cropMatrix(b.width, b.height, 512f, viewPx, zoom, off), Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
                                onDone(out)
                            }
                        },
                        enabled = bmp != null,
                        shapes = ButtonDefaults.shapes(),
                    ) { Text("Apply") }
                }
            }
        }
    }
}
