package com.chat360.chatbot.ui.util

import android.media.MediaPlayer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

/**
 * Ensures only one voice bubble plays at a time - starting playback on one controller pauses
 * whichever other controller is currently playing. Player instances register themselves here
 * on play and stop.
 */
private object VoicePlaybackCoordinator {
    private var current: VoicePlaybackController? = null

    fun onPlay(controller: VoicePlaybackController) {
        if (current !== controller) current?.pause()
        current = controller
    }

    fun onStop(controller: VoicePlaybackController) {
        if (current === controller) current = null
    }
}

/** Plays either a local file path or a remote URL - a sent bubble prefers [localFilePath] (instant, no network wait). */
class VoicePlaybackController {
    var isPlaying by mutableStateOf(false)
        private set
    var positionMs by mutableStateOf(0)
        private set
    var durationMs by mutableStateOf(0)
        private set

    var scope: kotlinx.coroutines.CoroutineScope? = null
    private var player: MediaPlayer? = null
    private var tickerJob: Job? = null
    /** True once the current [player] has successfully prepared [loadedSource] - lets a paused
     * (not completed) player resume via plain start() instead of restarting from zero. */
    private var isPrepared = false
    private var loadedSource: String? = null

    fun playOrPause(localFilePath: String?, remoteUrl: String?) {
        if (isPlaying) {
            pause()
            return
        }
        val source = localFilePath?.takeIf { File(it).exists() } ?: remoteUrl ?: return
        val mp = player ?: MediaPlayer().also { player = it }
        try {
            if (isPrepared && loadedSource == source) {
                // Resuming from pause, or replaying after completion (onCompletion seeks to 0).
                mp.start()
                isPlaying = true
                VoicePlaybackCoordinator.onPlay(this)
                startTicker()
            } else {
                isPrepared = false
                loadedSource = source
                mp.reset()
                mp.setDataSource(source)
                mp.setOnCompletionListener {
                    isPlaying = false
                    positionMs = 0
                    it.seekTo(0)
                    VoicePlaybackCoordinator.onStop(this)
                }
                mp.setOnPreparedListener {
                    durationMs = it.duration
                    isPrepared = true
                    it.start()
                    isPlaying = true
                    VoicePlaybackCoordinator.onPlay(this)
                    startTicker()
                }
                mp.prepareAsync()
            }
        } catch (_: Exception) {
            isPlaying = false
            isPrepared = false
        }
    }

    fun pause() {
        try {
            if (player?.isPlaying == true) player?.pause()
        } catch (_: Exception) { /* no-op */ }
        isPlaying = false
        tickerJob?.cancel()
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = scope?.launch {
            while (isPlaying) {
                positionMs = try { player?.currentPosition ?: 0 } catch (_: Exception) { 0 }
                delay(100)
            }
        }
    }

    fun release() {
        tickerJob?.cancel()
        VoicePlaybackCoordinator.onStop(this)
        player?.release()
        player = null
        isPlaying = false
        isPrepared = false
        loadedSource = null
        positionMs = 0
        durationMs = 0
    }
}

@Composable
fun rememberVoicePlaybackController(): VoicePlaybackController {
    val coroutineScope = rememberCoroutineScope()
    val controller = remember { VoicePlaybackController() }
    controller.scope = coroutineScope
    DisposableEffect(controller) {
        onDispose { controller.release() }
    }
    return controller
}
