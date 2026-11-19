package com.instagrrm.app

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.util.LruCache
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

data class Thumb(val bmp: Bitmap?, val ms: Long)

object Thumbs {
    private val cache = LruCache<String, Thumb>(300)
    private val gate = Semaphore(3)
    suspend fun get(ctx: Context, m: Media): Thumb {
        cache.get(m.uri)?.let { return it }
        return gate.withPermit {
            withContext(Dispatchers.IO) {
                val r = MediaMetadataRetriever()
                try {
                    r.setDataSource(ctx, Uri.parse(m.uri))
                    val ms = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                    val bmp = if (m.audio) null else r.getFrameAtTime(1_000_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)?.let {
                        Bitmap.createScaledBitmap(it, 270, (it.height * 270f / it.width).toInt().coerceAtLeast(1), true)
                    }
                    Thumb(bmp, ms).also { cache.put(m.uri, it) }
                } catch (e: Exception) {
                    Thumb(null, 0L)
                } finally {
                    try { r.release() } catch (e: Exception) { }
                }
            }
        }
    }
    fun clear() = cache.evictAll()
}

fun fmt(ms: Long): String {
    val s = ms / 1000
    val h = s / 3600
    val m = (s % 3600) / 60
    val x = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, x) else "%d:%02d".format(m, x)
}

fun gb(b: Long) = "%.1f GB".format(b / 1_073_741_824.0)
fun sizeText(b: Long) = if (b >= 1_073_741_824L) gb(b) else "%d MB".format(b / 1_048_576L)

class DelPlan(val trash: List<Uri>, val perm: List<Media>, val total: Int)

/** Share, Move and Delete shared by the gallery and the Storage screen. */
class FileOps(val ctx: Context, val scope: CoroutineScope, val onDone: () -> Unit) {
    var moveItems by mutableStateOf<List<Media>?>(null)
    var plan by mutableStateOf<DelPlan?>(null)
    var busy by mutableStateOf(false)
    lateinit var pickTree: ManagedActivityResultLauncher<Uri?, Uri?>
    lateinit var trash: ManagedActivityResultLauncher<IntentSenderRequest, ActivityResult>

    private fun toast(t: String) = Toast.makeText(ctx, t, Toast.LENGTH_LONG).show()

    fun share(items: List<Media>) {
        if (items.isEmpty()) return
        val uris = ArrayList(items.map { Uri.parse(it.uri) })
        val i = if (uris.size == 1) Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris[0])
        else Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
        i.setType("*/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        ctx.startActivity(Intent.createChooser(i, "Share"))
    }

    fun move(items: List<Media>) {
        if (items.isEmpty()) return
        moveItems = items
        pickTree.launch(null)
    }

    fun doMove(dest: Uri) {
        val items = moveItems ?: return
        moveItems = null
        busy = true
        scope.launch {
            val res = withContext(Dispatchers.IO) {
                var ok = 0
                var fail = 0
                val target = DocumentsContract.buildDocumentUriUsingTree(dest, DocumentsContract.getTreeDocumentId(dest))
                items.forEach { m ->
                    val src = Uri.parse(m.uri)
                    val moved = try {
                        DocumentsContract.moveDocument(ctx.contentResolver, src, Uri.parse(m.parent), target) != null
                    } catch (e: Exception) { false }
                    if (moved || copyThenDelete(src, m.name, target)) ok++ else fail++
                }
                ok to fail
            }
            busy = false
            toast("Moved ${res.first}" + if (res.second > 0) ", ${res.second} failed" else "")
            onDone()
        }
    }

    private fun copyThenDelete(src: Uri, name: String, target: Uri): Boolean {
        return try {
            val type = ctx.contentResolver.getType(src) ?: "application/octet-stream"
            val out = DocumentsContract.createDocument(ctx.contentResolver, target, type, name) ?: return false
            ctx.contentResolver.openInputStream(src)!!.use { i ->
                ctx.contentResolver.openOutputStream(out)!!.use { o -> i.copyTo(o) }
            }
            DocumentsContract.deleteDocument(ctx.contentResolver, src)
        } catch (e: Exception) { false }
    }

    fun delete(items: List<Media>) {
        if (items.isEmpty()) return
        scope.launch {
            plan = withContext(Dispatchers.IO) {
                val t = mutableListOf<Uri>()
                val perm = mutableListOf<Media>()
                items.forEach { m ->
                    val mu = if (Build.VERSION.SDK_INT >= 31) {
                        try { MediaStore.getMediaUri(ctx, Uri.parse(m.uri)) } catch (e: Exception) { null }
                    } else null
                    if (mu != null) t.add(mu) else perm.add(m)
                }
                DelPlan(t, perm, items.size)
            }
        }
    }

    fun confirm(p: DelPlan) {
        plan = null
        scope.launch {
            if (p.perm.isNotEmpty()) withContext(Dispatchers.IO) {
                p.perm.forEach { try { DocumentsContract.deleteDocument(ctx.contentResolver, Uri.parse(it.uri)) } catch (e: Exception) { } }
            }
            if (p.trash.isNotEmpty() && Build.VERSION.SDK_INT >= 30) {
                val pi = MediaStore.createTrashRequest(ctx.contentResolver, p.trash, true)
                trash.launch(IntentSenderRequest.Builder(pi.intentSender).build())
            } else onDone()
        }
    }
}

