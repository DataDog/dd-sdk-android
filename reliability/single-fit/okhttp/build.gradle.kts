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
    id("com.github.ben-manes.versions")
    id("test-pyramid-api-usage")

    // Tests
    id("de.mobilej.unmock")
    alias(libs.plugins.apolloPlugin)
}

android {
    namespace = "com.datadog.android.okhttp.integration"
}

dependencies {
    implementation(project(":features:dd-sdk-android-trace"))
    implementation(project(":features:dd-sdk-android-trace-otel"))
    implementation(project(":integrations:dd-sdk-android-okhttp"))
    implementation(project(":integrations:dd-sdk-android-okhttp-otel"))
    implementation(libs.kotlin)

    // Testing
    testImplementation(project(":tools:unit")) {
        attributes {
            attribute(
                com.android.build.api.attributes.ProductFlavorAttr.of("platform"),
                objects.named("jvm")
            )
        }
    }
    testImplementation(project(":dd-sdk-android-internal"))
    testImplementation(testFixtures(project(":dd-sdk-android-core")))
    testImplementation(testFixtures(project(":features:dd-sdk-android-trace")))
    testImplementation(project(":reliability:stub-core"))
    testImplementation(project(":integrations:dd-sdk-android-apollo"))
    testImplementation(project(":features:dd-sdk-android-rum"))
    testImplementation(libs.bundles.jUnit5)
    testImplementation(libs.bundles.testTools)
    testImplementation(libs.okHttp)
    testImplementation(libs.okHttpMock)
    testImplementation(libs.gson)
    testImplementation(libs.apolloRuntime)
    unmock(libs.robolectric)
}

apollo {
    service("testService") {
        srcDir("src/test/resources/graphql")
        packageName.set("com.datadog.android.testgraphql")
        schemaFiles.from("src/test/resources/graphql/schema.graphqls")

        outputDirConnection {
            connectToKotlinSourceSet("test")
        }
    }
}

unMock {
    keep("android.util.Singleton")
    keep("com.android.internal.util.FastPrintWriter")
    keep("dalvik.system.BlockGuard")
    keep("dalvik.system.CloseGuard")
    keepStartingWith("android.os")
    keepStartingWith("android.util.")
    keepStartingWith("org.json")
}

datadogBuild {
    applyAndroidLibraryConfig()
    applyKotlinConfig(jvmBytecodeTarget = JvmTarget.JVM_11)
    applyJunitConfig()
    applyDependencyUpdateConfig()
}
