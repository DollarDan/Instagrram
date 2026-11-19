package com.instagrrm.app

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** One ExoPlayer shared by the screen and the notification service. */
object PlayerHolder {
    var session: Sess? = null
    private var p: ExoPlayer? = null
    private val h = Handler(Looper.getMainLooper())

    fun player(c: Context): ExoPlayer {
        p?.let { return it }
        val e = ExoPlayer.Builder(c.applicationContext)
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
        p = e
        return e
    }

    fun start(c: Context, s: Sess, sleepMinutes: Int) {
        val pl = player(c)
        session = s
        pl.setMediaItems(s.items.map {
            MediaItem.Builder().setUri(it.uri).setMediaMetadata(
                MediaMetadata.Builder().setTitle(it.name.substringBeforeLast('.')).setArtist(s.title).build()
            ).build()
        }, s.start, C.TIME_UNSET)
        pl.repeatMode = if (s.loopEach || s.startLoop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_ALL
        pl.prepare()
        pl.playWhenReady = true
        sleep(sleepMinutes)
    }

    fun sleep(minutes: Int) {
        h.removeCallbacksAndMessages(null)
        if (minutes > 0) h.postDelayed({ p?.pause() }, minutes * 60_000L)
    }

    fun next() = p?.let { val n = it.mediaItemCount; if (n > 0) it.seekTo((it.currentMediaItemIndex + 1) % n, C.TIME_UNSET) }
    fun prev() = p?.let { val n = it.mediaItemCount; if (n > 0) it.seekTo((it.currentMediaItemIndex - 1 + n) % n, C.TIME_UNSET) }

    fun stop() {
        h.removeCallbacksAndMessages(null)
        p?.let { it.stop(); it.clearMediaItems() }
        session = null
    }
}

/** Makes notification next/previous always move to another item, even in Loop-each mode. */
class LoopPlayer(p: Player) : ForwardingPlayer(p) {
    override fun seekToNext() = seekToNextMediaItem()
    override fun seekToNextMediaItem() { PlayerHolder.next() }
    override fun seekToPrevious() = seekToPreviousMediaItem()
    override fun seekToPreviousMediaItem() { PlayerHolder.prev() }
}

class PlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        mediaSession = MediaSession.Builder(this, LoopPlayer(PlayerHolder.player(this)))
            .setSessionActivity(open).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        PlayerHolder.stop()
        stopSelf()
    }

    override fun onDestroy() {
        mediaSession?.release()
        mediaSession = null
        super.onDestroy()
    }
}

@Composable
fun PlayerScreen(s: Sess, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val player = remember { PlayerHolder.player(ctx) }
    val base = if (s.loopEach) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_ALL
    var index by remember { mutableIntStateOf(player.currentMediaItemIndex) }
    var loopCur by remember { mutableStateOf(player.repeatMode == Player.REPEAT_MODE_ONE) }
    var progress by remember { mutableFloatStateOf(0f) }

    DisposableEffect(player) {
        val l = object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                index = player.currentMediaItemIndex
                if (reason != Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT) {
                    player.repeatMode = base // next video starts with the folder's chosen mode
                    loopCur = base == Player.REPEAT_MODE_ONE
                }
            }
        }
        player.addListener(l)
        onDispose { player.removeListener(l) }
    }
    LaunchedEffect(player) {
        while (true) {
            val d = player.duration
            progress = if (d > 0 && d != C.TIME_UNSET) (player.currentPosition.toFloat() / d).coerceIn(0f, 1f) else 0f
            delay(250)
        }
    }

    val item = s.items.getOrNull(index)
    Box(
        Modifier.fillMaxSize().background(Color.Black)
            .pointerInput(Unit) { detectTapGestures { player.playWhenReady = !player.playWhenReady } }
            .pointerInput(Unit) {
                var total = 0f
                detectVerticalDragGestures(
                    onDragStart = { total = 0f },
                    onDragEnd = {
                        if (total < -120f) PlayerHolder.next() else if (total > 120f) PlayerHolder.prev()
                    }
                ) { change, dy -> total += dy; change.consume() }
            }
    ) {
        AndroidView(
            factory = { c -> PlayerView(c).apply { useController = false; this.player = PlayerHolder.player(c); keepScreenOn = true } },
            modifier = Modifier.fillMaxSize()
        )
        if (item?.audio == true) Cover(item)

        Text(
            "←", color = Color.White, fontSize = 28.sp,
            modifier = Modifier.align(Alignment.TopStart).padding(12.dp).clickable(onClick = onBack).padding(8.dp)
        )

        Row(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 28.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(Modifier.width(210.dp)) {
                Text(s.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("${index + 1} of ${s.items.size}", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp, bottom = 8.dp))
                Box(Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(3.dp)).background(Color(0xFF444444))) {
                    Box(Modifier.fillMaxWidth(progress).height(3.dp).background(Amber))
                }
            }
            if (!s.single) {
                val scrollOn = !loopCur
                val c = if (scrollOn) Amber else Muted
                Text(
                    "↕ Scroll " + if (scrollOn) "ON" else "OFF",
                    color = c, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(Color(0xB3121212))
                        .border(2.dp, c, RoundedCornerShape(50))
                        .clickable {
                            loopCur = !loopCur
                            player.repeatMode = if (loopCur) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_ALL
                        }
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                )
            }
        }
    }
}

@Composable
fun Cover(item: Media) {
    val ctx = LocalContext.current
    val art by produceState<Bitmap?>(null, item.uri) {
        value = withContext(Dispatchers.IO) {
            try {
                val r = MediaMetadataRetriever()
                r.setDataSource(ctx, Uri.parse(item.uri))
                val b = r.embeddedPicture
                r.release()
                b?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
            } catch (e: Exception) { null }
        }
    }
    Column(
        Modifier.fillMaxSize().background(Bg),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(280.dp).clip(RoundedCornerShape(24.dp))
                .background(Brush.linearGradient(listOf(Amber, Color(0xFF7A4A10)))),
            contentAlignment = Alignment.Center
        ) {
            val b = art
            if (b != null) Image(b.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else Text("♪", fontSize = 120.sp, color = Bg)
        }
        Text(
            item.name, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center, modifier = Modifier.padding(top = 20.dp, start = 24.dp, end = 24.dp)
        )
    }
}