@Composable
fun rememberFileOps(onDone: () -> Unit): FileOps {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val done by rememberUpdatedState(onDone)
    val ops = remember { FileOps(ctx, scope) { done() } }
    ops.pickTree = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) ops.doMove(uri) else ops.moveItems = null
    }
    ops.trash = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { ops.onDone() }
    return ops
}

@Composable
fun FileOps.Dialogs() {
    plan?.let { p ->
        AlertDialog(
            onDismissRequest = { plan = null },
            title = { Text("Delete ${p.total} item" + if (p.total > 1) "s?" else "?") },
            text = {
                Text(buildString {
                    if (p.trash.isNotEmpty()) append("${p.trash.size} will go to the recycle bin and can be recovered. ")
                    if (p.perm.isNotEmpty()) append("${p.perm.size} cannot go to the recycle bin and will be deleted permanently.")
                })
            },
            confirmButton = { TextButton({ confirm(p) }) { Text("Delete", color = Danger) } },
            dismissButton = { TextButton({ plan = null }) { Text("Cancel") } }
        )
    }
    if (busy) AlertDialog(
        onDismissRequest = {}, confirmButton = {},
        title = { Text("Moving files…") },
        text = { CircularProgressIndicator(color = Amber) }
    )
}

@Composable
fun ActionBtn(text: String, color: Color = Color(0xFFEEEEEE), onClick: () -> Unit) {
    Text(
        text, color = color, fontSize = 13.sp,
        modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(Color(0xFF2A2A2A)).clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GalleryScreen(folder: Folder, onPlay: (Sess) -> Unit, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf<List<Media>?>(null) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    fun reload() {
        scope.launch {
            items = withContext(Dispatchers.IO) { listMedia(ctx, folder.uri) }
            selected = emptySet()
        }
    }
    LaunchedEffect(folder.uri) { reload() }
    val ops = rememberFileOps { reload() }
    ops.Dialogs()
    BackHandler(enabled = selected.isNotEmpty()) { selected = emptySet() }

    val list = items
    val selecting = selected.isNotEmpty()
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                if (selecting) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("✕", fontSize = 22.sp, modifier = Modifier.clickable { selected = emptySet() }.padding(end = 14.dp, top = 8.dp, bottom = 8.dp))
                        Text("${selected.size} selected of ${list?.size ?: 0}", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                    Text("Select all", color = Amber, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { selected = list.orEmpty().map { it.uri }.toSet() }.padding(8.dp))
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("←", fontSize = 24.sp, modifier = Modifier.clickable(onClick = onBack).padding(end = 14.dp, top = 8.dp, bottom = 8.dp))
                        Text(folder.name, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                    Text("${list?.size ?: 0} items", color = Muted, fontSize = 13.sp)
                }
            }
            Spacer(Modifier.height(8.dp))
            when {
                list == null -> Text("Loading…", color = Muted)
                list.isEmpty() -> Text("No videos or MP3 files in this folder.", color = Muted)
                else -> LazyVerticalGrid(
                    GridCells.Fixed(3),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    contentPadding = PaddingValues(bottom = if (selecting) 96.dp else 16.dp)
                ) {
                    itemsIndexed(list, key = { _, m -> m.uri }) { i, m ->
                        val sel = m.uri in selected
                        Cell(m, sel, selecting,
                            onTap = {
                                if (selecting) selected = if (sel) selected - m.uri else selected + m.uri
                                else onPlay(Sess(folder.name, list, folder.loopEach, false, i, true))
                            },
                            onLong = { selected = if (sel) selected - m.uri else selected + m.uri })
                    }
                }
            }
        }
        if (selecting) Row(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(CardBg).padding(16.dp),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            val chosen = list.orEmpty().filter { it.uri in selected }
            ActionBtn("Share") { ops.share(chosen) }
            ActionBtn("Move") { ops.move(chosen) }
            ActionBtn("Delete", Danger) { ops.delete(chosen) }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Cell(m: Media, sel: Boolean, selecting: Boolean, onTap: () -> Unit, onLong: () -> Unit) {
    val ctx = LocalContext.current
    val th by produceState<Thumb?>(null, m.uri) { value = Thumbs.get(ctx, m) }
    Box(
        Modifier.aspectRatio(0.75f).clip(RoundedCornerShape(8.dp))
            .background(if (m.audio) Color(0xFF2A2410) else Color(0xFF2A2A2A))
            .combinedClickable(onClick = onTap, onLongClick = onLong)
    ) {
        val b = th?.bmp
        if (b != null) Image(b.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        if (m.audio) Text("♪", Modifier.align(Alignment.Center), color = Amber, fontSize = 36.sp)
        th?.let {
            Text(
                fmt(it.ms), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.align(Alignment.BottomEnd).padding(5.dp)
                    .background(Color(0xA6000000), RoundedCornerShape(6.dp)).padding(horizontal = 5.dp, vertical = 1.dp)
            )
        }
        if (sel) Box(Modifier.fillMaxSize().background(Color(0x99121212)))
        if (selecting) Box(
            Modifier.align(Alignment.TopStart).padding(6.dp).size(22.dp).clip(CircleShape)
                .background(if (sel) Amber else Color.Transparent)
                .border(2.dp, if (sel) Amber else Color.White, CircleShape),
            contentAlignment = Alignment.Center
        ) { if (sel) Text("✓", color = Bg, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
    }
}
