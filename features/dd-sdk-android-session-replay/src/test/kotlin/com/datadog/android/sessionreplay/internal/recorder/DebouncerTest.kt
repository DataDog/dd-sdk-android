/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder

import android.os.Handler
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.internal.time.TimeProvider
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class DebouncerTest {
    @Mock lateinit var mockHandler: Handler

    @Mock lateinit var mockSdkCore: FeatureSdkCore

    @Mock lateinit var mockTimeProvider: TimeProvider

    @Mock lateinit var mockTimeBank: TimeBank

    private lateinit var testedDebouncer: Debouncer
    private var fakeClockMs = 0L
    private var fakeScheduledTimeMs = 0L
    private var fakeScheduledCapture: Runnable? = null
    private val fakeCaptureTimes = mutableListOf<Long>()
    private val fakeCapture = Runnable { fakeCaptureTimes.add(fakeClockMs) }

    @BeforeEach
    fun `set up`() {
        whenever(mockSdkCore.timeProvider).thenReturn(mockTimeProvider)
        whenever(mockTimeProvider.getDeviceElapsedTimeNanos()).thenAnswer { fakeClockMs * 1_000_000L }
        whenever(mockTimeBank.updateAndCheck(any())).thenReturn(true)
        whenever(mockHandler.postDelayed(any(), any())).thenAnswer {
            assertThat(fakeScheduledCapture).isNull()
            fakeScheduledCapture = it.getArgument(0)
            fakeScheduledTimeMs = fakeClockMs + it.getArgument<Long>(1)
            true
        }
        org.mockito.kotlin.doAnswer {
            fakeScheduledCapture = null
            null
        }.whenever(mockHandler).removeCallbacksAndMessages(null)
        testedDebouncer = newDebouncer()
    }

    @Test
    fun `M capture despite continuous draws W debounce {one pending callback}`() {
        // When
        for (fakeDrawTime in 0L..320L step 16) {
            advanceTo(fakeDrawTime)
            testedDebouncer.debounce(fakeCapture)
        }

        // Then
        assertThat(fakeCaptureTimes).containsExactly(64L, 128L, 192L, 256L, 320L)
    }

    @Test
    fun `M post traversal after drawing W debounce {minimum interval elapsed}`() {
        // Given
        testedDebouncer.debounce(fakeCapture)
        advanceTo(500L)

        // When
        testedDebouncer.debounce(fakeCapture)

        // Then
        assertThat(fakeCaptureTimes).containsExactly(64L)
        advanceTo(500L)
        assertThat(fakeCaptureTimes).containsExactly(64L, 500L)
    }

    @Test
    fun `M retain final update W debounce {budget recovers without more draws}`() {
        // Given
        whenever(mockTimeBank.updateAndCheck(any())).thenReturn(false)
        whenever(mockTimeBank.timeUntilAvailableInNs()).thenReturn(800_000_000L)
        testedDebouncer.debounce(fakeCapture)
        advanceTo(64L)
        assertThat(fakeCaptureTimes).isEmpty()

        // When
        whenever(mockTimeBank.updateAndCheck(any())).thenReturn(true)
        advanceTo(864L)

        // Then
        assertThat(fakeCaptureTimes).containsExactly(864L)
        assertThat(fakeScheduledCapture).isNull()
        verify(mockTimeBank, times(2)).updateAndCheck(any())
    }

    @Test
    fun `M capture latest update W debounce {budget retry already pending}`() {
        // Given
        whenever(mockTimeBank.updateAndCheck(any())).thenReturn(false)
        whenever(mockTimeBank.timeUntilAvailableInNs()).thenReturn(800_000_000L)
        testedDebouncer.debounce({ error("Superseded capture must not run") })
        advanceTo(500L)

        // When
        testedDebouncer.debounce(fakeCapture)
        whenever(mockTimeBank.updateAndCheck(any())).thenReturn(true)
        advanceTo(864L)

        // Then
        assertThat(fakeCaptureTimes).containsExactly(864L)
    }

    @Test
    fun `M capture navigation promptly W debounce {forced with pending budget debt}`() {
        // Given
        whenever(mockTimeBank.updateAndCheck(any())).thenReturn(false)
        whenever(mockTimeBank.timeUntilAvailableInNs()).thenReturn(800_000_000L)
        testedDebouncer.debounce(fakeCapture)
        advanceTo(100L)

        // When
        testedDebouncer.debounce(fakeCapture, force = true)
        advanceTo(100L)

        // Then
        assertThat(fakeCaptureTimes).containsExactly(100L)
        assertThat(fakeScheduledCapture).isNull()
        verify(mockTimeBank).consume(0L)
    }

    @Test
    fun `M cancel pending retry W cancel {already dispatched callback}`() {
        // Given
        whenever(mockTimeBank.updateAndCheck(any())).thenReturn(false)
        testedDebouncer.debounce(fakeCapture)
        advanceTo(64L)
        val fakeOldCallback = fakeScheduledCapture!!

        // When
        testedDebouncer.cancel()
        fakeOldCallback.run()
        advanceTo(5_000L)

        // Then
        assertThat(fakeCaptureTimes).isEmpty()
        assertThat(fakeScheduledCapture).isNull()
    }

    @Test
    fun `M stop retrying W capture {listener stopped or windows disappeared}`() {
        // Given
        testedDebouncer.debounce({ testedDebouncer.cancel() }, force = true)

        // When
        advanceTo(1_000L)

        // Then
        assertThat(fakeScheduledCapture).isNull()
    }

    @Test
    fun `M avoid busy retries W debounce {budget delay rounds to zero}`() {
        // Given
        whenever(mockTimeBank.updateAndCheck(any())).thenReturn(false)
        whenever(mockTimeBank.timeUntilAvailableInNs()).thenReturn(0L)
        testedDebouncer.debounce(fakeCapture)

        // When
        advanceTo(640L)

        // Then
        verify(mockTimeBank, times(10)).updateAndCheck(any())
    }

    @Test
    fun `M charge execution time W debounce {dynamic optimization enabled}`() {
        // Given
        testedDebouncer.debounce({ fakeClockMs += 20L })

        // When
        advanceTo(100L)

        // Then
        verify(mockTimeBank).consume(20_000_000L)
    }

    @Test
    fun `M ignore budget W debounce {dynamic optimization disabled}`() {
        // Given
        testedDebouncer = newDebouncer(dynamicOptimizationEnabled = false)
        testedDebouncer.debounce(fakeCapture)

        // When
        advanceTo(64L)

        // Then
        assertThat(fakeCaptureTimes).containsExactly(64L)
        verifyNoInteractions(mockTimeBank)
    }

    @Test
    fun `M keep synchronous legacy cadence W debounce {opt-in omitted}`() {
        // Given
        testedDebouncer = Debouncer(
            mockHandler,
            sdkCore = mockSdkCore,
            timeBank = mockTimeBank,
            dynamicOptimizationEnabled = false
        )
        testedDebouncer.debounce(fakeCapture)
        fakeClockMs = 32L
        testedDebouncer.debounce(fakeCapture)

        // When: the delayed callback is still pending, but the original threshold is reached.
        fakeClockMs = 64L
        testedDebouncer.debounce(fakeCapture)

        // Then: flag-off keeps the original in-draw capture and cancellation behavior.
        assertThat(fakeCaptureTimes).containsExactly(64L)
        assertThat(fakeScheduledCapture).isNull()
        verifyNoInteractions(mockTimeBank)
    }

    @Test
    fun `M keep legacy budget rejection W debounce {opt-in omitted}`() {
        // Given
        testedDebouncer = Debouncer(
            mockHandler,
            sdkCore = mockSdkCore,
            timeBank = mockTimeBank,
            dynamicOptimizationEnabled = true
        )
        whenever(mockTimeBank.updateAndCheck(any())).thenReturn(false)
        testedDebouncer.debounce(fakeCapture)

        // When
        advanceTo(1_000L)

        // Then: no new retry behavior for other users.
        assertThat(fakeCaptureTimes).isEmpty()
        assertThat(fakeScheduledCapture).isNull()
        verify(mockTimeBank, times(1)).updateAndCheck(any())
    }

    @Test
    fun `M leave legacy pending work unchanged W cancel {opt-in omitted}`() {
        // Given
        testedDebouncer = Debouncer(
            mockHandler,
            sdkCore = mockSdkCore,
            timeBank = mockTimeBank,
            dynamicOptimizationEnabled = false
        )
        testedDebouncer.debounce(fakeCapture)

        // When
        testedDebouncer.cancel()
        advanceTo(64L)

        // Then
        assertThat(fakeCaptureTimes).containsExactly(64L)
    }

    private fun newDebouncer(dynamicOptimizationEnabled: Boolean = true) = Debouncer(
        mockHandler,
        sdkCore = mockSdkCore,
        timeBank = mockTimeBank,
        dynamicOptimizationEnabled = dynamicOptimizationEnabled,
        adaptiveCaptureSchedulingEnabled = true
    )

    private fun advanceTo(targetMs: Long) {
        while (fakeScheduledCapture != null && fakeScheduledTimeMs <= targetMs) {
            fakeClockMs = fakeScheduledTimeMs
            val fakeCallback = fakeScheduledCapture!!
            fakeScheduledCapture = null
            fakeCallback.run()
        }
        fakeClockMs = targetMs
    }
}
