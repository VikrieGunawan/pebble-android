@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)

package com.pebble.player.ui

import androidx.compose.ui.util.lerp
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.layout.Layout
import androidx.compose.animation.BoundsTransform
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
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
import kotlinx.coroutines.delay
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
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

/** Natural name order: letters A–Z (a–z), then names starting with digits (1, 2, … 10), then symbols. */
val NaturalOrder = Comparator<String> { a, b ->
    fun grp(s: String) = s.firstOrNull()?.let { if (it.isLetter()) 0 else if (it.isDigit()) 1 else 2 } ?: 2
    val ga = grp(a)
    val gb = grp(b)
    if (ga != gb) return@Comparator ga - gb
    var i = 0
    var j = 0
    while (i < a.length && j < b.length) {
        if (a[i].isDigit() && b[j].isDigit()) {
            var ei = i
            while (ei < a.length && a[ei].isDigit()) ei++
            var ej = j
            while (ej < b.length && b[ej].isDigit()) ej++
            val na = a.substring(i, ei).trimStart('0')
            val nb = b.substring(j, ej).trimStart('0')
            val c = if (na.length != nb.length) na.length - nb.length else na.compareTo(nb)
            if (c != 0) return@Comparator c
            i = ei; j = ej
        } else {
            val c = a[i].lowercaseChar().compareTo(b[j].lowercaseChar())
            if (c != 0) return@Comparator c
            i++; j++
        }
    }
    val c = (a.length - i) - (b.length - j)
    if (c != 0) c else a.compareTo(b)
}

/** Smooth horizontal slide (like the website's page changes). dir = 1 → forward, -1 → back. */
fun slideTransition(dir: Int): ContentTransform =
    (slideInHorizontally(tween(340, easing = FastOutSlowInEasing)) { it * dir / 3 } + fadeIn(tween(260))) togetherWith
        (slideOutHorizontally(tween(340, easing = FastOutSlowInEasing)) { -it * dir / 3 } + fadeOut(tween(160)))

/** Opens the "more" bottom sheet for a video (provided by MainFlow). */
val LocalFolderMenu = staticCompositionLocalOf<(String) -> Unit> { {} }
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

/** One toast at a time: a new one replaces the old at once instead of queuing behind it (the same on every Android version). */
private var lastToast: android.widget.Toast? = null
fun pebbleToast(ctx: android.content.Context, msg: String, long: Boolean = false) {
    lastToast?.cancel()
    lastToast = android.widget.Toast.makeText(ctx.applicationContext, msg, if (long) android.widget.Toast.LENGTH_LONG else android.widget.Toast.LENGTH_SHORT).also { it.show() }
}

/** Grid / list switch (the website's two-button toggle). */
/** Phones (short side under 600dp) get fixed columns; tablets keep the adaptive grid. */
@Composable
fun isPhoneScreen(): Boolean {
    val c = LocalConfiguration.current
    return minOf(c.screenWidthDp, c.screenHeightDp) < 600
}

/** Most columns a phone can show in landscape: 3 only on long screens (about 20:9), otherwise 2. */
@Composable
fun maxGridCols(): Int {
    val c = LocalConfiguration.current
    val hi = maxOf(c.screenWidthDp, c.screenHeightDp).toFloat()
    val lo = minOf(c.screenWidthDp, c.screenHeightDp).toFloat().coerceAtLeast(1f)
    return if (hi / lo >= 2f) 3 else 2
}

