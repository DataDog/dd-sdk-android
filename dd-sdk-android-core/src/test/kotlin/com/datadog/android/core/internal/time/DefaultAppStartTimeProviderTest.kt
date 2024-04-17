/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.time

import android.os.Build
import android.os.Process
import android.os.SystemClock
import com.datadog.android.core.DatadogCore
import com.datadog.android.core.internal.system.BuildSdkVersionProvider
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.data.Offset
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.concurrent.TimeUnit

@Extensions(
    ExtendWith(ForgeExtension::class)
)
class DefaultAppStartTimeProviderTest {
    @Test
    fun `M return process start time W appStartTime { N+ }`(
        @IntForgery(min = Build.VERSION_CODES.N) apiVersion: Int
    ) {
        // GIVEN
        val mockBuildSdkVersionProvider: BuildSdkVersionProvider = mock()
        whenever(mockBuildSdkVersionProvider.version) doReturn apiVersion
        val diffMs = SystemClock.elapsedRealtime() - Process.getStartElapsedRealtime()
        val startTimeNs = System.nanoTime() - TimeUnit.MILLISECONDS.toNanos(diffMs)

        // WHEN
        val timeProvider = DefaultAppStartTimeProvider(mockBuildSdkVersionProvider)
        val providedStartTime = timeProvider.appStartTimeNs

        // THEN
        assertThat(providedStartTime)
            .isCloseTo(startTimeNs, Offset.offset(TimeUnit.MILLISECONDS.toNanos(100)))
    }

    @Test
    fun `M return rum load time W appStartTime { Legacy }`(
        @IntForgery(min = Build.VERSION_CODES.LOLLIPOP, max = Build.VERSION_CODES.N) apiVersion: Int
    ) {
        // GIVEN
        val mockBuildSdkVersionProvider: BuildSdkVersionProvider = mock()
        whenever(mockBuildSdkVersionProvider.version) doReturn apiVersion
        val startTimeNs = DatadogCore.startupTimeNs

        // WHEN
        val timeProvider = DefaultAppStartTimeProvider(mockBuildSdkVersionProvider)
        val providedStartTime = timeProvider.appStartTimeNs

        // THEN
        assertThat(providedStartTime).isEqualTo(startTimeNs)
    }
}
