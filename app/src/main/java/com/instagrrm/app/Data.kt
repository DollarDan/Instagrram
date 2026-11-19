package com.instagrrm.app

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import org.json.JSONArray
import org.json.JSONObject

data class Media(val uri: String, val name: String, val audio: Boolean, val parent: String = "", val size: Long = 0L)
data class Folder(
    val name: String,
    val uri: String,
    val shuffle: Boolean = false,
    val loopEach: Boolean = false, // false = Auto-scroll (default), true = Loop each
    val sleep: Boolean = false
)
data class Sess(
    val title: String,
    val items: List<Media>,
    val loopEach: Boolean,
    val single: Boolean = false,
    val start: Int = 0,
    val startLoop: Boolean = false
)

object Store {
    private lateinit var sp: SharedPreferences
    fun init(c: Context) {
        if (!::sp.isInitialized) sp = c.getSharedPreferences("instagrrm", Context.MODE_PRIVATE)
    }

    var folders: List<Folder>
        get() {
            val a = JSONArray(sp.getString("folders", "[]"))
            return (0 until a.length()).map {
                val o = a.getJSONObject(it)
                Folder(
                    o.getString("name"), o.getString("uri"),
                    o.optBoolean("shuffle"), o.optBoolean("loopEach"), o.optBoolean("sleep")
                )
            }
        }
        set(v) {
            val a = JSONArray()
            v.forEach {
                a.put(
                    JSONObject().put("name", it.name).put("uri", it.uri).put("shuffle", it.shuffle)
                        .put("loopEach", it.loopEach).put("sleep", it.sleep)
                )
            }
            sp.edit().putString("folders", a.toString()).apply()
        }

    var singleSleep: Boolean
        get() = sp.getBoolean("singleSleep", false)
        set(v) { sp.edit().putBoolean("singleSleep", v).apply() }

    var sleepMinutes: Int
        get() = sp.getInt("sleepMinutes", 30)
        set(v) { sp.edit().putInt("sleepMinutes", v).apply() }
}

/** Videos and MP3 files directly inside a folder the user added (call off the main thread). */
fun listMedia(ctx: Context, treeUri: String): List<Media> {
    val dir = DocumentFile.fromTreeUri(ctx, Uri.parse(treeUri)) ?: return emptyList()
    return dir.listFiles().filter { f ->
        f.isFile && (f.type?.startsWith("video/") == true || f.type?.startsWith("audio/") == true ||
            f.name?.endsWith(".mp3", true) == true)
    }.sortedBy { it.name?.lowercase() ?: "" }.map { f ->
        val n = f.name ?: "Media"
        Media(f.uri.toString(), n, f.type?.startsWith("audio/") == true || n.endsWith(".mp3", true), dir.uri.toString(), f.length())
    }
}
