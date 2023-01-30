package com.chat360.chatbot.common

import android.content.Context
import androidx.annotation.NonNull
import dagger.hilt.android.qualifiers.ApplicationContext

public class CoreConfigs constructor(@NonNull botId: String,applicationContext: Context) {
    var botId: String? = botId
    var ymAuthenticationToken = ""
    var deviceToken = ""
    var statusBarColor = -1
    var statusBarColorFromHex = ""
    var showCloseButton = true
    var closeButtonColor = -1
    var closeButtonColorFromHex = ""
    var version = 1

/*
    init {
        this.botId = botId
    }*/
}