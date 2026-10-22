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
    namespace = "com.datadog.android.flags.integration"
    testBuildType = "release"
    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
}

dependencies {
    implementation(project(":dd-sdk-android-core"))
    implementation(project(":dd-sdk-android-internal"))
    implementation(project(":features:dd-sdk-android-rum"))
    val flagsArtifactDirectory = providers.gradleProperty("flagsArtifactDirectory").orNull
    if (flagsArtifactDirectory == null) {
        implementation(project(":features:dd-sdk-android-flags"))
        implementation(project(":features:dd-sdk-android-flags-openfeature"))
    } else {
        implementation(files("$flagsArtifactDirectory/dd-sdk-android-flags-release.aar"))
        implementation(files("$flagsArtifactDirectory/dd-sdk-android-flags-openfeature-release.aar"))
    }
    androidTestImplementation(libs.openFeatureKotlinSdk)
    androidTestImplementation(libs.coroutinesCore)
    androidTestImplementation(libs.androidXTestCore)
    androidTestImplementation(libs.androidXTestRunner)
    androidTestImplementation(libs.androidXTestJUnitExt)
    androidTestImplementation(libs.okHttpMock)
    testImplementation(libs.openFeatureKotlinSdk)
    testImplementation(libs.coroutinesTest)
    implementation(libs.kotlinRuntime)

    // Testing
    testImplementation(project(":tools:unit")) {
        attributes {
            attribute(
                com.android.build.api.attributes.ProductFlavorAttr.of("platform"),
                objects.named("jvm")
            )
        }
    }
    testImplementation(testFixtures(project(":dd-sdk-android-core")))
    testImplementation(testFixtures(project(":dd-sdk-android-internal")))
    testImplementation(project(":reliability:stub-core"))
    testImplementation(libs.bundles.jUnit5)
    testImplementation(libs.bundles.testTools)
    testImplementation(libs.okHttp)
    unmock(libs.robolectric)
}

unMock {
    keepStartingWith("android.os")
    keepStartingWith("org.json")
    keep("android.util.LruCache")
}

datadogBuild {
    applyAndroidLibraryConfig()
    applyKotlinConfig(jvmBytecodeTarget = JvmTarget.JVM_11)
    applyJunitConfig()
}
