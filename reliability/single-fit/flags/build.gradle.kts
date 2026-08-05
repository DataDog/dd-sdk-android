/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

plugins {
    // Apply ktlint before AGP: built-in Kotlin otherwise causes duplicate ktlint tasks.
    id("ktlint")

    // Build
    id("com.android.library")
    id("datadogBuildConfig")

    // Analysis tools
    id("test-pyramid-api-usage")

    // Tests
    id("de.mobilej.unmock")
}

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
    testImplementation(libs.coroutinesTest)
    testImplementation(libs.bundles.jUnit5)
    testImplementation(libs.bundles.testTools)
    testImplementation(libs.okHttp)
    testImplementation(libs.gson)
    testImplementation(libs.coroutinesCore)
    unmock(libs.robolectric)
}

unMock {
    keepStartingWith("android.os")
    keepStartingWith("org.json")
    keep("android.util.LruCache")
}

datadogBuild {
    applyAndroidLibraryConfig()
    applyKotlinConfig()
    applyJunitConfig()
}
