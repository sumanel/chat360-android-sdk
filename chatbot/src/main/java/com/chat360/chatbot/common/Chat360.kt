package com.chat360.chatbot.common

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.fragment.app.Fragment
import com.chat360.chatbot.android.ChatComposeActivity
import com.chat360.chatbot.android.ChatComposeFragment
import com.chat360.chatbot.common.models.ConfigService
import com.chat360.chatbot.config.Chat360Config

class Chat360 {
    var coreConfig: CoreConfigs? = null

    fun getInstance(): Chat360 {
        return synchronized(Companion) {
            instance ?: Chat360().also { instance = it }
        }
    }

    companion object {
        @Volatile private var instance: Chat360? = null
    }

    fun setBaseUrl(url: String) {
        ConfigService.getInstance()?.setBaseUrl(url)
    }

    /** Applies reusable Compose UI configuration without changing any SDK source. */
    fun initialize(config: Chat360Config) {
        val current = coreConfig ?: throw IllegalStateException("Set CoreConfigs before calling initialize.")
        current.chat360Config = config
    }

    fun setHandleWindowEvent(handleWindowEvent: (Map<String, String>) -> Map<String, String>) {
        ConfigService.WebEventHandler.handleWindowEvent = handleWindowEvent
    }

    fun sendEventToBot(event: Map<String, String>) {
        ConfigService.WebEventHandler.sendEventToBot?.let { it(event) }
    }

    fun startBot(context: Context) {
        try {
            if (validate(context)) {
                ConfigService.getInstance()!!.setConfigData(coreConfig!!)
                val intent = Intent(context, ChatComposeActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(intent)
            }
        } catch (e: java.lang.Exception) {
            throw java.lang.Exception(
                """
            Exception in staring chat bot ::
            Exception message :: ${e.message}
            """.trimIndent()
            )
        }
    }

    @Throws(Exception::class)
    fun getChatBotView(context: Context): Fragment? {
        try {
            if (validate(context)) {
                ConfigService.getInstance()?.setConfigData(coreConfig!!)
                return ChatComposeFragment()
            }
        } catch (e: Exception) {
            throw Exception(
                """
                Exception in staring chat bot ::
                Exception message :: ${e.message}
                """.trimIndent()
            )
        }
        return null
    }

    @Deprecated("Not Available for now")
    fun getUnreadMessageCount(): Int {
        return Constants.UNREAD_MESSAGE_COUNT
    }

    @Throws(java.lang.Exception::class)
    private fun validate(context: Context?): Boolean {
        if (context == null) {
            throw java.lang.Exception("Context passed is null. Please pass valid context")
        }
        if (coreConfig == null) {
            throw java.lang.Exception("Please initialise config, it cannot be null.")
        }
        if (coreConfig?.botId == null || coreConfig?.botId?.trim()!!.isEmpty()) {
            throw java.lang.Exception("botId is not configured. Please set botId before calling startChatbot()")
        }
        if (!(coreConfig?.version === 1 || coreConfig?.version === 2)) {
            throw java.lang.Exception("version can be either 1 or 2")
        }
        return true
    }

}
