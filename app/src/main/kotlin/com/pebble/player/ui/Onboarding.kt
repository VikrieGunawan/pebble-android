package com.pebble.player.ui

import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pebble.player.data.K
import com.pebble.player.data.Prefs
import com.pebble.player.data.Profile
import com.pebble.player.data.Store
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private const val LAST = 7 // processing step index

@Composable
fun Onboarding(prefs: Prefs, store: Store) {
    val scope = rememberCoroutineScope()
    var step by remember { mutableIntStateOf(0) }
    var back by remember { mutableStateOf(false) }
    var profile by remember { mutableStateOf(prefs.profile) }
    val cs = MaterialTheme.colorScheme

    fun go(n: Int) { back = n < step; step = n }
    fun finish() = scope.launch {
        store.saveProfile(profile)
        store.put(K.onb, true)
    }

    val focus = androidx.compose.ui.platform.LocalFocusManager.current
    val kb = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    Box(Modifier.fillMaxSize().background(cs.background).pointerInput(Unit) {
        detectTapGestures(onTap = { focus.clearFocus(); kb?.hide() })
    }) {
        // soft animated blobs
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(Brush.radialGradient(listOf(cs.primary.copy(alpha = 0.22f), Color.Transparent), center = Offset(size.width * 0.1f, size.height * 0.05f), radius = size.width * 0.9f), radius = size.width, center = Offset(size.width * 0.1f, size.height * 0.05f))
            drawCircle(Brush.radialGradient(listOf(cs.tertiary.copy(alpha = 0.18f), Color.Transparent), center = Offset(size.width * 0.95f, size.height * 0.95f), radius = size.width * 0.9f), radius = size.width, center = Offset(size.width * 0.95f, size.height * 0.95f))
        }
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            // header: dots + skip
            PAnim(step < LAST, enter = fadeIn(), exit = fadeOut()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.weight(1f)) {
                        for (i in 0 until LAST) {
                            val w by animateDpAsState(if (i == step) 22.dp else 8.dp, MaterialTheme.motionScheme.defaultSpatialSpec(), label = "dot")
                            val dotCol by androidx.compose.animation.animateColorAsState(
                if (i == step) cs.primary else if (i < step) cs.primaryContainer else cs.outlineVariant,
                MaterialTheme.motionScheme.defaultEffectsSpec(), label = "dotc",
            )
            Box(Modifier.height(8.dp).width(w).clip(CircleShape).background(dotCol))
                        }
                    }
                    TextButton(onClick = { scope.launch { store.saveProfile(profile); store.put(K.onb, true) } }) { Text("Skip") }
                }
            }
            val spSpec = MaterialTheme.motionScheme.defaultSpatialSpec<IntOffset>()
            val spSpec2 = MaterialTheme.motionScheme.defaultSpatialSpec<androidx.compose.ui.unit.IntSize>()
            val efSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
            Box(Modifier.weight(1f).fillMaxWidth()) {
                AnimatedContent(
                    targetState = step,
                    transitionSpec = {
                        val dir = if (back) -1 else 1
                        (slideInHorizontally(spSpec) { dir * it / 3 } + fadeIn(efSpec)) togetherWith
                            (slideOutHorizontally(spSpec) { -dir * it / 3 } + fadeOut(efSpec))
                    },
                    label = "step",
                ) { s ->
                    Column(
                        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = if (s == 0 || s == 3 || s == 5 || s == 7) Arrangement.Center else Arrangement.Top,
                    ) {
                        when (s) {
                            0 -> WelcomeStep()
                            1 -> NameStep(profile) { profile = it }
                            2 -> AvatarStep(profile) { profile = it }
                            3 -> ThemeStep(prefs, store)
                            4 -> AccentStep(prefs, store)
                            5 -> LayoutStep(prefs, store)
                            6 -> PlaybackStep(prefs, store)
                            else -> ProcessingStep(prefs, profile, store) { finish() }
                        }
                    }
                }
            }
            PAnim(step < LAST, enter = fadeIn(), exit = fadeOut()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp)) {
                    PAnim(
                        step > 0,
                        enter = fadeIn(efSpec) + expandHorizontally(spSpec2, expandFrom = Alignment.Start),
                        exit = fadeOut(efSpec) + shrinkHorizontally(spSpec2, shrinkTowards = Alignment.Start),
                    ) {
                        Row {
                            OutlinedButton(onClick = { go(step - 1) }, shapes = ButtonDefaults.shapes(), modifier = Modifier.height(56.dp)) { Text("Back") }
                            Spacer(Modifier.width(12.dp))
                        }
                    }
                    Button(onClick = { go(step + 1) }, shapes = ButtonDefaults.shapes(), modifier = Modifier.weight(1f).height(56.dp)) {
                        Text(if (step == 0) "Get started" else if (step == LAST - 1) "Finish" else "Next", fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.width(8.dp))
                        Icon("arrow_forward", null, Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun StepTitle(title: String, sub: String) {
    Spacer(Modifier.height(8.dp))
    Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
    Spacer(Modifier.height(6.dp))
    Text(sub, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    Spacer(Modifier.height(22.dp))
}

private val PLAY_PATH = androidx.compose.ui.graphics.vector.PathParser()
    .parsePathString("M8 6.82v10.36c0 .79.87 1.27 1.54.84l8.14-5.18c.62-.39.62-1.29 0-1.69L9.54 5.98C8.87 5.55 8 6.03 8 6.82z").toPath()

/** Same artwork as the launcher/web icon: gradient square, soft frame and play, scaled like the home-screen icon. */
@Composable
fun AppMark(size: Int) {
    val sc = remember { Animatable(0.6f) }
    LaunchedEffect(Unit) { sc.animateTo(1f, androidx.compose.animation.core.spring(0.45f, 220f)) }
    androidx.compose.foundation.Canvas(Modifier.size(size.dp).scale(sc.value)) {
        val n = this.size.width
        drawRoundRect(
            Brush.linearGradient(listOf(Color(0xFFFF8A5C), Color(0xFF7B61FF)), Offset.Zero, Offset(n, n)),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(n * 0.354f),
        )
        val k = 0.93f // same content scale as the adaptive launcher icon
        withTransform({ scale(k, k, Offset(n / 2f, n / 2f)) }) {
            drawRoundRect(
                Color.White.copy(alpha = 0.35f), Offset(n * 5f / 48f, n * 5f / 48f), androidx.compose.ui.geometry.Size(n * 38f / 48f, n * 38f / 48f),
                androidx.compose.ui.geometry.CornerRadius(n * 13f / 48f), style = androidx.compose.ui.graphics.drawscope.Stroke(n * 1.5f / 48f),
            )
            withTransform({ translate(n * 9f / 48f, n * 9f / 48f); scale(n * 30f / 48f / 24f, n * 30f / 48f / 24f, Offset.Zero) }) {
                drawPath(PLAY_PATH, Color.White)
            }
        }
    }
}

@Composable
private fun WelcomeStep() {
    AppMark(128)
    Spacer(Modifier.height(28.dp))
    val words = listOf("Welcome", "to", "Pebble")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        words.forEachIndexed { i, w ->
            var on by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) { delay(250L + i * 140L); on = true }
            val a by animateFloatAsState(if (on) 1f else 0f, MaterialTheme.motionScheme.slowEffectsSpec(), label = "w")
            val y by animateDpAsState(if (on) 0.dp else 18.dp, MaterialTheme.motionScheme.slowSpatialSpec(), label = "wy")
            Text(w, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false,
                color = if (i == 2) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.graphicsLayer { alpha = a; translationY = y.toPx() })
        }
    }
    Spacer(Modifier.height(12.dp))
    Text("Your videos, your style.\nEverything stays on your device.", style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
}

@Composable
private fun NameStep(p: Profile, onChange: (Profile) -> Unit) {
    val focusMgr = androidx.compose.ui.platform.LocalFocusManager.current
    StepTitle("What should we call you?", "Your name shows in the greeting. You can skip this.")
    PhotoAvatar(p, 132.dp) { onChange(p.copy(photo = it, emoji = "")) }
    Spacer(Modifier.height(24.dp))
    OutlinedTextField(
        value = p.name, onValueChange = { onChange(p.copy(name = it.take(24))) },
        label = { Text("Your name") }, singleLine = true, shape = RoundedCornerShape(20.dp),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { focusMgr.clearFocus() }), modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun AvatarStep(p: Profile, onChange: (Profile) -> Unit) {
    StepTitle("Pick your avatar", "Choose an emoji and a color.")
    Avatar(p, 112.dp)
    Spacer(Modifier.height(20.dp))
    EmojiColorPicker(p, onChange)
    Spacer(Modifier.height(16.dp))
}

@Composable
private fun AccentStep(prefs: Prefs, store: Store) {
    val scope = rememberCoroutineScope()
    val cs = MaterialTheme.colorScheme
    val canYou = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val you = canYou && prefs.dynamic
    StepTitle("Pick your color", "Choose one color, or let your wallpaper decide.")
    if (canYou) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(if (you) cs.primaryContainer else cs.surfaceContainer)
                .clickable { scope.launch { store.put(K.dyn, !prefs.dynamic) } }.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon("palette", null, Modifier.size(28.dp), tint = if (you) cs.onPrimaryContainer else cs.primary)
            Column(Modifier.weight(1f)) {
                Text("Material You", fontWeight = FontWeight.Bold)
                Text(if (you) "Colors come from your wallpaper" else "Use colors from your wallpaper", style = MaterialTheme.typography.bodySmall, color = if (you) cs.onPrimaryContainer else cs.onSurfaceVariant)
            }
            PebbleSwitch(prefs.dynamic, { v -> scope.launch { store.put(K.dyn, v) } })
        }
        Spacer(Modifier.height(16.dp))
    }
    val a by animateFloatAsState(if (you) 0.38f else 1f, MaterialTheme.motionScheme.defaultEffectsSpec(), label = "accA")
    Column(Modifier.fillMaxWidth().graphicsLayer { alpha = a }) {
        SwatchGrid(selected = prefs.accent, autoOption = false, enabled = !you) { c -> scope.launch { store.put(K.accent, c) } }
    }
    if (you) Text("Turn off Material You to pick a color.", Modifier.padding(top = 10.dp), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
    Spacer(Modifier.height(16.dp))
}

@Composable
private fun ToggleRow(icon: String, title: String, sub: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(cs.surfaceContainerHigh).clickable { onChange(!checked) }.padding(16.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(cs.secondaryContainer), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = cs.onSecondaryContainer)
        }
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        }
        PebbleSwitch(checked, onChange)
    }
}

