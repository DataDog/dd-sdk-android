/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

plugins {
    // Applied before the Android plugin on purpose (not under "Analysis tools"): AGP 9 applies
    // the Kotlin plugin itself, and ktlint-gradle 14.2.0 registers its Android source-set tasks
    // twice when it comes after the Kotlin plugin.
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
    namespace = "com.datadog.android.trace.integration"

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
    }
}

dependencies {
    implementation(project(":dd-sdk-android-core"))
    implementation(project(":features:dd-sdk-android-trace"))
    implementation(project(":features:dd-sdk-android-trace-otel"))
    implementation(libs.kotlin)

    // Desugaring SDK
    coreLibraryDesugaring(libs.androidDesugaringSdk)

    // Testing
    testImplementation(project(":tools:unit")) {
        attributes {
            attribute(
                com.android.build.api.attributes.ProductFlavorAttr.of("platform"),
                objects.named("jvm")
            )
        }
    }
    testImplementation(project(":reliability:stub-core"))
    testImplementation(project(":dd-sdk-android-internal"))
    testImplementation(project(":features:dd-sdk-android-trace-internal"))
    testImplementation(testFixtures(project(":dd-sdk-android-core")))
    testImplementation(testFixtures(project(":features:dd-sdk-android-trace")))
    testImplementation(libs.bundles.jUnit5)
    testImplementation(libs.bundles.testTools)
    testImplementation(libs.okHttp)
    testImplementation(libs.gson)
    unmock(libs.robolectric)
}

unMock {
    keep("android.util.Singleton")
    keep("com.android.internal.util.FastPrintWriter")
    keep("dalvik.system.BlockGuard")
    keep("dalvik.system.CloseGuard")
    keepStartingWith("android.os")
    keepStartingWith("org.json")
}

datadogBuild {
    applyAndroidLibraryConfig()
    applyKotlinConfig()
    applyJunitConfig()
}
