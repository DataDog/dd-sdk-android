/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder

import android.os.Handler
import com.datadog.android.api.feature.Feature.Companion.RUM_FEATURE_NAME
import com.datadog.android.api.feature.FeatureScope
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.internal.time.TimeProvider
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.BoolForgery
import fr.xgouchet.elmyr.annotation.LongForgery
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
import org.mockito.kotlin.clearInvocations
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.concurrent.TimeUnit

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal class DebouncerTest {

    @Mock
    lateinit var mockHandler: Handler

    @Mock
    lateinit var mockSdkCore: FeatureSdkCore

    @Mock
    lateinit var mockTimeBank: TimeBank

    @Mock
    lateinit var mockRumFeature: FeatureScope

    @Mock
    lateinit var mockTimeProvider: TimeProvider

    @Mock
    lateinit var mockFrameHealthMonitor: FrameHealthMonitor

    @LongForgery(min = 0L)
    var fakeInitialTimeNs: Long = 0L

    @BoolForgery
    var fakeDynamicOptimizationEnabled: Boolean = false

    lateinit var testedDebouncer: Debouncer

    @BeforeEach
    fun `set up`() {
        whenever(mockTimeBank.updateAndCheck(any())).thenReturn(true)
        whenever(mockSdkCore.getFeature(RUM_FEATURE_NAME)).thenReturn(mockRumFeature)
        whenever(mockSdkCore.timeProvider) doReturn mockTimeProvider
        whenever(mockTimeProvider.getDeviceElapsedTimeNanos()) doReturn fakeInitialTimeNs
        testedDebouncer = Debouncer(
            mockHandler,
            TEST_MAX_DELAY_THRESHOLD_IN_NS,
            sdkCore = mockSdkCore,
            timeBank = mockTimeBank,
            dynamicOptimizationEnabled = fakeDynamicOptimizationEnabled
        )
    }

    @Test
    fun `M not optimize W dynamicOptimizationEnabled is false`() {
        // Given
        whenever(mockTimeBank.updateAndCheck(any())).thenReturn(false)
        testedDebouncer = Debouncer(
            mockHandler,
            TEST_MAX_DELAY_THRESHOLD_IN_NS,
            sdkCore = mockSdkCore,
            dynamicOptimizationEnabled = false,
            timeBank = mockTimeBank
        )
        val fakeRunnable = TestRunnable()
        val fakeSecondRunnable = TestRunnable()

        // When
        testedDebouncer.debounce(fakeRunnable)

        val fakeExpiredTime = fakeInitialTimeNs + TEST_MAX_DELAY_THRESHOLD_IN_NS
        whenever(mockTimeProvider.getDeviceElapsedTimeNanos()) doReturn fakeExpiredTime
        testedDebouncer.debounce(fakeSecondRunnable)

        // Then
        assertThat(fakeSecondRunnable.wasExecuted).isTrue()
    }

    @Test
    fun `M send telemetry W frame is skipped by time bank`() {
        // Given
        testedDebouncer = Debouncer(
            mockHandler,
            TEST_MAX_DELAY_THRESHOLD_IN_NS,
            sdkCore = mockSdkCore,
            timeBank = mockTimeBank,
            dynamicOptimizationEnabled = true
        )
        whenever(mockTimeBank.updateAndCheck(any())).thenReturn(false)
        val fakeRunnable = TestRunnable()
        val fakeSecondRunnable = TestRunnable()

        // When
        testedDebouncer.debounce(fakeRunnable)
        val fakeExpiredTime = fakeInitialTimeNs + TEST_MAX_DELAY_THRESHOLD_IN_NS
        whenever(mockTimeProvider.getDeviceElapsedTimeNanos()) doReturn fakeExpiredTime
        testedDebouncer.debounce(fakeSecondRunnable)

        // Then
        verify(mockRumFeature, times(1)).sendEvent(any())
    }

    @Test
    fun `M tag the skip reason as time bank W debounce { time bank denies, frame health disabled }`() {
        // Given
        testedDebouncer = Debouncer(
            mockHandler,
            TEST_MAX_DELAY_THRESHOLD_IN_NS,
            sdkCore = mockSdkCore,
            timeBank = mockTimeBank,
            dynamicOptimizationEnabled = true
        )
        whenever(mockTimeBank.updateAndCheck(any())).thenReturn(false)

        // When
        testedDebouncer.debounce(TestRunnable())
        whenever(mockTimeProvider.getDeviceElapsedTimeNanos())
            .doReturn(fakeInitialTimeNs + TEST_MAX_DELAY_THRESHOLD_IN_NS)
        testedDebouncer.debounce(TestRunnable())

        // Then
        argumentCaptor<Map<String, String>> {
            verify(mockRumFeature).sendEvent(capture())
            assertThat(firstValue["reason"]).isEqualTo("time_bank")
        }
    }

    @Test
    fun `M tag the skip reason as frame health W debounce { frame health degraded, time bank allows }`() {
        // Given
        testedDebouncer = Debouncer(
            mockHandler,
            TEST_MAX_DELAY_THRESHOLD_IN_NS,
            sdkCore = mockSdkCore,
            timeBank = mockTimeBank,
            dynamicOptimizationEnabled = false,
            jankAwareBackoffPolicy = JankAwareBackoffPolicy(
                isEnabled = true,
                frameHealthMonitor = mockFrameHealthMonitor
            )
        )
        whenever(mockFrameHealthMonitor.isDegraded()).thenReturn(true)

        // When
        testedDebouncer.debounce(TestRunnable())
        whenever(mockTimeProvider.getDeviceElapsedTimeNanos())
            .doReturn(fakeInitialTimeNs + TEST_MAX_DELAY_THRESHOLD_IN_NS)
        testedDebouncer.debounce(TestRunnable())

        // Then
        argumentCaptor<Map<String, String>> {
            verify(mockRumFeature).sendEvent(capture())
            assertThat(firstValue["reason"]).isEqualTo("frame_health")
        }
    }

    @Test
    fun `M tag both skip reasons W debounce { time bank denies and frame health degraded }`() {
        // Given
        testedDebouncer = Debouncer(
            mockHandler,
            TEST_MAX_DELAY_THRESHOLD_IN_NS,
            sdkCore = mockSdkCore,
            timeBank = mockTimeBank,
            dynamicOptimizationEnabled = true,
            jankAwareBackoffPolicy = JankAwareBackoffPolicy(
                isEnabled = true,
                frameHealthMonitor = mockFrameHealthMonitor
            )
        )
        whenever(mockTimeBank.updateAndCheck(any())).thenReturn(false)
        whenever(mockFrameHealthMonitor.isDegraded()).thenReturn(true)

        // When
        testedDebouncer.debounce(TestRunnable())
        whenever(mockTimeProvider.getDeviceElapsedTimeNanos())
            .doReturn(fakeInitialTimeNs + TEST_MAX_DELAY_THRESHOLD_IN_NS)
        testedDebouncer.debounce(TestRunnable())

        // Then
        argumentCaptor<Map<String, String>> {
            verify(mockRumFeature).sendEvent(capture())
            assertThat(firstValue["reason"]).isEqualTo("time_bank,frame_health")
        }
    }

    @Test
    fun `M delegate to the delayed handler W debounce { first request }`() {
        // Given
        val fakeRunnable = TestRunnable()
        whenever(mockHandler.postDelayed(any(), any())).then {
            (it.arguments[0] as Runnable).run()
            true
        }

        // When
        testedDebouncer.debounce(fakeRunnable)

        // Then
        verify(mockHandler).removeCallbacksAndMessages(null)
        verify(mockHandler).postDelayed(any(), eq(Debouncer.DEBOUNCE_TIME_IN_MS))
    }

    @Test
    fun `M skip the handler and execute the runnable in place W debounce { threshold reached }`() {
        // Given
        val fakeRunnable = TestRunnable()
        val fakeSecondRunnable = TestRunnable()
        testedDebouncer.debounce(fakeRunnable)
        val fakeExpiredTime = fakeInitialTimeNs + TEST_MAX_DELAY_THRESHOLD_IN_NS
        whenever(mockTimeProvider.getDeviceElapsedTimeNanos()) doReturn fakeExpiredTime

        // When
        testedDebouncer.debounce(fakeSecondRunnable)

        // Then
        verify(mockHandler, times(1)).postDelayed(
            any(),
            eq(Debouncer.DEBOUNCE_TIME_IN_MS)
        )
        verify(mockHandler, times(2)).removeCallbacksAndMessages(null)
        assertThat(fakeRunnable.wasExecuted).isFalse
        assertThat(fakeSecondRunnable.wasExecuted).isTrue
    }

    @Test
    fun `M execute the runnable once W debounce { high frequency, delay threshold reached  }`(
        forge: Forge
    ) {
        // Given
        val fakeDelayedRunnables = forge.aList(size = forge.anInt(min = 1, max = 10)) {
            TestRunnable()
        }
        val delayInterval = (TEST_MAX_DELAY_THRESHOLD_IN_NS / fakeDelayedRunnables.size) - 1
        val fakeExecutedRunnable = TestRunnable()
        var currentTime = fakeInitialTimeNs
        whenever(mockTimeProvider.getDeviceElapsedTimeNanos()).thenAnswer { currentTime }

        fakeDelayedRunnables.forEach {
            testedDebouncer.debounce(it)
            currentTime += delayInterval
        }

        // When
        currentTime = fakeInitialTimeNs + TEST_MAX_DELAY_THRESHOLD_IN_NS
        testedDebouncer.debounce(fakeExecutedRunnable)

        // Then
        verify(mockHandler, times(fakeDelayedRunnables.size))
            .postDelayed(any(), eq(Debouncer.DEBOUNCE_TIME_IN_MS))
        verify(mockHandler, times(fakeDelayedRunnables.size + 1))
            .removeCallbacksAndMessages(null)
        assertThat(fakeDelayedRunnables.count { it.wasExecuted }).isEqualTo(0)
        assertThat(fakeExecutedRunnable.wasExecuted).isTrue
    }

    @Test
    fun `M switch to the handler W debounce { delay threshold reached, more runnables after  }`(
        forge: Forge
    ) {
        // Given
        val fakeDelayedRunnablesPack1 = forge.aList(size = forge.anInt(min = 1, max = 10)) {
            TestRunnable()
        }
        val fakeDelayedRunnablesPack2 = forge.aList(size = forge.anInt(min = 1, max = 10)) {
            TestRunnable()
        }
        // we remove 1ms just to make sure the threshold is not reached before the next debounce is
        // called
        val delayInterval = (TEST_MAX_DELAY_THRESHOLD_IN_NS / fakeDelayedRunnablesPack1.size) - 1
        val fakeExecutedRunnable = TestRunnable()
        var currentTime = fakeInitialTimeNs
        whenever(mockTimeProvider.getDeviceElapsedTimeNanos()).thenAnswer { currentTime }

        fakeDelayedRunnablesPack1.forEach {
            testedDebouncer.debounce(it)
            currentTime += delayInterval
        }

        currentTime = fakeInitialTimeNs + TEST_MAX_DELAY_THRESHOLD_IN_NS
        testedDebouncer.debounce(fakeExecutedRunnable)

        // When
        currentTime += (1L * fakeDelayedRunnablesPack1.size)
        fakeDelayedRunnablesPack2.forEach {
            testedDebouncer.debounce(it)
        }

        // Then
        val numOfDelayedInvocations = fakeDelayedRunnablesPack1.size +
            fakeDelayedRunnablesPack2.size
        verify(mockHandler, times(numOfDelayedInvocations)).postDelayed(
            any(),
            eq(Debouncer.DEBOUNCE_TIME_IN_MS)
        )
        val numOfCancelInvocations = fakeDelayedRunnablesPack1.size +
            fakeDelayedRunnablesPack2.size + 1
        verify(mockHandler, times(numOfCancelInvocations)).removeCallbacksAndMessages(null)
        assertThat(fakeDelayedRunnablesPack1.count { it.wasExecuted }).isEqualTo(0)
        assertThat(fakeDelayedRunnablesPack2.count { it.wasExecuted }).isEqualTo(0)
        assertThat(fakeExecutedRunnable.wasExecuted).isTrue
    }

    @Test
    fun `M double the backoff multiplier W debounce { time bank keeps skipping }`() {
        // Given
        testedDebouncer = Debouncer(
            mockHandler,
            TEST_MAX_DELAY_THRESHOLD_IN_NS,
            sdkCore = mockSdkCore,
            timeBank = mockTimeBank,
            dynamicOptimizationEnabled = true
        )
        whenever(mockTimeBank.updateAndCheck(any())).thenReturn(false)
        var currentTime = fakeInitialTimeNs
        whenever(mockTimeProvider.getDeviceElapsedTimeNanos()).thenAnswer { currentTime }

        // debounce()'s firstRequest handling anchors lastTimeRecordWasPerformed to the current
        // call's own time, so the very first call always measures a zero elapsed time and takes
        // the delayed path regardless of any prior time jump - it never reaches executeRunnable.
        // This warm-up call consumes that one-time quirk so every subsequent big jump below
        // measures elapsed time against a real prior call, not against itself.
        testedDebouncer.debounce(TestRunnable())

        // When - two consecutive skips, each guaranteed to take the immediate path regardless of
        // the multiplier's current value, since the jump is far larger than any reachable
        // threshold; a third call with only a tiny elapsed time must then take the delayed path,
        // revealing the current multiplier through the delay it gets posted with.
        val fakeFirstRunnable = TestRunnable()
        currentTime += TEST_MAX_DELAY_THRESHOLD_IN_NS * LARGE_JUMP_MULTIPLIER
        testedDebouncer.debounce(fakeFirstRunnable)

        val fakeSecondRunnable = TestRunnable()
        currentTime += TEST_MAX_DELAY_THRESHOLD_IN_NS * LARGE_JUMP_MULTIPLIER
        testedDebouncer.debounce(fakeSecondRunnable)

        currentTime += 1L
        val fakeThirdRunnable = TestRunnable()
        testedDebouncer.debounce(fakeThirdRunnable)

        // Then - two skips double the multiplier twice: 1 -> 2 -> 4
        verify(mockHandler).postDelayed(any(), eq(Debouncer.DEBOUNCE_TIME_IN_MS * 4))
        assertThat(fakeFirstRunnable.wasExecuted).isFalse
        assertThat(fakeSecondRunnable.wasExecuted).isFalse
        assertThat(fakeThirdRunnable.wasExecuted).isFalse
    }

    @Test
    fun `M cap the backoff multiplier W debounce { time bank keeps skipping past the ceiling }`() {
        // Given
        testedDebouncer = Debouncer(
            mockHandler,
            TEST_MAX_DELAY_THRESHOLD_IN_NS,
            sdkCore = mockSdkCore,
            timeBank = mockTimeBank,
            dynamicOptimizationEnabled = true
        )
        whenever(mockTimeBank.updateAndCheck(any())).thenReturn(false)
        var currentTime = fakeInitialTimeNs
        whenever(mockTimeProvider.getDeviceElapsedTimeNanos()).thenAnswer { currentTime }

        // consume the firstRequest quirk - see the equivalent comment on the "double" test above
        testedDebouncer.debounce(TestRunnable())

        // When - five consecutive skips: 1 -> 2 -> 4 -> 8 -> 16, and the fifth must not double
        // further since 16 * 2 * DEBOUNCE_TIME_IN_MS would exceed MAX_BACKOFF_TIME_IN_MS
        repeat(5) {
            currentTime += TEST_MAX_DELAY_THRESHOLD_IN_NS * LARGE_JUMP_MULTIPLIER
            testedDebouncer.debounce(TestRunnable())
        }

        currentTime += 1L
        val fakeFinalRunnable = TestRunnable()
        testedDebouncer.debounce(fakeFinalRunnable)

        // Then - capped at 16x (MAX_BACKOFF_TIME_IN_MS / DEBOUNCE_TIME_IN_MS)
        verify(mockHandler).postDelayed(any(), eq(Debouncer.DEBOUNCE_TIME_IN_MS * 16))
        assertThat(fakeFinalRunnable.wasExecuted).isFalse
    }

    @Test
    fun `M decay the backoff multiplier gradually W debounce { a capture succeeds after backing off }`() {
        // Given
        testedDebouncer = Debouncer(
            mockHandler,
            TEST_MAX_DELAY_THRESHOLD_IN_NS,
            sdkCore = mockSdkCore,
            timeBank = mockTimeBank,
            dynamicOptimizationEnabled = true
        )
        var currentTime = fakeInitialTimeNs
        whenever(mockTimeProvider.getDeviceElapsedTimeNanos()).thenAnswer { currentTime }

        // consume the firstRequest quirk - see the equivalent comment on the "double" test above
        testedDebouncer.debounce(TestRunnable())

        // three consecutive skips grow the multiplier to 8 (1 -> 2 -> 4 -> 8)
        whenever(mockTimeBank.updateAndCheck(any())).thenReturn(false)
        repeat(3) {
            currentTime += TEST_MAX_DELAY_THRESHOLD_IN_NS * LARGE_JUMP_MULTIPLIER
            testedDebouncer.debounce(TestRunnable())
        }

        // When - the time bank now allows a capture through
        whenever(mockTimeBank.updateAndCheck(any())).thenReturn(true)
        currentTime += TEST_MAX_DELAY_THRESHOLD_IN_NS * LARGE_JUMP_MULTIPLIER
        val fakeSuccessfulRunnable = TestRunnable()
        testedDebouncer.debounce(fakeSuccessfulRunnable)

        currentTime += 1L
        val fakeNextRunnable = TestRunnable()
        testedDebouncer.debounce(fakeNextRunnable)

        // Then - the successful capture halves the multiplier (8 -> 4); it does not reset it to 1,
        // which would otherwise let a briefly-healthy reading undo the whole backoff at once
        verify(mockHandler).postDelayed(any(), eq(Debouncer.DEBOUNCE_TIME_IN_MS * 4))
        assertThat(fakeSuccessfulRunnable.wasExecuted).isTrue
        assertThat(fakeNextRunnable.wasExecuted).isFalse
    }

    @Test
    fun `M skip the capture W debounce { time bank allows, but frame health monitor reports degraded }`() {
        // Given
        testedDebouncer = Debouncer(
            mockHandler,
            TEST_MAX_DELAY_THRESHOLD_IN_NS,
            sdkCore = mockSdkCore,
            timeBank = mockTimeBank,
            dynamicOptimizationEnabled = true,
            jankAwareBackoffPolicy = JankAwareBackoffPolicy(
                isEnabled = true,
                frameHealthMonitor = mockFrameHealthMonitor
            )
        )
        whenever(mockTimeBank.updateAndCheck(any())).thenReturn(true)
        whenever(mockFrameHealthMonitor.isDegraded()).thenReturn(true)
        val fakeRunnable = TestRunnable()
        val fakeSecondRunnable = TestRunnable()

        // When
        testedDebouncer.debounce(fakeRunnable)
        val fakeExpiredTime = fakeInitialTimeNs + TEST_MAX_DELAY_THRESHOLD_IN_NS
        whenever(mockTimeProvider.getDeviceElapsedTimeNanos()) doReturn fakeExpiredTime
        testedDebouncer.debounce(fakeSecondRunnable)

        // Then - the time bank alone would have allowed this, but the frame health monitor alone
        // is enough reason to skip
        assertThat(fakeSecondRunnable.wasExecuted).isFalse
        verify(mockRumFeature, times(1)).sendEvent(any())
    }

    @Test
    fun `M skip the capture W debounce { jank aware backoff enabled, dynamic optimization disabled }`() {
        // Given - dynamicOptimizationEnabled is off, proving the two flags are independent: the
        // time bank is never consulted, but jank-aware backoff still applies on its own.
        testedDebouncer = Debouncer(
            mockHandler,
            TEST_MAX_DELAY_THRESHOLD_IN_NS,
            sdkCore = mockSdkCore,
            timeBank = mockTimeBank,
            dynamicOptimizationEnabled = false,
            jankAwareBackoffPolicy = JankAwareBackoffPolicy(
                isEnabled = true,
                frameHealthMonitor = mockFrameHealthMonitor
            )
        )
        whenever(mockFrameHealthMonitor.isDegraded()).thenReturn(true)
        val fakeRunnable = TestRunnable()
        val fakeSecondRunnable = TestRunnable()

        // When
        testedDebouncer.debounce(fakeRunnable)
        val fakeExpiredTime = fakeInitialTimeNs + TEST_MAX_DELAY_THRESHOLD_IN_NS
        whenever(mockTimeProvider.getDeviceElapsedTimeNanos()) doReturn fakeExpiredTime
        testedDebouncer.debounce(fakeSecondRunnable)

        // Then
        assertThat(fakeSecondRunnable.wasExecuted).isFalse
        verify(mockTimeBank, never()).updateAndCheck(any())
    }

    @Test
    fun `M never consult frame health monitor W debounce { jank aware backoff disabled }`() {
        // Given - dynamicOptimizationEnabled is on, proving that alone does not imply jank-aware
        // backoff: isDegraded() must never even be called when the dedicated flag is off.
        testedDebouncer = Debouncer(
            mockHandler,
            TEST_MAX_DELAY_THRESHOLD_IN_NS,
            sdkCore = mockSdkCore,
            timeBank = mockTimeBank,
            dynamicOptimizationEnabled = true,
            jankAwareBackoffPolicy = JankAwareBackoffPolicy(
                isEnabled = false,
                frameHealthMonitor = mockFrameHealthMonitor
            )
        )
        whenever(mockTimeBank.updateAndCheck(any())).thenReturn(true)
        val fakeRunnable = TestRunnable()
        val fakeSecondRunnable = TestRunnable()

        // When
        testedDebouncer.debounce(fakeRunnable)
        val fakeExpiredTime = fakeInitialTimeNs + TEST_MAX_DELAY_THRESHOLD_IN_NS
        whenever(mockTimeProvider.getDeviceElapsedTimeNanos()) doReturn fakeExpiredTime
        testedDebouncer.debounce(fakeSecondRunnable)

        // Then
        assertThat(fakeSecondRunnable.wasExecuted).isTrue
        verify(mockFrameHealthMonitor, never()).isDegraded()
    }

    @Test
    fun `M execute immediately and reset the multiplier W debounce { called right after forceNextExecution }`() {
        // Given - grow the backoff multiplier via repeated skips, same as the "double" test above
        testedDebouncer = Debouncer(
            mockHandler,
            TEST_MAX_DELAY_THRESHOLD_IN_NS,
            sdkCore = mockSdkCore,
            timeBank = mockTimeBank,
            dynamicOptimizationEnabled = true
        )
        whenever(mockTimeBank.updateAndCheck(any())).thenReturn(false)
        var currentTime = fakeInitialTimeNs
        whenever(mockTimeProvider.getDeviceElapsedTimeNanos()).thenAnswer { currentTime }

        testedDebouncer.debounce(TestRunnable()) // consume the firstRequest quirk
        repeat(3) {
            currentTime += TEST_MAX_DELAY_THRESHOLD_IN_NS * LARGE_JUMP_MULTIPLIER
            testedDebouncer.debounce(TestRunnable())
        } // multiplier now 8
        clearInvocations(mockHandler)

        // When - force a reset, then debounce right away with the time bank allowing a capture
        whenever(mockTimeBank.updateAndCheck(any())).thenReturn(true)
        testedDebouncer.forceNextExecution()
        val fakeForcedRunnable = TestRunnable()
        testedDebouncer.debounce(fakeForcedRunnable)

        // Then - executes immediately despite the accumulated 8x backoff and despite almost no
        // real time having passed since the last (skipped) attempt
        assertThat(fakeForcedRunnable.wasExecuted).isTrue

        // And - the next call right after, with only a tiny elapsed time, is delayed using the
        // reset multiplier (1x) - not the pre-reset 8x forceNextExecution exists to clear
        whenever(mockTimeBank.updateAndCheck(any())).thenReturn(false)
        currentTime += 1L
        val fakeNextRunnable = TestRunnable()
        testedDebouncer.debounce(fakeNextRunnable)
        verify(mockHandler).postDelayed(any(), eq(Debouncer.DEBOUNCE_TIME_IN_MS))
        assertThat(fakeNextRunnable.wasExecuted).isFalse
    }

    private class TestRunnable : Runnable {
        var wasExecuted: Boolean = false

        override fun run() {
            wasExecuted = true
        }
    }

    companion object {
        private val TEST_MAX_DELAY_THRESHOLD_IN_NS = TimeUnit.SECONDS.toNanos(2)

        // Larger than any reachable backoff multiplier (capped at 16x), so advancing time by this
        // much always takes the immediate path regardless of the multiplier's current value.
        private const val LARGE_JUMP_MULTIPLIER = 64L
    }
}
