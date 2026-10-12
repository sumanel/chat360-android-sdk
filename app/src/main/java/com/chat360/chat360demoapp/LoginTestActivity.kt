package com.chat360.chat360demoapp

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.chat360.chatbot.common.Chat360
import com.chat360.chatbot.common.CoreConfigs
import com.chat360.chatbot.domain.thirdparty.ThirdPartyTokenManager
import com.chat360.chatbot.network.rest.thirdparty.ThirdPartyTasksApiService
import com.chat360.chatbot.ui.theme.Chat360ThemePreset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Test screen for login / logout / switching user under the same dealer: pick EMP1001 or EMP1002, open the
 * chat (its menu is the history), switch, and see that the other user's history does not carry over.
 * "Server history" asks `rooms/list` for the signed-in user directly, to compare with what the menu shows.
 */
class LoginTestActivity : ComponentActivity() {
    private val users = listOf("EMP1001", "EMP1002")
    private val prefs by lazy { getSharedPreferences("demo_login_test", Context.MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) { Screen() }
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun Screen() {
        var user by remember { mutableStateOf(prefs.getString("user", null)) }
        val log = remember { mutableStateListOf<String>() }
        val scope = rememberCoroutineScope()

        fun note(line: String) = log.add(0, line)

        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Login / history test", style = MaterialTheme.typography.headlineSmall)
            Text("Dealer: ${BuildConfig.DEALER_ID}   Base URL: ${BuildConfig.BASE_URL}")
            Text(
                if (user == null) "Not logged in" else "Logged in as: $user",
                style = MaterialTheme.typography.titleMedium,
            )

            users.forEach { emp ->
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = user != emp,
                    onClick = {
                        val from = user
                        user = emp
                        prefs.edit().putString("user", emp).apply()
                        note(if (from == null) "Logged in as $emp" else "Switched $from -> $emp (local history of $from is cleared when the chat opens)")
                    },
                ) { Text("Login as $emp") }
            }

            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = user != null,
                onClick = { openChat(user!!) },
            ) { Text("Open chat (menu = history)") }

            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                enabled = user != null,
                onClick = {
                    val emp = user!!
                    note("Checking server history for $emp ...")
                    scope.launch { note(fetchServerHistory(emp)) }
                },
            ) { Text("Server history for current user") }

            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                enabled = user != null,
                onClick = {
                    Chat360().getInstance().clearUserData(applicationContext, BuildConfig.BOT_ID)
                    note("Logged out $user - local conversations, saved session and roles removed")
                    user = null
                    prefs.edit().remove("user").apply()
                },
            ) { Text("Logout") }

            HorizontalDivider()
            Text("Log", style = MaterialTheme.typography.titleSmall)
            log.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }

    private fun openChat(emp: String) {
        val chat360 = Chat360().getInstance()
        chat360.setBaseUrl(BuildConfig.BASE_URL)
        chat360.coreConfig = CoreConfigs(
            BuildConfig.BOT_ID,
            applicationContext,
            false,
            mapOf("dealer_id" to BuildConfig.DEALER_ID, "emp_id" to emp),
            false,
            true,
        ).apply {
            themePreset = Chat360ThemePreset.CUSTOM
            customLightColors = HyundaiLightColors
            customDarkColors = HyundaiDarkColors
            customTypography = HyundaiTypography
            customBranding = HyundaiBranding
            Chat360UIConfig = HyundaiConfig
            apiKey = BuildConfig.API_KEY
            clientId = BuildConfig.CLIENT_ID
            endUserId = emp
            salesExecutive = mapOf("dealer_code" to BuildConfig.DEALER_ID, "emp_code" to emp)
        }
        chat360.startBot(this)
    }

    private suspend fun fetchServerHistory(emp: String): String = withContext(Dispatchers.IO) {
        runCatching {
            val api = ThirdPartyTasksApiService(BuildConfig.BASE_URL)
            val token = ThirdPartyTokenManager(api, BuildConfig.CLIENT_ID, BuildConfig.API_KEY).validToken()
            val page = api.fetchRoomsList(token, agentId = emp, limit = 50, offset = 0)
            val rooms = page.rooms.joinToString(", ") { it.roomName.ifBlank { it.roomId.take(8) } }
            "Server rooms for $emp: ${page.rooms.size}${if (page.hasMore) "+" else ""}" + if (rooms.isNotEmpty()) " ($rooms)" else ""
        }.getOrElse { "Server history for $emp failed: ${it.message}" }
    }
}
