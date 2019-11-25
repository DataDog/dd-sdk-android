import com.datadog.gradle.Dependencies
import com.datadog.gradle.androidTestImplementation
import com.datadog.gradle.config.AndroidConfig
import com.datadog.gradle.config.dependencyUpdateConfig
import com.datadog.gradle.config.detektConfig
import com.datadog.gradle.config.jacocoConfig
import com.datadog.gradle.config.junitConfig
import com.datadog.gradle.config.kotlinConfig
import com.datadog.gradle.config.ktLintConfig
import com.datadog.gradle.config.publishingConfig
import com.datadog.gradle.testImplementation

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

    sourceSets.named("main") {
        java.srcDir("src/main/kotlin")
    }
    sourceSets.named("test") {
        java.srcDir("src/test/kotlin")
    }
    sourceSets.named("androidTest") {
        java.srcDir("src/androidTest/kotlin")
    }

    // TODO when using Android Plugin 3.6.+
    // enableAdditionalTestOutput=true
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(Dependencies.Libraries.Gson)
    implementation(Dependencies.Libraries.Kotlin)
    implementation(Dependencies.Libraries.OkHttp)

    testImplementation(Dependencies.Libraries.JUnit5)
    testImplementation(Dependencies.Libraries.TestTools)
    testImplementation(Dependencies.Libraries.OkHttpMock)

    androidTestImplementation(Dependencies.Libraries.JetpackBenchmark)
    androidTestImplementation(Dependencies.Libraries.AndroidTestTools)
}

kotlinConfig()
detektConfig()
ktLintConfig()
junitConfig()
jacocoConfig()
dependencyUpdateConfig()
publishingConfig("${rootDir.canonicalPath}/repo")
