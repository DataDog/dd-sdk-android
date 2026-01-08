/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.insights.internal

import android.os.Handler
import com.datadog.android.insights.internal.DefaultInsightsCollector.Companion.GC_COUNT
import com.datadog.android.insights.internal.DefaultInsightsCollector.Companion.ONE_SECOND_NS
import com.datadog.android.insights.internal.domain.TimelineEvent
import com.datadog.android.insights.internal.extensions.Mb
import com.datadog.android.insights.internal.platform.Platform
import com.datadog.android.rum.internal.instrumentation.insights.InsightsUpdatesListener
import fr.xgouchet.elmyr.annotation.DoubleForgery
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.data.Offset
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.Mockito.verifyNoMoreInteractions
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Suppress("OPT_IN_USAGE")
@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class DefaultInsightsCollectorTest {

    private lateinit var testedInsightsCollector: DefaultInsightsCollector

    @Mock
    private lateinit var mockHandler: Handler

    @Mock
    private lateinit var mockPlatform: Platform

    @Mock
    private lateinit var mockInsightsUpdatesListener: InsightsUpdatesListener

    @BeforeEach
    fun `set up`() {
        testedInsightsCollector = DefaultInsightsCollector(
            maxSize = 5,
            updateIntervalMs = 0L,
            handler = mockHandler,
            platform = mockPlatform
        )

        testedInsightsCollector.addUpdateListener(mockInsightsUpdatesListener)

        whenever(mockHandler.post(any())).thenAnswer {
            (it.arguments[0] as Runnable).run()
            true
        }

        whenever(mockHandler.postDelayed(any(), any())).thenAnswer {
            (it.arguments[0] as Runnable).run()
            true
        }
    }

    @Test
    fun `M notify listener and append Action W onAction()`() {
        // When
        testedInsightsCollector.onAction()

        // Then
        verify(mockInsightsUpdatesListener).onDataUpdated()
        verifyNoMoreInteractions(mockInsightsUpdatesListener)
        assertThat(testedInsightsCollector.eventsState).hasSize(1)
        assertThat(testedInsightsCollector.eventsState).first().isEqualTo(TimelineEvent.Action)
    }

    @Test
    fun `M clear previous events and set viewName W onNewView()`() {
        // Given
        testedInsightsCollector.onAction()

        // When
        testedInsightsCollector.onNewView(name = "Home")

        // Then
        assertThat(testedInsightsCollector.viewName).isEqualTo("Home")
        assertThat(testedInsightsCollector.eventsState).isEmpty()
        verify(mockInsightsUpdatesListener, times(2)).onDataUpdated()
        verifyNoMoreInteractions(mockInsightsUpdatesListener)
    }

    @Test
    fun `M append SlowFrame event W onSlowFrame()`(@LongForgery(min = 0) fakeDurationNs: Long) {
        // When
        testedInsightsCollector.onSlowFrame(startedTimestamp = 0L, durationNs = fakeDurationNs)

        // Then
        verify(mockInsightsUpdatesListener).onDataUpdated()
        val e = testedInsightsCollector.eventsState.single()
        assertThat(e).isInstanceOf(TimelineEvent.SlowFrame::class.java)
        assertThat(e.durationNs).isEqualTo(fakeDurationNs)
    }

    @Test
    fun `M append LongTask event W onLongTask()`(@LongForgery(min = 0) fakeDurationNs: Long) {
        // When
        testedInsightsCollector.onLongTask(startedTimestamp = 0L, durationNs = fakeDurationNs)

        // Then
        verify(mockInsightsUpdatesListener).onDataUpdated()
        val e = testedInsightsCollector.eventsState.single()
        assertThat(e is TimelineEvent.LongTask).isTrue
        assertThat(e.durationNs).isEqualTo(fakeDurationNs)
    }

    @Test
    fun `M append Resource event W onNetworkRequest()`(@LongForgery(min = 0) fakeDurationNs: Long) {
        // When
        testedInsightsCollector.onNetworkRequest(startedTimestamp = 0L, durationNs = fakeDurationNs)

        // Then
        verify(mockInsightsUpdatesListener).onDataUpdated()
        val e = testedInsightsCollector.eventsState.single()
        assertThat(e is TimelineEvent.Resource).isTrue
        assertThat(e.durationNs).isEqualTo(fakeDurationNs)
    }

    @Test
    fun `M set vmRssMb W onMemoryVital() {non-null}`(
        @DoubleForgery(min = 0.0) fakeMemoryValue: Double
    ) {
        // When
        testedInsightsCollector.onMemoryVital(memoryValue = fakeMemoryValue)

        // Then
        verify(mockHandler).post(any())
        verify(mockInsightsUpdatesListener).onDataUpdated()
        assertThat(testedInsightsCollector.vmRssMb).isNotNaN
    }

    @Test
    fun `M set vmRssMb to NaN W onMemoryVital() {null}`() {
        // When
        testedInsightsCollector.onMemoryVital(memoryValue = null)

        // Then
        verify(mockInsightsUpdatesListener).onDataUpdated()
        assertThat(testedInsightsCollector.vmRssMb).isNaN
    }

    @Test
    fun `M set cpuTicksPerSecond to NaN W viewDuration less than 1s`(@DoubleForgery(min = 0.0) fakeCpuTicks: Double) {
        // When
        testedInsightsCollector.onCpuVital(cpuTicks = fakeCpuTicks)

        // Then
        verify(mockInsightsUpdatesListener).onDataUpdated()
        assertThat(testedInsightsCollector.cpuTicksPerSecond).isNaN
    }

    @Test
    fun `M set cpuTicksPerSecond rounded W onCpuVital()`(
        @DoubleForgery(min = 0.0, max = 1e6) fakeCpuTicks: Double,
        @LongForgery(min = ONE_SECOND_NS, max = 10 * ONE_SECOND_NS) fakeStartTimeNs: Long
    ) {
        // Given
        whenever(mockPlatform.nanoTime()).thenAnswer { fakeStartTimeNs }

        // When
        testedInsightsCollector.onCpuVital(cpuTicks = fakeCpuTicks)

        // Then
        assertThat(testedInsightsCollector.cpuTicksPerSecond).isNotNaN
        assertThat(testedInsightsCollector.cpuTicksPerSecond).isCloseTo(
            fakeCpuTicks / (fakeStartTimeNs / ONE_SECOND_NS.toDouble()),
            Offset.offset(0.01)
        )
    }

    @Test
    fun `M set slowFramesRate rounded W onSlowFrameRate()`(@DoubleForgery(min = 0.0, max = 500.0) fakeRate: Double) {
        // When
        testedInsightsCollector.onSlowFrameRate(rate = fakeRate)

        // Then
        verify(mockInsightsUpdatesListener).onDataUpdated()
        assertThat(testedInsightsCollector.slowFramesRate).isNotNaN
        assertThat(testedInsightsCollector.slowFramesRate).isCloseTo(
            fakeRate,
            Offset.offset(0.01)
        )
    }

    @Test
    fun `M drop all events on maxSize change W maxSize setter()`() {
        // Given
        repeat(3) { testedInsightsCollector.onAction() }
        assertThat(testedInsightsCollector.eventsState).hasSize(3)

        // When
        testedInsightsCollector.maxSize = 2

        // Then
        assertThat(testedInsightsCollector.eventsState).isEmpty()
    }

    @Test
    fun `M update gcCallsPerSecond and nativeHeapMb W onAction()`(
        @LongForgery(min = ONE_SECOND_NS, max = 10 * ONE_SECOND_NS) fakeStartTimeNs: Long,
        @IntForgery(min = 0, max = 1_000) fakeGcCount: Int,
        @LongForgery(min = 0L, max = 100 * 1024 * 1024) fakeNativeHeapSize: Long
    ) {
        // Given
        whenever(mockPlatform.nanoTime()).thenAnswer { fakeStartTimeNs }
        whenever(mockPlatform.getRuntimeStat(GC_COUNT)).thenReturn(fakeGcCount.toString())
        whenever(mockPlatform.getNativeHeapAllocatedSize()).thenReturn(fakeNativeHeapSize)

        // When
        testedInsightsCollector.onAction()

        // Then
        assertThat(testedInsightsCollector.gcCallsPerSecond).isNotNaN
        assertThat(testedInsightsCollector.gcCallsPerSecond).isCloseTo(
            fakeGcCount / (fakeStartTimeNs / ONE_SECOND_NS.toDouble()),
            Offset.offset(0.01)
        )

        assertThat(testedInsightsCollector.nativeHeapMb).isNotNaN
        assertThat(testedInsightsCollector.nativeHeapMb).isCloseTo(
            fakeNativeHeapSize.toDouble().Mb,
            Offset.offset(1.0)
        )
    }

    @Test
    fun `M set gcCallsPerSecond to NaN W onAction() {invalid statName}`(
        @LongForgery(min = ONE_SECOND_NS, max = 10 * ONE_SECOND_NS) fakeStartTimeNs: Long
    ) {
        // Given
        whenever(mockPlatform.nanoTime()).thenAnswer { fakeStartTimeNs }
        whenever(mockPlatform.getRuntimeStat(any())).thenReturn(null)

        // When
        testedInsightsCollector.onAction()

        // Then
        assertThat(testedInsightsCollector.gcCallsPerSecond).isNaN
    }
}
