package com.chat360.chatbot

import android.app.*
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import com.chat360.chatbot.android.ChatActivity
import com.chat360.chatbot.common.Constants
import com.chat360.chatbot.common.models.ConfigService

fun showChat360Notification(
    applicationContext: Context, data: MutableMap<String, String>, alert: String
) {
    var isInBackground: Boolean = true
    val NOTIFICATION_CHANNEL_ID = applicationContext.packageName
    val NOTIFICATION_ID = 100

    val ii: Intent = Intent(applicationContext, ChatActivity::class.java)

    ii.data = Uri.parse("custom://" + System.currentTimeMillis())
    ii.action = "actionstring" + System.currentTimeMillis()
    ii.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP

    val myProcess = ActivityManager.RunningAppProcessInfo()
    ActivityManager.getMyMemoryState(myProcess)
    isInBackground =
        myProcess.importance != ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND

    val pi = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        PendingIntent.getActivity(applicationContext, 0, ii, PendingIntent.FLAG_IMMUTABLE)

    }
    else {
        PendingIntent.getActivity(applicationContext, 0, ii, PendingIntent.FLAG_UPDATE_CURRENT)
    }

    Constants.UNREAD_MESSAGE_COUNT += 1
    if(isInBackground){
        val notification: Notification =
            NotificationCompat.Builder(applicationContext, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(getNotificationIcon(applicationContext)!!).setAutoCancel(true).setContentText(alert)
                .setContentIntent(pi)
                .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
                .setContentTitle(data["title"]).build()
        ii.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        val notificationManager = applicationContext.getSystemService(
            Context.NOTIFICATION_SERVICE
        ) as NotificationManager

        notificationManager.notify(NOTIFICATION_ID, notification)
    }


}

private fun getNotificationIcon(context: Context?): Int? {
    val useWhiteIcon = true
    return if (useWhiteIcon) context?.let { ConfigService.getInstance(it)?.getConfig()?.notificationSmallIcon }
    else context?.let { ConfigService.getInstance(it)?.getConfig()?.notificationLargeIcon }
}
