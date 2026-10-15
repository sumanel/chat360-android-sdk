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
import androidx.compose.material3.OutlinedTextField
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
 * Test-user form: type any employee id/code and dealer id/code, sign in as them, open the chat (its menu is the
 * history), then switch to another employee and see that the previous one's history does not carry over. Recent users
 * are listed for one-tap switching. "Server history" asks `rooms/list` for the signed-in employee directly, to compare
 * with what the menu shows. The main screen's chat buttons run as the same user (see [DemoUserStore]).
 */
class LoginTestActivity : ComponentActivity() {
    private val store by lazy { DemoUserStore(applicationContext) }

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
        var user by remember { mutableStateOf(if (store.isSignedIn()) store.current() else null) }
        var recents by remember { mutableStateOf(store.recents()) }
        // The form starts on whoever was used last (or the build's default), so a small edit is all a switch needs.
        var empField by remember { mutableStateOf(store.current().empId) }
        var dealerField by remember { mutableStateOf(store.current().dealerId) }
        var formError by remember { mutableStateOf<String?>(null) }
        val log = remember { mutableStateListOf<String>() }
        val scope = rememberCoroutineScope()

        fun note(line: String) = log.add(0, line)

        fun signIn(next: DemoUser) {
            val from = user
            store.signIn(next)
            user = next
            recents = store.recents()
            empField = next.empId
            dealerField = next.dealerId
            formError = null
            note(
                when {
                    from == null -> "Signed in as ${next.empId} (dealer ${next.dealerId})"
                    from == next -> "Still ${next.empId} (dealer ${next.dealerId})"
                    else -> "Switched ${from.empId} -> ${next.empId} (local history of ${from.empId} is cleared when the chat opens)"
                },
            )
        }

        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Test user", style = MaterialTheme.typography.headlineSmall)
            Text("Base URL: ${BuildConfig.BASE_URL}\nBot: ${BuildConfig.BOT_ID}", style = MaterialTheme.typography.bodySmall)
            Text(
                user?.let { "Signed in as: ${it.empId}  (dealer ${it.dealerId})" } ?: "Not signed in",
                style = MaterialTheme.typography.titleMedium,
            )

            OutlinedTextField(
                value = empField,
                onValueChange = { empField = it; formError = null },
                label = { Text("Employee ID / code (emp_id)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = dealerField,
                onValueChange = { dealerField = it; formError = null },
                label = { Text("Dealer ID / code (dealer_id)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            formError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }

            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    val parsed = DemoUser.parse(dealer = dealerField, emp = empField)
                    if (parsed == null) formError = "Enter both an employee and a dealer id (no spaces)." else signIn(parsed)
                },
            ) { Text("Sign in as this employee") }

            if (recents.isNotEmpty()) {
                Text("Recent", style = MaterialTheme.typography.titleSmall)
                recents.forEach { recent ->
                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        enabled = user != recent,
                        onClick = { signIn(recent) },
                    ) { Text("${recent.empId}  ·  dealer ${recent.dealerId}") }
                }
            }

            HorizontalDivider()

            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = user != null,
                onClick = { openChat(user!!) },
            ) { Text("Open chat (menu = history)") }

            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                enabled = user != null,
                onClick = {
                    val emp = user!!.empId
                    note("Checking server history for $emp ...")
                    scope.launch { note(fetchServerHistory(emp)) }
                },
            ) { Text("Server history for current user") }

            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                enabled = user != null,
                onClick = {
                    Chat360().getInstance().clearUserData(applicationContext, BuildConfig.BOT_ID)
                    note("Logged out ${user?.empId} - local conversations, saved session and roles removed")
                    user = null
                    store.signOut()
                },
            ) { Text("Logout") }

            HorizontalDivider()
            Text("Log", style = MaterialTheme.typography.titleSmall)
            log.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }

    private fun openChat(user: DemoUser) {
        val chat360 = Chat360().getInstance()
        chat360.setBaseUrl(BuildConfig.BASE_URL)
        chat360.coreConfig = CoreConfigs(
            BuildConfig.BOT_ID,
            applicationContext,
            false,
            user.meta(),
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
            endUserId = user.empId
            salesExecutive = user.salesExecutive()
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
