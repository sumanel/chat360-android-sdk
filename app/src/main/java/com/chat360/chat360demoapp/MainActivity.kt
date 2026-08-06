package com.chat360.chat360demoapp


import android.content.Intent
import android.os.Build
import android.widget.Toast
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
import com.chat360.chatbot.config.Chat360UIConfig
import com.chat360.chatbot.config.DefaultTheme
import com.chat360.chatbot.config.FeatureConfig
import com.chat360.chatbot.config.ThemeConfig
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody


class MainActivity : AppCompatActivity() {
    companion object {
        private const val HYUNDAI_DEALER_AUTH_URL =
            "https://staging.chat360.io/api/client_hyundai_lms/hyundai/dealerAuth/"
        private const val HYUNDAI_DEALER_CODE = "DLR001"
    }

    private val httpClient = OkHttpClient()
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
        val hyundaiButton = findViewById<MaterialButton>(R.id.buttonOpenNativePocHyundai)
        hyundaiButton.setOnClickListener {
            authenticateHyundaiDealer(hyundaiButton) {
                // Hyundai isn't a library-shipped preset - it's assigned here as CUSTOM details,
                // exactly like any other client would configure their own brand.
                chat360.coreConfig = CoreConfigs(nativePocBotId, applicationContext, flutter, meta, false, true).apply {
                    themePreset = Chat360ThemePreset.CUSTOM
                    customLightColors = HyundaiLightColors
                    customDarkColors = HyundaiDarkColors
                    customTypography = HyundaiTypography
                    customBranding = HyundaiBranding
                    Chat360UIConfig = HyundaiConfig
                }
                chat360.startBot(this)
            }
        }
        findViewById<MaterialButton>(R.id.buttonOpenNativePocMaruti).setOnClickListener {
            chat360.coreConfig = CoreConfigs(nativePocBotId, applicationContext, flutter, meta, false, true).apply {
                themePreset = Chat360ThemePreset.CUSTOM
                customLightColors = MarutiSuzukiLightColors
                customDarkColors = MarutiSuzukiDarkColors
                customTypography = MarutiSuzukiTypography
                customBranding = MarutiSuzukiBranding
                Chat360UIConfig = MarutiSuzukiConfig
            }
            chat360.startBot(this)
        }
    }

    private fun authenticateHyundaiDealer(
        button: MaterialButton,
        onAuthenticated: () -> Unit,
    ) {
        button.isEnabled = false
        val request = Request.Builder()
            .url(HYUNDAI_DEALER_AUTH_URL)
            .header("Content-Type", "application/json")
            .header("Cookie", "multidb_pin_writes=y")
            .post("{\"dealer_code\":\"$HYUNDAI_DEALER_CODE\"}"
                .toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        httpClient.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                showDealerAuthFailure(button, "Unable to authenticate dealer. Please try again.")
            }

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                response.use {
                    runOnUiThread {
                        button.isEnabled = true
                        if (response.code == 200) {
                            onAuthenticated()
                        } else {
                            Toast.makeText(
                                this@MainActivity,
                                "Dealer authentication failed. Please try again.",
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    }
                }
            }
        })
    }

    private fun showDealerAuthFailure(button: MaterialButton, message: String) {
        runOnUiThread {
            button.isEnabled = true
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }
}
