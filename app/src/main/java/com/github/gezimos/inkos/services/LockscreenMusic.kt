package com.github.gezimos.inkos.services

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.github.gezimos.inkos.R
import com.github.gezimos.inkos.helper.AudioWidgetHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Music controls on the lock screen, for any player.
 *
 * The Kompakt's own lock screen has a music widget, but it is wired to Mudita's player by
 * name and never shows anything else. This draws one in the same place, the same size, for
 * whatever else is playing. It is an accessibility overlay: a window type the system draws
 * above the lock screen, which only an accessibility service may add. The player is found
 * through the notification listener inkOS already runs for the home screen's music widget.
 *
 * Ported from Katapult by gezimos (GPL-3.0), whose lock-screen music widget works this way;
 * see LockscreenWidgetService there. Only the music widget came across.
 *
 * It shows only while the phone is locked with the screen on, the PIN pad is not up, no call
 * is ringing or in progress, and the player is not Mudita's own - that one already has its
 * widget, and two would stack.
 */
class LockscreenMusic(private val service: AccessibilityService) :
    LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val savedState = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: SavedStateRegistry get() = savedState.savedStateRegistry

    private val handler = Handler(Looper.getMainLooper())
    private val playing = mutableStateOf<AudioWidgetHelper.MediaPlayerInfo?>(null)
    private var scope: CoroutineScope? = null
    private var overlay: ComposeView? = null

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = evaluate()
    }

    /**
     * Events come only from the system UI, so on unlock the last one is the lock screen
     * leaving while it still reports itself locked mid-animation. Nothing fires after that to
     * take the widget down, so while it is up it looks again on a short tick.
     */
    private val recheck = Runnable { evaluate() }

    fun start() {
        savedState.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
        scope = CoroutineScope(Dispatchers.Main + SupervisorJob()).also { s ->
            s.launch {
                AudioWidgetHelper.getInstance(service).mediaPlayerState.collect { info ->
                    playing.value = info
                    evaluate()
                }
            }
        }
        service.registerReceiver(screenReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        })
        evaluate()
    }

    fun stop() {
        handler.removeCallbacks(recheck)
        scope?.cancel()
        scope = null
        remove()
        try { service.unregisterReceiver(screenReceiver) } catch (_: Exception) {}
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        store.clear()
    }

    fun evaluate() {
        val keyguard = service.getSystemService(KeyguardManager::class.java)
        val power = service.getSystemService(PowerManager::class.java)
        val info = playing.value
        val show = keyguard.isKeyguardLocked &&
            power.isInteractive &&
            info != null &&
            info.packageName != MUDITA_PLAYER &&
            !inCall() &&
            !pinShowing()
        if (show) add() else remove()
        handler.removeCallbacks(recheck)
        if (overlay != null) handler.postDelayed(recheck, RECHECK_MS)
    }

    private fun add() {
        if (overlay != null) return
        val helper = AudioWidgetHelper.getInstance(service)
        val night = (service.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        val view = ComposeView(service).apply {
            setViewTreeLifecycleOwner(this@LockscreenMusic)
            setViewTreeViewModelStoreOwner(this@LockscreenMusic)
            setViewTreeSavedStateRegistryOwner(this@LockscreenMusic)
            setContent {
                val info = playing.value ?: return@setContent
                LockscreenMusicWidget(
                    info = info,
                    ink = if (night) Color.White else Color.Black,
                    surface = if (night) Color.Black else Color.White,
                    onOpenApp = { helper.openMediaApp() },
                    onPrevious = { helper.skipToPrevious() },
                    onPlayPause = { helper.playPauseMedia() },
                    onNext = { helper.skipToNext() },
                    onStop = { helper.stopMedia() },
                )
            }
        }
        val density = service.resources.displayMetrics.density
        val params = WindowManager.LayoutParams().apply {
            type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
            format = PixelFormat.TRANSLUCENT
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            width = (WIDTH_DP * density).toInt()
            height = WindowManager.LayoutParams.WRAP_CONTENT
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = (TOP_DP * density).toInt()
        }
        try {
            service.getSystemService(WindowManager::class.java).addView(view, params)
            overlay = view
        } catch (_: Exception) {
            overlay = null
        }
    }

    private fun remove() {
        overlay?.let {
            try { service.getSystemService(WindowManager::class.java).removeView(it) } catch (_: Exception) {}
        }
        overlay = null
    }

    /**
     * The PIN pad is up when a visible text field holds input focus. Looked at, not waited
     * for: showing the pad again does not always fire a focus event.
     */
    private fun pinShowing(): Boolean = try {
        val focus = service.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        val showing = focus != null && focus.isVisibleToUser &&
            focus.className?.contains("EditText") == true
        @Suppress("DEPRECATION")
        focus?.recycle()
        showing
    } catch (_: Exception) {
        false
    }

    /** A ringing or live call draws its own screen over the lock screen. */
    private fun inCall(): Boolean = try {
        when (service.getSystemService(AudioManager::class.java).mode) {
            AudioManager.MODE_RINGTONE,
            AudioManager.MODE_IN_CALL,
            AudioManager.MODE_IN_COMMUNICATION -> true
            else -> false
        }
    } catch (_: Exception) {
        false
    }

    private companion object {
        const val MUDITA_PLAYER = "com.mudita.audio.player"
        // Where the stock widget sits: 332dp wide, its top 220dp down. Two dp wider, so a
        // column of the stock one never shows at the edge when both are drawn.
        const val WIDTH_DP = 334f
        const val TOP_DP = 220f
        const val RECHECK_MS = 120L
    }
}

