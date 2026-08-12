package com.chat360.chat360demoapp


import android.content.Intent
import android.os.Build
import android.util.Log
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
import org.json.JSONObject


class MainActivity : AppCompatActivity() {
    companion object {
        // Hyundai's own dealer-validation gate, unrelated to Chat360's rooms/history API -
        // this app owns the URL, the request shape, and the identifiers entirely.
        private const val HYUNDAI_EMPLOYEE_AUTH_URL =
            "https://app.chat360.io/api/client_hyundai_lms/sales-executive/validate/"
        private const val HYUNDAI_DEALER_CODE = "DLR001"
        private const val HYUNDAI_EMPLOYEE_CODE = "EMP1001"
        private const val HYUNDAI_EMPLOYEE_NAME = "Rahul Sharma"
        private const val HYUNDAI_EMPLOYEE_STATUS = "ACTIVE"
    }

    private val httpClient = OkHttpClient()
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
            var metaMap: Map<String, String> = mapOf(
                "dealer_id" to HYUNDAI_DEALER_CODE,
                "emp_id" to HYUNDAI_EMPLOYEE_CODE,
            )
            if(eventData["type"] == "get_auth_chat360") {
                 metaMap = metaMap + mapOf(
                    "token" to "New Token from app",
                )
            } else if(eventData["type"] == "get_date") {
                metaMap = metaMap + mapOf(
                    "dynamic_date" to  java.time.ZonedDateTime.now().toString()
                )
            }

//            Handler(Looper.getMainLooper()).postDelayed({
//                chat360.sendEventToBot(mapOf(
//                    "type" to "initiate_payment_chat360",
//                    "payment_status" to "0",
//                    "message" to "not able to payment"))
//
//            }, 10_000)
            chat360.sendEventToBot(metaMap)

            metaMap
        }


        // To Change the color of status bar, by default it will pick app theme
        chat360.coreConfig!!.statusBarColor = R.color.purple_500

        // To Change the color of close button, default color is white
        chat360.coreConfig!!.closeButtonColor = R.color.white

        /* Note: if color is set from both setStatusBarColor and statusBarColorFromHex,
         * statusBarColorFromHex will take priority
         * */
        chat360.coreConfig!!.statusBarColorFromHex = "#4299E1"

        /* Note: if color is set from both closeButtonColor and closeButtonColorHex,
         * closeButtonColorHex will take priority
         * */
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
            hyundaiButton.isEnabled = true
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
}
