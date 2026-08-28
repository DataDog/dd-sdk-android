/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.internal.time

import android.os.Process
import com.datadog.android.internal.system.BuildSdkVersionProvider
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
                val timeProvider = timeProviderFactory()
                val diffMs = timeProvider.getDeviceUptimeMillis() - Process.getStartUptimeMillis()
                val computedAppStartTimeNs =
                    timeProvider.getDeviceElapsedTimeNanos() - TimeUnit.MILLISECONDS.toNanos(diffMs)
                val contentProviderCreateTimeNs = DdRumContentProvider.createTimeNs
                val isAfterContentProviderInit = computedAppStartTimeNs > contentProviderCreateTimeNs
                val isTooFarBeforeContentProviderInit =
                    contentProviderCreateTimeNs - computedAppStartTimeNs >
                        PROCESS_START_TO_CP_START_DIFF_THRESHOLD_NS

                /**
                 * Guard against unexpected values from [Process.getStartUptimeMillis].
                 * Two directions are checked and fall back to [DdRumContentProvider] creation time:
                 * - computedAppStartTimeNs > createTimeNs: app start appears to be after content provider init,
                 * which is impossible.
                 * - computedAppStartTimeNs is more than the threshold before createTimeNs: app start appears
                 * unreasonably far in the past.
                 */
                if (isAfterContentProviderInit || isTooFarBeforeContentProviderInit) {
                    contentProviderCreateTimeNs
                } else {
                    computedAppStartTimeNs
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
