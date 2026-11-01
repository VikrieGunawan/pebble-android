package com.pebble.player

import android.os.Build
import android.content.res.Configuration
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.pebble.player.ui.PebbleRoot

class MainActivity : ComponentActivity() {
    private var openUri by mutableStateOf<Uri?>(null)

    // System "Bold text" would add +300 to every weight; cap it so text stays readable but not extremely thick.
    override fun attachBaseContext(newBase: Context) {
        val c = Configuration(newBase.resources.configuration)
        if (Build.VERSION.SDK_INT >= 31 && c.fontWeightAdjustment != Configuration.FONT_WEIGHT_ADJUSTMENT_UNDEFINED && c.fontWeightAdjustment > 100) c.fontWeightAdjustment = 100
        super.attachBaseContext(newBase.createConfigurationContext(c))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        openUri = intent?.takeIf { it.action == Intent.ACTION_VIEW }?.data
        val app = application as PebbleApp
        setContent { PebbleRoot(app, openUri) { openUri = null } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.action == Intent.ACTION_VIEW) openUri = intent.data
    }
}
