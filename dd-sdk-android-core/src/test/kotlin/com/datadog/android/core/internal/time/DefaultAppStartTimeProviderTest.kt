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
        @LongForgery(min = 0L) fakeCurrentTimeNs: Long,
        forge: Forge
    ) {
        // Given
        whenever(mockBuildSdkVersionProvider.version).thenReturn(apiVersion)
        whenever(mockTimeProvider.getDeviceElapsedTimeNs()).thenReturn(fakeCurrentTimeNs)
        val diffMs = SystemClock.elapsedRealtime() - Process.getStartElapsedRealtime()
        val startTimeNs = fakeCurrentTimeNs - TimeUnit.MILLISECONDS.toNanos(diffMs)

        DdRumContentProvider.createTimeNs = startTimeNs +
            forge.aLong(min = 0, max = DefaultAppStartTimeProvider.PROCESS_START_TO_CP_START_DIFF_THRESHOLD_NS)

        val testedProvider = DefaultAppStartTimeProvider(
            mockTimeProvider,
            mockBuildSdkVersionProvider
        )

        // When
        val providedStartTime = testedProvider.appStartTimeNs

        // Then
        assertThat(providedStartTime).isEqualTo(startTimeNs)
    }

    @Test
    fun `M fall back to DdRumContentProvider W appStartTime { N+ getStartElapsedRealtime returns buggy value }`(
        @IntForgery(min = Build.VERSION_CODES.N) apiVersion: Int,
        @LongForgery(min = 0L) fakeCurrentTimeNs: Long,
        forge: Forge
    ) {
        // GIVEN
        whenever(mockBuildSdkVersionProvider.version).thenReturn(apiVersion)

        val diffMs = SystemClock.elapsedRealtime() - Process.getStartElapsedRealtime()
        val startTimeNs = fakeCurrentTimeNs - TimeUnit.MILLISECONDS.toNanos(diffMs)

        DdRumContentProvider.createTimeNs = startTimeNs +
            forge.aLong(min = DefaultAppStartTimeProvider.PROCESS_START_TO_CP_START_DIFF_THRESHOLD_NS)

        // WHEN
        val testedProvider = DefaultAppStartTimeProvider(
            mockTimeProvider,
            mockBuildSdkVersionProvider
        )

        val providedStartTime = testedProvider.appStartTimeNs

        // THEN
        assertThat(providedStartTime).isEqualTo(DdRumContentProvider.createTimeNs)
    }

    @Test
    fun `M return content provider load time W appStartTime { Legacy }`(
        @IntForgery(min = Build.VERSION_CODES.M, max = Build.VERSION_CODES.N) apiVersion: Int
    ) {
        // Given
        whenever(mockBuildSdkVersionProvider.version).thenReturn(apiVersion)
        val startTimeNs = DdRumContentProvider.createTimeNs
        val testedProvider = DefaultAppStartTimeProvider(
            mockTimeProvider,
            mockBuildSdkVersionProvider
        )

        // When
        val providedStartTime = testedProvider.appStartTimeNs

        // Then
        assertThat(providedStartTime).isEqualTo(startTimeNs)
    }

    @Test
    fun `M return app uptime W appUptimeNs`(
        @IntForgery(min = Build.VERSION_CODES.N) apiVersion: Int,
        @LongForgery(min = 1000000L) fakeCurrentTimeNs: Long,
        @LongForgery(min = 100L, max = 999999L) fakeUptimeNs: Long
    ) {
        // Given
        whenever(mockBuildSdkVersionProvider.version).thenReturn(apiVersion)
        val diffMs = SystemClock.elapsedRealtime() - Process.getStartElapsedRealtime()
        val fakeStartTimeNs = fakeCurrentTimeNs - TimeUnit.MILLISECONDS.toNanos(diffMs)
        whenever(mockTimeProvider.getDeviceElapsedTimeNs())
            .thenReturn(fakeCurrentTimeNs)
            .thenReturn(fakeStartTimeNs + fakeUptimeNs)

        // When
        val testedProvider = DefaultAppStartTimeProvider(
            mockTimeProvider,
            mockBuildSdkVersionProvider
        )
        testedProvider.appStartTimeNs
        val uptime = testedProvider.appUptimeNs

        // Then
        assertThat(uptime).isEqualTo(fakeUptimeNs)
    }

    @Test
    fun `M return increasing uptime W appUptimeNs called multiple times`(
        @IntForgery(min = Build.VERSION_CODES.N) apiVersion: Int,
        @LongForgery(min = 1000000L) fakeCurrentTimeNs: Long
    ) {
        // Given
        whenever(mockBuildSdkVersionProvider.version).thenReturn(apiVersion)
        val diffMs = SystemClock.elapsedRealtime() - Process.getStartElapsedRealtime()
        val fakeStartTimeNs = fakeCurrentTimeNs - TimeUnit.MILLISECONDS.toNanos(diffMs)
        whenever(mockTimeProvider.getDeviceElapsedTimeNs())
            .thenReturn(fakeCurrentTimeNs)
            .thenReturn(fakeStartTimeNs + 100L)
            .thenReturn(fakeStartTimeNs + 200L)

        // When
        val testedProvider = DefaultAppStartTimeProvider(
            mockTimeProvider,
            mockBuildSdkVersionProvider
        )
        testedProvider.appStartTimeNs
        val uptime1 = testedProvider.appUptimeNs
        val uptime2 = testedProvider.appUptimeNs

        // Then
        assertThat(uptime2).isGreaterThan(uptime1)
    }
}
