import com.datadog.gradle.Dependencies
import com.datadog.gradle.Dependencies.Versions
import com.datadog.gradle.androidTestImplementation
import com.datadog.gradle.config.AndroidConfig
import com.datadog.gradle.config.detektConfig
import com.datadog.gradle.config.kotlinConfig
import com.datadog.gradle.config.ktLintConfig
import com.datadog.gradle.implementation
import org.gradle.api.JavaVersion

plugins {
    id("com.android.application")
    kotlin("android")
    kotlin("android.extensions")
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
        versionCode = 42
        versionName = "4.2.13"

        multiDexEnabled = true
        vectorDrawables.useSupportLibrary = true

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

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

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    packagingOptions {
        exclude("META-INF/jvm.kotlin_module")
        exclude("META-INF/LICENSE.md")
        exclude("META-INF/LICENSE-notice.md")
    }
}

repositories {
    google()
    mavenLocal()
    mavenCentral()
}

dependencies {
    implementation(project(":dd-sdk-android")) {
        exclude("com.google.guava")
    }
    implementation(Dependencies.Libraries.Gson)
    implementation(Dependencies.Libraries.Kotlin)
    implementation(Dependencies.Libraries.AndroidxSupportBase)
    implementation(Dependencies.Libraries.AndroidXMultidex)
    implementation(Dependencies.Libraries.Elmyr)

    androidTestImplementation(project(":tools:unit"))
    androidTestImplementation("net.wuerl.kotlin:assertj-core-kotlin:${Versions.AssertJ}")
    androidTestImplementation(Dependencies.Libraries.IntegrationTests)
    androidTestImplementation(Dependencies.Libraries.OkHttpMock)

    detekt(project(":tools:detekt"))
    detekt(Dependencies.Libraries.DetektCli)
}

kotlinConfig()
detektConfig()
ktLintConfig()
