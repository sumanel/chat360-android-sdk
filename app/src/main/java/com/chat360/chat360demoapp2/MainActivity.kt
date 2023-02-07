package com.chat360.chat360demoapp2

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.TextView
import android.widget.Toast
import com.chat360.chat360demoapp.R
import com.chat360.chatbot.common.Chat360
import com.chat360.chatbot.common.CoreConfigs
import com.google.android.gms.tasks.OnCompleteListener
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.messaging.FirebaseMessaging

class MainActivity : AppCompatActivity() {
    private val botId = "949aa72d-0317-4de6-acac-2fdef98aa7b0"

    //Todo - Remove FCM in Release and Make User to add new Token
    private var fcmToken = ""
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val chat360 = Chat360().getInstance()
        chat360.coreConfig = CoreConfigs(botId, applicationContext)

        //askNotificationPermission()
        FirebaseMessaging.getInstance().token.addOnCompleteListener(OnCompleteListener { task ->
            if (!task.isSuccessful) {
                Toast.makeText(this, task.exception.toString(), Toast.LENGTH_SHORT).show()
                Log.e(
                    "msg",
                    "Fetching FCM registration token failed",
                    task.exception
                )
                return@OnCompleteListener
            }
            // Get new FCM registration token
            fcmToken = task.result
            // Log and toast
            Log.d("fcm_token", fcmToken)

            //To receive notifications on relevant Device
            chat360.coreConfig!!.deviceToken = fcmToken

        })
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

        //To set the Small Notification Icon
        chat360.coreConfig!!.notificationSmallIcon = R.drawable.chat360logo

        //To set the Large Notification Icon
        chat360.coreConfig!!.notificationLargeIcon = R.drawable.chat360logo

        //To get Unread Message Count in Integer
        val textViewUnreadMessageCount = findViewById<TextView>(R.id.textViewUnreadMessageCount)
        if(chat360.getUnreadMessageCount()<=0){
            textViewUnreadMessageCount.visibility = View.INVISIBLE
        }
        else{
            textViewUnreadMessageCount.visibility = View.VISIBLE
            textViewUnreadMessageCount.text = chat360.getUnreadMessageCount().toString()
        }

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

    // Declare the launcher at the top of your Activity/Fragment:
/*    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            // FCM SDK (and your app) can post notifications.
        } else {
            // TODO: Inform user that that your app will not show notifications.

        }
    }

    private fun askNotificationPermission() {
        // This is only necessary for API level >= 33 (TIRAMISU)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            ) {
                // FCM SDK (and your app) can post notifications.
            } else if (shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
                // TODO: display an educational UI explaining to the user the features that will be enabled
                //       by them granting the POST_NOTIFICATION permission. This UI should provide the user
                //       "OK" and "No thanks" buttons. If the user selects "OK," directly request the permission.
                //       If the user selects "No thanks," allow the user to continue without notifications.
            } else {
                // Directly ask for the permission
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }*/

}