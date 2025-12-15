/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.time

import android.os.Build
import android.os.Process
import android.os.SystemClock
import com.datadog.android.core.internal.system.BuildSdkVersionProvider
import com.datadog.android.rum.DdRumContentProvider
import fr.xgouchet.elmyr.Forge
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
        @IntForgery(min = Build.VERSION_CODES.N) apiVersion: Int,
        forge: Forge
    ) {
        // GIVEN
        val mockBuildSdkVersionProvider: BuildSdkVersionProvider = mock()
        whenever(mockBuildSdkVersionProvider.version) doReturn apiVersion

        val diffMs = SystemClock.elapsedRealtime() - Process.getStartElapsedRealtime()
        val startTimeNs = System.nanoTime() - TimeUnit.MILLISECONDS.toNanos(diffMs)

        DdRumContentProvider.createTimeNs = startTimeNs +
            forge.aLong(min = 0, max = DefaultAppStartTimeProvider.PROCESS_START_TO_CP_START_DIFF_THRESHOLD_NS)

        val testedProvider = DefaultAppStartTimeProvider(mockBuildSdkVersionProvider)

        // WHEN
        val providedStartTime = testedProvider.appStartTimeNs

        // THEN
        assertThat(providedStartTime)
            .isCloseTo(startTimeNs, Offset.offset(TimeUnit.MILLISECONDS.toNanos(100)))
    }

    @Test
    fun `M fall back to DdRumContentProvider W appStartTime { N+ getStartElapsedRealtime returns buggy value }`(
        @IntForgery(min = Build.VERSION_CODES.N) apiVersion: Int,
        forge: Forge
    ) {
        // GIVEN
        val mockBuildSdkVersionProvider: BuildSdkVersionProvider = mock()
        whenever(mockBuildSdkVersionProvider.version) doReturn apiVersion

        val diffMs = SystemClock.elapsedRealtime() - Process.getStartElapsedRealtime()
        val startTimeNs = System.nanoTime() - TimeUnit.MILLISECONDS.toNanos(diffMs)

        DdRumContentProvider.createTimeNs = startTimeNs +
            forge.aLong(min = DefaultAppStartTimeProvider.PROCESS_START_TO_CP_START_DIFF_THRESHOLD_NS)

        val testedProvider = DefaultAppStartTimeProvider(mockBuildSdkVersionProvider)

        // WHEN
        val providedStartTime = testedProvider.appStartTimeNs

        // THEN
        assertThat(providedStartTime)
            .isCloseTo(DdRumContentProvider.createTimeNs, Offset.offset(TimeUnit.MILLISECONDS.toNanos(100)))
    }

    @Test
    fun `M return content provider load time W appStartTime { Legacy }`(
        @IntForgery(min = Build.VERSION_CODES.M, max = Build.VERSION_CODES.N) apiVersion: Int
    ) {
        // GIVEN
        val mockBuildSdkVersionProvider: BuildSdkVersionProvider = mock()
        whenever(mockBuildSdkVersionProvider.version) doReturn apiVersion
        val startTimeNs = DdRumContentProvider.createTimeNs
        val testedProvider = DefaultAppStartTimeProvider(mockBuildSdkVersionProvider)

        // WHEN
        val providedStartTime = testedProvider.appStartTimeNs

        // THEN
        assertThat(providedStartTime).isEqualTo(startTimeNs)
    }

    @Test
    fun `M return app uptime W appUptimeNs`(
        @IntForgery(min = Build.VERSION_CODES.M) apiVersion: Int
    ) {
        // GIVEN
        val mockBuildSdkVersionProvider: BuildSdkVersionProvider = mock {
            on { version } doReturn apiVersion
        }
        val testedProvider = DefaultAppStartTimeProvider(mockBuildSdkVersionProvider)

        // WHEN
        val beforeNs = System.nanoTime()
        val appStartTimeNs = testedProvider.appStartTimeNs
        val uptime = testedProvider.appUptimeNs
        val afterNs = System.nanoTime()

        // THEN
        val expectedUptime = beforeNs - appStartTimeNs
        assertThat(uptime)
            .isGreaterThan(0)
            .isCloseTo(expectedUptime, Offset.offset(TimeUnit.MILLISECONDS.toNanos(100)))
            .isLessThanOrEqualTo(afterNs - appStartTimeNs)
    }

    @Test
    fun `M return increasing uptime W appUptimeNs called multiple times`(
        @IntForgery(min = Build.VERSION_CODES.M) apiVersion: Int
    ) {
        // GIVEN
        val mockBuildSdkVersionProvider: BuildSdkVersionProvider = mock()
        whenever(mockBuildSdkVersionProvider.version) doReturn apiVersion
        val testedProvider = DefaultAppStartTimeProvider(mockBuildSdkVersionProvider)

        // WHEN
        val uptime1 = testedProvider.appUptimeNs
        Thread.sleep(10)
        val uptime2 = testedProvider.appUptimeNs

        // THEN
        assertThat(uptime2).isGreaterThan(uptime1)
    }
}
