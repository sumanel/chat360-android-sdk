package com.chat360.chatbot.ui.util

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import java.util.Locale

/**
 * Continuous dictation via Android's on-device [SpeechRecognizer]: interim + final results
 * merge into one running [transcript] as the user talks. Android's recognizer completes after
 * each utterance rather than listening continuously, so [onResults] immediately starts a fresh
 * session while still [isListening] to keep the dictation going.
 */
class SpeechToTextController(private val context: Context) {
    var isListening by mutableStateOf(false)
        private set
    var transcript by mutableStateOf("")
        private set
    var error by mutableStateOf<String?>(null)
        private set

    var requestPermissionLauncher: () -> Unit = {}

    private var recognizer: SpeechRecognizer? = null
    private var finalizedText = ""
    private var pendingStart = false
    private var language = Locale.getDefault().toLanguageTag()

    fun isSupported(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    /** Starts listening immediately if permission is already granted, otherwise prompts first. */
    fun requestStart(languageTag: String = Locale.getDefault().toLanguageTag()) {
        if (isListening || !isSupported()) return
        language = languageTag
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
        finalizedText = ""
        transcript = ""
        error = null
        val rec = recognizer ?: SpeechRecognizer.createSpeechRecognizer(context).also { recognizer = it }
        rec.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle) {
                val text = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                if (text.isNotBlank()) finalizedText = listOf(finalizedText, text).filter { it.isNotBlank() }.joinToString(" ")
                transcript = finalizedText
                if (isListening) restart()
            }

            override fun onPartialResults(partialResults: Bundle) {
                val text = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                transcript = listOf(finalizedText, text).filter { it.isNotBlank() }.joinToString(" ")
            }

            override fun onError(errorCode: Int) {
                // NO_MATCH/SPEECH_TIMEOUT just mean a silent pause between utterances, not a
                // real failure, so listening restarts instead of surfacing an error.
                if (isListening && (errorCode == SpeechRecognizer.ERROR_NO_MATCH || errorCode == SpeechRecognizer.ERROR_SPEECH_TIMEOUT)) {
                    restart()
                } else if (isListening) {
                    error = "Speech recognition error"
                    isListening = false
                }
            }

            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        isListening = true
        rec.startListening(buildIntent())
    }

    private fun restart() {
        try { recognizer?.startListening(buildIntent()) } catch (_: Exception) { /* no-op */ }
    }

    private fun buildIntent() = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
    }

    /** Ends dictation - the accumulated [transcript] is left for the caller to keep or discard. */
    fun stop() {
        isListening = false
        try { recognizer?.stopListening() } catch (_: Exception) { /* no-op */ }
    }

    fun release() {
        isListening = false
        recognizer?.destroy()
        recognizer = null
    }
}

@Composable
fun rememberSpeechToTextController(): SpeechToTextController {
    val context = LocalContext.current
    val controller = remember { SpeechToTextController(context) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        controller.onPermissionResult(granted)
    }
    controller.requestPermissionLauncher = { launcher.launch(Manifest.permission.RECORD_AUDIO) }
    DisposableEffect(controller) {
        onDispose { controller.release() }
    }
    return controller
}
