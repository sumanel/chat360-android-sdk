package com.chat360.chatbot

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import androidx.viewbinding.ViewBinding
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

    }
}