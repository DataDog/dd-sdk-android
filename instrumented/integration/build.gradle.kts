/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

import com.datadog.gradle.config.AndroidConfig
import com.datadog.gradle.config.depotProxied
import com.datadog.gradle.config.java17
import com.datadog.gradle.config.taskConfig
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    // Applied before the Android plugin on purpose (not under "Analysis tools"): AGP 9 applies
    // the Kotlin plugin itself, and ktlint-gradle 14.2.0 registers its Android source-set tasks
    // twice when it comes after the Kotlin plugin.
    id("ktlint")

    // Build
    id("com.android.application")
    alias(libs.plugins.composeCompilerPlugin)
    id("datadogBuildConfig")
}

android {

    compileSdk = AndroidConfig.TARGET_SDK
    buildToolsVersion = AndroidConfig.BUILD_TOOLS_VERSION

    @Suppress("MagicNumber")
    defaultConfig {
        minSdk = AndroidConfig.MIN_SDK
        targetSdk = AndroidConfig.TARGET_SDK
        versionCode = 42
        versionName = "4.2.13"

        buildFeatures {
            buildConfig = true
            compose = true
        }

        vectorDrawables.useSupportLibrary = true

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    namespace = "com.datadog.android.sdk.integration"

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    compileOptions {
        java17()
        if (project.hasProperty(com.datadog.gradle.Properties.USE_DESUGARING)) {
            isCoreLibraryDesugaringEnabled = true
        }
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

repositories.depotProxied(providers) {
    google()
    mavenLocal()
    mavenCentral()
}

dependencies {
    if (project.hasProperty(com.datadog.gradle.Properties.USE_DESUGARING)) {
        coreLibraryDesugaring(libs.androidDesugaringSdk)
    }
    implementation(project(":features:dd-sdk-android-webview"))
    implementation(project(":features:dd-sdk-android-session-replay"))
    implementation(project(":features:dd-sdk-android-logs"))
    implementation(project(":features:dd-sdk-android-trace"))
    implementation(project(":features:dd-sdk-android-rum"))
    implementation(project(":dd-sdk-android-internal"))
    implementation(project(":integrations:dd-sdk-android-okhttp"))
    implementation(project(":integrations:dd-sdk-android-cronet"))
    implementation(libs.okHttp)
    implementation(libs.cronetPlayServices)

    compileOnly(platform(libs.androidXComposeBom))
    compileOnly(libs.androidXComposeRuntime)

    implementation(libs.gson)
    implementation(libs.kotlin)
    // androidTest network wrappers use kotlinx.coroutines; declared on main so consistent
    // resolution aligns the whole module to the SDK's coroutines version (vs the old 1.6.4
    // pulled transitively via androidx.test), and it lands on the androidTest compile classpath.
    implementation(libs.coroutinesCore)
    implementation(libs.bundles.androidXSupportBase)
    implementation(libs.elmyr)
    implementation(libs.leakCanaryAndroid)

    androidTestImplementation(project(":dd-sdk-android-internal"))
    androidTestImplementation(project(":integrations:dd-sdk-android-compose"))
    androidTestImplementation(libs.androidXComposeMaterial)
    androidTestImplementation(libs.androidXComposeUiTestJUnit4)
    debugImplementation(platform(libs.androidXComposeBom))
    debugImplementation(libs.androidXComposeUiTestManifest)
    androidTestImplementation(libs.leakCanaryInstrumentation)
    androidTestImplementation(project(":tools:unit")) {
        attributes {
            attribute(
                com.android.build.api.attributes.ProductFlavorAttr.of("platform"),
                objects.named("art")
            )
        }
    }
    androidTestImplementation(libs.assertJ)
    androidTestImplementation(libs.bundles.integrationTests)
    androidTestImplementation(libs.okHttpMock)
    androidTestImplementation(project(":features:dd-sdk-android-trace-internal"))
    androidTestImplementation(testFixtures(project(":features:dd-sdk-android-trace")))
    if (project.hasProperty(com.datadog.gradle.Properties.USE_API21_JAVA_BACKPORT)) {
        // this is needed to make AssertJ working on APIs <24
        androidTestImplementation(project(":tools:javabackport"))
    }
}

datadogBuild {
    applyKotlinConfig()
}

taskConfig<KotlinCompile> {
    compilerOptions {
        // Integration fixtures intentionally access Kotlin-internal SDK APIs via INVISIBLE_*
        // suppressions, which KGP 2.2 reports with the ERROR_SUPPRESSION diagnostic.
        freeCompilerArgs.add("-Xwarning-level=ERROR_SUPPRESSION:disabled")
    }
}
