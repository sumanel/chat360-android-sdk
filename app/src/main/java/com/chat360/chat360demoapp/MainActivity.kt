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
        private const val HYUNDAI_EMPLOYEE_CODE = "EMP1001"
        private const val HYUNDAI_EMPLOYEE_NAME = "Rahul Sharma"
        private const val HYUNDAI_EMPLOYEE_STATUS = "ACTIVE"
    }

    private val httpClient = OkHttpClient()
    private val nativePocBotId = "0f22919b-fa77-4ddf-a26e-2dace99e3f83"
    private val botId = nativePocBotId
    private val flutter = false
    private val meta = mapOf(
        "dealer_id" to "123",
        "emp_id" to "4567",
    )

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val chat360 = Chat360().getInstance()
        chat360.coreConfig = CoreConfigs(botId, applicationContext, flutter, meta, false,true)

        chat360.setBaseUrl("https://staging.chat360.io");
        // dealer_id/emp_id reach the bot via `meta` at session-init (CoreConfigs.meta above) -
        // this callback isn't where the flow's @variables get their values from anymore.
        // A response still has to be returned here on every call, though: this bot's
        // WINDOW_EVENT nodes have should_receive_data=true, so the flow stays blocked until the
        // host answers - returning the same `meta` map (re-echoing it back) is enough to satisfy
        // that regardless of what the node actually asked for. ChatRepository forwards a
        // non-empty return value to the bot on its own, so calling chat360.sendEventToBot(...)
        // here too would send it twice.
        chat360.setHandleWindowEvent { eventData ->
            when (eventData["type"]) {
                "get_auth_chat360" -> meta + mapOf("token" to "New Token from app")
                "get_date" -> meta + mapOf("dynamic_date" to java.time.ZonedDateTime.now().toString())
                else -> meta
            }
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
            ChatComposeActivity.launch(
                this,
                botId = nativePocBotId,
                baseUrl = "https://staging.chat360.io",
                themePreset = Chat360ThemePreset.DEFAULT,
                clientId = "6344bb99-7cd7-4985-b86f-3da0a0ee1647",
                apiKey = "sPZq65Op.oabnSyIyxWDWI5XzgjWwPx7bfXfLpW4N",
                endUserId = HYUNDAI_EMPLOYEE_CODE,
            )
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
                        apiKey = "sPZq65Op.oabnSyIyxWDWI5XzgjWwPx7bfXfLpW4N"
                        clientId = "6344bb99-7cd7-4985-b86f-3da0a0ee1647"
                        endUserId = HYUNDAI_EMPLOYEE_CODE
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
