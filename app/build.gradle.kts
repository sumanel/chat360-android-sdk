plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
    kotlin("plugin.serialization") version "1.5.0"
}

android {
    namespace = "com.chat360.chat360demoapp"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.chat360.chat360demoapp"
        minSdk = 23
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

    }

    // Two environments for the Hyundai demo bot config (botId/baseUrl/clientId/apiKey/dealer &
    // emp meta) - "dev" is the POC bot on the dev.chat360.io test platform, "prod" is the live production bot's
    // credentials. Selecting the variant (dev*/prod*) picks which set MainActivity reads via
    // BuildConfig; the demo app is built/shipped from the prod variant by default.
    flavorDimensions += "environment"
    productFlavors {
        create("dev") {
            dimension = "environment"
            buildConfigField("String", "BOT_ID", "\"0f22919b-fa77-4ddf-a26e-2dace99e3f83\"")
            buildConfigField("String", "BASE_URL", "\"https://dev.chat360.io\"")
            buildConfigField("String", "CLIENT_ID", "\"6344bb99-7cd7-4985-b86f-3da0a0ee1647\"")
            buildConfigField("String", "API_KEY", "\"sPZq65Op.oabnSyIyxWDWI5XzgjWwPx7bfXfLpW4N\"")
            buildConfigField("String", "DEALER_ID", "\"A10AA\"")
            buildConfigField("String", "EMP_ID", "\"EMP101\"")
            // The sales executive the demo reports to `third-party-tasks/sales-exectives`.
            buildConfigField("String", "SALES_DEALER_CODE", "\"A10AA\"")
            buildConfigField("String", "SALES_EMP_CODE", "\"EMP101\"")
        }
        create("staging") {
            dimension = "environment"
            buildConfigField("String", "BOT_ID", "\"87a0f7c9-3499-4887-998b-0ca3c0d33834\"")
            buildConfigField("String", "BASE_URL", "\"https://staging.chat360.io\"")
            buildConfigField("String", "CLIENT_ID", "\"c7b9a9a6-1ea9-41cc-a9e5-325489c83efb\"")
            buildConfigField("String", "API_KEY", "\"X4XcW5Z6.7FbJfyoX2g3roXAcRy9shhaXxgSR0Zxi\"")
            buildConfigField("String", "DEALER_ID", "\"D1\"")
            buildConfigField("String", "EMP_ID", "\"EMP1\"")
            buildConfigField("String", "SALES_DEALER_CODE", "\"D1\"")
            buildConfigField("String", "SALES_EMP_CODE", "\"EMP1\"")
        }
        create("prod") {
            dimension = "environment"
            buildConfigField("String", "BOT_ID", "\"2e97deac-2877-495f-a568-8e0e5438fec1\"")
            buildConfigField("String", "BASE_URL", "\"https://app.chat360.io\"")
            buildConfigField("String", "CLIENT_ID", "\"c7b9a9a6-1ea9-41cc-a9e5-325489c83efb\"")
            buildConfigField("String", "API_KEY", "\"X4XcW5Z6.7FbJfyoX2g3roXAcRy9shhaXxgSR0Zxi\"")
            buildConfigField("String", "DEALER_ID", "\"A10AA\"")
            buildConfigField("String", "EMP_ID", "\"EMP101\"")
            // Production doesn't serve the sales-exectives route yet (404), which the SDK treats as "let them through".
            buildConfigField("String", "SALES_DEALER_CODE", "\"A10AA\"")
            buildConfigField("String", "SALES_EMP_CODE", "\"EMP101\"")
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                    getDefaultProguardFile("proguard-android-optimize.txt"),
                    "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.1"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.navigation.runtime.ktx)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(project(":chatbot"))
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
