/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.insights

import android.os.Handler
import com.datadog.android.insights.internal.domain.TimelineEvent
import com.datadog.android.insights.internal.platform.Platform
import com.datadog.android.rum.internal.instrumentation.insights.InsightsUpdatesListener
import fr.xgouchet.elmyr.annotation.DoubleForgery
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
        // Construct with no periodic ticks to avoid relying on delayed scheduling
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
    fun `M append SlowFrame event W onSlowFrame()`() {
        // When
        testedInsightsCollector.onSlowFrame(startedTimestamp = 0L, durationNs = 16_700_000L)

        // Then
        verify(mockInsightsUpdatesListener).onDataUpdated()
        val e = testedInsightsCollector.eventsState.single()
        assertThat(e is TimelineEvent.SlowFrame).isTrue
        assertThat(e.durationNs).isEqualTo(16_700_000L)
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
    fun `M set vmRssMb from memory vital W onMemoryVital() {non-null}`(
        @DoubleForgery(min = 0.0) fakememoryValue: Double
    ) {
        // When
        testedInsightsCollector.onMemoryVital(memoryValue = fakememoryValue)

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
    fun `M set slowFramesRate rounded W onSlowFrameRate()`(@DoubleForgery(min = 0.0) fakeRate: Double) {
        // When
        testedInsightsCollector.onSlowFrameRate(rate = fakeRate)

        // Then
        verify(mockInsightsUpdatesListener).onDataUpdated()
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
}