@Composable
private fun PlaybackStep(prefs: Prefs, store: Store) {
    val scope = rememberCoroutineScope()
    StepTitle("Playback and feel", "Switch on what you like.")
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ToggleRow("play_circle", "Autoplay", "Start as soon as a video opens", prefs.autoplay) { v -> scope.launch { store.put(K.autoplay, v) } }
        ToggleRow("fullscreen", "Rotate with the video", "Wide videos go landscape automatically", prefs.autoFs) { v -> scope.launch { store.put(K.autoFs, v) } }
        ToggleRow("blur_on", "Resume where you stopped", "Continue from your last position", prefs.resume) { v -> scope.launch { store.put(K.resume, v) } }
    }
}

// ---------- processing + done ----------

private data class Particle(val dx: Float, val dy: Float, val rot: Float, val color: Color, val delay: Float)

@Composable
fun Confetti(trigger: Boolean, modifier: Modifier = Modifier) {
    val p = remember { Animatable(0f) }
    val parts = remember {
        val cols = listOf(Color(0xFFFF8A5C), Color(0xFF7B61FF), Color(0xFF12A77A), Color(0xFFE5336D), Color(0xFF1E88E5), Color(0xFFF5B700))
        List(26) { i ->
            val a = i / 26f * 6.2831f + Random.nextFloat() * 0.3f
            val d = 160f + Random.nextFloat() * 200f
            Particle(cos(a) * d, sin(a) * d, Random.nextFloat() * 540f - 270f, cols[i % cols.size], Random.nextFloat() * 0.2f)
        }
    }
    LaunchedEffect(trigger) { if (trigger) p.animateTo(1f, tween(1500, easing = LinearEasing)) }
    Canvas(modifier) {
        if (p.value <= 0f) return@Canvas
        parts.forEach { q ->
            val t = ((p.value - q.delay) / (1f - q.delay)).coerceIn(0f, 1f)
            val e = 1f - (1f - t) * (1f - t)
            val c = Offset(size.width / 2f + q.dx * e, size.height / 2f + q.dy * e + 60f * t * t)
            drawCircle(q.color.copy(alpha = (1f - t * t).coerceIn(0f, 1f)), radius = 7f, center = c)
        }
    }
}

