package com.chat360.chatbot.common

import android.content.Context
import android.graphics.drawable.Drawable
import androidx.annotation.NonNull
import com.chat360.chatbot.R
import dagger.hilt.android.qualifiers.ApplicationContext

public class CoreConfigs constructor(@NonNull botId: String, applicationContext: Context, flutter: Boolean) {
    var botId: String? = botId
   // var ymAuthenticationToken = ""
    @Deprecated("not currently in use") var deviceToken = ""
    var statusBarColor = -1
    var statusBarColorFromHex = ""
    var showCloseButton = true
    var closeButtonColor = -1
    var closeButtonColorFromHex = ""
    var version = 1
    var notificationSmallIcon = -1
    var notificationLargeIcon = -1
    var flutter: Boolean = flutter
/*
    init {
        this.botId = botId
    }*/
}