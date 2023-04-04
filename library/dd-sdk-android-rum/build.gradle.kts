/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

import com.datadog.gradle.config.AndroidConfig
import com.datadog.gradle.config.dependencyUpdateConfig
import com.datadog.gradle.config.javadocConfig
import com.datadog.gradle.config.junitConfig
import com.datadog.gradle.config.kotlinConfig
import com.datadog.gradle.config.publishingConfig
import com.datadog.gradle.config.setLibraryVersion
import java.nio.file.Paths

plugins {
    // Build
    id("com.android.library")
    kotlin("android")
    id("com.google.devtools.ksp")

    // Publish
    `maven-publish`
    signing
    id("org.jetbrains.dokka")

    // Analysis tools
    id("com.github.ben-manes.versions")

    // Tests
    id("de.mobilej.unmock")

    // Internal Generation
    id("thirdPartyLicences")
    id("apiSurface")
    id("transitiveDependencies")
}

android {
    compileSdk = AndroidConfig.TARGET_SDK
    buildToolsVersion = AndroidConfig.BUILD_TOOLS_VERSION

    defaultConfig {
        minSdk = AndroidConfig.MIN_SDK
        targetSdk = AndroidConfig.TARGET_SDK

        consumerProguardFiles(Paths.get(rootDir.path, "consumer-rules.pro").toString())

        setLibraryVersion()
    }

    namespace = "com.datadog.android.rum"

    sourceSets.named("main") {
        java.srcDir("src/main/kotlin")
        java.srcDir("build/generated/json2kotlin/main/kotlin")
    }
    sourceSets.named("test") {
        java.srcDir("src/test/kotlin")
    }
    sourceSets.named("androidTest") {
        java.srcDir("src/androidTest/kotlin")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    libraryVariants.configureEach {
        addJavaSourceFoldersToModel(
            layout.buildDirectory
                .dir("generated/ksp/$name/kotlin").get().asFile
        )
    }

    packagingOptions {
        resources {
            excludes += listOf(
                "META-INF/jvm.kotlin_module",
                "META-INF/LICENSE.md",
                "META-INF/LICENSE-notice.md"
            )
        }
    }

    lint {
        warningsAsErrors = true
        abortOnError = true
        checkReleaseBuilds = false
        checkGeneratedSources = true
        ignoreTestSources = true
    }
}

dependencies {
    implementation(project(":dd-sdk-android"))
    implementation(libs.kotlin)
    implementation(libs.gson)
    implementation(libs.okHttp)

    // Android Instrumentation
    implementation(libs.androidXCore)
    implementation(libs.bundles.androidXNavigation)
    implementation(libs.androidXRecyclerView)

    // Generate NoOp implementations
    ksp(project(":tools:noopfactory"))

    testImplementation(project(":tools:unit"))
    testImplementation(libs.bundles.jUnit5)
    testImplementation(libs.bundles.testTools)
    testImplementation(libs.okHttpMock)
    testImplementation(libs.bundles.openTracing)
    unmock(libs.robolectric)

    // TODO MTG-12 detekt(project(":tools:detekt"))
    // TODO MTG-12 detekt(libs.detektCli)
}

unMock {
    keep("android.os.BaseBundle")
    keep("android.os.Bundle")
    keep("android.os.Parcel")
    keepStartingWith("com.android.internal.util.")
    keepStartingWith("android.util.")
    keep("android.content.ComponentName")
    keep("android.content.ContentProvider")
    keep("android.content.IContentProvider")
    keep("android.content.ContentProviderNative")
    keep("android.net.Uri")
    keep("android.os.Looper")
    keep("android.os.MessageQueue")
    keep("android.os.SystemProperties")
    keep("android.view.Choreographer")
    keep("android.view.DisplayEventReceiver")
    keepStartingWith("org.json")
}

apply(from = "clone_rum_schema.gradle.kts")
apply(from = "clone_telemetry_schema.gradle.kts")
apply(from = "generate_rum_models.gradle.kts")
apply(from = "generate_telemetry_models.gradle.kts")

kotlinConfig(false)
junitConfig()
javadocConfig()
dependencyUpdateConfig()
publishingConfig(
    "The RUM feature to use with the Datadog monitoring " +
        "library for Android applications."
)
