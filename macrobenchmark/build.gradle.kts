import com.datadog.gradle.config.AndroidConfig

plugins {
    id("com.android.test")
    kotlin("android")
}

android {
    namespace = "com.datadog.android.macrobenchmark"
    compileSdk = AndroidConfig.TARGET_SDK

    defaultConfig {
        minSdk = 23
        targetSdk = AndroidConfig.TARGET_SDK

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        testInstrumentationRunnerArguments["androidx.benchmark.suppressErrors"] = "EMULATOR"
    }

    buildTypes {
        // This benchmark buildType is used for benchmarking, and should function like your
        // release build (for example, with minification on). It"s signed with a debug key
        // for easy local/CI testing.
        create("benchmark") {
            isDebuggable = true
            signingConfig = getByName("debug").signingConfig
            matchingFallbacks += listOf("release")
        }
    }

    targetProjectPath = ":sample:benchmark"
    experimentalProperties["android.experimental.self-instrumenting"] = true
}

dependencies {
    implementation(libs.androidXTestJUnitExt)
    implementation(libs.androidXEspressoCore)
    implementation(libs.uiautomator)
    implementation(libs.benchmarkMacroJunit4)
}

androidComponents {
    beforeVariants(selector().all()) {
        it.enable = it.buildType == "benchmark"
    }
}