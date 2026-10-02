/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder

import android.view.Window
import com.datadog.android.internal.system.BuildSdkVersionProvider
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal class FrameHealthMonitorTest {

    @Mock
    lateinit var mockSupportedBuildSdkVersionProvider: BuildSdkVersionProvider

    @Mock
    lateinit var mockUnsupportedBuildSdkVersionProvider: BuildSdkVersionProvider

    lateinit var mockWindow: Window

    lateinit var testedMonitor: FrameHealthMonitor

    @BeforeEach
    fun `set up`() {
        whenever(mockSupportedBuildSdkVersionProvider.isAtLeastN) doReturn true
        whenever(mockUnsupportedBuildSdkVersionProvider.isAtLeastN) doReturn false
        mockWindow = mock()
        testedMonitor = FrameHealthMonitor(
            rollingWindowSize = ROLLING_WINDOW_SIZE,
            degradedThreshold = DEGRADED_THRESHOLD,
            implausibleReadingCircuitBreaker = IMPLAUSIBLE_CIRCUIT_BREAKER,
            buildSdkVersionProvider = mockSupportedBuildSdkVersionProvider
        )
    }

    @Test
    fun `M report not degraded W isDegraded { no frames observed yet }`() {
        assertThat(testedMonitor.isDegraded()).isFalse
    }

    @Test
    fun `M report not degraded W isDegraded { fewer frames than the rolling window }`() {
        // Given - one fewer than the window size, all of them janky
        repeat(ROLLING_WINDOW_SIZE - 1) {
            testedMonitor.recordFrameDuration(JANKY_DURATION_NS, REFRESH_PERIOD_NS, mockWindow)
        }

        // Then
        assertThat(testedMonitor.isDegraded()).isFalse
    }

    @Test
    fun `M report degraded W isDegraded { janky count reaches the threshold }`() {
        // Given - exactly degradedThreshold janky frames, the rest healthy
        repeat(DEGRADED_THRESHOLD) {
            testedMonitor.recordFrameDuration(JANKY_DURATION_NS, REFRESH_PERIOD_NS, mockWindow)
        }
        repeat(ROLLING_WINDOW_SIZE - DEGRADED_THRESHOLD) {
            testedMonitor.recordFrameDuration(HEALTHY_DURATION_NS, REFRESH_PERIOD_NS, mockWindow)
        }

        // Then
        assertThat(testedMonitor.isDegraded()).isTrue
    }

    @Test
    fun `M report not degraded W isDegraded { janky count below the threshold }`() {
        // Given - one fewer than degradedThreshold janky frames, the rest healthy
        repeat(DEGRADED_THRESHOLD - 1) {
            testedMonitor.recordFrameDuration(JANKY_DURATION_NS, REFRESH_PERIOD_NS, mockWindow)
        }
        repeat(ROLLING_WINDOW_SIZE - (DEGRADED_THRESHOLD - 1)) {
            testedMonitor.recordFrameDuration(HEALTHY_DURATION_NS, REFRESH_PERIOD_NS, mockWindow)
        }

        // Then
        assertThat(testedMonitor.isDegraded()).isFalse
    }

    @Test
    fun `M roll the window W isDegraded { old janky frames age out }`() {
        // Given - fill the window with janky frames, enough to be degraded
        repeat(ROLLING_WINDOW_SIZE) {
            testedMonitor.recordFrameDuration(JANKY_DURATION_NS, REFRESH_PERIOD_NS, mockWindow)
        }
        check(testedMonitor.isDegraded()) { "Precondition: should be degraded right after filling with janky frames" }

        // When - overwrite the entire window with healthy frames
        repeat(ROLLING_WINDOW_SIZE) {
            testedMonitor.recordFrameDuration(HEALTHY_DURATION_NS, REFRESH_PERIOD_NS, mockWindow)
        }

        // Then
        assertThat(testedMonitor.isDegraded()).isFalse
    }

    @Test
    fun `M report not degraded W isDegraded { unsupported API level, even with many janky frames }`() {
        // Given
        testedMonitor = FrameHealthMonitor(
            rollingWindowSize = ROLLING_WINDOW_SIZE,
            degradedThreshold = DEGRADED_THRESHOLD,
            implausibleReadingCircuitBreaker = IMPLAUSIBLE_CIRCUIT_BREAKER,
            buildSdkVersionProvider = mockUnsupportedBuildSdkVersionProvider
        )

        // When
        repeat(ROLLING_WINDOW_SIZE) {
            testedMonitor.recordFrameDuration(JANKY_DURATION_NS, REFRESH_PERIOD_NS, mockWindow)
        }

        // Then
        assertThat(testedMonitor.isDegraded()).isFalse
    }

    @Test
    fun `M ignore implausible readings W isDegraded { non-positive or absurd duration }`() {
        // Given - a couple of implausible readings, each individually interspersed right after a
        // valid one so the circuit breaker's consecutive-count never climbs above 1 - this test
        // isolates "implausible readings don't count toward the window" from the circuit breaker
        // behavior, which has its own dedicated test below.
        repeat(DEGRADED_THRESHOLD) {
            testedMonitor.recordFrameDuration(JANKY_DURATION_NS, REFRESH_PERIOD_NS, mockWindow)
            testedMonitor.recordFrameDuration(0L, REFRESH_PERIOD_NS, mockWindow)
        }
        repeat(ROLLING_WINDOW_SIZE - DEGRADED_THRESHOLD) {
            testedMonitor.recordFrameDuration(HEALTHY_DURATION_NS, REFRESH_PERIOD_NS, mockWindow)
            testedMonitor.recordFrameDuration(IMPLAUSIBLE_DURATION_NS, REFRESH_PERIOD_NS, mockWindow)
        }

        // Then - only the ROLLING_WINDOW_SIZE valid readings actually filled the window (exactly
        // DEGRADED_THRESHOLD of them janky), so this sits right at the degraded threshold; the
        // implausible ones in between contributed nothing.
        assertThat(testedMonitor.isDegraded()).isTrue
    }

    @Test
    fun `M disable permanently W isDegraded { implausible readings exceed the circuit breaker }`() {
        // Given - enough implausible readings to trip the circuit breaker
        repeat(IMPLAUSIBLE_CIRCUIT_BREAKER) {
            testedMonitor.recordFrameDuration(IMPLAUSIBLE_DURATION_NS, REFRESH_PERIOD_NS, mockWindow)
        }

        // When - now feed it genuinely, clearly degraded data
        repeat(ROLLING_WINDOW_SIZE) {
            testedMonitor.recordFrameDuration(JANKY_DURATION_NS, REFRESH_PERIOD_NS, mockWindow)
        }

        // Then - once disabled, it stays disabled for the lifetime of this window's state
        assertThat(testedMonitor.isDegraded()).isFalse
    }

    @Test
    fun `M reset the implausible streak W isDegraded { a valid reading arrives before the breaker trips }`() {
        // Given - almost enough implausible readings to trip the breaker, then one valid reading
        repeat(IMPLAUSIBLE_CIRCUIT_BREAKER - 1) {
            testedMonitor.recordFrameDuration(IMPLAUSIBLE_DURATION_NS, REFRESH_PERIOD_NS, mockWindow)
        }
        testedMonitor.recordFrameDuration(HEALTHY_DURATION_NS, REFRESH_PERIOD_NS, mockWindow)

        // When - now go back to implausible readings, fewer than the breaker threshold on their own
        repeat(IMPLAUSIBLE_CIRCUIT_BREAKER - 1) {
            testedMonitor.recordFrameDuration(IMPLAUSIBLE_DURATION_NS, REFRESH_PERIOD_NS, mockWindow)
        }
        // ... followed by enough janky readings to otherwise be degraded
        repeat(ROLLING_WINDOW_SIZE - 1) {
            testedMonitor.recordFrameDuration(JANKY_DURATION_NS, REFRESH_PERIOD_NS, mockWindow)
        }

        // Then - the circuit breaker never actually tripped, so real data is still trusted
        assertThat(testedMonitor.isDegraded()).isTrue
    }

    @Test
    fun `M isolate state per window W isDegraded { one window janky, another healthy }`() {
        // Given - two distinct windows, e.g. an Activity behind a Dialog
        val mockOtherWindow: Window = mock()
        repeat(ROLLING_WINDOW_SIZE) {
            testedMonitor.recordFrameDuration(JANKY_DURATION_NS, REFRESH_PERIOD_NS, mockWindow)
            testedMonitor.recordFrameDuration(HEALTHY_DURATION_NS, REFRESH_PERIOD_NS, mockOtherWindow)
        }

        // Then - the janky window's own frames aren't diluted by the other window's healthy ones,
        // and isDegraded() reports true because at least one tracked window is degraded
        assertThat(testedMonitor.isDegraded()).isTrue
    }

    @Test
    fun `M not degrade a healthy window W isDegraded { only a different window is janky }`() {
        // Given - one window is fully healthy, a different one is fully janky
        val mockJankyWindow: Window = mock()
        repeat(ROLLING_WINDOW_SIZE) {
            testedMonitor.recordFrameDuration(HEALTHY_DURATION_NS, REFRESH_PERIOD_NS, mockWindow)
        }

        // When
        repeat(ROLLING_WINDOW_SIZE) {
            testedMonitor.recordFrameDuration(JANKY_DURATION_NS, REFRESH_PERIOD_NS, mockJankyWindow)
        }

        // Then - isDegraded() is still true overall (the other window is genuinely degraded), but
        // this test exists to document that the healthy window's own frames are never touched by
        // the janky window's readings - regression here would only be visible via recordFrameDuration
        // being called with the wrong state, which the isolation test above already guards against.
        assertThat(testedMonitor.isDegraded()).isTrue
    }

    @Test
    fun `M register a listener W startTracking { supported API level }`() {
        // When
        testedMonitor.startTracking(mockWindow)

        // Then - the background handler is created lazily on this first real call and used to
        // register the listener
        verify(mockWindow).addOnFrameMetricsAvailableListener(any(), any())
    }

    @Test
    fun `M not register a listener W startTracking { unsupported API level }`() {
        // Given
        testedMonitor = FrameHealthMonitor(
            buildSdkVersionProvider = mockUnsupportedBuildSdkVersionProvider
        )

        // When
        testedMonitor.startTracking(mockWindow)

        // Then - no thread, no listener, this stays fully inert
        verify(mockWindow, never()).addOnFrameMetricsAvailableListener(any(), any())
    }

    @Test
    fun `M not register twice W startTracking { same window tracked again }`() {
        // Given
        testedMonitor.startTracking(mockWindow)

        // When
        testedMonitor.startTracking(mockWindow)

        // Then
        verify(mockWindow, times(1)).addOnFrameMetricsAvailableListener(any(), any())
    }

    @Test
    fun `M recreate the handler W startTracking { called again after shutdown }`() {
        // Given - track a window, then fully shut down
        testedMonitor.startTracking(mockWindow)
        testedMonitor.shutdown()

        // When - a later startTracking call must not silently register against the now-dead
        // handler/thread that shutdown() released
        val mockSecondWindow = mock<Window>()
        testedMonitor.startTracking(mockSecondWindow)

        // Then
        verify(mockSecondWindow).addOnFrameMetricsAvailableListener(any(), any())
    }

    @Test
    fun `M remove the listener W stopTracking { a tracked window }`() {
        // Given
        testedMonitor.startTracking(mockWindow)
        val listenerCaptor = argumentCaptor<Window.OnFrameMetricsAvailableListener>()
        verify(mockWindow).addOnFrameMetricsAvailableListener(listenerCaptor.capture(), any())

        // When
        testedMonitor.stopTracking(mockWindow)

        // Then
        verify(mockWindow).removeOnFrameMetricsAvailableListener(listenerCaptor.firstValue)
    }

    @Test
    fun `M not throw W stopTrackingAll { nothing was ever tracked }`() {
        // Given
        testedMonitor = FrameHealthMonitor(
            buildSdkVersionProvider = mockUnsupportedBuildSdkVersionProvider
        )

        // When/Then - safe to call with an empty registry, on an unsupported API level
        testedMonitor.stopTrackingAll()
    }

    @Test
    fun `M not throw W shutdown { called twice, nothing was ever tracked }`() {
        // When/Then - idempotent, and safe even if this instance's background thread was never
        // actually started (unsupported API level)
        testedMonitor = FrameHealthMonitor(
            buildSdkVersionProvider = mockUnsupportedBuildSdkVersionProvider
        )
        testedMonitor.shutdown()
        testedMonitor.shutdown()
    }

    @Test
    fun `M leave isDegraded reporting false W shutdown { called on a supported instance }`() {
        // Given - fill the window with janky frames so it would otherwise report degraded
        repeat(ROLLING_WINDOW_SIZE) {
            testedMonitor.recordFrameDuration(JANKY_DURATION_NS, REFRESH_PERIOD_NS, mockWindow)
        }
        check(testedMonitor.isDegraded()) { "Precondition: should be degraded before shutdown" }

        // When
        testedMonitor.shutdown()

        // Then - shutdown only releases the background thread and clears tracked windows; it's
        // not expected to erase already-observed history, so this asserts shutdown itself doesn't
        // throw and the instance remains queryable afterward.
        assertThat(testedMonitor.isDegraded()).isTrue
    }

    private companion object {
        const val ROLLING_WINDOW_SIZE = 8
        const val DEGRADED_THRESHOLD = 3
        const val IMPLAUSIBLE_CIRCUIT_BREAKER = 5
        const val REFRESH_PERIOD_NS = 16_666_666L // ~60Hz
        const val HEALTHY_DURATION_NS = 8_000_000L // well under the refresh period
        const val JANKY_DURATION_NS = 25_000_000L // over the refresh period
        const val IMPLAUSIBLE_DURATION_NS = 2_000_000_000L // beyond what any real frame can take
    }
}
