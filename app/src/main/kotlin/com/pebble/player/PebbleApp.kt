package com.pebble.player

import android.app.Application
import android.util.Log
import com.pebble.player.data.MediaRepo
import com.pebble.player.data.Store
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File

class PebbleApp : Application() {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val store by lazy { Store(this) }
    val media by lazy { MediaRepo(this) }
    val crashFile get() = File(filesDir, "last_crash.txt")

    override fun onCreate() {
        super.onCreate()
        // Save the stack trace of a crash so it can be shown (and copied) on the next launch.
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try { crashFile.writeText("Thread: ${t.name}\n" + Log.getStackTraceString(e)) } catch (_: Throwable) { }
            prev?.uncaughtException(t, e)
        }
    }
}
