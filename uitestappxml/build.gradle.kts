import com.datadog.gradle.config.AndroidConfig
import com.datadog.gradle.config.configureFlavorForUiTest

plugins {
    id("com.android.application")
    kotlin("android")
}
android {
    namespace = "com.datadog.android.uitestappxml"
    compileSdk = AndroidConfig.TARGET_SDK

    defaultConfig {
        applicationId = "com.datadog.android.uitestappxml"
        minSdk = AndroidConfig.MIN_SDK
        targetSdk = AndroidConfig.TARGET_SDK
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        configureFlavorForUiTest(project.rootDir)
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {

    implementation(libs.androidXCoreKtx)
    implementation(libs.androidXAppCompat)
    implementation(libs.googleMaterial)
    implementation(libs.androidx.activity)
    implementation(libs.androidXConstraintLayout)
    testImplementation(libs.jUnit4)
    androidTestImplementation(libs.androidXTestJUnitExt)
    androidTestImplementation(libs.androidXEspressoCore)

    implementation(project(":features:dd-sdk-android-logs"))
    implementation(project(":features:dd-sdk-android-rum"))
    implementation(project(":features:dd-sdk-android-trace"))
    implementation(project(":features:dd-sdk-android-trace-otel"))
    implementation(project(":features:dd-sdk-android-ndk"))
    implementation(project(":features:dd-sdk-android-webview"))
    implementation(project(":features:dd-sdk-android-session-replay"))
    implementation(project(":features:dd-sdk-android-session-replay-material"))
    implementation(project(":integrations:dd-sdk-android-glide"))
    implementation(project(":integrations:dd-sdk-android-okhttp"))
}
