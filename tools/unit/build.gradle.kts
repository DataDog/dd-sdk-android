/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

import com.datadog.gradle.config.AndroidConfig
import com.datadog.gradle.config.dependencyUpdateConfig
import com.datadog.gradle.config.java11
import com.datadog.gradle.config.junitConfig
import com.datadog.gradle.config.kotlinConfig
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.library")
    kotlin("android")
    id("com.github.ben-manes.versions")
    id("com.datadoghq.dependency-license")
    id("de.mobilej.unmock")
}

android {
    compileSdk = AndroidConfig.TARGET_SDK
    buildToolsVersion = AndroidConfig.BUILD_TOOLS_VERSION

    defaultConfig {
        minSdk = AndroidConfig.MIN_SDK
        targetSdk = AndroidConfig.TARGET_SDK
    }

    namespace = "com.datadog.tools.unit"

    compileOptions {
        java11()
    }

    sourceSets.named("main") {
        java.srcDir("src/main/kotlin")
        java.srcDir("src/main/java")
    }
    sourceSets.named("test") {
        java.srcDir("src/test/kotlin")
    }
    sourceSets.named("androidTest") {
        java.srcDir("src/androidTest/kotlin")
    }

    flavorDimensions += "platform"
    productFlavors {
        register("art") {
            isDefault = false
        }
        register("jvm") {
            isDefault = true
        }
    }

    packaging {
        resources {
            excludes.addAll(listOf("META-INF/LICENSE.md", "META-INF/LICENSE-notice.md"))
        }
    }
}

dependencies {
    implementation(libs.kotlin)
    implementation(libs.kotlinReflect)
    implementation(libs.bundles.jUnit5)
    implementation(libs.bundles.testTools)
    implementation(libs.gson)
    implementation(libs.mockitoKotlin)

    testImplementation(libs.bundles.jUnit5)
    testImplementation(libs.bundles.testTools)
    unmock(libs.robolectric)
}

unMock {
    keepStartingWith("org.json")
}

// It has to target 11 even if it is for unit-tests and this lib is not client facing, because
// with bytecode of Java 17 there is an error:
// Cannot inline bytecode built with JVM target 17 into bytecode that is being built with JVM target 11
kotlinConfig(jvmBytecodeTarget = JvmTarget.JVM_11)
junitConfig()
dependencyUpdateConfig()
