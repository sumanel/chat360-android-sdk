package com.chat360.chatbot.common

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.fragment.app.Fragment
import com.chat360.chatbot.android.ChatActivity
import com.chat360.chatbot.android.ChatFragment
import com.chat360.chatbot.common.models.ConfigService

class Chat360 {
    var coreConfig: CoreConfigs? = null

    private lateinit var botPluginInstance: Chat360

    fun getInstance(): Chat360 {
        Log.d("chat-bot_getInstance_chat360","=================")
        synchronized(Chat360::class.java) {
            botPluginInstance = Chat360()
        }
        return botPluginInstance
    }

    fun startBot(context: Context) {

        Log.d("chat-bot_startBot","=================")
        try {
            if (validate(context)) {
                ConfigService.getInstance()!!.setConfigData(coreConfig!!)
                val intent = Intent(context, ChatActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(intent)

                Log.d("chat-bot_startBot2","=================")
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

        Log.d("chat-bot_getChatbotView","=================")
        try {
            if (validate(context)) {
                ConfigService.getInstance()?.setConfigData(coreConfig!!)
                return ChatFragment()
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