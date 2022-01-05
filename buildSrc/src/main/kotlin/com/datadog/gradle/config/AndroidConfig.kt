/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.gradle.config

import com.android.build.api.dsl.LibraryDefaultConfig
import com.datadog.gradle.utils.Version

object AndroidConfig {

    const val TARGET_SDK = 31
    const val MIN_SDK = 19
    const val BUILD_TOOLS_VERSION = "31.0.0"

    val VERSION = Version(1, 11, 0, Version.Type.Release)
}

@Suppress("UnstableApiUsage")
fun LibraryDefaultConfig.setLibraryVersion(
    versionCode: Int = AndroidConfig.VERSION.code,
    versionName: String = AndroidConfig.VERSION.name
) {
    buildConfigField("int", "SDK_VERSION_CODE", "$versionCode")
    buildConfigField("String", "SDK_VERSION_NAME", "\"$versionName\"")
}
