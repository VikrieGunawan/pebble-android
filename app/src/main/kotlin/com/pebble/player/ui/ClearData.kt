package com.pebble.player.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pebble.player.PebbleApp
import kotlinx.coroutines.delay

/** Full-screen, step-by-step wipe. Hosted at the root so clearing the store never removes it. */
@Composable
fun ClearDataScreen(app: PebbleApp, onDone: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val steps = listOf("Clearing watch history", "Removing favorites", "Resetting settings", "Deleting profile photo", "Clearing cache", "Wiping app data")
    var done by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        delay(500)
        val ctx = app.applicationContext
        for (i in steps.indices) {
            when (i) {
                0 -> app.store.clearHistory()
                1 -> app.store.clearFavs()
                2 -> app.store.resetSettings()
                3 -> ctx.filesDir.listFiles { f -> f.name.startsWith("profile_") }?.forEach { it.delete() }
                4 -> { ThumbCache.clear(); ctx.cacheDir.deleteRecursively() }
                5 -> app.store.clearAll()
            }
            delay(750)
            done = i + 1
        }
        delay(700)
        onDone()
    }
    val progress by animateFloatAsState(done / steps.size.toFloat(), MaterialTheme.motionScheme.slowSpatialSpec(), label = "p")
    Column(Modifier.fillMaxSize().background(cs.background).statusBarsPadding().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        CircularWavyProgressIndicator(progress = { progress }, modifier = Modifier.size(140.dp))
        Spacer(Modifier.height(18.dp))
        Text("Clearing app data", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Text("${(progress * 100).toInt()}%", color = cs.primary, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(24.dp))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            steps.forEachIndexed { i, t ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Box(Modifier.size(28.dp).clip(CircleShape).background(if (i < done) cs.primary else cs.surfaceContainerHighest), contentAlignment = Alignment.Center) {
                        if (i < done) Icon("check", null, Modifier.size(17.dp), tint = cs.onPrimary)
                        else if (i == done) LoadingIndicator(Modifier.size(24.dp))
                    }
                    Text(t, color = if (i <= done) cs.onSurface else cs.onSurfaceVariant.copy(alpha = 0.6f))
                }
            }
        }
        AnimatedVisibility(done == steps.size) { Text("All clean. Taking you back to Welcome…", Modifier.padding(top = 24.dp), color = cs.onSurfaceVariant) }
    }
}
