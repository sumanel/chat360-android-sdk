package com.chat360.chatbot

import androidx.appcompat.app.AppCompatActivity
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.FragmentTransaction
import androidx.viewbinding.ViewBinding
import android.os.Bundle
import android.view.View
import com.chat360.chatbot.common.utils.viewBinding
import com.chat360.chatbot.databinding.FragmentChatBinding

class ChatActivity : AppCompatActivity() {
    private val activityBinding by viewBinding(FragmentChatBinding::inflate)
    private fun <T : ViewBinding> initBinding(binding: T): View {
        return with(binding) {
            root
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(initBinding(activityBinding))
        loadFragment()
        backPressed()
    }

    private fun backPressed() {
        onBackPressedDispatcher.addCallback(this /* lifecycle owner */, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
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
        })
    }

    private fun loadFragment() {
        val transaction: FragmentTransaction = supportFragmentManager.beginTransaction();
        transaction.replace(R.id.fragmentContainerView, ChatFragment.newInstance())
        transaction.addToBackStack(null)
        transaction.commit()
    }


}