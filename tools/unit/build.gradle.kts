/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

import com.datadog.gradle.config.AndroidConfig
import com.datadog.gradle.config.java17

plugins {
    // Apply ktlint before AGP: built-in Kotlin otherwise causes duplicate ktlint tasks.
    id("ktlint")

    // Build
    id("com.android.library")
    id("de.mobilej.unmock")
    id("datadogBuildConfig")
}

android {
    compileSdk = AndroidConfig.TARGET_SDK
    buildToolsVersion = AndroidConfig.BUILD_TOOLS_VERSION

    defaultConfig {
        minSdk = AndroidConfig.MIN_SDK
    }

    namespace = "com.datadog.tools.unit"

    compileOptions {
        java17()
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
    implementation(libs.kotlinRuntime)
    implementation(libs.kotlinReflect)
    implementation(libs.bundles.jUnit5)
    implementation(libs.bundles.testTools)
    implementation(libs.gson)

    unmock(libs.robolectric)
}

unMock {
    keepStartingWith("org.json")
}

datadogBuild {
    applyKotlinConfig()
    applyJunitConfig()
}
