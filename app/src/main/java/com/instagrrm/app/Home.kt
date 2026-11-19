package com.instagrrm.app

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun Card(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(CardBg).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        content = content
    )
}

@Composable
fun Label(t: String) = Text(
    t.uppercase(), color = Muted, fontSize = 12.sp, letterSpacing = 1.sp,
    modifier = Modifier.padding(start = 4.dp, top = 14.dp, bottom = 8.dp)
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(onStart: (Sess, Int) -> Unit, onGallery: (Folder) -> Unit, onStorage: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var folders by remember { mutableStateOf(Store.folders) }
    fun save(l: List<Folder>) { folders = l; Store.folders = l }
    fun toast(t: String) = Toast.makeText(ctx, t, Toast.LENGTH_SHORT).show()
    fun upd(f: Folder, n: Folder) = save(folders.map { if (it.uri == f.uri) n else it })

    var edit by remember { mutableStateOf(false) }
    var singleSleep by remember { mutableStateOf(Store.singleSleep) }
    var link by remember { mutableStateOf("") }
    var nameDialog by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var pending by remember { mutableStateOf<Sess?>(null) }

    fun begin(s: Sess, sleep: Boolean) { if (sleep) pending = s else onStart(s, 0) }
    fun play(f: Folder) {
        scope.launch {
            val items = withContext(Dispatchers.IO) { listMedia(ctx, f.uri) }
            if (items.isEmpty()) toast("No videos or MP3 files found in ${f.name}")
            else begin(Sess(f.name, if (f.shuffle) items.shuffled() else items, f.loopEach), f.sleep)
        }
    }

    val treePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            try {
                ctx.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (e: Exception) { }
            val n = newName.ifBlank { DocumentFile.fromTreeUri(ctx, uri)?.name ?: "Folder" }
            save(folders + Folder(n, uri.toString()))
        }
    }
    val singlePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: Exception) { }
            val name = DocumentFile.fromSingleUri(ctx, uri)?.name ?: "Video"
            begin(Sess("Single video", listOf(Media(uri.toString(), name, false)), true, true), singleSleep)
        }
    }

    if (nameDialog) AlertDialog(
        onDismissRequest = { nameDialog = false },
        title = { Text("Folder name") },
        text = { OutlinedTextField(newName, { newName = it }, singleLine = true, placeholder = { Text("e.g. Reels_wisdom") }) },
        confirmButton = { TextButton({ nameDialog = false; treePicker.launch(null) }) { Text("Choose folder") } },
        dismissButton = { TextButton({ nameDialog = false }) { Text("Cancel") } }
    )
    pending?.let { s ->
        AlertDialog(
            onDismissRequest = { pending = null },
            confirmButton = {},
            title = { Text("Sleep timer") },
            text = {
                Column {
                    listOf(15, 30, 45, 60, 90).forEach { m ->
                        TextButton({ Store.sleepMinutes = m; pending = null; onStart(s, m) }) {
                            Text("$m minutes", color = if (m == Store.sleepMinutes) Amber else Color.White)
                        }
                    }
                }
            }
        )
    }

    if (edit) {
        EditFolders(folders, { save(it) }, { edit = false })
        return
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                IgIcon(); Text("Instagrrm", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
            Box(Modifier.clickable { toast("Recent links arrive in phase 4") }.padding(8.dp)) { LinkIcon() }
        }
        Spacer(Modifier.height(8.dp))
        Card(Modifier.padding(bottom = 8.dp)) {
            Box(Modifier.weight(1f).padding(start = 6.dp)) {
                if (link.isEmpty()) Text("Paste YouTube or Instagram link", color = Muted, fontSize = 14.sp)
                BasicTextField(link, { link = it }, singleLine = true, textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 14.sp), cursorBrush = SolidColor(Amber))
            }
            Ring("↓", Amber, true, "Download") { toast("Downloading arrives in phase 4") }
        }
        Card(Modifier.padding(bottom = 8.dp)) {
            Ring("▶", Amber, true, "Play single video") { singlePicker.launch(arrayOf("video/*")) }
            Text("Single video", Modifier.weight(1f), fontWeight = FontWeight.Bold)
            Ring("☾", Blue, singleSleep, "Sleep timer") { singleSleep = !singleSleep; Store.singleSleep = singleSleep }
        }
        Label("Folders")
        folders.forEach { f ->
            Card(Modifier.padding(bottom = 8.dp)) {
                Box(Modifier.clickable { onGallery(f) }.padding(4.dp)) { FolderIcon() }
                Text(
                    f.name, Modifier.weight(1f).combinedClickable(onClick = { play(f) }, onLongClick = { edit = true }).padding(vertical = 10.dp),
                    maxLines = 1
                )
                Ring("⇄", Pink, f.shuffle, "Shuffle") { upd(f, f.copy(shuffle = !f.shuffle)) }
                Ring("↕", Amber, f.loopEach, "Scroll mode: filled means loop each, empty means auto-scroll") {
                    upd(f, f.copy(loopEach = !f.loopEach))
                    toast(if (!f.loopEach) "Loop each video until you scroll" else "Auto-scroll to the next video")
                }
                Ring("☾", Blue, f.sleep, "Sleep timer") { upd(f, f.copy(sleep = !f.sleep)) }
            }
        }
        Text(
            "+ Add new folder", color = Amber, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                .border(1.dp, Color(0xFF555555), RoundedCornerShape(16.dp))
                .clickable { newName = ""; nameDialog = true }.padding(16.dp)
        )
        Card(Modifier.padding(bottom = 8.dp).clickable { onStorage() }) {
            FolderIcon(Modifier.padding(4.dp))
            Column(Modifier.weight(1f)) {
                Text("Storage", fontWeight = FontWeight.Bold)
                Text(remember { storageLine() }, color = Muted, fontSize = 13.sp)
            }
            Text("›", color = Muted)
        }
        Label("Instagram accounts")
        Card(Modifier.clickable { toast("Account setup arrives in phase 4") }) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                listOf("Account 1", "Account 2", "Account 3").forEach { Text("$it  ·  Not set", color = Muted) }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun EditFolders(folders: List<Folder>, onChange: (List<Folder>) -> Unit, onDone: () -> Unit) {
    val rowH = with(LocalDensity.current) { 64.dp.toPx() }
    var list by remember { mutableStateOf(folders) }
    var drag by remember { mutableIntStateOf(-1) }
    var off by remember { mutableFloatStateOf(0f) }
    var listTop by remember { mutableFloatStateOf(0f) }
    var barTop by remember { mutableFloatStateOf(Float.MAX_VALUE) }
    var over by remember { mutableStateOf(false) }

    fun swap(a: Int, b: Int) {
        val m = list.toMutableList()
        val t = m[a]; m[a] = m[b]; m[b] = t
        list = m; onChange(m)
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Edit folders", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                TextButton(onDone) { Text("Done", color = Amber, fontWeight = FontWeight.Bold) }
            }
            Label("Drag to re-order")
            Column(Modifier.onGloballyPositioned { listTop = it.positionInRoot().y }) {
                list.forEachIndexed { _, f ->
                    key(f.uri) {
                        val dragging = drag >= 0 && drag < list.size && list[drag].uri == f.uri
                        Box(
                            Modifier.fillMaxWidth().height(64.dp).padding(bottom = 8.dp)
                                .zIndex(if (dragging) 1f else 0f)
                                .pointerInput(f.uri) {
                                    detectDragGestures(
                                        onDragStart = { drag = list.indexOfFirst { it.uri == f.uri }; off = 0f },
                                        onDragEnd = {
                                            if (over && drag >= 0) {
                                                list = list.filterIndexed { i, _ -> i != drag }
                                                onChange(list)
                                            }
                                            drag = -1; off = 0f; over = false
                                        },
                                        onDragCancel = { drag = -1; off = 0f; over = false }
                                    ) { change, d ->
                                        change.consume()
                                        off += d.y
                                        var i = drag
                                        while (i >= 0 && off > rowH / 2 && i < list.size - 1) { swap(i, i + 1); i++; off -= rowH }
                                        while (i >= 0 && off < -rowH / 2 && i > 0) { swap(i, i - 1); i--; off += rowH }
                                        drag = i
                                        over = listTop + i * rowH + off + rowH / 2 > barTop
                                    }
                                }
                                .graphicsLayer { translationY = if (dragging) off else 0f }
                        ) {
                            Row(
                                Modifier.fillMaxSize()
                                    .then(if (dragging) Modifier.shadow(12.dp, RoundedCornerShape(16.dp)) else Modifier)
                                    .clip(RoundedCornerShape(16.dp)).background(CardBg)
                                    .then(if (dragging) Modifier.border(1.dp, Amber, RoundedCornerShape(16.dp)) else Modifier)
                                    .padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                FolderIcon(Modifier.padding(4.dp))
                                Text(f.name, Modifier.weight(1f), maxLines = 1)
                                Text("≡", color = Muted, fontSize = 24.sp)
                            }
                        }
                    }
                }
            }
        }
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(110.dp)
                .background(if (over) Color(0xFF5A2222) else Color(0xFF2A1A1A))
                .onGloballyPositioned { barTop = it.positionInRoot().y },
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Drag a folder here to remove it", color = Danger, fontWeight = FontWeight.Bold)
            Text("Files on the phone are not deleted", color = Muted, fontSize = 13.sp)
        }
    }
}
