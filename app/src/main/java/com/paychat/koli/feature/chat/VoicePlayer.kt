package com.paychat.koli.feature.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.compose.ui.platform.LocalContext
import java.util.concurrent.TimeUnit

/**
 * Plays voice messages.
 *
 * One player is shared by the whole conversation rather than one per bubble:
 * an ExoPlayer holds a hardware codec, and a screen full of them would exhaust
 * the device's decoders. Starting a new message stops whatever was playing,
 * which is also what people expect.
 */
class VoicePlaybackState(private val player: ExoPlayer) {

    private val _playingId = mutableStateOf<String?>(null)

    /** The message currently playing, observed by the bubbles. */
    val playingId: State<String?> get() = _playingId

    fun toggle(messageId: String, source: String) {
        if (_playingId.value == messageId) {
            stop()
            return
        }
        player.setMediaItem(MediaItem.fromUri(source))
        player.prepare()
        player.play()
        _playingId.value = messageId
    }

    fun stop() {
        player.stop()
        player.clearMediaItems()
        _playingId.value = null
    }

    internal fun onEnded() {
        _playingId.value = null
    }
}

@Composable
fun rememberVoicePlayback(): VoicePlaybackState {
    val context = LocalContext.current
    val player = remember { ExoPlayer.Builder(context).build() }
    val state = remember(player) { VoicePlaybackState(player) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) state.onEnded()
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    return state
}

/** "0:07", "1:04". */
fun formatDuration(durationMs: Long): String {
    val totalSeconds = TimeUnit.MILLISECONDS.toSeconds(durationMs)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
