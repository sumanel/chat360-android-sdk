package com.chat360.chatbot.common

import android.content.Context
import android.content.Intent
import androidx.fragment.app.Fragment
import com.chat360.chatbot.ChatActivity
import com.chat360.chatbot.ChatFragment

class Chat360 {

    private lateinit var botPluginInstance: Chat360

    fun getInstance(): Chat360{

            synchronized(Chat360::class.java) {
                    botPluginInstance = Chat360()
                }
        return botPluginInstance
    }
   fun startBot(context: Context){
       val intent = Intent(context, ChatActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    context.startActivity(intent)

        }

    fun getChatBotView(context: Context): Fragment {
        return ChatFragment.newInstance()

    }
}