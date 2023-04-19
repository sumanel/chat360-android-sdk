package com.chat360.chatbot.android

import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.FragmentTransaction
import androidx.viewbinding.ViewBinding
import android.os.Bundle
import android.view.View
import com.chat360.chatbot.R.*
import com.chat360.chatbot.common.Constants
import com.chat360.chatbot.common.utils.viewBinding
import com.chat360.chatbot.databinding.ActivityChatBinding

class ChatActivity : AppCompatActivity() {
    private val activityBinding by viewBinding(ActivityChatBinding::inflate)
    private fun <T : ViewBinding> initBinding(binding: T): View {
        return with(binding) {
            root
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(initBinding(activityBinding))
        if (!Constants.isNetworkAvailable(this)) {
            Constants.showNoInternetDialog(this)
        } else {
            loadFragment()
        }

        // backPressed()
    }

    override fun onBackPressed() {
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
        transaction.replace(id.fragmentContainerView, ChatFragment.newInstance())
        transaction.addToBackStack(null)
        transaction.commit()
    }

}