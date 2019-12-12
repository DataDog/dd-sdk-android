import com.datadog.gradle.Dependencies
import com.datadog.gradle.androidTestImplementation
import com.datadog.gradle.implementation
import com.datadog.gradle.config.AndroidConfig
import com.datadog.gradle.config.kotlinConfig
import com.datadog.gradle.Dependencies.Versions

plugins {
    id("com.android.application")
    kotlin("android")
    kotlin("android.extensions")
    `maven-publish`
    id("com.github.ben-manes.versions")
    id("io.gitlab.arturbosch.detekt")
    id("org.jlleitschuh.gradle.ktlint")
    jacoco
}

android {

    compileSdkVersion(AndroidConfig.TARGET_SDK)
    buildToolsVersion(AndroidConfig.BUILD_TOOLS_VERSION)

    defaultConfig {
        minSdkVersion(AndroidConfig.MIN_SDK)
        targetSdkVersion(AndroidConfig.TARGET_SDK)

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
    implementation(project(":dd-sdk-android"))
    implementation(Dependencies.Libraries.Gson)
    implementation(Dependencies.Libraries.Kotlin)
    implementation(Dependencies.Libraries.AndroidSupportBase)

    androidTestImplementation("net.wuerl.kotlin:assertj-core-kotlin:${Versions.AssertJ}")
    androidTestImplementation(Dependencies.Libraries.IntegrationTests)
    androidTestImplementation(Dependencies.Libraries.OkHttpMock)
}

kotlinConfig()
