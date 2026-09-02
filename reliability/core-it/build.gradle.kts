/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

import com.datadog.gradle.config.AndroidConfig
import com.datadog.gradle.config.java17
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    // Build
    id("com.android.application")
    // Applied before `kotlin("android")` on purpose (not under "Analysis tools"): ktlint-gradle
    // 14.2.0 registers its Android source-set tasks twice when it comes after the Kotlin plugin.
    id("ktlint")
    kotlin("android")
    id("datadogBuildConfig")

    // Analysis tools
    id("test-pyramid-api-usage")
}

// TODO RUM-18189 Support new AGP DSL
@Suppress("DEPRECATION")
android {

    compileSdk = AndroidConfig.TARGET_SDK
    buildToolsVersion = AndroidConfig.BUILD_TOOLS_VERSION
    namespace = "com.datadog.android.core.integration"

    defaultConfig {
        minSdk = AndroidConfig.MIN_SDK
        targetSdk = AndroidConfig.TARGET_SDK
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        if (project.hasProperty(com.datadog.gradle.Properties.USE_DESUGARING)) {
            isCoreLibraryDesugaringEnabled = true
        }
        java17()
    }

    packaging {
        resources {
            excludes += listOf(
                "META-INF/jvm.kotlin_module",
                "META-INF/LICENSE.md",
                "META-INF/LICENSE-notice.md"
            )
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            testProguardFile("test-proguard-rules.pro")
        }
    }
}

dependencies {
    if (project.hasProperty(com.datadog.gradle.Properties.USE_DESUGARING)) {
        coreLibraryDesugaring(libs.androidDesugaringSdk)
    }
    implementation(project(":dd-sdk-android-core"))
    implementation(libs.kotlin)

    // Testing
    androidTestImplementation(project(":tools:unit")) {
        attributes {
            attribute(
                com.android.build.api.attributes.ProductFlavorAttr.of("platform"),
                objects.named("art")
            )
        }
    }
    androidTestImplementation(project(":reliability:stub-feature"))
    androidTestImplementation(libs.assertJ)
    androidTestImplementation(libs.mockitoAndroid)
    androidTestImplementation(libs.bundles.integrationTests)
    androidTestImplementation(libs.elmyrJVM)
    androidTestImplementation(libs.okHttp)
    androidTestImplementation(libs.okHttpMock)
    androidTestImplementation(libs.gson)
    if (project.hasProperty(com.datadog.gradle.Properties.USE_API21_JAVA_BACKPORT)) {
        // this is needed to make AssertJ working on APIs <24
        androidTestImplementation(project(":tools:javabackport"))
    }
}

datadogBuild {
    applyKotlinConfig(jvmBytecodeTarget = JvmTarget.JVM_11)
    applyJunitConfig()
}
