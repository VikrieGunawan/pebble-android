package com.pebble.player

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.pebble.player.ui.CrashScreen
import com.pebble.player.ui.PebbleRoot

class MainActivity : ComponentActivity() {
    private var openUri by mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        openUri = intent?.takeIf { it.action == Intent.ACTION_VIEW }?.data
        val app = application as PebbleApp
        val crash = runCatching { app.crashFile.takeIf { it.exists() }?.readText() }.getOrNull()
        setContent {
            var report by remember { mutableStateOf(crash) }
            if (report != null) CrashScreen(report!!) { runCatching { app.crashFile.delete() }; report = null }
            else PebbleRoot(app, openUri) { openUri = null }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.action == Intent.ACTION_VIEW) openUri = intent.data
    }
}
