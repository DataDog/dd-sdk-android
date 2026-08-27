/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

import com.datadog.gradle.config.taskConfig

plugins {
    // Build
    id("org.jetbrains.kotlin.jvm")
    id("datadogBuildConfig")

    // Analysis tools
    id("ktlint")
}

dependencies {
    implementation(libs.kotlin)
    implementation(libs.kotlinReflect)
    implementation(libs.kotlinSP)
    implementation(libs.kotlinPoet)
    implementation(libs.kotlinPoetKsp)

    testImplementation(libs.bundles.jUnit5)
    testImplementation(libs.bundles.testTools)
    testImplementation(libs.kspTesting)
}

datadogBuild {
    applyKotlinConfig()
    applyJunitConfig()
}

taskConfig<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    compilerOptions {
        optIn.add("kotlin.RequiresOptIn")
    }
}
