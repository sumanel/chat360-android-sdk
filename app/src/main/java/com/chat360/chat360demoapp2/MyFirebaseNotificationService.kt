package com.chat360.chat360demoapp2

import android.util.Log
import com.chat360.chatbot.showChat360Notification
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class MyFirebaseNotificationService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("onNewToken", token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        Log.e("Notification_data",message.data.toString())
        if (message.data.containsKey("chat360_alert")) {
            showChat360Notification(
                applicationContext, message.data, message.data["chat360_alert"].toString()
            )
        }
    }
}