package com.chat360.chat360demoapp2

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.chat360.chat360demoapp.R
import com.google.android.material.button.MaterialButton

class ChatBotDemoFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        val view = inflater.inflate(R.layout.fragment_chat_bot_demo, container, false)
        view.findViewById<MaterialButton>(R.id.buttonTakeMeToABot).setOnClickListener {

            (activity as ChatBotDemoActivity).showBotView()
        }
        return view
    }
}