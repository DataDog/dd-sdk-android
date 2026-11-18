/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    // Keep SQLDelight 1.x code generation outside Android's built-in Kotlin plugin.
    id("org.jetbrains.kotlin.jvm")
    id("datadogBuildConfig")
    id("ktlint")
    alias(libs.plugins.sqlDelightGradlePlugin)
}

dependencies {
    implementation(libs.kotlinRuntime)
}

sqldelight {
    database("LogsDatabase") {
        packageName = "com.datadog.android.sample"
        dialect = "sqlite:3.24"
    }
}

datadogBuild {
    applyKotlinConfig(jvmBytecodeTarget = JvmTarget.JVM_11)
}
