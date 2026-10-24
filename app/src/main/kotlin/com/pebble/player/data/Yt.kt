package com.pebble.player.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** YouTube library + the server (localhost, tunnel...) that YouTube embeds are played from. */
object YtStore {
    data class Item(val id: String, val name: String, val by: String, val short: Boolean, val added: Long)

    var items by mutableStateOf<List<Item>>(emptyList()); private set
    var server by mutableStateOf(""); private set

    private fun sp(c: Context) = c.getSharedPreferences("pebble_yt", Context.MODE_PRIVATE)

    fun load(c: Context) {
        val p = sp(c)
        server = p.getString("server", "") ?: ""
        items = try {
            val a = JSONArray(p.getString("items", "[]"))
            (0 until a.length()).map { a.getJSONObject(it).let { o -> Item(o.getString("id"), o.getString("name"), o.optString("by"), o.optBoolean("short"), o.optLong("added")) } }
        } catch (_: Exception) { emptyList() }
    }

    private fun save(c: Context) {
        val a = JSONArray()
        items.forEach { a.put(JSONObject().put("id", it.id).put("name", it.name).put("by", it.by).put("short", it.short).put("added", it.added)) }
        sp(c).edit().putString("items", a.toString()).apply()
    }

    fun add(c: Context, i: Item) { if (items.none { it.id == i.id }) { items = items + i; save(c) } }
    fun has(id: String) = items.any { it.id == id }
    fun remove(c: Context, id: String) { items = items.filter { it.id != id }; save(c) }
    fun rename(c: Context, id: String, name: String) { items = items.map { if (it.id == id) it.copy(name = name) else it }; save(c) }

    fun setServer(c: Context, url: String) { server = normalize(url); sp(c).edit().putString("server", server).apply() }

    /** "localhost:8080" -> "http://localhost:8080"; no trailing slash. */
    fun normalize(s: String): String {
        val t = s.trim().trimEnd('/')
        if (t.isEmpty()) return ""
        return if (Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://").containsMatchIn(t)) t else "http://$t"
    }

    /** YouTube refuses embeds from bare IP addresses (127.x is fine). */
    fun isBadIp(url: String): Boolean {
        val h = try { Uri.parse(url).host ?: "" } catch (_: Exception) { "" }
        return Regex("^(\\d{1,3}\\.){3}\\d{1,3}$").matches(h) && !h.startsWith("127.")
    }

    fun origin(url: String): String = try {
        val u = Uri.parse(url)
        "${u.scheme}://${u.authority}"
    } catch (_: Exception) { url }

    /** Connected = the server answers (any HTTP status below 500). */
    suspend fun ping(url: String): Boolean = withContext(Dispatchers.IO) {
        if (url.isBlank()) return@withContext false
        try {
            val c = URL(url).openConnection() as HttpURLConnection
            c.connectTimeout = 4000
            c.readTimeout = 4000
            c.useCaches = false
            try { c.responseCode in 100..499 } finally { c.disconnect() }
        } catch (_: Exception) { false }
    }

    fun parseId(s: String): String? {
        val t = s.trim()
        if (t.isEmpty()) return null
        return try {
            val u = Uri.parse(if (Regex("^[a-z]+://", RegexOption.IGNORE_CASE).containsMatchIn(t)) t else "https://$t")
            val h = (u.host ?: "").removePrefix("www.").removePrefix("m.").removePrefix("music.")
            val seg = u.pathSegments
            when (h) {
                "youtu.be" -> seg.firstOrNull()?.takeIf { it.matches(ID) }
                "youtube.com", "youtube-nocookie.com" ->
                    u.getQueryParameter("v")?.takeIf { it.matches(ID) }
                        ?: if (seg.size >= 2 && seg[0] in listOf("shorts", "embed", "live", "v")) seg[1].takeIf { it.matches(ID) } else null
                else -> null
            }
        } catch (_: Exception) { null }
    }
    private val ID = Regex("^[\\w-]{11}$")

    fun isShortLink(s: String) = s.contains("/shorts/", true)

    /** Title + channel from YouTube's public oEmbed. */
    suspend fun oembed(id: String, short: Boolean): Triple<String, String, Boolean>? = withContext(Dispatchers.IO) {
        try {
            val page = if (short) "https://www.youtube.com/shorts/$id" else "https://www.youtube.com/watch?v=$id"
            val c = URL("https://www.youtube.com/oembed?format=json&url=" + Uri.encode(page)).openConnection() as HttpURLConnection
            c.connectTimeout = 7000
            c.readTimeout = 7000
            try {
                if (c.responseCode != 200) return@withContext null
                val j = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
                val w = j.optInt("width"); val h = j.optInt("height")
                Triple(j.optString("title", "YouTube video"), j.optString("author_name"), short || (w > 0 && h > 0 && w.toFloat() / h < 0.62f))
            } finally { c.disconnect() }
        } catch (_: Exception) { null }
    }

    suspend fun thumb(id: String): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val c = URL("https://i.ytimg.com/vi/$id/mqdefault.jpg").openConnection() as HttpURLConnection
            c.connectTimeout = 7000
            c.readTimeout = 7000
            try { c.inputStream.use { BitmapFactory.decodeStream(it) } } finally { c.disconnect() }
        } catch (_: Exception) { null }
    }

    fun toVideo(i: Item): Video = Video(
        id = -((i.id.hashCode().toLong() and 0x7fffffffL) + 1),
        uri = Uri.parse("yt://${i.id}"), name = i.name, folder = if (i.short) "Shorts" else "YouTube",
        path = if (i.short) "Shorts/" else "YouTube/", dur = 0, size = 0, added = i.added, ext = "YouTube",
    )

    fun idOf(v: Video): String = v.uri.host ?: ""
}
