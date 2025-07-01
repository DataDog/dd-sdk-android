import com.datadog.gradle.config.configureFlavorForBenchmark2

plugins {
    id("com.android.library")
    kotlin("android")
    id("androidx.benchmark")
}

android {
    namespace = "com.datadog.android.macrobenchmark"
    compileSdk = 36

    defaultConfig {
        minSdk = 24
        targetSdk = 36

        testInstrumentationRunner = "androidx.benchmark.junit4.AndroidBenchmarkRunner"
        testInstrumentationRunnerArguments["androidx.benchmark.suppressErrors"] = "EMULATOR,LOW-BATTERY"

        configureFlavorForBenchmark2(project.rootDir)
    }

    buildFeatures {
        buildConfig = true
    }

    testBuildType = "release"
    buildTypes {
        debug {
            // Since isDebuggable can"t be modified by gradle for library modules,
            // it must be done in a manifest - see src/androidTest/AndroidManifest.xml
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "benchmark-proguard-rules.pro"
            )
        }
        release {
            isDefault = true
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
    androidTestImplementation(libs.androidXTestRunner)
    androidTestImplementation(libs.androidXTestJUnitExt)
    androidTestImplementation(libs.jUnit4)
    androidTestImplementation(libs.benchmarkJunit4)

    androidTestImplementation(project(":features:dd-sdk-android-logs"))
    androidTestImplementation(project(":features:dd-sdk-android-rum"))
    androidTestImplementation(project(":features:dd-sdk-android-trace"))
    androidTestImplementation(project(":features:dd-sdk-android-trace-otel"))
    androidTestImplementation(project(":features:dd-sdk-android-ndk"))
    androidTestImplementation(project(":features:dd-sdk-android-webview"))
    androidTestImplementation(project(":features:dd-sdk-android-session-replay"))
    androidTestImplementation(project(":features:dd-sdk-android-session-replay-material"))
    androidTestImplementation(project(":features:dd-sdk-android-session-replay-compose"))
    androidTestImplementation(project(":integrations:dd-sdk-android-compose"))
    androidTestImplementation(project(":integrations:dd-sdk-android-glide"))
    androidTestImplementation(project(":integrations:dd-sdk-android-okhttp"))
    androidTestImplementation(project(":tools:benchmark"))
    // Add your dependencies here. Note that you cannot benchmark code
    // in an app module this way - you will need to move any code you
    // want to benchmark to a library module:
    // https://developer.android.com/studio/projects/android-library#Convert

}
