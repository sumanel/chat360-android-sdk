package com.chat360.chatbot.ui.util

import android.content.ContextWrapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The dictation permission flow: a refused permission must surface a message, and it must be clearable. */
class SpeechToTextControllerTest {

    private fun controller(supported: Boolean = true, granted: Boolean = false) =
        SpeechToTextController(
            context = ContextWrapper(null),
            recognitionAvailable = { supported },
            audioPermissionGranted = { granted },
        )

    @Test
    fun requestWithoutPermissionAsksTheLauncher() {
        val stt = controller(granted = false)
        var asked = 0
        stt.requestPermissionLauncher = { asked++ }
        stt.requestStart()
        assertEquals(1, asked)
        assertNull(stt.error)
        assertFalse(stt.isListening)
    }

    @Test
    fun denyingThePromptSetsError() {
        val stt = controller(granted = false)
        stt.requestStart()
        stt.onPermissionResult(false)
        assertEquals(SpeechToTextController.PERMISSION_DENIED_MESSAGE, stt.error)
        assertFalse(stt.isListening)
    }

    @Test
    fun permissionResultWithoutAPendingRequestIsIgnored() {
        val stt = controller()
        stt.onPermissionResult(false)
        assertNull(stt.error)
    }

    @Test
    fun unsupportedRecognizerNeitherPromptsNorSetsError() {
        val stt = controller(supported = false)
        var asked = 0
        stt.requestPermissionLauncher = { asked++ }
        stt.requestStart()
        assertEquals(0, asked)
        assertNull(stt.error)
    }

    @Test
    fun dismissErrorClearsIt() {
        val stt = controller()
        stt.requestStart()
        stt.onPermissionResult(false)
        assertTrue(stt.error != null)
        stt.dismissError()
        assertNull(stt.error)
    }

    @Test
    fun requestingAgainClearsThePreviousError() {
        val stt = controller()
        stt.requestStart()
        stt.onPermissionResult(false)
        assertTrue(stt.error != null)
        stt.requestStart()
        assertNull(stt.error)
    }
}
