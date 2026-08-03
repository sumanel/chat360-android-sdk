package com.chat360.chat360demoapp


import android.content.Intent
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.annotation.RequiresApi
import com.chat360.chatbot.android.ChatComposeActivity
import com.chat360.chatbot.common.Chat360
import com.chat360.chatbot.ui.theme.Chat360ThemePreset
import com.chat360.chatbot.common.CoreConfigs
import com.chat360.chatbot.config.BehaviorConfig
import com.chat360.chatbot.config.BrandingConfig
import com.chat360.chatbot.config.Chat360Config
import com.chat360.chatbot.config.DefaultTheme
import com.chat360.chatbot.config.FeatureConfig
import com.chat360.chatbot.config.ThemeConfig
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.FloatingActionButton


class MainActivity : AppCompatActivity() {
    // Real production bot used to validate the native rewrite POCs end-to-end (see plan).
    private val nativePocBotId = "2e97deac-2877-495f-a568-8e0e5438fec1"
    private val botId = nativePocBotId
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

        chat360.setBaseUrl("https://app.chat360.io");
        chat360.setHandleWindowEvent { eventData ->
            print(eventData)
            var metaMap : Map<String, String> = mapOf()
            if(eventData["type"] == "get_auth_chat360") {
                 metaMap = mapOf(
                    "token" to "New Token from app",
                )
            } else if(eventData["type"] == "get_date") {
                metaMap = mapOf(
                    "dynamic_date" to  java.time.ZonedDateTime.now().toString()
                )
            } else if (eventData["type"] == "initiate_payment") {
                metaMap = mapOf()
            }

            Handler(Looper.getMainLooper()).postDelayed({
                chat360.sendEventToBot(mapOf(
                    "type" to "initiate_payment_chat360",
                    "payment_status" to "0",
                    "message" to "not able to payment"))

            }, 10_000)
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
        findViewById<MaterialButton>(R.id.buttonOpenNativePoc).setOnClickListener {
            ChatComposeActivity.launch(this, botId = nativePocBotId, themePreset = Chat360ThemePreset.DEFAULT)
        }
        findViewById<MaterialButton>(R.id.buttonOpenNativePocHyundai).setOnClickListener {
            // Hyundai isn't a library-shipped preset - it's assigned here as CUSTOM details,
            // exactly like any other client would configure their own brand.
            chat360.coreConfig = CoreConfigs(nativePocBotId, applicationContext, flutter, meta, false, true).apply {
                themePreset = Chat360ThemePreset.CUSTOM
                customLightColors = HyundaiLightColors
                customDarkColors = HyundaiDarkColors
                customTypography = HyundaiTypography
                customBranding = HyundaiBranding
                chat360Config = HyundaiConfig
            }
            chat360.startBot(this)
        }
        findViewById<MaterialButton>(R.id.buttonOpenNativePocMaruti).setOnClickListener {
            chat360.coreConfig = CoreConfigs(nativePocBotId, applicationContext, flutter, meta, false, true).apply {
                themePreset = Chat360ThemePreset.CUSTOM
                customLightColors = MarutiSuzukiLightColors
                customDarkColors = MarutiSuzukiDarkColors
                customTypography = MarutiSuzukiTypography
                customBranding = MarutiSuzukiBranding
                chat360Config = MarutiSuzukiConfig
            }
            chat360.startBot(this)
        }
    }

}
