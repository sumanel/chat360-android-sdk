# Chatbot Room cache implementation

## What changed

- Added a Room database named `chat360_chat_cache.db` with two tables:
  - `chat_conversations` stores a conversation ID, bot ID, optional server room ID, title, and timestamps.
  - `chat_messages` stores ordered conversation entries. Incoming websocket/history/starter envelopes are saved unchanged as `RAW`; locally submitted user messages are saved as `USER`.
- Added `ChatCacheRepository` as the cache boundary used by `ChatViewModel`.
- The websocket repository now exposes each incoming raw envelope to the view model. Renderable bot envelopes from the websocket, REST history, and conversation starter are committed to Room before the callback returns; this prevents bot messages being lost when the screen closes quickly. Restoring uses the same message parser and retains rich bot-message rendering.
- When a server room has cached messages, the SDK restores those messages from Room first for an instant paint, then always fetches the room's latest history page from the server and overwrites both the on-screen messages and the Room cache with it - so messages that landed elsewhere (another device/session) since the cache was last written still show up. A response that comes back empty never overwrites an already-populated cache/display, to avoid a transient backend hiccup wiping out real local history. A locally-sent (`USER`) message the backend hasn't indexed into its own history endpoint yet (e.g. sent right before a fast room switch) is carried forward into the replacement instead of being wiped by it, and keeps reappearing at the end of the transcript until a later refresh's history page genuinely includes it.
- Cache replay explicitly bypasses the optional `suppressInitialBotMessages` setting. That setting applies only to a new live session and must not hide bot messages from an existing saved conversation after an app restart.
- “New chat” now creates a separate Room conversation before clearing the UI. New user and websocket messages are saved under that conversation.
- The history drawer now observes Room and displays saved conversations instead of placeholder rows. Selecting an entry replays its cached messages.
- Added Room runtime, Kotlin coroutine support, and the KSP-based Room compiler. KSP is used instead of KAPT so the project remains compatible with modern Android Studio JDKs.

## Cache behavior

Conversation titles are derived from the latest non-empty user message (trimmed to 80 characters). The cache is scoped by bot ID, so conversations for different bots do not appear together. Room keeps the data locally on the device until the host app’s storage is cleared or the database is deleted.

## Files added

- `chatbot/src/main/java/com/chat360/chatbot/cache/ChatCacheDatabase.kt`
- `chatbot/src/main/java/com/chat360/chatbot/cache/ChatCacheRepository.kt`

## Verification

`git diff --check` passes. `:chatbot:compileReleaseKotlin` and `:app:assembleRelease` pass when Gradle runs with Java 21. The project’s Kotlin 1.9 KAPT integration is not compatible with Java 25, so Room uses KSP instead.

## Gradle JDK requirement

AGP 8.5.2 and Kotlin 1.9.0 in this project must run Gradle on Java 17 or Java 21. Java 25 is unsupported and causes the KAPT `IllegalAccessError` shown in the original build log (and can also fail AGP before compilation). In Android Studio, select a Java 17/21 JDK under **Settings → Build, Execution, Deployment → Build Tools → Gradle → Gradle JDK**.
