package com.chat360.chat360demoapp2

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.chat360.chat360demoapp.R
import com.chat360.chatbot.common.Chat360
import com.chat360.chatbot.common.CoreConfigs
import com.google.android.gms.tasks.OnCompleteListener
import com.google.firebase.messaging.FirebaseMessaging

class ChatBotDemoActivity : AppCompatActivity() {
    private val botId = "949aa72d-0317-4de6-acac-2fdef98aa7b0"
    private var fcmToken = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat_bot_demo)
        loadFragment(ChatBotDemoFragment())
    }

    private fun initialiseBot(): Chat360 {
        //Get Chat360 instance
        val chat360 = Chat360().getInstance()
        chat360.coreConfig = CoreConfigs(botId, applicationContext)

        FirebaseMessaging.getInstance().token.addOnCompleteListener(OnCompleteListener { task ->
            if (!task.isSuccessful) {
                Toast.makeText(this, task.exception.toString(), Toast.LENGTH_SHORT).show()
                Log.e(
                    "msg", "Fetching FCM registration token failed", task.exception
                )
                return@OnCompleteListener
            }
            // Get new FCM registration token
            fcmToken = task.result
            Log.d("msg", fcmToken)
            chat360.coreConfig!!.deviceToken = fcmToken

        })
        chat360.coreConfig!!.deviceToken = fcmToken

        // To Change the color of status bar, by default it will pick app theme
        chat360.coreConfig!!.statusBarColor = R.color.purple_500

        // To Change the color of close button, default color is white
        chat360.coreConfig!!.closeButtonColor = R.color.white

        /* Note: if color is set from both setStatusBarColor and statusBarColorFromHex,
         * statusBarColorFromHex will take priority
         * */
        // To set statusBarColor from hexadecimal color code
        chat360.coreConfig!!.statusBarColorFromHex = "#49c656"

        /* Note: if color is set from both closeButtonColor and closeButtonColorHex,
         * closeButtonColorHex will take priority
         * */
        // To set closeButtonColor from hexadecimal color code
        chat360.coreConfig!!.closeButtonColorFromHex = "#ffffff"
        return chat360
    }

    private fun loadFragment(frag: Fragment) {

        val transaction = supportFragmentManager.beginTransaction()
        transaction.replace(R.id.fragmentContainerViewChatBot, frag)
        transaction.addToBackStack(null)
        transaction.commit()
    }

    fun showBotView() {
        val chat360 = initialiseBot()
        loadFragment(chat360.getChatBotView(this)!!)
    }

    override fun onBackPressed() {
        try {
            if (supportFragmentManager.backStackEntryCount == 1) {
                finish()
            }
            else {
                onBackPressedDispatcher.onBackPressed()
            }
        } catch (e: Exception) {
            //Some problem occurred please relaunch the bot
            finish()
        }
    }
}