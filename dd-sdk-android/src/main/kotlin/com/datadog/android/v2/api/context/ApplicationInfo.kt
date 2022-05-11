/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.v2.api.context

/**
 * Holds information about the current Application.
 * @property packageName the package name as mentioned in the manifest
 * @property versionName the version name (user facing string) as mentioned in the manifest
 * @property versionCode the version code (int) as mentioned in the manifest
 */
data class ApplicationInfo(
    val packageName: String,
    val versionName: String,
    val versionCode: Int
)
