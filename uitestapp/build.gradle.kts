import com.datadog.gradle.config.AndroidConfig
import com.datadog.gradle.config.dependencyUpdateConfig
import com.datadog.gradle.config.java17
import com.datadog.gradle.config.junitConfig
import com.datadog.gradle.config.kotlinConfig
import com.datadog.gradle.plugin.InstrumentationMode

plugins {
    id("com.android.application")
    kotlin("android")
    kotlin("kapt")
    kotlin("plugin.serialization")
    id("kotlin-parcelize")
    alias(libs.plugins.datadogGradlePlugin)
    id("transitiveDependencies")
}

@Suppress("StringLiteralDuplication")
android {
    namespace = "com.datadog.android.uitestapp"
    compileSdk = AndroidConfig.TARGET_SDK
    buildToolsVersion = AndroidConfig.BUILD_TOOLS_VERSION

    defaultConfig {
        minSdk = AndroidConfig.MIN_SDK
        targetSdk = AndroidConfig.TARGET_SDK
        versionCode = AndroidConfig.VERSION.code
        versionName = AndroidConfig.VERSION.name
        multiDexEnabled = true

        buildFeatures {
            buildConfig = true
        }
        vectorDrawables.useSupportLibrary = true
    }
    compileOptions {
        java17()
    }

    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = libs.versions.androidXComposeRuntime.get()
    }

    buildTypes {
        create("uitesting") {
            isDebuggable = true
            signingConfig = getByName("debug").signingConfig
            matchingFallbacks += listOf("debug")
        }
    }
}

datadog {
    composeInstrumentation = InstrumentationMode.AUTO
}

dependencies {

    implementation(libs.kotlin)

    // Android dependencies
    implementation(libs.adapterDelegatesViewBinding)
    implementation(libs.androidXMultidex)
    implementation(libs.bundles.androidXNavigation)
    implementation(libs.androidXAppCompat)
    implementation(libs.androidXConstraintLayout)
    implementation(libs.androidXLifecycleCompose)
    implementation(libs.googleMaterial)
    implementation(libs.bundles.glide)
    implementation(libs.timber)
    implementation(platform(libs.androidXComposeBom))
    implementation(libs.material3Android)
    implementation(libs.bundles.androidXCompose)
    implementation(libs.coilCompose)
    implementation(libs.daggerLib)
    kapt(libs.daggerCompiler)
    kapt(libs.glideCompiler)
    implementation(libs.coroutinesCore)
    implementation(libs.bundles.ktorClient)
    implementation(libs.kotlinxSerializationJson)
    implementation(project(":features:dd-sdk-android-logs"))
    implementation(project(":features:dd-sdk-android-rum"))
    implementation(project(":features:dd-sdk-android-trace"))
    implementation(project(":features:dd-sdk-android-trace-otel"))
    implementation(project(":features:dd-sdk-android-ndk"))
    implementation(project(":features:dd-sdk-android-webview"))
    implementation(project(":features:dd-sdk-android-session-replay"))
    implementation(project(":features:dd-sdk-android-session-replay-material"))
    implementation(project(":features:dd-sdk-android-session-replay-compose"))
    implementation(project(":integrations:dd-sdk-android-compose"))
    implementation(project(":integrations:dd-sdk-android-glide"))
    implementation(project(":integrations:dd-sdk-android-okhttp"))
    implementation(project(":tools:benchmark"))

    testImplementation(libs.bundles.jUnit5)
    testImplementation(libs.bundles.testTools)
    testImplementation(libs.systemStubsJupiter)
    testImplementation(libs.ktorClientMock)
}

kotlinConfig()
junitConfig()
dependencyUpdateConfig()
