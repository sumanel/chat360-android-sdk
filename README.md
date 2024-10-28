# Chat360 Android SDK Documentation

## Overview
The Chat360 Android SDK allows you to integrate a chatbot into your Android application. This documentation provides a quick guide to get you started with the SDK.


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
       implementation 'com.github.sumanel:chat360-android-sdk:1.0.+'
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
        chat360.coreConfig = CoreConfigs(botId, applicationContext, flutter, meta, false)

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
- **isDebug**: A map of additional metadata Chat36O internals.


### UI Customization
You can customize the appearance of the chatbot interface using the following options:

- **statusBarColor**: Set the color of the status bar.
- **closeButtonColor**: Set the color of the close button.
- **statusBarColorFromHex**: Set the status bar color using a hexadecimal.
- **closeButtonColorFromHex**: Set the close button color using a hexadecimal.

## Example
Here’s a complete example of a simple implementation:

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