@Composable
private fun LockscreenMusicWidget(
    info: AudioWidgetHelper.MediaPlayerInfo,
    ink: Color,
    surface: Color,
    onOpenApp: () -> Unit,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onStop: () -> Unit,
) {
    val corner = 14.dp
    val stroke = 2.5.dp
    val shape = RoundedCornerShape(corner)
    val metadata = info.controller?.metadata
    val art = remember(metadata) {
        try {
            metadata?.getBitmap(android.media.MediaMetadata.METADATA_KEY_ALBUM_ART)
                ?: metadata?.getBitmap(android.media.MediaMetadata.METADATA_KEY_ART)
                ?: metadata?.getBitmap(android.media.MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        } catch (_: Exception) {
            null
        }
    }
    // The short description a session publishes may carry only the title - Music Box's does
    // - so the full metadata is the fallback for both.
    fun field(short: String?, key: String) = short?.trim()?.takeIf { it.isNotBlank() }
        ?: metadata?.getString(key)?.trim()?.takeIf { it.isNotBlank() }
    val label = listOfNotNull(
        field(info.title, android.media.MediaMetadata.METADATA_KEY_TITLE),
        field(info.artist, android.media.MediaMetadata.METADATA_KEY_ARTIST),
    ).joinToString(" - ")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(90.dp)
            .clip(shape)
            .background(surface)
            .border(stroke, ink, shape),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1.4f)
                .padding(horizontal = 12.dp)
                .clickable { onOpenApp() },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label.ifEmpty { stringResource(R.string.lockscreen_music_nothing) },
                fontSize = 18.sp,
                color = ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(Modifier.fillMaxWidth().height(stroke).background(ink))
        Row(
            modifier = Modifier.fillMaxWidth().weight(2f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(bottomStart = corner))
                    .background(if (art != null) ink else surface)
                    .clickable { onOpenApp() }
                    .padding(bottom = 3.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (art != null) {
                    Image(
                        bitmap = art.asImageBitmap(),
                        contentDescription = stringResource(R.string.open_music_app),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.MusicNote,
                        contentDescription = stringResource(R.string.open_music_app),
                        tint = ink,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
            Box(Modifier.fillMaxHeight().width(stroke).background(ink))
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(horizontal = 8.dp)
                    .padding(bottom = 3.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Control(Icons.Rounded.SkipPrevious, R.string.previous_track, ink, 32, onPrevious)
                Control(
                    if (info.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    R.string.play_or_pause, ink, 42, onPlayPause,
                )
                Control(Icons.Rounded.SkipNext, R.string.next_track, ink, 32, onNext)
                Control(Icons.Rounded.Stop, R.string.stop_playback, ink, 32, onStop)
            }
        }
    }
}

@Composable
private fun Control(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: Int,
    ink: Color,
    sizeDp: Int,
    onClick: () -> Unit,
) {
    Icon(
        imageVector = icon,
        contentDescription = stringResource(description),
        tint = ink,
        modifier = Modifier.size(sizeDp.dp).clickable { onClick() },
    )
}
