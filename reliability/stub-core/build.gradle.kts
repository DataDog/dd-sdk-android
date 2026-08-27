/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    // Build
    id("com.android.library")
    // Applied before `kotlin("android")` on purpose (not under "Analysis tools"): ktlint-gradle
    // 14.2.0 registers its Android source-set tasks twice when it comes after the Kotlin plugin.
    id("ktlint")
    kotlin("android")
    id("datadogBuildConfig")

    // Analysis tools
    id("test-pyramid-api-usage")

    // Tests
    id("de.mobilej.unmock")
}

// TODO RUM-18189 Support new AGP DSL
@Suppress("DEPRECATION")
android {
    namespace = "com.datadog.android.core.stub"
}

dependencies {
    implementation(project(":dd-sdk-android-internal"))
    implementation(project(":dd-sdk-android-core"))
    implementation(libs.kotlin)

    // Testing
    implementation(project(":tools:unit")) {
        attributes {
            attribute(
                com.android.build.api.attributes.ProductFlavorAttr.of("platform"),
                objects.named("jvm")
            )
        }
    }
    implementation(libs.bundles.jUnit5)
    implementation(libs.bundles.testTools)
    implementation(libs.okHttp)
    implementation(libs.gson)
}

datadogBuild {
    applyAndroidLibraryConfig()
    applyKotlinConfig(jvmBytecodeTarget = JvmTarget.JVM_11)
}
