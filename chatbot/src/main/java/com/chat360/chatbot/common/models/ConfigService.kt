package com.chat360.chatbot.common.models

import android.app.Application
import android.util.Log
import com.chat360.chatbot.common.CoreConfigs

class ConfigService {

    private var config: CoreConfigs

    init {
        config = CoreConfigs("", Application(),false,null, false)
    }

    companion object {
        private var configInstance: ConfigService? = null
        public fun getInstance(): ConfigService? {
            Log.d("chat-bot_getInstance","=================")
            if (configInstance == null) {
                synchronized(ConfigService::class.java) {
                    if (configInstance == null) {
                        configInstance = ConfigService()
                    }
                }
            }
            return configInstance
        }

    }

    fun setConfigData(config: CoreConfigs): Boolean {
        this.config = config
        return true
    }

    fun getConfig(): CoreConfigs {
        return config
    }
}