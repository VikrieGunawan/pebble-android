package com.pebble.player.data

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.ds by preferencesDataStore("pebble")

data class Profile(val name: String = "", val emoji: String = "", val color: Int = 0, val photo: String = "")
data class Prog(val pos: Long, val dur: Long, val ts: Long)

data class Prefs(
    val loaded: Boolean = false,
    val onboarded: Boolean = false,
    val theme: Int = 0,            // 0 system, 1 light, 2 dark
    val dynamic: Boolean = true,   // Material You
    val accent: Int = 0xFFE8502B.toInt(),
    val layout: Int = 0,           // 0 grid, 1 list
    val sort: Int = 1,             // 0 newest, 1 name, 2 longest
    val autoplay: Boolean = true,
    val resume: Boolean = true,
    val autoFs: Boolean = true,
    val skip: Int = 10,
    val speed: Float = 1f,
    val fit: Int = 0,              // 0 fit, 1 zoom, 2 stretch
    val orient: Int = 0,           // 0 auto, 1 portrait, 2 landscape
    val onLost: Int = 0,           // 0 pause, 1 keep playing
    val wake: Boolean = true,
    val amoled: Boolean = false,   // pure black in dark mode
    val gridSize: Int = 1,         // 0 compact, 1 normal, 2 large
    val profile: Profile = Profile(),
    val blacklist: Set<String> = emptySet(),
    val favs: Set<String> = emptySet(),
    val progress: Map<String, Prog> = emptyMap(),
)

object K {
    val onb = booleanPreferencesKey("onb")
    val theme = intPreferencesKey("theme")
    val dyn = booleanPreferencesKey("dyn")
    val accent = intPreferencesKey("accent")
    val layout = intPreferencesKey("layout")
    val sort = intPreferencesKey("sort")
    val autoplay = booleanPreferencesKey("autoplay")
    val resume = booleanPreferencesKey("resume")
    val autoFs = booleanPreferencesKey("autofs")
    val skip = intPreferencesKey("skip")
    val speed = floatPreferencesKey("speed")
    val fit = intPreferencesKey("fit")
    val orient = intPreferencesKey("orient")
    val onLost = intPreferencesKey("onlost")
    val wake = booleanPreferencesKey("wake")
    val amoled = booleanPreferencesKey("amoled")
    val gridSize = intPreferencesKey("gridsize")
    val pName = stringPreferencesKey("p_name")
    val pEmoji = stringPreferencesKey("p_emoji")
    val pColor = intPreferencesKey("p_color")
    val pPhoto = stringPreferencesKey("p_photo")
    val bl = stringSetPreferencesKey("bl")
    val favs = stringSetPreferencesKey("favs")
    val prog = stringPreferencesKey("prog")
}

class Store(context: Context) {
    private val ds = context.applicationContext.ds
    /** Phones default to List, tablets (smallest width >= 600dp) to Grid. */
    private val defLayout = if (context.resources.configuration.smallestScreenWidthDp >= 600) 0 else 1

    val prefs: Flow<Prefs> = ds.data.map { p ->
        Prefs(
            loaded = true,
            onboarded = p[K.onb] ?: false,
            theme = p[K.theme] ?: 0,
            dynamic = p[K.dyn] ?: true,
            accent = p[K.accent] ?: 0xFFE8502B.toInt(),
            layout = p[K.layout] ?: defLayout,
            sort = p[K.sort] ?: 1,
            autoplay = p[K.autoplay] ?: true,
            resume = p[K.resume] ?: true,
            autoFs = p[K.autoFs] ?: true,
            skip = p[K.skip] ?: 10,
            speed = p[K.speed] ?: 1f,
            fit = p[K.fit] ?: 0,
            orient = p[K.orient] ?: 0,
            onLost = p[K.onLost] ?: 0,
            wake = p[K.wake] ?: true,
            amoled = p[K.amoled] ?: false,
            gridSize = p[K.gridSize] ?: 1,
            profile = Profile(p[K.pName] ?: "", p[K.pEmoji] ?: "", p[K.pColor] ?: 0, p[K.pPhoto] ?: ""),
            blacklist = p[K.bl] ?: emptySet(),
            favs = p[K.favs] ?: emptySet(),
            progress = parseProg(p[K.prog]),
        )
    }

    suspend fun <T> put(key: Preferences.Key<T>, value: T) { ds.edit { it[key] = value } }
    suspend fun edit(block: (MutablePreferences) -> Unit) { ds.edit(block) }

    suspend fun saveProfile(p: Profile) = ds.edit {
        it[K.pName] = p.name; it[K.pEmoji] = p.emoji; it[K.pColor] = p.color; it[K.pPhoto] = p.photo
    }

    suspend fun saveProgress(uri: String, pos: Long, dur: Long) = ds.edit {
        val o = readJson(it[K.prog])
        o.put(uri, JSONArray().put(pos).put(dur).put(System.currentTimeMillis()))
        if (o.length() > 300) {
            val oldest = o.keys().asSequence().minByOrNull { k -> o.getJSONArray(k).getLong(2) }
            if (oldest != null) o.remove(oldest)
        }
        it[K.prog] = o.toString()
    }

    suspend fun toggleFav(uri: String) = ds.edit {
        val s = (it[K.favs] ?: emptySet()).toMutableSet()
        if (!s.add(uri)) s.remove(uri)
        it[K.favs] = s
    }

    suspend fun toggleBlacklist(path: String) = ds.edit {
        val s = (it[K.bl] ?: emptySet()).toMutableSet()
        if (!s.add(path)) s.remove(path)
        it[K.bl] = s
    }

    suspend fun removeProgress(uri: String) = ds.edit {
        val o = readJson(it[K.prog]); o.remove(uri); it[K.prog] = o.toString()
    }

    suspend fun clearHistory() = ds.edit { it.remove(K.prog) }
    suspend fun clearFavs() = ds.edit { it.remove(K.favs) }

    suspend fun resetSettings() = ds.edit {
        val keys = listOf<Preferences.Key<*>>(
            K.theme, K.dyn, K.accent, K.layout, K.sort, K.autoplay, K.resume, K.autoFs,
            K.skip, K.speed, K.fit, K.orient, K.onLost, K.wake, K.amoled, K.gridSize,
        )
        keys.forEach { k -> it.remove(k) }
    }

    suspend fun clearAll() = ds.edit { it.clear() }

    private fun readJson(s: String?): JSONObject = try { JSONObject(s ?: "{}") } catch (e: Exception) { JSONObject() }

    private fun parseProg(s: String?): Map<String, Prog> {
        if (s.isNullOrEmpty()) return emptyMap()
        return try {
            val o = JSONObject(s)
            o.keys().asSequence().associateWith { k ->
                val a = o.getJSONArray(k)
                Prog(a.getLong(0), a.getLong(1), a.getLong(2))
            }
        } catch (e: Exception) { emptyMap() }
    }
}