/** Columns used by the grid on a phone: 1 in portrait, 2 or 3 in landscape (3 only when the screen allows it and it is chosen). */
@Composable
fun phoneCols(prefs: Prefs): Int {
    val c = LocalConfiguration.current
    return if (c.screenWidthDp <= c.screenHeightDp) 1 else if (prefs.gridSize == 0 && maxGridCols() >= 3) 3 else 2
}

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
    state: LazyGridState = rememberLazyGridState(),
) {
    val folderMenu = LocalFolderMenu.current
    val row = prefs.layout == 1
    val phone = isPhoneScreen()
    val cols = phoneCols(prefs)
    LazyVerticalGrid(
        state = state,
        columns = if (row) GridCells.Fixed(1) else if (phone) GridCells.Fixed(cols) else GridCells.Adaptive(listOf(136, 168, 220)[prefs.gridSize.coerceIn(0, 2)].dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) { header() }
        if (folders.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) { SectionLabel("folder", t("Folders"), folders.size) }
            items(folders, key = { "f:" + it.first }) { f -> FolderTile(f.first.trimEnd('/').substringAfterLast('/'), f.second, row, Modifier.animateItem(), { folderMenu(f.first) }) { onFolder(f.first) } }
            if (videos.isNotEmpty()) item(span = { GridItemSpan(maxLineSpan) }) { SectionLabel("movie", t("Videos"), videos.size) }
        }
        if (videos.isEmpty() && folders.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) { empty() }
        items(videos, key = { it.id }) { v -> VideoCard(v, prefs, row, Modifier.animateItem()) { onClick(v) } }
    }
}

private const val MORPH_MS = 340

private fun morphSpec(): ContentTransform =
    ContentTransform(EnterTransition.None, ExitTransition.None, sizeTransform = SizeTransform(clip = false) { _, _ -> tween(MORPH_MS, easing = FastOutSlowInEasing) })

/** Shared bounds without any fade: the element itself moves and resizes between the list and grid layouts. */
@Composable
private fun SharedTransitionScope.morph(key: String, av: AnimatedVisibilityScope, z: Float = 1f, remeasure: Boolean = false): Modifier {
    val st = rememberSharedContentState(key)
    val bt = BoundsTransform { _, _ -> tween(MORPH_MS, easing = FastOutSlowInEasing) }
    // remeasure = relayout every frame (backgrounds, text); otherwise the default scale-to-bounds (cheap)
    return if (remeasure) Modifier.sharedBounds(
        st, av, enter = EnterTransition.None, exit = ExitTransition.None, boundsTransform = bt,
        resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds, zIndexInOverlay = z,
    ) else Modifier.sharedBounds(
        st, av, enter = EnterTransition.None, exit = ExitTransition.None, boundsTransform = bt, zIndexInOverlay = z,
    )
}

/** Folder card (grid) / folder row (list): background, icon, texts and menu all glide between the two layouts. */
@Composable
fun FolderTile(name: String, count: Int, row: Boolean, modifier: Modifier, onMore: (() -> Unit)? = null, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    SharedTransitionLayout(modifier) {
        val sc = this
        AnimatedContent(row, transitionSpec = { morphSpec() }, label = "folder") { r ->
            val av: AnimatedVisibilityScope = this
            val sh: @Composable (String, Float, Boolean) -> Modifier = { k, z, r2 -> sc.morph(k, av, z, r2) }
            val bg: @Composable BoxScope.() -> Unit = { Box(Modifier.matchParentSize().then(sh("fbg-$name", 0f, true)).background(cs.surfaceContainer, RoundedCornerShape(24.dp))) }
            val title: @Composable () -> Unit = { Text(name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = sh("ft-$name", 1f, true).fillMaxWidth()) }
            val sub: @Composable () -> Unit = {
                Text(videoCount(count), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, maxLines = 1, modifier = sh("fs-$name", 1f, true).fillMaxWidth())
            }
            val more: @Composable () -> Unit = {
                if (onMore != null) Box(sh("fm-$name", 1f, false).size(40.dp).clip(CircleShape).clickable(onClick = onMore), contentAlignment = Alignment.Center) {
                    Icon("more_vert", t("Folder menu"), Modifier.size(22.dp), tint = cs.onSurfaceVariant)
                }
            }
            if (r) Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).clickable(onClick = onClick)) {
                bg()
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    IconTile("folder", 52.dp, modifier = sh("fi-$name", 1f, false))
                    Column(Modifier.weight(1f)) { title(); sub() }
                    if (onMore != null) more() else Icon("chevron_right", null, Modifier.size(22.dp), tint = cs.onSurfaceVariant)
                }
            } else Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).clickable(onClick = onClick)) {
                bg()
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                        IconTile("folder", 52.dp, modifier = sh("fi-$name", 1f, false))
                        Spacer(Modifier.weight(1f))
                        Box(Modifier.offset(x = 8.dp, y = (-8).dp)) { more() }
                    }
                    Column { title(); sub() }
                }
            }
        }
    }
}

