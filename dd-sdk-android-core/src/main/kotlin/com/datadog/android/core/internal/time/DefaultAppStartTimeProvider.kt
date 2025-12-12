/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.time

import android.annotation.SuppressLint
import android.os.Build
import android.os.Process
import android.os.SystemClock
import com.datadog.android.core.internal.system.BuildSdkVersionProvider
import com.datadog.android.rum.DdRumContentProvider
import java.util.concurrent.TimeUnit

internal class DefaultAppStartTimeProvider(
    buildSdkVersionProvider: BuildSdkVersionProvider = BuildSdkVersionProvider.DEFAULT
) : AppStartTimeProvider {

    override val appStartTimeNs: Long by lazy(LazyThreadSafetyMode.PUBLICATION) {
        @SuppressLint("NewApi")
        when {
            buildSdkVersionProvider.version >= Build.VERSION_CODES.N -> {
                val diffMs = SystemClock.elapsedRealtime() - Process.getStartElapsedRealtime()
                System.nanoTime() - TimeUnit.MILLISECONDS.toNanos(diffMs)
            }
            else -> DdRumContentProvider.createTimeNs
        }
    }

    override val appUptimeNs: Long
        get() = System.nanoTime() - appStartTimeNs
}
