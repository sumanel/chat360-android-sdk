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
    var customBaseURL: String? = null

    fun setBaseURL(baseURL: String) : Unit {
        this.customBaseURL = baseURL
    }

    fun setMetadata(context: Context?,map: Map<String,String>): Unit {
        if (context != null) {
            ConfigService.getInstance(context)?.setMetadata(map)
        }
    }

    private lateinit var botPluginInstance: Chat360

    fun getInstance(): Chat360 {
        synchronized(Chat360::class.java) {
            botPluginInstance = Chat360()
        }
        return botPluginInstance
    }

    fun startBot(context: Context) {
        try {
            if (validate(context)) {
                ConfigService.getInstance(context)!!.setConfigData(coreConfig!!)
                if (customBaseURL != null){
                    ConfigService.getInstance(context)!!.setBaseURL(customBaseURL!!)
                }
                val intent = Intent(context, ChatActivity::class.java)
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
                ConfigService.getInstance(context)?.setConfigData(coreConfig!!)
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