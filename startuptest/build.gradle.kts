import com.datadog.gradle.config.AndroidConfig

plugins {
    id("com.android.test")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.datadog.android.startuptest"
    compileSdk = AndroidConfig.TARGET_SDK

    defaultConfig {
        minSdk = AndroidConfig.MIN_SDK
        targetSdk = AndroidConfig.TARGET_SDK
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Link the target app module
    targetProjectPath = ":uitestappxml"

    buildTypes {
        create("uitesting") {
            isDebuggable = true
            signingConfig = getByName("debug").signingConfig
            matchingFallbacks += listOf("debug")
        }
    }

    testOptions {
        animationsDisabled = true
    }
}

dependencies {
    implementation("androidx.test.ext:junit:1.3.0")
    implementation("androidx.test:rules:1.7.0")
    implementation("androidx.test.uiautomator:uiautomator:2.3.0")
}

androidComponents {
    beforeVariants(selector().all()) {
        if (it.buildType != "uitesting") {
            it.enable = false
        }
    }
}
