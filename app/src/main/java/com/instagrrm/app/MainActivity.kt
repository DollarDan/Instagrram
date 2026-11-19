package com.instagrrm.app

import android.Manifest
import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture

class MainActivity : ComponentActivity() {
    private var controller: ListenableFuture<MediaController>? = null

    private val notifPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.init(applicationContext)
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        // Connecting a controller starts the media service that shows the notification.
        controller = MediaController.Builder(this, SessionToken(this, ComponentName(this, PlaybackService::class.java))).buildAsync()
        setContent { InstagrrmTheme { Surface(Modifier.fillMaxSize()) { App() } } }
    }

    override fun onDestroy() {
        controller?.let { MediaController.releaseFuture(it) }
        controller = null
        super.onDestroy()
    }
}

@Composable
fun App() {
    val ctx = LocalContext.current
    var play by remember { mutableStateOf(PlayerHolder.session) }
    var gallery by remember { mutableStateOf<Folder?>(null) }
    var storage by remember { mutableStateOf(false) }
    fun start(sess: Sess, minutes: Int) {
        PlayerHolder.start(ctx, sess, minutes)
        play = sess
    }
    val s = play
    if (s != null) {
        val leave = { PlayerHolder.stop(); play = null }
        BackHandler(onBack = leave)
        PlayerScreen(s, leave)
    } else {
        androidx.compose.foundation.layout.Box(Modifier.fillMaxSize().systemBarsPadding()) {
            val g = gallery
            when {
                g != null -> {
                    BackHandler { gallery = null }
                    GalleryScreen(g, { start(it, if (g.sleep) Store.sleepMinutes else 0) }, { gallery = null })
                }
                storage -> {
                    BackHandler { storage = false }
                    StorageScreen(Store.folders, { start(it, 0) }, { storage = false })
                }
                else -> HomeScreen({ sess, m -> start(sess, m) }, { gallery = it }, { storage = true })
            }
        }
    }
}
