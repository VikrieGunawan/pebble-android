@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)

package com.pebble.player.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pebble.player.data.Prefs
import com.pebble.player.data.Video

/** Opens the "more" bottom sheet for a video (provided by MainFlow). */
val LocalVideoMenu = staticCompositionLocalOf<(Video) -> Unit> { {} }

fun fmtSize(b: Long): String = if (b >= 1_073_741_824L) "%.1f GB".format(b / 1073741824.0) else "%.1f MB".format(b / 1048576.0)

/** Rounded-square icon tile, like the website's feature tiles. */
@Composable
fun IconTile(icon: String, size: Dp = 44.dp, filled: Boolean = false, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    Box(
        modifier.size(size).clip(RoundedCornerShape(size * 0.32f)).background(if (filled) cs.primary else cs.primaryContainer),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, Modifier.size(size * 0.52f), tint = if (filled) cs.onPrimary else cs.primary) }
}

@Composable
fun PageHeader(icon: String, title: String, subtitle: String, trailing: @Composable () -> Unit = {}) {
    val cs = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.size(52.dp).clip(RoundedCornerShape(18.dp)).background(cs.surfaceContainerHigh), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(28.dp), tint = cs.primary)
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        }
        trailing()
    }
}

@Composable
fun SectionLabel(icon: String, text: String, count: Int? = null) {
    val cs = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(30.dp).clip(RoundedCornerShape(10.dp)).background(cs.primaryContainer), contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size(17.dp), tint = cs.primary) }
        Text(text.uppercase(), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.ExtraBold, color = cs.onSurfaceVariant, letterSpacing = 1.2.sp, modifier = Modifier.weight(1f))
        if (count != null) Box(Modifier.clip(RoundedCornerShape(50)).background(cs.primaryContainer).padding(horizontal = 10.dp, vertical = 3.dp)) {
            Text("$count", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.ExtraBold, color = cs.primary)
        }
    }
}

/** Grid / list switch (the website's two-button toggle). */
@Composable
fun LayoutToggle(layout: Int, onChange: (Int) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(Modifier.clip(RoundedCornerShape(50)).background(cs.surfaceContainerHigh).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        listOf("grid_view" to 0, "view_list" to 1).forEach { (ic, v) ->
            val on = layout == v
            Box(
                Modifier.size(width = 46.dp, height = 38.dp).clip(RoundedCornerShape(50)).background(if (on) cs.primary else Color.Transparent).clickable { onChange(v) },
                contentAlignment = Alignment.Center,
            ) { Icon(ic, null, Modifier.size(22.dp), tint = if (on) cs.onPrimary else cs.onSurfaceVariant) }
        }
    }
}

