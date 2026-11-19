package com.instagrrm.app

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.os.StatFs
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FolderStat(val total: Long, val videos: List<Media>)

fun storageLine(): String {
    val s = StatFs(Environment.getExternalStorageDirectory().path)
    return "${gb(s.totalBytes - s.availableBytes)} used · ${gb(s.availableBytes)} free"
}

/** Total size and every video inside a folder (including subfolders). Call off the main thread. */
fun scanFolder(ctx: Context, tree: String): FolderStat {
    var total = 0L
    val vids = mutableListOf<Media>()
    fun walk(d: DocumentFile, depth: Int) {
        for (f in d.listFiles()) {
            if (f.isDirectory) {
                if (depth < 4) walk(f, depth + 1)
            } else {
                val len = f.length()
                total += len
                if (f.type?.startsWith("video/") == true)
                    vids.add(Media(f.uri.toString(), f.name ?: "video", false, d.uri.toString(), len))
            }
        }
    }
    DocumentFile.fromTreeUri(ctx, Uri.parse(tree))?.let { walk(it, 0) }
    return FolderStat(total, vids)
}

fun clearTemp(ctx: Context): Long {
    var n = 0L
    listOfNotNull(ctx.cacheDir, ctx.externalCacheDir).forEach { d ->
        d.walkBottomUp().forEach { f ->
            if (f != d) { if (f.isFile) n += f.length(); f.delete() }
        }
    }
    return n
}

@Composable
fun StorageScreen(folders: List<Folder>, onPlay: (Sess) -> Unit, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val stats = remember { mutableStateMapOf<String, FolderStat>() }
    fun toast(t: String) = Toast.makeText(ctx, t, Toast.LENGTH_LONG).show()
    var round by remember { mutableIntStateOf(0) }
    LaunchedEffect(round) {
        stats.clear()
        folders.forEach { f -> stats[f.uri] = withContext(Dispatchers.IO) { scanFolder(ctx, f.uri) } }
    }
    val ops = rememberFileOps { round++ }
    ops.Dialogs()

    val line = remember(round) { storageLine() }
    val st = remember(round) { StatFs(Environment.getExternalStorageDirectory().path) }
    val usedFrac = ((st.totalBytes - st.availableBytes).toFloat() / st.totalBytes).coerceIn(0f, 1f)
    val biggest = stats.values.flatMap { it.videos }.sortedByDescending { it.size }.take(5)

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("←", fontSize = 24.sp, modifier = Modifier.clickable(onClick = onBack).padding(end = 14.dp, top = 8.dp, bottom = 8.dp))
            Text("Storage", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(8.dp))
        Card {
            FolderIcon(Modifier.padding(4.dp))
            Column(Modifier.weight(1f)) {
                Text(line, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xFF444444))) {
                    Box(Modifier.fillMaxWidth(usedFrac).height(4.dp).background(Amber))
                }
            }
        }
        Label("Folder sizes")
        if (folders.isEmpty()) Text("Add a folder on Home to see its size.", color = Muted)
        folders.forEach { f ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(f.name, Modifier.weight(1f), maxLines = 1)
                Text(stats[f.uri]?.let { sizeText(it.total) } ?: "…", color = Muted, fontSize = 13.sp)
            }
        }
        Label("Largest videos")
        if (biggest.isEmpty()) Text(if (stats.size < folders.size) "Scanning…" else "No videos found.", color = Muted)
        biggest.forEach { m ->
            Card(Modifier.padding(bottom = 8.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                    Text(m.name, maxLines = 1)
                    Text(sizeText(m.size), color = Muted, fontSize = 13.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ActionBtn("Play") { onPlay(Sess("Largest videos", listOf(m), true, true)) }
                        ActionBtn("Move") { ops.move(listOf(m)) }
                        ActionBtn("Delete", Danger) { ops.delete(listOf(m)) }
                        ActionBtn("Compress") { toast("Compression arrives in phase 3") }
                    }
                }
            }
        }
        Label("Cleaner")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionBtn("Clear temp files") {
                scope.launch { toast("Freed " + sizeText(withContext(Dispatchers.IO) { clearTemp(ctx) })) }
            }
            ActionBtn("Free memory") {
                Thumbs.clear(); System.gc()
                toast("Released memory used by Instagrrm")
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "Compress a folder… (opens file manager)", color = Bg, fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Amber)
                .clickable { toast("Compression arrives in phase 3") }.padding(14.dp)
        )
        Spacer(Modifier.height(24.dp))
    }
}