@Composable
private fun ProcessingStep(prefs: Prefs, profile: Profile, store: Store, onFinished: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val accentName = ACCENTS.firstOrNull { it.second == prefs.accent }?.first ?: "Pebble"
    val labels = listOf(
        "Saving your profile" to (profile.name.ifBlank { "Guest" }),
        "Applying your theme" to listOf("System", "Light", "Dark")[prefs.theme.coerceIn(0, 2)],
        "Painting your accent color" to (if (prefs.dynamic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) "Material You" else accentName),
        "Arranging your library" to (if (prefs.layout == 1) "List view" else "Grid view"),
        "Tuning playback" to listOf(if (prefs.autoplay) "Autoplay" else "", if (prefs.autoFs) "Auto rotate" else "", if (prefs.resume) "Resume" else "").filter { it.isNotEmpty() }.joinToString(" · ").ifEmpty { "Defaults" },
        "Getting everything ready" to "Ready to go",
    )
    var done by remember { mutableIntStateOf(0) }
    var finished by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        store.saveProfile(profile)
        delay(700)
        for (i in labels.indices) { delay(800); done = i + 1 }
        delay(400); finished = true
        delay(2300); onFinished()
    }
    val progress by animateFloatAsState(done / labels.size.toFloat(), MaterialTheme.motionScheme.slowSpatialSpec(), label = "p")
    Box(Modifier.fillMaxWidth().heightIn(min = 600.dp), contentAlignment = Alignment.Center) {
        PAnim(!finished, exit = fadeOut() + androidx.compose.animation.scaleOut(targetScale = 0.9f)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(168.dp), contentAlignment = Alignment.Center) {
                    CircularWavyProgressIndicator(progress = { progress }, modifier = Modifier.size(168.dp))
                    Avatar(profile, 96.dp)
                }
                Spacer(Modifier.height(14.dp))
                Text("Setting things up", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Text("${(progress * 100).toInt()}%", style = MaterialTheme.typography.titleMedium, color = cs.primary, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(18.dp))
                Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    labels.forEachIndexed { i, (t, v) ->
                        val on = i < done
                        val cur = i == done
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            val bgc by androidx.compose.animation.animateColorAsState(if (on) cs.primary else cs.surfaceContainerHighest, MaterialTheme.motionScheme.defaultEffectsSpec(), label = "chk")
                            Box(Modifier.size(30.dp).clip(CircleShape).background(bgc), contentAlignment = Alignment.Center) {
                                PAnim(on, enter = scaleIn(MaterialTheme.motionScheme.fastSpatialSpec()) + fadeIn(), exit = fadeOut()) {
                                    Icon("check", null, Modifier.size(18.dp), tint = cs.onPrimary)
                                }
                                PAnim(cur && !on, enter = fadeIn(), exit = fadeOut()) { LoadingIndicator(Modifier.size(26.dp)) }
                            }
                            val ty by animateDpAsState(if (on) (-10).dp else 0.dp, MaterialTheme.motionScheme.defaultSpatialSpec(), label = "ty")
                            val sa by animateFloatAsState(if (on) 1f else 0f, MaterialTheme.motionScheme.defaultEffectsSpec(), label = "sub")
                            Box(Modifier.weight(1f).height(40.dp), contentAlignment = Alignment.CenterStart) {
                                Text(t, fontWeight = FontWeight.SemiBold, color = if (on || cur) cs.onSurface else cs.onSurfaceVariant.copy(alpha = 0.6f), modifier = Modifier.offset(y = ty))
                                Text(v, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, modifier = Modifier.offset(y = 11.dp).graphicsLayer { alpha = sa })
                            }
                        }
                    }
                }
            }
        }
        PAnim(finished, enter = fadeIn() + scaleIn(initialScale = 0.7f, animationSpec = MaterialTheme.motionScheme.slowSpatialSpec())) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(180.dp), contentAlignment = Alignment.Center) {
                    Confetti(true, Modifier.fillMaxSize())
                    Box(Modifier.size(112.dp).clip(CircleShape).background(cs.primary), contentAlignment = Alignment.Center) {
                        Icon("check", null, Modifier.size(60.dp), tint = cs.onPrimary)
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text(if (profile.name.isBlank()) "You're all set" else "You're all set, ${profile.name}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                Text("Enjoy Pebble.", color = cs.onSurfaceVariant)
            }
        }
    }
}
