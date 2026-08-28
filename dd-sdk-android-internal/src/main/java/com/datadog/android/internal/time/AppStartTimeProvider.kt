/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.internal.time

import com.datadog.android.internal.system.BuildSdkVersionProvider

interface AppStartTimeProvider {
    /**
     * Provide the time the application started in nanoseconds from device boot, or our best guess
     * if the actual start time is not available.
     */
    val appStartTimeNs: Long

    /**
     * Provide the time since the application started in nanoseconds from device boot, or our best guess
     * if the actual start time is not available.
     */
    val appUptimeNs: Long

    companion object {
        fun create(
            timeProviderFactory: () -> TimeProvider,
            buildSdkVersionProvider: BuildSdkVersionProvider = BuildSdkVersionProvider.DEFAULT
        ): AppStartTimeProvider = DefaultAppStartTimeProvider(
            timeProviderFactory = timeProviderFactory,
            buildSdkVersionProvider = buildSdkVersionProvider
        )
    }
}
