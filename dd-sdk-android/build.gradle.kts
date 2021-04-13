/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

import com.datadog.gradle.Dependencies
import com.datadog.gradle.api
import com.datadog.gradle.config.AndroidConfig
import com.datadog.gradle.config.BuildConfigPropertiesKeys
import com.datadog.gradle.config.GradlePropertiesKeys
import com.datadog.gradle.config.dependencyUpdateConfig
import com.datadog.gradle.config.detektConfig
import com.datadog.gradle.config.jacocoConfig
import com.datadog.gradle.config.javadocConfig
import com.datadog.gradle.config.junitConfig
import com.datadog.gradle.config.kotlinConfig
import com.datadog.gradle.config.ktLintConfig
import com.datadog.gradle.config.publishingConfig
import com.datadog.gradle.implementation
import com.datadog.gradle.testImplementation

plugins {
    // Build
    id("com.android.library")
    kotlin("android")
    kotlin("kapt")

    // Publish
    `maven-publish`
    signing
    id("org.jetbrains.dokka")

    // Analysis tools
    id("com.github.ben-manes.versions")
    id("io.gitlab.arturbosch.detekt")
    id("org.jlleitschuh.gradle.ktlint")

    // Tests
    jacoco
    id("de.mobilej.unmock")

    // Internal Generation
    id("thirdPartyLicences")
    id("apiSurface")
    id("transitiveDependencies")
    id("jsonschema2poko")
}

fun isLogEnabledInRelease(): String {
    return project.findProperty(GradlePropertiesKeys.FORCE_ENABLE_LOGCAT) as? String ?: "false"
}

android {
    compileSdkVersion(AndroidConfig.TARGET_SDK)
    buildToolsVersion(AndroidConfig.BUILD_TOOLS_VERSION)

    defaultConfig {
        minSdkVersion(AndroidConfig.MIN_SDK)
        targetSdkVersion(AndroidConfig.TARGET_SDK)

        versionCode = AndroidConfig.VERSION.code
        versionName = AndroidConfig.VERSION.name
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
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
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

    packagingOptions {
        exclude("META-INF/jvm.kotlin_module")
        exclude("META-INF/LICENSE.md")
        exclude("META-INF/LICENSE-notice.md")
    }

    lintOptions {
        isWarningsAsErrors = true
        isAbortOnError = true
        isCheckReleaseBuilds = false
        isCheckGeneratedSources = true
        isIgnoreTestSources = true
    }
}

dependencies {
    implementation(Dependencies.Libraries.Kotlin)

    // Network
    implementation(Dependencies.Libraries.OkHttp)
    implementation(Dependencies.Libraries.Gson)
    implementation(Dependencies.Libraries.KronosNTP)

    // Android Instrumentation
    implementation(Dependencies.Libraries.AndroidXCore)
    implementation(Dependencies.Libraries.AndroidXNavigation)
    implementation(Dependencies.Libraries.AndroidXRecyclerView)
    implementation(Dependencies.Libraries.AndroidXWorkManager)

    // OpenTracing
    api(Dependencies.Libraries.OpenTracing)

    // Generate NoOp implementations
    kapt(project(":tools:noopfactory"))

    // Testing
    testImplementation(project(":tools:unit"))
    testImplementation(Dependencies.Libraries.JUnit5)
    testImplementation(Dependencies.Libraries.TestTools)
    testImplementation(Dependencies.Libraries.OkHttpMock)
    unmock(Dependencies.Libraries.Robolectric)

    // Static Analysis
    detekt(project(":tools:detekt"))
    detekt(Dependencies.Libraries.DetektCli)
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
}

apply(from = "clone_dd_trace.gradle.kts")
apply(from = "clone_rum_schema.gradle.kts")

jsonSchema2Poko {
    inputDirPath = "src/main/json"
    targetPackageName = "com.datadog.android.rum.model"
    ignoredFiles = arrayOf("_common-schema.json", "long_task-schema.json")
    nameMapping = mapOf(
        "action-schema.json" to "ActionEvent",
        "error-schema.json" to "ErrorEvent",
        "resource-schema.json" to "ResourceEvent",
        "view-schema.json" to "ViewEvent"
    )
}

kotlinConfig()
detektConfig(
    excludes = listOf(
        "**/com/datadog/android/rum/model/**",
        "**/com/datadog/android/core/model/**"
    )
)
ktLintConfig()
junitConfig()
jacocoConfig()
javadocConfig()
dependencyUpdateConfig()
publishingConfig("Datadog monitoring library for Android applications.")
