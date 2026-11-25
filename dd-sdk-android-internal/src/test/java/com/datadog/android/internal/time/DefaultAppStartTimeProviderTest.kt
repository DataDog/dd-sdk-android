/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.internal.time

import android.os.Process
import com.datadog.android.rum.DdRumContentProvider
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.concurrent.TimeUnit

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class DefaultAppStartTimeProviderTest {

    @Mock
    private lateinit var mockTimeProvider: TimeProvider

    private lateinit var testedProvider: DefaultAppStartTimeProvider
    private var originalContentProviderCreateTimeNs: Long = 0L
    private var fakeAppStartTimeNs: Long = 0L
    private var fakeCurrentTimeNs: Long = 0L

    @BeforeEach
    fun `set up`(forge: Forge) {
        originalContentProviderCreateTimeNs = DdRumContentProvider.createTimeNs
        fakeAppStartTimeNs = forge.aLong(min = TimeUnit.MINUTES.toNanos(1), max = TimeUnit.DAYS.toNanos(1))
        val processUptimeMs = forge.aLong(min = TimeUnit.SECONDS.toMillis(11), max = TimeUnit.MINUTES.toMillis(1))
        fakeCurrentTimeNs = fakeAppStartTimeNs + TimeUnit.MILLISECONDS.toNanos(processUptimeMs)
        whenever(mockTimeProvider.getDeviceUptimeMillis()) doReturn Process.getStartUptimeMillis() + processUptimeMs
        whenever(mockTimeProvider.getDeviceElapsedTimeNanos()) doReturn fakeCurrentTimeNs
        DdRumContentProvider.createTimeNs = fakeAppStartTimeNs + forge.aLong(
            min = 1L,
            max = DefaultAppStartTimeProvider.PROCESS_START_TO_CP_START_DIFF_THRESHOLD_NS
        )
        testedProvider = DefaultAppStartTimeProvider { mockTimeProvider }
    }

    @AfterEach
    fun `tear down`() {
        DdRumContentProvider.createTimeNs = originalContentProviderCreateTimeNs
    }

    @Test
    fun `M return process start time W appStartTimeNs`() {
        // When
        val appStartTimeNs = testedProvider.appStartTimeNs

        // Then
        assertThat(appStartTimeNs).isEqualTo(fakeAppStartTimeNs)
    }

    @Test
    fun `M return process start time W appStartTimeNs {exactly at fallback threshold}`() {
        // Given
        DdRumContentProvider.createTimeNs = fakeAppStartTimeNs +
            DefaultAppStartTimeProvider.PROCESS_START_TO_CP_START_DIFF_THRESHOLD_NS

        // When
        val appStartTimeNs = testedProvider.appStartTimeNs

        // Then
        assertThat(appStartTimeNs).isEqualTo(fakeAppStartTimeNs)
    }

    @Test
    fun `M fall back to content provider creation time W appStartTimeNs {before fallback threshold}`() {
        // Given
        DdRumContentProvider.createTimeNs = fakeAppStartTimeNs +
            DefaultAppStartTimeProvider.PROCESS_START_TO_CP_START_DIFF_THRESHOLD_NS + 1L

        // When
        val appStartTimeNs = testedProvider.appStartTimeNs

        // Then
        assertThat(appStartTimeNs).isEqualTo(DdRumContentProvider.createTimeNs)
    }

    @Test
    fun `M fall back to content provider creation time W appStartTimeNs {after content provider creation}`() {
        // Given
        DdRumContentProvider.createTimeNs = fakeAppStartTimeNs - 1L

        // When
        val appStartTimeNs = testedProvider.appStartTimeNs

        // Then
        assertThat(appStartTimeNs).isEqualTo(DdRumContentProvider.createTimeNs)
    }

    @Test
    fun `M return increasing time since process start W appUptimeNs called multiple times`(
        @LongForgery(min = 1L, max = 1000000000L) fakeTimeIncrementNs: Long
    ) {
        // Given
        whenever(mockTimeProvider.getDeviceElapsedTimeNanos())
            .doReturn(fakeCurrentTimeNs)
            .doReturn(fakeCurrentTimeNs + fakeTimeIncrementNs)
            .doReturn(fakeCurrentTimeNs + 2 * fakeTimeIncrementNs)
        testedProvider.appStartTimeNs

        // When
        val firstUptimeNs = testedProvider.appUptimeNs
        val secondUptimeNs = testedProvider.appUptimeNs

        // Then
        assertThat(firstUptimeNs).isEqualTo(fakeCurrentTimeNs + fakeTimeIncrementNs - fakeAppStartTimeNs)
        assertThat(secondUptimeNs).isEqualTo(firstUptimeNs + fakeTimeIncrementNs)
    }
}
