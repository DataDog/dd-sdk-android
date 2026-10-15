/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.library")
    id("ktlint")
    kotlin("android")
    id("datadogBuildConfig")
    id("test-pyramid-api-usage")
    id("de.mobilej.unmock")
}

@Suppress("DEPRECATION")
android {
    namespace = "com.datadog.android.flags.integration"
}

dependencies {
    testImplementation(project(":dd-sdk-android-core"))
    testImplementation(project(":dd-sdk-android-internal"))
    testImplementation(project(":features:dd-sdk-android-flags"))
    testImplementation(project(":features:dd-sdk-android-flags-openfeature"))
    testImplementation(project(":features:dd-sdk-android-rum"))
    testImplementation(project(":tools:unit")) {
        attributes {
            attribute(
                com.android.build.api.attributes.ProductFlavorAttr.of("platform"),
                objects.named("jvm")
            )
        }
    }
    testImplementation(testFixtures(project(":dd-sdk-android-core")))
    testImplementation(project(":reliability:stub-core"))
    testImplementation(libs.bundles.jUnit5)
    testImplementation(libs.bundles.testTools)
    testImplementation(libs.okHttp)
    testImplementation(libs.okHttpMock)
    testImplementation(libs.gson)
    testImplementation(libs.coroutinesCore)
    unmock(libs.robolectric)
}

unMock {
    keepStartingWith("android.os")
    keepStartingWith("org.json")
}

datadogBuild {
    applyAndroidLibraryConfig()
    applyKotlinConfig(jvmBytecodeTarget = JvmTarget.JVM_11)
    applyJunitConfig()
}
