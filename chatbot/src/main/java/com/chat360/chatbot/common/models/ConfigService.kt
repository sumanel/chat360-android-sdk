package com.chat360.chatbot.common.models

import android.app.Application
import com.chat360.chatbot.common.CoreConfigs

class ConfigService {

    private var config: CoreConfigs
    private var baseUrl: String? = null

    object WebEventHandler {
        var handleWindowEvent: ((Map<String, String>) -> Map<String, String>)? = null
        var sendEventToBot: ((Map<String, String>) -> Unit)? = { event ->
            Chat360JSBridge.send("CHAT360_WINDOW_EVENT", event)
        }
    }

    init {
        config = CoreConfigs("", Application(),false,null, false, false)
    }

    companion object {
        private var configInstance: ConfigService? = null
        public fun getInstance(): ConfigService? {
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

    fun setBaseUrl(url: String) {
        baseUrl = url;
    }

    fun getBaseUrl(): String? {
        return baseUrl;
    }
}