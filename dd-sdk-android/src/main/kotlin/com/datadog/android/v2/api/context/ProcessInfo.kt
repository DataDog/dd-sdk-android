/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.v2.api.context

import android.app.ActivityManager.RunningAppProcessInfo

/**
 * Holds information about the current process.
 * @property isMainProcess whether this is the main or a secondary process for the app
 * @property processImportance one of [RunningAppProcessInfo] IMPORTANCE level
 */
data class ProcessInfo(
    val isMainProcess: Boolean,
    val processImportance: Int
)
