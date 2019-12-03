import com.datadog.gradle.Dependencies
import com.datadog.gradle.androidTestImplementation
import com.datadog.gradle.config.AndroidConfig
import com.datadog.gradle.config.kotlinConfig

plugins {
    id("com.android.library")
    id("androidx.benchmark")
    kotlin("android")
    kotlin("android.extensions")
    `maven-publish`
    id("com.github.ben-manes.versions")
    id("io.gitlab.arturbosch.detekt")
    id("org.jlleitschuh.gradle.ktlint")
    id("thirdPartyLicences")
    jacoco
}

android {

    compileSdkVersion(AndroidConfig.TARGET_SDK)
    buildToolsVersion(AndroidConfig.BUILD_TOOLS_VERSION)

    defaultConfig {
        minSdkVersion(AndroidConfig.MIN_SDK)
        targetSdkVersion(AndroidConfig.TARGET_SDK)
        versionCode = AndroidConfig.VERSION.code
        versionName = AndroidConfig.VERSION.name

        testInstrumentationRunner = "androidx.benchmark.junit4.AndroidBenchmarkRunner"
        testInstrumentationRunnerArgument("androidx.benchmark.suppressErrors", "EMULATOR,UNLOCKED")
    }

    // TODO when using Android Plugin 3.6.+
    // enableAdditionalTestOutput=true
    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    sourceSets.named("main") {
        java.srcDir("src/main/kotlin")
    }
    sourceSets.named("test") {
        java.srcDir("src/test/kotlin")
    }
    sourceSets.named("androidTest") {
        java.srcDir("src/androidTest/kotlin")
    }
}

repositories {
    google()
    mavenLocal()
    mavenCentral()
}


dependencies {
    implementation("com.datadoghq:dd-sdk-android:1.0.0-alpha1")
    implementation(Dependencies.Libraries.Kotlin)
    androidTestImplementation(Dependencies.Libraries.AndroidTestTools)
    androidTestImplementation(Dependencies.Libraries.JetpackBenchmark)
    androidTestImplementation(Dependencies.Libraries.OkHttpMock)

}

kotlinConfig()

