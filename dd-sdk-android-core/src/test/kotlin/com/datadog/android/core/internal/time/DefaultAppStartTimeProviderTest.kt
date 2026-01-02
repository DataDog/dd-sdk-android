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
import com.datadog.android.internal.time.TimeProvider
import com.datadog.android.rum.DdRumContentProvider
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.whenever
import java.util.concurrent.TimeUnit

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
class DefaultAppStartTimeProviderTest {

    @Mock
    private lateinit var mockTimeProvider: TimeProvider

    @Mock
    private lateinit var mockBuildSdkVersionProvider: BuildSdkVersionProvider

    @Test
    fun `M return process start time W appStartTime { N+ }`(
        @IntForgery(min = Build.VERSION_CODES.N) apiVersion: Int,
        @LongForgery(min = 0L) fakeCurrentTimeNs: Long
    ) {
        // GIVEN
        whenever(mockBuildSdkVersionProvider.version) doReturn apiVersion
        whenever(mockTimeProvider.getDeviceElapsedTimeNanos()) doReturn fakeCurrentTimeNs
        val diffMs = SystemClock.elapsedRealtime() - Process.getStartElapsedRealtime()
        val expectedStartTimeNs = fakeCurrentTimeNs - TimeUnit.MILLISECONDS.toNanos(diffMs)
        DdRumContentProvider.createTimeNs = expectedStartTimeNs
        val testedProvider = DefaultAppStartTimeProvider(
            { mockTimeProvider },
            mockBuildSdkVersionProvider
        )

        // WHEN
        val providedStartTime = testedProvider.appStartTimeNs

        // THEN
        assertThat(providedStartTime).isEqualTo(expectedStartTimeNs)
    }

    @Test
    fun `M fall back to DdRumContentProvider W appStartTime { N+ getStartElapsedRealtime returns buggy value }`(
        @IntForgery(min = Build.VERSION_CODES.N) apiVersion: Int,
        @LongForgery(min = 0L) fakeCurrentTimeNs: Long,
        forge: Forge
    ) {
        // GIVEN
        whenever(mockBuildSdkVersionProvider.version) doReturn apiVersion
        whenever(mockTimeProvider.getDeviceElapsedTimeNanos()) doReturn fakeCurrentTimeNs
        val diffMs = SystemClock.elapsedRealtime() - Process.getStartElapsedRealtime()
        val startTimeNs = fakeCurrentTimeNs - TimeUnit.MILLISECONDS.toNanos(diffMs)
        DdRumContentProvider.createTimeNs = startTimeNs +
            forge.aLong(min = DefaultAppStartTimeProvider.PROCESS_START_TO_CP_START_DIFF_THRESHOLD_NS + 1)
        val testedProvider = DefaultAppStartTimeProvider(
            { mockTimeProvider },
            mockBuildSdkVersionProvider
        )

        // WHEN
        val providedStartTime = testedProvider.appStartTimeNs

        // THEN
        assertThat(providedStartTime).isEqualTo(DdRumContentProvider.createTimeNs)
    }

    @Test
    fun `M return content provider load time W appStartTime { Legacy }`(
        @IntForgery(min = Build.VERSION_CODES.M, max = Build.VERSION_CODES.N) apiVersion: Int
    ) {
        // GIVEN
        whenever(mockBuildSdkVersionProvider.version) doReturn apiVersion
        val startTimeNs = DdRumContentProvider.createTimeNs
        val testedProvider = DefaultAppStartTimeProvider(
            { mockTimeProvider },
            mockBuildSdkVersionProvider
        )

        // WHEN
        val providedStartTime = testedProvider.appStartTimeNs

        // THEN
        assertThat(providedStartTime).isEqualTo(startTimeNs)
    }

    @Test
    fun `M return app uptime W appUptimeNs`(
        @IntForgery(min = Build.VERSION_CODES.N) apiVersion: Int,
        @LongForgery(min = 1000000L) fakeStartTimeNs: Long,
        @LongForgery(min = 1000L, max = 100000L) fakeUptimeNs: Long
    ) {
        // Given
        whenever(mockBuildSdkVersionProvider.version) doReturn apiVersion

        val diffMs = SystemClock.elapsedRealtime() - Process.getStartElapsedRealtime()
        val fakeCurrentTimeNs = fakeStartTimeNs + TimeUnit.MILLISECONDS.toNanos(diffMs)

        whenever(mockTimeProvider.getDeviceElapsedTimeNanos())
            .doReturn(fakeCurrentTimeNs)
            .doReturn(fakeStartTimeNs + fakeUptimeNs)

        val testedProvider = DefaultAppStartTimeProvider(
            { mockTimeProvider },
            mockBuildSdkVersionProvider
        )

        // When
        testedProvider.appStartTimeNs
        val uptime = testedProvider.appUptimeNs

        // Then
        assertThat(uptime).isEqualTo(fakeUptimeNs)
    }

    @Test
    fun `M return increasing uptime W appUptimeNs called multiple times`(
        @IntForgery(min = Build.VERSION_CODES.N) apiVersion: Int,
        @LongForgery(min = 1000000L) fakeStartTimeNs: Long
    ) {
        // Given
        whenever(mockBuildSdkVersionProvider.version) doReturn apiVersion
        val diffMs = SystemClock.elapsedRealtime() - Process.getStartElapsedRealtime()
        val fakeCurrentTimeNs = fakeStartTimeNs + TimeUnit.MILLISECONDS.toNanos(diffMs)

        whenever(mockTimeProvider.getDeviceElapsedTimeNanos())
            .doReturn(fakeCurrentTimeNs)
            .doReturn(fakeStartTimeNs + 100L)
            .doReturn(fakeStartTimeNs + 200L)

        val testedProvider = DefaultAppStartTimeProvider(
            { mockTimeProvider },
            mockBuildSdkVersionProvider
        )

        // When
        testedProvider.appStartTimeNs
        val uptime1 = testedProvider.appUptimeNs
        val uptime2 = testedProvider.appUptimeNs

        // Then
        assertThat(uptime2).isGreaterThan(uptime1)
    }
}
