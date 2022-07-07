/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

import com.datadog.gradle.config.AndroidConfig
import com.datadog.gradle.config.BuildConfigPropertiesKeys
import com.datadog.gradle.config.GradlePropertiesKeys
import com.datadog.gradle.config.dependencyUpdateConfig
import com.datadog.gradle.config.detektConfig
import com.datadog.gradle.config.javadocConfig
import com.datadog.gradle.config.junitConfig
import com.datadog.gradle.config.kotlinConfig
import com.datadog.gradle.config.ktLintConfig
import com.datadog.gradle.config.publishingConfig
import com.datadog.gradle.config.setLibraryVersion

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
    id("io.gitlab.arturbosch.detekt")
    id("org.jlleitschuh.gradle.ktlint")

    // Tests
    id("de.mobilej.unmock")

    // Internal Generation
    id("thirdPartyLicences")
    id("apiSurface")
    id("transitiveDependencies")
}

fun isLogEnabledInRelease(): String {
    return project.findProperty(GradlePropertiesKeys.FORCE_ENABLE_LOGCAT) as? String ?: "false"
}

android {
    compileSdk = AndroidConfig.TARGET_SDK
    buildToolsVersion = AndroidConfig.BUILD_TOOLS_VERSION

    defaultConfig {
        minSdk = AndroidConfig.MIN_SDK
        targetSdk = AndroidConfig.TARGET_SDK

        setLibraryVersion()

        consumerProguardFiles("consumer-rules.pro")
    }

    sourceSets.named("main") {
        java.srcDir("src/main/kotlin")
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
        unitTests.apply {
            isReturnDefaultValues = true
        }
    }

    buildTypes {
        getByName("release") {
            buildConfigField(
                "Boolean",
                BuildConfigPropertiesKeys.LOGCAT_ENABLED,
                isLogEnabledInRelease()
            )
        }

        getByName("debug") {
            buildConfigField(
                "Boolean",
                BuildConfigPropertiesKeys.LOGCAT_ENABLED,
                "true"
            )
        }
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
    api(project(":library:dd-sdk-android-session-replay"))
    implementation(libs.kotlin)

    // Network
    implementation(libs.okHttp)
    implementation(libs.gson)
    implementation(libs.kronosNTP)

    // Android Instrumentation
    implementation(libs.androidXCore)
    implementation(libs.bundles.androidXNavigation)
    implementation(libs.androidXRecyclerView)
    implementation(libs.androidXWorkManager)

    // OpenTracing
    api(libs.bundles.openTracing)

    // Generate NoOp implementations
    ksp(project(":tools:noopfactory"))

    // Testing
    testImplementation(project(":tools:unit"))
    testImplementation(libs.bundles.jUnit5)
    testImplementation(libs.bundles.testTools)
    testImplementation(libs.okHttpMock)
    unmock(libs.robolectric)

    // Static Analysis
    detekt(project(":tools:detekt"))
    detekt(libs.detektCli)
}

unMock {
    keep("android.os.BaseBundle")
    keep("android.os.Bundle")
    keep("android.os.Parcel")
    keepStartingWith("com.android.internal.util.")
    keepStartingWith("android.util.")
    keep("android.content.ComponentName")
    keep("android.os.Looper")
    keep("android.os.MessageQueue")
    keep("android.os.SystemProperties")
    keep("android.view.Choreographer")
    keep("android.view.DisplayEventReceiver")
    keepStartingWith("org.json")
}

apply(from = "clone_dd_trace.gradle.kts")
apply(from = "clone_rum_schema.gradle.kts")
apply(from = "clone_telemetry_schema.gradle.kts")
apply(from = "generate_rum_models.gradle.kts")
apply(from = "generate_telemetry_models.gradle.kts")
apply(from = "generate_core_models.gradle.kts")
apply(from = "generate_trace_models.gradle.kts")
apply(from = "generate_log_models.gradle.kts")

kotlinConfig()
detektConfig(
    excludes = listOf(
        "**/com/datadog/android/rum/model/**",
        "**/com/datadog/android/telemetry/model/**",
        "**/com/datadog/android/core/model/**",
        "**/com/datadog/android/tracing/model/**",
        "**/com/datadog/android/log/model/**"
    )
)
ktLintConfig()
junitConfig()
javadocConfig()
dependencyUpdateConfig()
publishingConfig("Datadog monitoring library for Android applications.")
