package com.chat360.chatbot.android

import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.FragmentTransaction
import android.os.Bundle
import android.util.Log
import com.chat360.chatbot.R
import com.chat360.chatbot.common.Constants

class ChatActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)
        if (!Constants.isNetworkAvailable(this)) {
            Constants.showNoInternetDialog(this)
        } else {
            loadFragment()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        super.onBackPressed()
        try {
            if (supportFragmentManager.backStackEntryCount == 1) {
                finish()
            } else {
                onBackPressedDispatcher.onBackPressed()
            }
        } catch (e: Exception) {
            //Some problem occurred please relaunch the bot
            finish()
        }
    }

    private fun loadFragment() {
        val transaction: FragmentTransaction = supportFragmentManager.beginTransaction()
        transaction.replace(R.id.fragmentContainerView, ChatFragment())
        transaction.addToBackStack(null)
        transaction.commit()
    }

}