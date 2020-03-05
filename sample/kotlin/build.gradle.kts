/*
 * Unless explicitly stated otherwise all pomFilesList in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2019 Datadog, Inc.
 */

import com.datadog.gradle.Dependencies
import com.datadog.gradle.config.AndroidConfig
import com.datadog.gradle.config.dependencyUpdateConfig
import com.datadog.gradle.config.detektConfig
import com.datadog.gradle.config.javadocConfig
import com.datadog.gradle.config.junitConfig
import com.datadog.gradle.config.kotlinConfig
import com.datadog.gradle.config.ktLintConfig

plugins {
    id("com.android.application")
    id("androidx.benchmark")
    kotlin("android")
    kotlin("android.extensions")
    `maven-publish`
    id("com.github.ben-manes.versions")
    id("io.gitlab.arturbosch.detekt")
    id("org.jlleitschuh.gradle.ktlint")
    id("thirdPartyLicences")
    id("org.jetbrains.dokka")
    id("com.jfrog.bintray")
    jacoco
}

android {
    compileSdkVersion(AndroidConfig.TARGET_SDK)
    buildToolsVersion(AndroidConfig.BUILD_TOOLS_VERSION)

    defaultConfig {
        minSdkVersion(AndroidConfig.MIN_SDK)
        targetSdkVersion(AndroidConfig.TARGET_SDK)
        versionCode = AndroidConfig.VERSION.code
        versionName = AndroidConfig.VERSION.name

        buildConfigField(
            "String",
            "DD_CLIENT_TOKEN",
            "\"${project.findProperty("DD_CLIENT_TOKEN") ?: ""}\""
        )

        multiDexEnabled = true

        vectorDrawables.useSupportLibrary = true
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

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    flavorDimensions("version")
    productFlavors {
        register("staging") {
            dimension = "version"
            buildConfigField(
                "String",
                "DD_OVERRIDE_LOGS_URL",
                "\"${project.findProperty("DD_OVERRIDE_LOGS_URL") ?: ""}\""
            )
            buildConfigField(
                "String",
                "DD_OVERRIDE_TRACES_URL",
                "\"${project.findProperty("DD_OVERRIDE_TRACES_URL") ?: ""}\""
            )
            buildConfigField(
                "String",
                "DD_OVERRIDE_RUM_URL",
                "\"${project.findProperty("DD_OVERRIDE_RUM_URL") ?: ""}\""
            )
            buildConfigField(
                "String",
                "DD_RUM_APPLICATION_ID",
                "\"${project.findProperty("DD_RUM_APPLICATION_ID") ?: ""}\""
            )
        }
        register("full") {
            dimension = "version"
            buildConfigField("String", "DD_OVERRIDE_LOGS_URL", "\"\"")
            buildConfigField("String", "DD_OVERRIDE_TRACES_URL", "\"\"")
            buildConfigField("String", "DD_OVERRIDE_RUM_URL", "\"\"")
            buildConfigField("String", "DD_RUM_APPLICATION_ID", "\"\"")
        }
    }
    dexOptions {
        javaMaxHeapSize = "4g"
    }
}

dependencies {

    api(project(":dd-sdk-android")) {
        exclude("com.google.guava", module = "listenablefuture")
    }

    api(project(":dd-sdk-androidx-fragments")) {
        exclude("com.google.guava", module = "listenablefuture")
    }

    // Android dependencies
    implementation(Dependencies.Libraries.AndroidXMultidex)
    implementation("androidx.appcompat:appcompat:1.1.0")
    implementation("androidx.constraintlayout:constraintlayout:2.0.0-beta4")
    implementation("com.google.android.material:material:1.0.0")
    implementation("androidx.vectordrawable:vectordrawable:1.1.0")
    implementation("androidx.navigation:navigation-fragment:2.1.0")
    implementation("androidx.navigation:navigation-ui:2.1.0")
    implementation("androidx.legacy:legacy-support-v4:1.0.0")
    implementation("androidx.lifecycle:lifecycle-extensions:2.1.0")

    implementation(Dependencies.Libraries.Kotlin)
    implementation(Dependencies.Libraries.Gson)
}

kotlinConfig()
detektConfig()
ktLintConfig()
junitConfig()
javadocConfig()
dependencyUpdateConfig()
