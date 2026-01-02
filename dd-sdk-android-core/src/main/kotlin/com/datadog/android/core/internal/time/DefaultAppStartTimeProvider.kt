/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.time

import android.os.Process
import android.os.SystemClock
import com.datadog.android.internal.system.BuildSdkVersionProvider
import com.datadog.android.internal.time.TimeProvider
import com.datadog.android.rum.DdRumContentProvider
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.seconds

internal class DefaultAppStartTimeProvider(
    private val timeProviderFactory: () -> TimeProvider,
    buildSdkVersionProvider: BuildSdkVersionProvider = BuildSdkVersionProvider.DEFAULT
) : AppStartTimeProvider {

    override val appStartTimeNs: Long by lazy(LazyThreadSafetyMode.PUBLICATION) {
        when {
            buildSdkVersionProvider.isAtLeastN -> {
                val diffMs = SystemClock.elapsedRealtime() - Process.getStartElapsedRealtime()
                val result = timeProviderFactory().getDeviceElapsedTimeNanos() - TimeUnit.MILLISECONDS.toNanos(diffMs)

                /**
                 * Occasionally [Process.getStartElapsedRealtime] returns buggy values. We filter them and fall back
                 * to the time of creation of [DdRumContentProvider].
                 */
                if (DdRumContentProvider.createTimeNs - result > PROCESS_START_TO_CP_START_DIFF_THRESHOLD_NS) {
                    DdRumContentProvider.createTimeNs
                } else {
                    result
                }
            }
            else -> DdRumContentProvider.createTimeNs
        }
    }

    override val appUptimeNs: Long
        get() = timeProviderFactory().getDeviceElapsedTimeNanos() - appStartTimeNs

    companion object {
        internal val PROCESS_START_TO_CP_START_DIFF_THRESHOLD_NS = 10.seconds.inWholeNanoseconds
    }
}
