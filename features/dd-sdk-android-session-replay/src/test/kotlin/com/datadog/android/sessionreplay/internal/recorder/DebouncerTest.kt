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
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
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
        whenever(mockTimeProvider.getDeviceElapsedTimeNs()) doReturn fakeInitialTimeNs
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
        whenever(mockTimeProvider.getDeviceElapsedTimeNs()) doReturn fakeExpiredTime
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
        whenever(mockTimeProvider.getDeviceElapsedTimeNs()) doReturn fakeExpiredTime
        testedDebouncer.debounce(fakeSecondRunnable)

        // Then
        verify(mockRumFeature, times(1)).sendEvent(any())
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
        whenever(mockTimeProvider.getDeviceElapsedTimeNs()) doReturn fakeExpiredTime

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
        whenever(mockTimeProvider.getDeviceElapsedTimeNs()).thenAnswer { currentTime }

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
        assertThat(fakeDelayedRunnables.filter { it.wasExecuted }.size).isEqualTo(0)
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
        whenever(mockTimeProvider.getDeviceElapsedTimeNs()).thenAnswer { currentTime }

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
        assertThat(fakeDelayedRunnablesPack1.filter { it.wasExecuted }.size).isEqualTo(0)
        assertThat(fakeDelayedRunnablesPack2.filter { it.wasExecuted }.size).isEqualTo(0)
        assertThat(fakeExecutedRunnable.wasExecuted).isTrue
    }

    private class TestRunnable : Runnable {
        var wasExecuted: Boolean = false

        override fun run() {
            wasExecuted = true
        }
    }

    companion object {
        private val TEST_MAX_DELAY_THRESHOLD_IN_NS = TimeUnit.SECONDS.toNanos(2)
    }
}
