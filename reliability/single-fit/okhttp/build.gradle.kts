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
    implementation(libs.apolloRuntime)
    unmock(libs.robolectric)
}

apollo {
    service("testService") {
        srcDir("src/test/resources/graphql")
        packageName.set("com.datadog.android.testgraphql")
        schemaFiles.from("src/test/resources/graphql/schema.graphqls")
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
    applyKotlinConfig(
        // TODO RUM-18200 We access internal members of another module in this module
        // This should be addressed properly, temporarily disable treating warnings as errors
        evaluateWarningsAsErrors = false
    )
    applyJunitConfig()
}
