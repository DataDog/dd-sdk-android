/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.gradle.config

import com.android.build.api.dsl.CompileOptions
import com.android.build.api.dsl.LibraryDefaultConfig
import com.datadog.gradle.utils.Version
import org.gradle.api.JavaVersion

object AndroidConfig {

    const val TARGET_SDK = 33
    const val MIN_SDK = 21
    const val MIN_SDK_FOR_WEAR = 23
    const val BUILD_TOOLS_VERSION = "33.0.2"

    val VERSION = Version(2, 1, 0, Version.Type.Snapshot)
}

// TODO RUMM-3263 Switch to Java 17 bytecode
fun CompileOptions.java11() {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

fun CompileOptions.java17() {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

@Suppress("UnstableApiUsage")
fun LibraryDefaultConfig.setLibraryVersion(
    versionCode: Int = AndroidConfig.VERSION.code,
    versionName: String = AndroidConfig.VERSION.name
) {
    buildConfigField("int", "SDK_VERSION_CODE", "$versionCode")
    buildConfigField("String", "SDK_VERSION_NAME", "\"$versionName\"")
}
