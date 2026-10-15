package com.chat360.chat360demoapp

import android.content.Context

/**
 * An employee under a dealer - who the demo chat runs as. Entered in the test-user form (see [LoginTestActivity]) so
 * several employees can be tried on one build.
 */
data class DemoUser(val dealerId: String, val empId: String) {

    /** `meta` for session-init: the bot flow reads these as `@dealer_id` / `@emp_id`. */
    fun meta(): Map<String, String> = mapOf("dealer_id" to dealerId, "emp_id" to empId)

    /** The sales-executive check (`third-party-tasks/sales-exectives`) takes the same two codes. */
    fun salesExecutive(): Map<String, String> = mapOf("dealer_code" to dealerId, "emp_code" to empId)

    companion object {
        private const val SEPARATOR = '|'

        /** Both ids, trimmed; null when either is missing or has a space or `|` inside it. */
        fun parse(dealer: String, emp: String): DemoUser? {
            val d = dealer.trim()
            val e = emp.trim()
            if (d.isEmpty() || e.isEmpty()) return null
            if (listOf(d, e).any { id -> id.any { it.isWhitespace() || it == SEPARATOR } }) return null
            return DemoUser(d, e)
        }

        fun encodeList(users: List<DemoUser>): String = users.joinToString("\n") { "${it.dealerId}$SEPARATOR${it.empId}" }

        /** Reads a saved list back, skipping any damaged line. */
        fun decodeList(saved: String): List<DemoUser> = saved.lineSequence().mapNotNull { line ->
            val parts = line.split(SEPARATOR)
            if (parts.size == 2) parse(parts[0], parts[1]) else null
        }.toList()
    }
}

/** [user] first (moved up if already present), at most [max] kept. */
fun List<DemoUser>.withMostRecent(user: DemoUser, max: Int = 10): List<DemoUser> = (listOf(user) + filter { it != user }).take(max)

/**
 * Remembers the current test user and the recent ones. Starts as the build flavor's own dealer and employee, so the
 * demo behaves as before until someone uses the form.
 */
class DemoUserStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("demo_users", Context.MODE_PRIVATE)

    /** The user the chat runs as: the last one chosen in the form, else the build flavor's default. */
    fun current(): DemoUser = DemoUser.parse(prefs.getString("dealer", null).orEmpty(), prefs.getString("emp", null).orEmpty())
        ?: DemoUser(BuildConfig.DEALER_ID, BuildConfig.EMP_ID)

    /** True once someone chose a user in the form (and has not logged out since). */
    fun isSignedIn(): Boolean = prefs.getBoolean("signed_in", false)

    fun signIn(user: DemoUser) {
        prefs.edit()
            .putString("dealer", user.dealerId).putString("emp", user.empId).putBoolean("signed_in", true)
            .putString("recent", DemoUser.encodeList(recents().withMostRecent(user)))
            .apply()
    }

    fun signOut() = prefs.edit().putBoolean("signed_in", false).apply()

    fun recents(): List<DemoUser> = DemoUser.decodeList(prefs.getString("recent", null).orEmpty())
}
