# Chat360 Android SDK Documentation

## Overview
The Chat360 Android SDK allows you to integrate a chatbot into your Android application. This documentation provides a quick guide to get you started with the SDK.

The chat UI is a fully native Jetpack Compose screen backed by a native WebSocket/REST client — there is no WebView involved. The public API below (`Chat360`, `CoreConfigs`, `startBot`, `getChatBotView`, `setHandleWindowEvent`, `sendEventToBot`) is unchanged for existing integrations; `startBot()`/`getChatBotView()` now launch/return the native screen transparently.


## Setup

### 1. Add Dependencies
Add the necessary dependencies in your `build.gradle` file:

```groovy 
    allprojects {
	    repositories {
	                    ...
		                maven { url 'https://jitpack.io' }   
                    }
                }
                     
```

```groovy
dependencies {
       implementation 'com.github.sumanel:chat360-android-sdk:1.2.+'
}
```

### 2. Initialize the SDK
In your main activity (e.g., `MainActivity`), initialize the Chat360 instance in the `onCreate` method.

```kotlin
class MainActivity : AppCompatActivity() {
    private val botId = "your-bot-id" // Replace with your actual bot ID
    private val flutter = false
    private val meta = mapOf("Key" to "Value")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val chat360 = Chat360().getInstance()
        chat360.coreConfig = CoreConfigs(botId, applicationContext, flutter, meta, false, false)

        // Set custom base URL (optional)
        chat360.setBaseUrl("https://your-custom-domain.com")

        // Configure window event handler (Only works if your bot has window event component enabled)
        // Note: If your bot doesn't have this component enabled, this configuration will have no effect
        chat360.setHandleWindowEvent { eventData ->
            // Handle window events and return updated metadata
            val metaMap = when (eventData) {
                "user_connected" -> mapOf(
                    "connection_time" to java.time.ZonedDateTime.now().toString()
                )
                "message_sent" -> mapOf(
                    "last_message_time" to java.time.ZonedDateTime.now().toString()
                )
                else -> mapOf()
            }
            metaMap
        }

        // Custom configurations
        chat360.coreConfig!!.statusBarColor = R.color.purple_500
        chat360.coreConfig!!.closeButtonColor = R.color.white
        chat360.coreConfig!!.statusBarColorFromHex = "#4299E1"
        chat360.coreConfig!!.closeButtonColorFromHex = "#ffffff"
    }
}
```

### 3. Launch the Chatbot
You can launch the chatbot from buttons or any UI elements in your activity.

```kotlin
findViewById<FloatingActionButton>(R.id.floatingActionButton).setOnClickListener {
    chat360.startBot(this)
}
```

## Configuration Options

### CoreConfigs
- **botId**: The unique identifier for your chatbot.
- **applicationContext**: The context of your application.
- **flutter**: Boolean indicating if Flutter integration is required.
- **meta**: A map of additional metadata for the chatbot.
- **useNewUI**: boolean to use new UI.
- **isDebug**: A additional debug tag for  Chat36O internals.
- **historyEnabled**: Whether previous conversation history is loaded when the chat opens. Defaults to `true`.
- **themePreset**: `Chat360ThemePreset.DEFAULT` (brand-neutral, no config needed) or `CUSTOM` (supply your own colors/typography/branding below). Defaults to `DEFAULT`.
- **customLightColors** / **customDarkColors**: Your own `Chat360Colors` palettes, used when `themePreset = CUSTOM`.
- **customTypography**: Your own `Chat360Typography` (fonts), used when `themePreset = CUSTOM`.
- **customBranding**: Your own `Chat360Branding` (title, logo, welcome copy), used when `themePreset = CUSTOM`.



### UI Customization

You can customize the appearance of the chatbot interface using the following options:

- **statusBarColor**: Set the color of the status bar.
- **closeButtonColor**: Set the color of the close button.
- **statusBarColorFromHex**: Set the status bar color using a hexadecimal.
- **closeButtonColorFromHex**: Set the close button color using a hexadecimal.

### Theming

By default the chat UI uses a brand-neutral look with no logo (falls back to an initial-letter avatar). The SDK does not ship any client-specific branding — to apply your own brand, switch to `CUSTOM` and supply your own colors/typography/branding:

```kotlin
chat360.coreConfig!!.themePreset = Chat360ThemePreset.CUSTOM
chat360.coreConfig!!.customBranding = Chat360Branding(
    botTitle = "Acme Support",
    logo = Chat360Logo.Remote("https://example.com/logo.png"),
)
chat360.coreConfig!!.customLightColors = Chat360Colors(/* ... */)
chat360.coreConfig!!.customDarkColors = Chat360Colors(/* ... */)
chat360.coreConfig!!.customTypography = Chat360Typography(/* ... */) // optional, defaults to the system font
```

Colors/branding fetched from the bot's own server-side appearance configuration (name, avatar, etc., when available) are layered on top of whichever preset you choose, so your `CUSTOM` values still act as sensible defaults.

### Advanced Configuration

#### Custom Base URL

You can set a custom base URL for the Chat360 service:

```kotlin
chat360.setBaseUrl("https://your-custom-domain.com")
```

#### Window Event Handler

The SDK allows you to handle various window events and dynamically update metadata based on these events. **Important: This functionality requires the window event component to be enabled for your bot. If your bot does not have this component enabled, the `setHandleWindowEvent` will have no effect. Please contact Chat360 support to enable this component for your bot.**

```kotlin
chat360.setHandleWindowEvent { eventData ->
    // Handle different types of events
    val metaMap = when (eventData) {
        "user_connected" -> mapOf(
            "connection_time" to java.time.ZonedDateTime.now().toString()
        )
        "message_sent" -> mapOf(
            "last_message_time" to java.time.ZonedDateTime.now().toString()
        )
        "user_typing" -> mapOf(
            "typing_timestamp" to java.time.ZonedDateTime.now().toString()
        )
        // Add more event handlers as needed
        else -> mapOf()
    }
    metaMap  // Return updated metadata
}
```

The event handler receives event data as a parameter and can return updated metadata as a Map. This is useful for:

- Tracking user interactions
- Adding dynamic timestamps
- Updating session information
- Responding to specific chat events

#### Added Support to send Event

```
chat360.sendEventToBot(Map<String,String>) 
```

## Example

Here's a complete example of a simple implementation:

```kotlin
class MainActivity : AppCompatActivity() {
    // Initialization and configuration...
    // (Refer to the previous sections)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        // ... Configuration code

        findViewById<MaterialButton>(R.id.buttonOpenActivity).setOnClickListener {
            chat360.startBot(this)
        }
    }
}
```

## Conclusion
Integrating the Chat360 Android SDK is straightforward and allows you to provide a seamless chatbot experience within your application.
