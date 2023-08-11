package com.chat360.chat360demoapp

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import com.chat360.chatbot.common.Chat360
import com.chat360.chatbot.common.CoreConfigs
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.FloatingActionButton

class MainActivity : AppCompatActivity() {
    private val botId = "Your Bot-Id here"
    private val flutter = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val chat360 = Chat360().getInstance()
        chat360.coreConfig = CoreConfigs(botId, applicationContext, flutter)

        // To Change the color of status bar, by default it will pick app theme
        chat360.coreConfig!!.statusBarColor = R.color.purple_500

        // To Change the color of close button, default color is white
        chat360.coreConfig!!.closeButtonColor = R.color.white

        /* Note: if color is set from both setStatusBarColor and statusBarColorFromHex,
         * statusBarColorFromHex will take priority
         * */
        // To set statusBarColor from hexadecimal color code
        chat360.coreConfig!!.statusBarColorFromHex = "#4299E1"

        /* Note: if color is set from both closeButtonColor and closeButtonColorHex,
         * closeButtonColorHex will take priority
         * */
        // To set closeButtonColor from hexadecimal color code
        chat360.coreConfig!!.closeButtonColorFromHex = "#ffffff"

        findViewById<MaterialButton>(R.id.buttonOpenActivity).setOnClickListener {

            Log.d("chat-bot_main","=================")
            chat360.startBot(this)
        }
        findViewById<FloatingActionButton>(R.id.floatingActionButton).setOnClickListener {

            Log.d("chat-bot_main","=================")
            chat360.startBot(this)
        }
        findViewById<MaterialButton>(R.id.buttonOpenActivityFragment).setOnClickListener {
            startActivity(Intent(this, ChatBotDemoActivity::class.java))
        }
    }

}