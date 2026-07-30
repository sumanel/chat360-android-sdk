package com.chat360.chatbot.ui.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

/** One completed take: the encoded audio file plus the amplitude samples captured while recording. */
data class VoiceRecording(val file: File, val amplitudes: List<Int>, val durationMs: Long)

private const val SAMPLE_INTERVAL_MS = 100L
private const val MAX_SAMPLES = 60

/**
 * Records a voice note via [MediaRecorder] (AAC/m4a - Android has no built-in WAV encoder, but
 * the backend's upload endpoint is format-agnostic and MediaPlayer plays m4a back natively, so
 * this is a deliberate format swap vs. the widget's recorder-js WAV output, not a feature gap).
 * Mirrors VoiceInput/index.tsx's onStart/onStop/onClear; [amplitudes]/[isRecording]/[elapsedMs]
 * are Compose state so a live waveform can observe them directly, matching LiveWaveform's
 * real-time bars without a second state-holder.
 */
class VoiceRecorderController(private val context: Context) {
    var isRecording by mutableStateOf(false)
        private set
    var amplitudes by mutableStateOf<List<Int>>(emptyList())
        private set
    var elapsedMs by mutableStateOf(0L)
        private set

    /** Set by [rememberVoiceRecorderController] each recomposition - always the latest permission launcher. */
    var requestPermissionLauncher: () -> Unit = {}

    /** Set by [rememberVoiceRecorderController] each recomposition - the composable's own coroutine scope. */
    var scope: CoroutineScope? = null

    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var startedAt = 0L
    private var tickerJob: Job? = null
    private var pendingStart = false

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    /** Starts recording immediately if permission is already granted, otherwise prompts first (getUserMedia's on-demand prompt). */
    fun requestStart() {
        if (isRecording) return
        if (!hasPermission()) {
            pendingStart = true
            requestPermissionLauncher()
            return
        }
        start()
    }

    fun onPermissionResult(granted: Boolean) {
        val wasPending = pendingStart
        pendingStart = false
        if (granted && wasPending) start()
    }

    private fun start() {
        val file = File(context.cacheDir, "chat360_voice_${System.currentTimeMillis()}.m4a")
        @Suppress("DEPRECATION")
        val mr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else MediaRecorder()
        try {
            mr.setAudioSource(MediaRecorder.AudioSource.MIC)
            mr.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            mr.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            mr.setAudioEncodingBitRate(96_000)
            mr.setAudioSamplingRate(44_100)
            mr.setOutputFile(file.absolutePath)
            mr.prepare()
            mr.start()
        } catch (_: Exception) {
            mr.release()
            return
        }
        recorder = mr
        outputFile = file
        amplitudes = emptyList()
        elapsedMs = 0L
        startedAt = SystemClock.elapsedRealtime()
        isRecording = true
        tickerJob = scope?.launch {
            while (isRecording) {
                delay(SAMPLE_INTERVAL_MS)
                if (!isRecording) break
                elapsedMs = SystemClock.elapsedRealtime() - startedAt
                val level = try { recorder?.maxAmplitude ?: 0 } catch (_: Exception) { 0 }
                amplitudes = (amplitudes + level).takeLast(MAX_SAMPLES)
            }
        }
    }

    /** Stops and returns the completed take, or null if nothing was recorded/it failed. */
    fun stop(): VoiceRecording? {
        if (!isRecording) return null
        isRecording = false
        tickerJob?.cancel()
        tickerJob = null
        val file = outputFile
        val capturedAmplitudes = amplitudes
        val capturedDuration = elapsedMs
        val stopped = try {
            recorder?.stop()
            true
        } catch (_: Exception) {
            false
        } finally {
            recorder?.release()
            recorder = null
        }
        outputFile = null
        return if (stopped && file != null && file.length() > 0) {
            VoiceRecording(file, capturedAmplitudes, capturedDuration)
        } else {
            file?.delete()
            null
        }
    }

    /** Stops (if needed) and discards the file entirely - mirrors onClear. */
    fun cancel() {
        tickerJob?.cancel()
        tickerJob = null
        if (isRecording) {
            try { recorder?.stop() } catch (_: Exception) { /* no-op: discarding anyway */ }
            recorder?.release()
            recorder = null
        }
        outputFile?.delete()
        outputFile = null
        isRecording = false
        amplitudes = emptyList()
        elapsedMs = 0L
    }
}

@Composable
fun rememberVoiceRecorderController(): VoiceRecorderController {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val controller = remember { VoiceRecorderController(context) }
    controller.scope = coroutineScope
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        controller.onPermissionResult(granted)
    }
    controller.requestPermissionLauncher = { launcher.launch(Manifest.permission.RECORD_AUDIO) }
    DisposableEffect(controller) {
        onDispose { controller.cancel() }
    }
    return controller
}
