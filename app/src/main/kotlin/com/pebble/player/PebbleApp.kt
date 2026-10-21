package com.pebble.player

import android.app.Application
import com.pebble.player.data.MediaRepo
import com.pebble.player.data.Store
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class PebbleApp : Application() {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val store by lazy { Store(this) }
    val media by lazy { MediaRepo(this) }
}
