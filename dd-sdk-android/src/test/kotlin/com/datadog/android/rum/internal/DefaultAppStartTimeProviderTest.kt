/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal

import android.os.Build
import android.os.Process
import android.os.SystemClock
import com.datadog.android.core.internal.system.BuildSdkVersionProvider
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.data.Offset
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import java.util.concurrent.TimeUnit

@Extensions(
    ExtendWith(ForgeExtension::class)
)
class DefaultAppStartTimeProviderTest {
    @Test
    fun `M return process start time W appStartTime { N+ }`() {
        // GIVEN
        val mockBuildSdkVersionProvider: BuildSdkVersionProvider = mock()
        whenever(mockBuildSdkVersionProvider.version()) doReturn Build.VERSION_CODES.O
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
        @IntForgery(min = Build.VERSION_CODES.KITKAT, max = Build.VERSION_CODES.N) apiVersion: Int
    ) {
        // GIVEN
        val mockBuildSdkVersionProvider: BuildSdkVersionProvider = mock()
        whenever(mockBuildSdkVersionProvider.version()) doReturn apiVersion
        val startTimeNs = RumFeature.startupTimeNs

        // WHEN
        val timeProvider = DefaultAppStartTimeProvider(mockBuildSdkVersionProvider)
        val providedStartTime = timeProvider.appStartTimeNs

        // THEN
        assertThat(providedStartTime).isEqualTo(startTimeNs)
    }
}
