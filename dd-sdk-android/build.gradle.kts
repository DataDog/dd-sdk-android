import com.datadog.gradle.config.*

plugins {
    id("com.android.library")
    kotlin("android")
    kotlin("android.extensions")
    `maven-publish`
    id("com.github.ben-manes.versions")
    id("io.gitlab.arturbosch.detekt")
    id("org.jlleitschuh.gradle.ktlint")
}

android {
    compileSdkVersion(AndroidConfig.TARGET_SDK)

    defaultConfig {
        minSdkVersion(AndroidConfig.MIN_SDK)
        targetSdkVersion(AndroidConfig.TARGET_SDK)
        versionCode = AndroidConfig.VERSION.code
        versionName = AndroidConfig.VERSION.name
    }

    sourceSets.named("main") {
        java.srcDir("src/main/kotlin")
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

repositories {
    google()
    mavenCentral()
    jcenter()
}

kotlinConfig()
detektConfig()
ktLintConfig()
dependencyUpdateConfig()
publishingConfig("${rootDir.canonicalPath}/repo")
