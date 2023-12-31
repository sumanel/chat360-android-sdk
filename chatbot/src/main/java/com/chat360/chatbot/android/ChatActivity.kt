package com.chat360.chatbot.android

import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.FragmentTransaction
import androidx.viewbinding.ViewBinding
import android.os.Bundle
import android.util.Log
import android.view.View
import com.chat360.chatbot.R
import com.chat360.chatbot.common.Constants
import com.chat360.chatbot.common.utils.viewBinding
import com.chat360.chatbot.databinding.ActivityChatBinding

class ChatActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        Log.d("chat-bot_activity_chat","=================")
        if (!Constants.isNetworkAvailable(this)) {
            Constants.showNoInternetDialog(this)
        } else {
            loadFragment()
        }

        Log.d("chat-bot_activity_chat2","=================")

        // backPressed()
    }

    override fun onBackPressed() {
        try {
            if (supportFragmentManager.backStackEntryCount == 1) {

                Log.d("chat-bot_loadfragment finish called","=================")
                finish()
            } else {

                Log.d("chat-bot_loadfragment on back press called","=================")
                onBackPressedDispatcher.onBackPressed()
            }
        } catch (e: Exception) {
            //Some problem occurred please relaunch the bot
            finish()
        }
    }

    private fun loadFragment() {

        Log.d("chat-bot_loadfragment","=================")
        val transaction: FragmentTransaction = supportFragmentManager.beginTransaction()
        transaction.replace(R.id.fragmentContainerView, ChatFragment())
        transaction.addToBackStack(null)
        transaction.commit()
    }

}