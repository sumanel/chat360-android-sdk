package com.chat360.chatbot.ui.theme

import com.chat360.chatbot.network.rest.dto.BotAppearanceDetails
import com.chat360.chatbot.ui.ChatUiState
import com.chat360.chatbot.ui.withAppearance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The host's configured logo (Hyundai's) is what shows. The server's appearance logo (the Chat360 one, for a bot with no
 * custom avatar) must never replace it - not on first load, and not after a dropped socket or a sleep/wake reload.
 */
class LogoResolutionTest {
    private val hyundai = Chat360Logo.Resource(lightResId = 1, darkResId = 2)
    private val chat360 = Chat360Logo.Remote("https://cdn.example/chat360.png")
    private val other = Chat360Logo.Remote("https://cdn.example/other.png")

    private fun appearanceWithAvatar(url: String?) =
        BotAppearanceDetails(chatboxHeader = BotAppearanceDetails.ChatboxHeader(chatboxAvatar = url))

    // --- config beats server ---

    @Test
    fun `the configured logo wins over the servers logo`() = assertEquals(hyundai, resolveLogo(configured = hyundai, fromServer = chat360))

    @Test
    fun `the servers logo only fills in when nothing is configured`() = assertEquals(chat360, resolveLogo(configured = null, fromServer = chat360))

    @Test
    fun `no logo anywhere stays none`() = assertNull(resolveLogo(configured = null, fromServer = null))

    // --- a reload never wipes a logo that is already there ---

    @Test
    fun `a successful appearance load records the servers logo`() {
        val state = ChatUiState().withAppearance(appearanceWithAvatar("https://cdn.example/chat360.png"), chatboxName = null)
        assertEquals(chat360, state.logoOverride)
    }

    @Test
    fun `a failed appearance reload keeps the last logo`() {
        val loaded = ChatUiState().withAppearance(appearanceWithAvatar("https://cdn.example/chat360.png"), chatboxName = null)
        val afterDrop = loaded.withAppearance(details = null, chatboxName = null) // the fetch failed after a reconnect
        assertEquals(chat360, afterDrop.logoOverride)
    }

    @Test
    fun `a reload without a logo keeps the last logo`() {
        val loaded = ChatUiState().withAppearance(appearanceWithAvatar("https://cdn.example/other.png"), chatboxName = null)
        val reloaded = loaded.withAppearance(appearanceWithAvatar(null), chatboxName = null)
        assertEquals(other, reloaded.logoOverride)
    }

    @Test
    fun `a reload with a new logo replaces the old one`() {
        val loaded = ChatUiState().withAppearance(appearanceWithAvatar("https://cdn.example/chat360.png"), chatboxName = null)
        assertEquals(other, loaded.withAppearance(appearanceWithAvatar("https://cdn.example/other.png"), chatboxName = null).logoOverride)
    }

    @Test
    fun `the hyundai logo still shows after a server logo arrives and then a drop`() {
        var state = ChatUiState()
        state = state.withAppearance(appearanceWithAvatar("https://cdn.example/chat360.png"), chatboxName = null)
        state = state.withAppearance(details = null, chatboxName = null)
        assertEquals(hyundai, resolveLogo(configured = hyundai, fromServer = state.logoOverride))
    }

    @Test
    fun `other appearance fields behave as before`() {
        val state = ChatUiState().withAppearance(appearanceWithAvatar(null), chatboxName = "Hyundai Sales")
        assertEquals("Hyundai Sales", state.botTitleOverride)
        assertEquals("a blank name clears the title", null, state.withAppearance(null, chatboxName = " ").botTitleOverride)
    }
}