/** The website's location bar: up button + one pill per folder level. Levels you opened and then left stay visible (dimmed). */
@Composable
fun LocationBar(path: String?, trail: String?, onPath: (String?) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val cur = remember(path) { path?.split('/')?.filter { it.isNotBlank() } ?: emptyList() }
    val segs = remember(path, trail) {
        val t = trail?.split('/')?.filter { it.isNotBlank() } ?: emptyList()
        if (t.size >= cur.size && t.take(cur.size) == cur) t else cur
    }
    val upA by animateFloatAsState(if (cur.isNotEmpty()) 1f else 0.35f, label = "up")
    val scroll = rememberScrollState(Int.MAX_VALUE) // opens already scrolled to the end: no slide when the page is rebuilt
    // Follow the active crumb: keep it in view while crumbs animate in/out, on every folder change.
    val active = remember { BringIntoViewRequester() }
    // Folder rows are rebuilt every time they scroll back into view: only animate real folder changes, not that rebuild.
    val settled = remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { withFrameNanos { }; settled.value = true }
    LaunchedEffect(path, segs) {
        if (settled.value) repeat(14) { delay(45); active.bringIntoView() }
    }
    val shape = RoundedCornerShape(22.dp)
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(MaterialTheme.colorScheme.surfaceContainer).border(1.dp, cs.outlineVariant.copy(alpha = 0.6f), shape).padding(6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier.size(40.dp).alpha(upA).clip(RoundedCornerShape(16.dp)).background(cs.surfaceContainerHigh)
                .clickable(enabled = cur.isNotEmpty()) { onPath(if (cur.size <= 1) null else cur.dropLast(1).joinToString("/") + "/") },
            contentAlignment = Alignment.Center,
        ) { Icon("arrow_upward", t("Up one level"), Modifier.size(22.dp)) }
        Row(Modifier.weight(1f).horizontalScroll(scroll), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            Crumb("video_library", t("Library"), cur.isEmpty(), false, if (cur.isEmpty()) Modifier.bringIntoViewRequester(active) else Modifier) { onPath(null) }
            segs.forEachIndexed { i, name ->
                val target = segs.take(i + 1).joinToString("/") + "/"
                key(target) {
                    var shown by remember { mutableStateOf(!settled.value) }
                    LaunchedEffect(Unit) { shown = true }
                    PAnim(shown, enter = fadeIn() + expandHorizontally(), exit = fadeOut() + shrinkHorizontally()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon("chevron_right", null, Modifier.size(20.dp), tint = cs.onSurfaceVariant)
                            Crumb("folder", name, i == cur.lastIndex, i >= cur.size, if (i == cur.lastIndex) Modifier.bringIntoViewRequester(active) else Modifier) { onPath(target) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Crumb(icon: String, label: String, cur: Boolean, dim: Boolean, extra: Modifier, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val bg by animateColorAsState(if (cur) cs.primary else Color.Transparent, label = "crumbBg")
    val fg by animateColorAsState(if (cur) cs.onPrimary else cs.onSurfaceVariant, label = "crumbFg")
    Row(
        extra.height(36.dp).alpha(if (dim) 0.5f else 1f).clip(RoundedCornerShape(14.dp)).background(bg).clickable(onClick = onClick).padding(start = 10.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, null, Modifier.size(19.dp), tint = fg)
        Text(label, color = fg, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, maxLines = 1)
    }
}

/**
 * Video card: ONE layout whose cover, title, format/size, ⋮ button and background are placed by an animated 0..1 value,
 * so list ⇄ grid glides smoothly (no fade, no double composition, no shared-element overlay).
 */
@Composable
fun VideoCard(v: Video, prefs: Prefs, row: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val menu = LocalVideoMenu.current
    val prog = prefs.progress[v.uri.toString()]
    val fav = v.uri.toString() in prefs.favs
    val bmp = rememberThumb(v)
    val t = animateFloatAsState(if (row) 0f else 1f, tween(MORPH_MS, easing = FastOutSlowInEasing), label = "morph")
    val shape = RoundedCornerShape(24.dp)
    Layout(
        content = {
            Box(Modifier.layoutId("bg").background(cs.surfaceContainer))
            Box(Modifier.layoutId("thumb").clip(RoundedCornerShape(18.dp)).background(cs.surfaceContainerHigh)) {
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
            Text(v.name, Modifier.layoutId("title"), fontWeight = FontWeight.Bold, maxLines = if (row) 2 else 1, overflow = TextOverflow.Ellipsis)
            Row(Modifier.layoutId("meta"), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (v.res.isNotEmpty()) Text(v.res, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.ExtraBold, color = cs.primary,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(cs.primaryContainer).padding(horizontal = 8.dp, vertical = 3.dp))
                if (v.size > 0) Text(fmtSize(v.size), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, maxLines = 1)
            }
            Box(Modifier.layoutId("more").clip(CircleShape).clickable { menu(v) }, contentAlignment = Alignment.Center) {
                Icon("more_vert", t("More"), Modifier.size(22.dp), tint = cs.onSurfaceVariant)
            }
        },
        modifier = modifier.fillMaxWidth().clip(shape).clickable(onClick = onClick),
    ) { ms, c ->
        val w = c.maxWidth
        val k = t.value // 0 = list, 1 = grid (read here: only re-measures while animating)
        val pad = 8.dp.roundToPx(); val btn = 40.dp.roundToPx(); val gap = 12.dp.roundToPx()
        val twL = 128.dp.roundToPx(); val twG = w - 2 * pad
        val tw = lerp(twL, twG, k); val th = tw * 9 / 16
        val xL = pad + twL + gap; val xG = 12.dp.roundToPx()
        val x = lerp(xL, xG, k)
        val textW = lerp(w - xL - gap - btn - pad, w - xG - pad - btn, k).coerceAtLeast(0)
        fun m(id: String) = ms.first { it.layoutId == id }
        val thumb = m("thumb").measure(Constraints.fixed(tw, th))
        val title = m("title").measure(Constraints(maxWidth = textW))
        val meta = m("meta").measure(Constraints(maxWidth = textW))
        val more = m("more").measure(Constraints.fixed(btn, btn))
        // list target
        val gL = 6.dp.roundToPx(); val colL = title.height + gL + meta.height
        val contentL = maxOf(twL * 9 / 16, colL, btn)
        val hL = contentL + 2 * pad
        // grid target
        val gG = 4.dp.roundToPx(); val colG = title.height + gG + meta.height
        val rowG = maxOf(colG, btn); val topG = pad + twG * 9 / 16 + 6.dp.roundToPx()
        val hG = topG + rowG + pad
        val h = lerp(hL, hG, k)
        val yTitle = lerp(pad + (contentL - colL) / 2, topG + (rowG - colG) / 2, k)
        val yMeta = lerp(pad + (contentL - colL) / 2 + title.height + gL, topG + (rowG - colG) / 2 + title.height + gG, k)
        val yMore = lerp(pad + (contentL - btn) / 2, topG + (rowG - btn) / 2, k)
        val bg = m("bg").measure(Constraints.fixed(w, h))
        layout(w, h) {
            bg.place(0, 0)
            thumb.place(pad, pad)
            title.place(x, yTitle)
            meta.place(x, yMeta)
            more.place(w - pad - btn, yMore)
        }
    }
}
