package com.chat360.chatbot.common.models

import android.app.Application
import android.content.Context
import android.util.Log
import android.content.SharedPreferences
import com.chat360.chatbot.common.CoreConfigs
import org.json.JSONException
import org.json.JSONObject

class ConfigService {

    private var config: CoreConfigs
    private var customBaseURL: String? = null
    private lateinit var sharedPreferences: SharedPreferences


    init {
        config = CoreConfigs("", Application(),false,null, false)
    }

    companion object {
        private var configInstance: ConfigService? = null
        public fun getInstance(context: Context): ConfigService? {
            if (configInstance == null) {
                synchronized(ConfigService::class.java) {
                    if (configInstance == null) {
                        configInstance = ConfigService()
                        configInstance?.setApplicationContext(context.applicationContext as Application)
                    }
                }
            }
            return configInstance
        }
    }

    fun setApplicationContext(context: Application) {
        sharedPreferences = context.getSharedPreferences("sdk_storage", Context.MODE_PRIVATE)
    }

    fun setBaseURL(baseURL: String?) : Unit {
        this.customBaseURL = "$baseURL/page?h="
    }

    fun setMetadata(metadata: Map<String, String>) {
        val jsonObject = JSONObject(metadata)
        val jsonString = jsonObject.toString()
        sharedPreferences.edit().putString("metadata", jsonString).apply()
    }

    fun getMetadata() : Map<String, String> {
        val jsonString = sharedPreferences.getString("metadata", null)
        val data = if (jsonString != null) {
            try {
                val jsonObject = JSONObject(jsonString)
                val map = mutableMapOf<String, String>()
                val keys = jsonObject.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    map[key] = jsonObject.getString(key)
                }
                map
            } catch (e: JSONException) {
                Log.e("ConfigService", "Failed to parse metadata from storage", e)
                emptyMap()
            }
        } else {
            emptyMap()
        }
        return data
    }

    fun setConfigData(config: CoreConfigs): Boolean {
        this.config = config
        return true
    }


    fun getBaseURL(): String? {
        return customBaseURL
    }

    fun getConfig(): CoreConfigs {
        return config
    }
}