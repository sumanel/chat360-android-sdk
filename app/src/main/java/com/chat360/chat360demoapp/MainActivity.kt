package com.chat360.chat360demoapp


import android.content.Intent
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.annotation.RequiresApi
import com.chat360.chatbot.common.Chat360
import com.chat360.chatbot.common.CoreConfigs
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.FloatingActionButton


class MainActivity : AppCompatActivity() {
    private val botId = "23e91c25-80fa-4b40-bef6-e8bd206cd0e9"
    private val flutter = false
    private val meta = mapOf(
        "Key" to "Value",
    )

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val chat360 = Chat360().getInstance()
        chat360.coreConfig = CoreConfigs(botId, applicationContext, flutter, meta, false,true)

        chat360.setBaseUrl("https://staging.chat360.io");
        chat360.setHandleWindowEvent { eventData ->
            print(eventData)
            var metaMap : Map<String, String> = mapOf()
            if(eventData["type"] == "get_auth") {
                 metaMap = mapOf(
                    "token" to "New Token from app",
                )
            } else if(eventData["type"] == "get_date") {
                metaMap = mapOf(
                    "dynamic_date" to  java.time.ZonedDateTime.now().toString()
                )
            } else if (eventData["type"] == "get_user") {
                metaMap = mapOf(
                    "user_id" to "123456789",
                    "user_name" to "John Doe"
                )
            }

            chat360.sendEventToBot(mapOf("status" to "pending"))

            metaMap
        }


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
            chat360.startBot(this)
        }
        findViewById<FloatingActionButton>(R.id.floatingActionButton).setOnClickListener {
            chat360.startBot(this)
        }
        findViewById<MaterialButton>(R.id.buttonOpenActivityFragment).setOnClickListener {
            startActivity(Intent(this, ChatBotDemoActivity::class.java))
        }
    }

}