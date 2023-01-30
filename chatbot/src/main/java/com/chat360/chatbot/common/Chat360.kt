package com.chat360.chatbot.common

import android.content.Context
import android.content.Intent
import androidx.fragment.app.Fragment
import com.chat360.chatbot.android.ChatActivity
import com.chat360.chatbot.android.ChatFragment
import com.chat360.chatbot.common.models.ConfigService

class Chat360 {
    var coreConfig: CoreConfigs? = null

    private lateinit var botPluginInstance: Chat360

    fun getInstance(): Chat360{
            synchronized(Chat360::class.java) {
                    botPluginInstance = Chat360()
                }
        return botPluginInstance
    }
   fun startBot(context: Context){
       try {
           if (validate(context)) {
               ConfigService.getInstance()!!.setConfigData(coreConfig!!)
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
                ConfigService.getInstance()?.setConfigData(coreConfig!!)
                return ChatFragment.newInstance()
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
        }/*
        if (coreConfig.customBaseUrl == null || coreConfig.customBaseUrl.isEmpty()) {
            throw java.lang.Exception("customBaseUrl cannot be null or empty.")
        }*/
        /*if (config.customLoaderUrl == null || config.customLoaderUrl.isEmpty() || !isValidUrl(config.customLoaderUrl)) {
            throw java.lang.Exception("Please provide valid customLoaderUrl")
        }
        if (config.payload != null) {
            try {
                URLEncoder.encode(Gson().toJson(config.payload), "UTF-8")
            } catch (e: java.lang.Exception) {
                throw java.lang.Exception(
                    """
                    In payload map, value can be of primitive type or json convertible value ::
                    Exception message :: ${e.message}
                    """.trimIndent()
                )
            }
        }*/
        if (!(coreConfig?.version === 1 || coreConfig?.version === 2)) {
            throw java.lang.Exception("version can be either 1 or 2")
        }
        return true
    }


/*
    fun setStatusBarColor(color : String){

    }
    fun setCloseButtonColor(color : String){

    }
    fun setStatusBarColor(colorHex : Int){

    }
    fun setCloseButtonColor(colorHex : Int){

    }*/

}