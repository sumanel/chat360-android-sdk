package com.chat360.chatbot

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.viewbinding.ViewBinding
import com.chat360.chatbot.common.utils.viewBinding
import com.chat360.chatbot.databinding.FragmentChatBinding


class ChatFragment : Fragment() {
    private val fragmentBinding by viewBinding(FragmentChatBinding::inflate)
    private fun <T : ViewBinding> initBinding(binding: T): View {
        return with(binding) {
            root
        }
    }
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return initBinding(fragmentBinding)
    }
    companion object {
        fun newInstance(): ChatFragment {
            return ChatFragment()
        }
    }
}