@Composable
fun EmptyCard(icon: String, title: String, sub: String) {
    val cs = MaterialTheme.colorScheme
    val line = cs.outlineVariant
    Column(
        Modifier.fillMaxWidth().padding(top = 18.dp).drawBehind {
            drawRoundRect(line, cornerRadius = CornerRadius(28.dp.toPx()), style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))), size = Size(size.width, size.height))
        }.padding(horizontal = 24.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        IconTile(icon, 76.dp)
        Spacer(Modifier.height(6.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text(sub, color = cs.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

@Composable
fun VideoCollection(
    videos: List<Video>, prefs: Prefs, onClick: (Video) -> Unit,
    header: @Composable () -> Unit = {}, empty: @Composable () -> Unit = {},
    folders: List<Pair<String, Int>> = emptyList(), onFolder: (String) -> Unit = {},
) {
    val row = prefs.layout == 1
    LazyVerticalGrid(
        columns = if (row) GridCells.Fixed(1) else GridCells.Adaptive(168.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(if (row) 10.dp else 14.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) { header() }
        if (folders.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) { SectionLabel("folder", "Folders", folders.size) }
            items(folders, key = { "f:" + it.first }) { f -> FolderTile(f.first, f.second, row, Modifier.animateItem()) { onFolder(f.first) } }
            item(span = { GridItemSpan(maxLineSpan) }) { SectionLabel("movie", "All videos", videos.size) }
        }
        if (videos.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) { empty() }
        items(videos, key = { it.id }) { v -> VideoCard(v, prefs, row, Modifier.animateItem()) { onClick(v) } }
    }
}

/** Folder card (grid) / folder row (list) that morphs between the two layouts. */
@Composable
fun FolderTile(name: String, count: Int, row: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    SharedTransitionLayout(modifier) {
        val sc = this
        AnimatedContent(row, transitionSpec = { fadeIn(tween(240)) togetherWith fadeOut(tween(160)) using SizeTransform(clip = false) }, label = "folder") { r ->
            val tile = with(sc) { Modifier.sharedBounds(rememberSharedContentState("fi-$name"), this@AnimatedContent) }
            if (r) Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(cs.surfaceContainer).clickable(onClick = onClick).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                IconTile("folder", 52.dp, modifier = tile)
                Column(Modifier.weight(1f)) {
                    Text(name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("$count video${if (count == 1) "" else "s"}", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                }
                Icon("chevron_right", null, Modifier.size(22.dp), tint = cs.onSurfaceVariant)
            } else Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(cs.surfaceContainer).clickable(onClick = onClick).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                IconTile("folder", 52.dp, modifier = tile)
                Column {
                    Text(name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("$count video${if (count == 1) "" else "s"}", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                }
            }
        }
    }
}

/** The website's location bar: up button + breadcrumb pills. */
@Composable
fun LocationBar(folder: String?, onFolder: (String?) -> Unit) {
    val cs = MaterialTheme.colorScheme
    var last by remember { mutableStateOf("") }
    LaunchedEffect(folder) { if (folder != null) last = folder }
    val upA by animateFloatAsState(if (folder != null) 1f else 0.35f, label = "up")
    val shape = RoundedCornerShape(22.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(cs.surfaceContainer).border(1.dp, cs.outlineVariant, shape).padding(6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier.size(40.dp).alpha(upA).clip(RoundedCornerShape(16.dp)).background(cs.surfaceContainerHigh).clickable(enabled = folder != null) { onFolder(null) },
            contentAlignment = Alignment.Center,
        ) { Icon("arrow_upward", "Up one level", Modifier.size(22.dp)) }
        Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            Crumb("video_library", "Library", folder == null) { onFolder(null) }
            PAnim(folder != null, enter = fadeIn() + expandHorizontally(), exit = fadeOut() + shrinkHorizontally()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon("chevron_right", null, Modifier.size(20.dp), tint = cs.onSurfaceVariant)
                    Crumb("folder", folder ?: last, true) { }
                }
            }
        }
    }
}

@Composable
private fun Crumb(icon: String, label: String, cur: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val bg by animateColorAsState(if (cur) cs.primary else Color.Transparent, label = "crumbBg")
    val fg by animateColorAsState(if (cur) cs.onPrimary else cs.onSurfaceVariant, label = "crumbFg")
    Row(
        Modifier.height(36.dp).clip(RoundedCornerShape(14.dp)).background(bg).clickable(onClick = onClick).padding(start = 10.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, null, Modifier.size(19.dp), tint = fg)
        Text(label, color = fg, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, maxLines = 1)
    }
}

/** Video card in the website's style; morphs between grid and list with a shared-bounds thumbnail. */
@Composable
fun VideoCard(v: Video, prefs: Prefs, row: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val menu = LocalVideoMenu.current
    val prog = prefs.progress[v.uri.toString()]
    val fav = v.uri.toString() in prefs.favs
    val bmp = rememberThumb(v)
    SharedTransitionLayout(modifier) {
        val sc = this
        AnimatedContent(row, transitionSpec = { fadeIn(tween(240)) togetherWith fadeOut(tween(160)) using SizeTransform(clip = false) }, label = "card") { r ->
            val shared = with(sc) { Modifier.sharedBounds(rememberSharedContentState("t-${v.id}"), this@AnimatedContent) }
            val thumb: @Composable (Modifier) -> Unit = { m ->
                Box(shared.then(m).aspectRatio(16f / 9f).clip(RoundedCornerShape(18.dp)).background(cs.surfaceContainerHigh)) {
                    bmp?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                        ?: Icon("movie", null, Modifier.align(Alignment.Center).size(30.dp), tint = cs.onSurfaceVariant)
                    if (v.dur > 0) Text(
                        fmt(v.dur), color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xAA000000)).padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                    if (fav) Icon("favorite", null, Modifier.align(Alignment.TopStart).padding(6.dp).size(18.dp), tint = cs.primary)
                    if (prog != null && prog.dur > 0) LinearProgressIndicator(
                        progress = { (prog.pos.toFloat() / prog.dur).coerceIn(0f, 1f) },
                        modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().height(3.dp), color = cs.primary, trackColor = Color.Transparent, drawStopIndicator = {},
                    )
                }
            }
            val meta: @Composable () -> Unit = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (v.ext.isNotEmpty()) Text(v.ext, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.ExtraBold, color = cs.primary,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(cs.primaryContainer).padding(horizontal = 8.dp, vertical = 3.dp))
                    Text(fmtSize(v.size), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, maxLines = 1)
                }
            }
            val more: @Composable () -> Unit = {
                Box(Modifier.size(40.dp).clip(CircleShape).clickable { menu(v) }, contentAlignment = Alignment.Center) { Icon("more_vert", "More", Modifier.size(22.dp), tint = cs.onSurfaceVariant) }
            }
            if (r) {
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(cs.surfaceContainer).clickable(onClick = onClick).padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    thumb(Modifier.width(128.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(v.name, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        meta()
                    }
                    more()
                }
            } else {
                // no clipped corners here, so the format pill and ⋮ button never get cut off
                Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(bottom = 6.dp)) {
                    thumb(Modifier.fillMaxWidth())
                    Row(Modifier.padding(start = 4.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(v.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            meta()
                        }
                        more()
                    }
                }
            }
        }
    }
}
