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
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
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

    private lateinit var testedProvider: DefaultAppStartTimeProvider

    @BeforeEach
    fun `set up`() {
        testedProvider =
            DefaultAppStartTimeProvider({ mockTimeProvider }, mockBuildSdkVersionProvider)
    }

    @Test
    fun `M return process start time W appStartTime { N+ }`(
        forge: Forge,
        @LongForgery(min = 0L) fakeCurrentTimeNs: Long
    ) {
        // GIVEN
        whenever(mockBuildSdkVersionProvider.isAtLeastN) doReturn true
        whenever(mockTimeProvider.getDeviceElapsedTimeNanos()) doReturn fakeCurrentTimeNs
        val diffMs = stubAndGetElapsedRealtimeMs() - Process.getStartElapsedRealtime()
        val startTimeNs = fakeCurrentTimeNs - TimeUnit.MILLISECONDS.toNanos(diffMs)
        DdRumContentProvider.createTimeNs = startTimeNs +
            forge.aLong(min = 0, max = DefaultAppStartTimeProvider.PROCESS_START_TO_CP_START_DIFF_THRESHOLD_NS)

        // WHEN
        val providedStartTime = testedProvider.appStartTimeNs

        // THEN
        assertThat(providedStartTime).isEqualTo(startTimeNs)
    }

    @Test
    fun `M fall back to DdRumContentProvider W appStartTime { N+ getStartElapsedRealtime returns buggy value }`(
        @LongForgery(min = 0L) fakeCurrentTimeNs: Long,
        forge: Forge
    ) {
        // GIVEN
        whenever(mockBuildSdkVersionProvider.isAtLeastN) doReturn true
        whenever(mockTimeProvider.getDeviceElapsedTimeNanos()) doReturn fakeCurrentTimeNs
        val diffMs = stubAndGetElapsedRealtimeMs() - Process.getStartElapsedRealtime()
        val startTimeNs = fakeCurrentTimeNs - TimeUnit.MILLISECONDS.toNanos(diffMs)
        DdRumContentProvider.createTimeNs = startTimeNs +
            forge.aLong(min = DefaultAppStartTimeProvider.PROCESS_START_TO_CP_START_DIFF_THRESHOLD_NS)

        // WHEN
        val providedStartTime = testedProvider.appStartTimeNs

        // THEN
        assertThat(providedStartTime).isEqualTo(DdRumContentProvider.createTimeNs)
    }

    @Test
    fun `M return content provider load time W appStartTime { Legacy }`() {
        // GIVEN
        whenever(mockBuildSdkVersionProvider.isAtLeastN) doReturn false
        val startTimeNs = DdRumContentProvider.createTimeNs

        // WHEN
        val providedStartTime = testedProvider.appStartTimeNs

        // THEN
        assertThat(providedStartTime).isEqualTo(startTimeNs)
    }

    @Test
    fun `M return app uptime W appUptimeNs`(
        @LongForgery(min = 1000000L) fakeStartTimeNs: Long,
        @LongForgery(min = 1000L, max = 100000L) fakeUptimeNs: Long
    ) {
        // Given
        whenever(mockBuildSdkVersionProvider.isAtLeastN) doReturn true

        val diffMs = stubAndGetElapsedRealtimeMs() - Process.getStartElapsedRealtime()
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
        @LongForgery(min = 1000000L) fakeStartTimeNs: Long
    ) {
        // Given
        whenever(mockBuildSdkVersionProvider.isAtLeastN) doReturn true
        val diffMs = stubAndGetElapsedRealtimeMs() - Process.getStartElapsedRealtime()
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

    @Test
    fun `M return increasing uptime W appUptimeNs called multiple times { below N }`(
        @LongForgery(min = 1000000L) fakeStartTimeNs: Long
    ) {
        // Given
        whenever(mockBuildSdkVersionProvider.isAtLeastN) doReturn false

        whenever(mockTimeProvider.getDeviceElapsedTimeNanos())
            .doReturn(fakeStartTimeNs + 100L)
            .doReturn(fakeStartTimeNs + 200L)
            .doReturn(fakeStartTimeNs + 300L)

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

    @Test
    fun `M return increasing uptime W appUptimeNs called multiple times { N+ }`(
        @LongForgery(min = 1000000L) fakeStartTimeNs: Long
    ) {
        // Given
        whenever(mockBuildSdkVersionProvider.isAtLeastN) doReturn true
        val diffMs = stubAndGetElapsedRealtimeMs() - Process.getStartElapsedRealtime()
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

    private fun stubAndGetElapsedRealtimeMs(): Long {
        val elapsedRealtimeMs = SystemClock.elapsedRealtime()
        whenever(mockTimeProvider.getDeviceElapsedRealtimeMillis()) doReturn elapsedRealtimeMs
        return elapsedRealtimeMs
    }
}
