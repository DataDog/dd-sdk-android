/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.profiling.internal

import android.app.Application
import com.datadog.android.api.InternalLogger
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.internal.time.TimeProvider
import com.datadog.android.profiling.forge.Configurator
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
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class ContinuousProfilingSchedulerTest {

    private lateinit var testedScheduler: ContinuousProfilingScheduler

    @Mock
    private lateinit var mockProfiler: Profiler

    @Mock
    private lateinit var mockSdkCore: FeatureSdkCore

    @Mock
    private lateinit var mockInternalLogger: InternalLogger

    @Mock
    private lateinit var mockApplication: Application

    @Mock
    private lateinit var mockSchedulerExecutor: ScheduledExecutorService

    @Mock
    private lateinit var mockFuture: ScheduledFuture<Any>

    @Mock
    private lateinit var mockTimeProvider: TimeProvider

    private var activeWindowStartedCount = 0

    private val fakeInstanceName = "test-sdk-instance"

    @BeforeEach
    fun `set up`() {
        whenever(mockSdkCore.internalLogger) doReturn mockInternalLogger
        whenever(mockSdkCore.name) doReturn fakeInstanceName
        whenever(mockProfiler.scheduledExecutorService) doReturn mockSchedulerExecutor
        whenever(
            mockSchedulerExecutor.schedule(
                any<Runnable>(),
                any(),
                any<TimeUnit>()
            )
        ) doReturn mockFuture

        // Make submit() execute the runnable immediately (synchronous for testing).
        whenever(mockSchedulerExecutor.submit(any<Runnable>())) doAnswer { invocation ->
            (invocation.getArgument<Runnable>(0)).run()
            @Suppress("UNCHECKED_CAST")
            mockFuture
        }

        activeWindowStartedCount = 0
        testedScheduler = ContinuousProfilingScheduler(
            profiler = mockProfiler,
            appContext = mockApplication,
            sdkCore = mockSdkCore,
            timeProvider = mockTimeProvider,
            sampleRate = 100f,
            onActiveWindowStarted = { activeWindowStartedCount++ }
        )
    }

    // region start()

    @Test
    fun `M schedule initial jittered cooldown W start()`() {
        // Given
        val delayCaptor = argumentCaptor<Long>()
        whenever(
            mockSchedulerExecutor.schedule(any<Runnable>(), delayCaptor.capture(), any<TimeUnit>())
        ) doReturn mockFuture

        // When
        testedScheduler.start(launchProfilingActive = false)

        // Then
        assertThat(delayCaptor.firstValue)
            .isBetween(0L, ContinuousProfilingScheduler.CONTINUOUS_COOLDOWN_DURATION_MS)
    }

    @Test
    fun `M start active window W initial cooldown fires after start()`() {
        // Given
        val runnableCaptor = argumentCaptor<Runnable>()
        testedScheduler.onRumSessionRenewed(sessionSampled = true)
        testedScheduler.start(launchProfilingActive = false)
        verify(mockSchedulerExecutor).schedule(runnableCaptor.capture(), any(), any())

        // When
        runnableCaptor.firstValue.run()

        // Then
        val durationCaptor = argumentCaptor<Int>()
        verify(mockProfiler).start(
            appContext = eq(mockApplication),
            startReason = eq(ProfilingStartReason.CONTINUOUS),
            additionalAttributes = eq(emptyMap()),
            sdkInstanceNames = eq(setOf(fakeInstanceName)),
            durationMs = durationCaptor.capture()
        )
        val captured = durationCaptor.firstValue.toLong()
        val windowBase = ContinuousProfilingScheduler.CONTINUOUS_WINDOW_DURATION_MS
        assertThat(captured).isBetween((windowBase * 0.8).toLong(), (windowBase * 1.2).toLong())
    }

    @Test
    fun `M NOT call profiler start W start() {launch profiling active}`() {
        // When
        testedScheduler.start(launchProfilingActive = true)

        // Then
        verify(mockProfiler, never()).start(any(), any(), any(), any(), any())
        verifyNoInteractions(mockSchedulerExecutor)
    }

    @Test
    fun `M set extendLaunchSession true W start() {launch profiling active}`() {
        // When
        testedScheduler.start(launchProfilingActive = true)

        // Then
        verify(mockProfiler).setExtendLaunchSession(true)
        assertThat(testedScheduler.isScheduling).isTrue()
    }

    @Test
    fun `M not start profiler W start() {not sampled in}`() {
        // Given
        testedScheduler = ContinuousProfilingScheduler(
            profiler = mockProfiler,
            appContext = mockApplication,
            sdkCore = mockSdkCore,
            timeProvider = mockTimeProvider,
            sampleRate = 0f
        )

        // When
        testedScheduler.start(launchProfilingActive = false)

        // Then
        verify(mockProfiler, never()).start(any(), any(), any(), any(), any())
    }

    @Test
    fun `M set extendLaunchSession false W start() {not sampled in}`() {
        // Given
        testedScheduler = ContinuousProfilingScheduler(
            profiler = mockProfiler,
            appContext = mockApplication,
            sdkCore = mockSdkCore,
            timeProvider = mockTimeProvider,
            sampleRate = 0f
        )

        // When
        testedScheduler.start(launchProfilingActive = false)

        // Then
        verify(mockProfiler).setExtendLaunchSession(false)
    }

    // endregion

    // region merged launch session

    @Test
    fun `M not start profiler W onAppLaunchProfilingComplete() {not sampled in}`() {
        // Given
        testedScheduler = ContinuousProfilingScheduler(
            profiler = mockProfiler,
            appContext = mockApplication,
            sdkCore = mockSdkCore,
            timeProvider = mockTimeProvider,
            sampleRate = 0f
        )
        testedScheduler.start(launchProfilingActive = false)

        // When
        testedScheduler.onAppLaunchProfilingComplete()

        // Then
        verify(mockProfiler, never()).start(any(), any(), any(), any(), any())
    }

    @Test
    fun `M NOT start profiling immediately W onAppLaunchProfilingComplete()`() {
        // Given
        testedScheduler.onRumSessionRenewed(sessionSampled = true)
        testedScheduler.start(launchProfilingActive = true)

        // When
        testedScheduler.onAppLaunchProfilingComplete()

        // Then — profiling must not start until the cooldown fires
        verify(mockProfiler, never()).start(any(), any(), any(), any(), any())
    }

    @Test
    fun `M start profiling cycle after cooldown fires W onAppLaunchProfilingComplete()`() {
        // Given
        testedScheduler.onRumSessionRenewed(sessionSampled = true)
        testedScheduler.start(launchProfilingActive = true)
        val runnableCaptor = argumentCaptor<Runnable>()
        testedScheduler.onAppLaunchProfilingComplete()
        verify(mockSchedulerExecutor).schedule(runnableCaptor.capture(), any(), any())

        // When — fire the cooldown runnable
        runnableCaptor.firstValue.run()

        // Then
        verify(mockProfiler).start(
            appContext = eq(mockApplication),
            startReason = eq(ProfilingStartReason.CONTINUOUS),
            additionalAttributes = eq(emptyMap()),
            sdkInstanceNames = eq(setOf(fakeInstanceName)),
            durationMs = any()
        )
    }

    @Test
    fun `M schedule jittered cooldown timer W onAppLaunchProfilingComplete()`() {
        // Given
        testedScheduler.start(launchProfilingActive = true)
        val delayCaptor = argumentCaptor<Long>()
        whenever(
            mockSchedulerExecutor.schedule(any<Runnable>(), delayCaptor.capture(), any<TimeUnit>())
        ) doReturn mockFuture

        // When
        testedScheduler.onAppLaunchProfilingComplete()

        // Then
        val cooldownBase = ContinuousProfilingScheduler.CONTINUOUS_COOLDOWN_DURATION_MS
        assertThat(delayCaptor.firstValue)
            .isBetween((cooldownBase * 0.8).toLong(), (cooldownBase * 1.2).toLong())
    }

    // endregion

    // region active window end

    @Test
    fun `M schedule jittered cooldown W active window ends`() {
        // Given
        testedScheduler.start(launchProfilingActive = true)
        val runnableCaptor = argumentCaptor<Runnable>()
        val delayCaptor = argumentCaptor<Long>()
        whenever(
            mockSchedulerExecutor.schedule(
                runnableCaptor.capture(),
                delayCaptor.capture(),
                any<TimeUnit>()
            )
        ) doReturn mockFuture
        testedScheduler.onAppLaunchProfilingComplete()
        // Fire the post-launch cooldown runnable to reach scheduleNextCycle
        runnableCaptor.firstValue.run()

        // When — fire the active window end runnable
        runnableCaptor.secondValue.run()

        // Then
        val cooldownBase = ContinuousProfilingScheduler.CONTINUOUS_COOLDOWN_DURATION_MS
        assertThat(delayCaptor.thirdValue)
            .isBetween((cooldownBase * 0.8).toLong(), (cooldownBase * 1.2).toLong())
    }

    // endregion

    // region onRumSessionRenewed

    @Test
    fun `M not start profiler W scheduleNextCycle {rum session not sampled}`() {
        // Given
        testedScheduler.onRumSessionRenewed(sessionSampled = false)
        val runnableCaptor = argumentCaptor<Runnable>()
        testedScheduler.start(launchProfilingActive = false)
        verify(mockSchedulerExecutor).schedule(runnableCaptor.capture(), any(), any())

        // When
        runnableCaptor.firstValue.run()

        // Then
        verify(mockProfiler, never()).start(any(), any(), any(), any(), any())
    }

    @Test
    fun `M not stop running profiler W onRumSessionRenewed {sessionSampled=false}`() {
        // Given
        testedScheduler.start(launchProfilingActive = false)

        // When
        testedScheduler.onRumSessionRenewed(sessionSampled = false)

        // Then
        verify(mockProfiler, never()).stop(any())
    }

    // endregion

    // region jitter

    @Test
    fun `M apply jitter within 20 percent W each active window starts`() {
        // Given
        val windowBase = ContinuousProfilingScheduler.CONTINUOUS_WINDOW_DURATION_MS
        val durationCaptor = argumentCaptor<Int>()

        testedScheduler.onRumSessionRenewed(sessionSampled = true)
        testedScheduler.start(launchProfilingActive = true)
        val runnableCaptor = argumentCaptor<Runnable>()
        testedScheduler.onAppLaunchProfilingComplete()
        verify(mockSchedulerExecutor, atLeastOnce())
            .schedule(runnableCaptor.capture(), any(), any())
        // Fire the active window end runnable to clear state for the next iteration
        runnableCaptor.lastValue.run()

        // Then
        verify(mockProfiler, atLeastOnce()).start(
            any(),
            any(),
            any(),
            any(),
            durationCaptor.capture()
        )
        assertThat(durationCaptor.firstValue.toLong()).isBetween(
            (windowBase * 0.8).toLong(),
            (windowBase * 1.2).toLong()
        )
    }

    // endregion

    // region stop

    @Test
    fun `M cancel pending future W stop()`() {
        // Given — run a cycle to populate pendingFuture
        testedScheduler.start(launchProfilingActive = true)
        testedScheduler.onAppLaunchProfilingComplete()

        // When
        testedScheduler.stop()

        // Then — pendingFuture is cancelled directly (no executor dispatch needed)
        verify(mockFuture).cancel(false)
    }

    @Test
    fun `M cancel grace period future W stop() {grace period running}`() {
        // Given
        openActiveWindow()
        testedScheduler.onBackground()

        // When
        testedScheduler.stop()

        // Then
        verify(mockFuture, atLeastOnce()).cancel(false)
    }

    // endregion

    // region lifecycle — onBackground / onForeground

    @Test
    fun `M start grace period timer W onBackground() {active window running}`() {
        // Given
        openActiveWindow()
        val delayCaptor = argumentCaptor<Long>()
        whenever(
            mockSchedulerExecutor.schedule(any<Runnable>(), delayCaptor.capture(), any<TimeUnit>())
        ) doReturn mockFuture

        // When
        testedScheduler.onBackground()

        // Then
        assertThat(delayCaptor.lastValue)
            .isEqualTo(ContinuousProfilingScheduler.BACKGROUND_GRACE_PERIOD_MS)
    }

    @Test
    fun `M cancel active window timer W onBackground() {active window running}`() {
        // Given
        openActiveWindow()

        // When
        testedScheduler.onBackground()

        // Then
        verify(mockFuture).cancel(false)
    }

    @Test
    fun `M NOT stop profiler W onBackground() {active window running}`() {
        // Given
        openActiveWindow()

        // When
        testedScheduler.onBackground()

        // Then
        verify(mockProfiler, never()).stop(any())
    }

    @Test
    fun `M resume active window W onForeground() {grace period not expired}`() {
        // Given
        openActiveWindow()
        testedScheduler.onBackground()

        // When
        testedScheduler.onForeground()

        // Then
        assertThat(testedScheduler.state).isEqualTo(ContinuousProfilingScheduler.State.ACTIVE)
        verify(mockProfiler, never()).start(any(), any(), any(), any(), eq(0))
    }

    @Test
    fun `M stop profiler W grace period expires`() {
        // Given
        openActiveWindow()
        testedScheduler.onBackground()
        val runnableCaptor = argumentCaptor<Runnable>()
        verify(mockSchedulerExecutor, atLeastOnce()).schedule(
            runnableCaptor.capture(),
            any(),
            any()
        )

        // When
        runnableCaptor.lastValue.run()

        // Then
        verify(mockProfiler).stop(fakeInstanceName)
    }

    @Test
    fun `M start cooldown W onForeground() {after grace period expired}`() {
        // Given
        openActiveWindow()
        testedScheduler.onBackground()
        val runnableCaptor = argumentCaptor<Runnable>()
        verify(mockSchedulerExecutor, atLeastOnce()).schedule(
            runnableCaptor.capture(),
            any(),
            any()
        )
        runnableCaptor.lastValue.run() // expire grace period
        val delayCaptor = argumentCaptor<Long>()
        whenever(
            mockSchedulerExecutor.schedule(any<Runnable>(), delayCaptor.capture(), any<TimeUnit>())
        ) doReturn mockFuture

        // When
        testedScheduler.onForeground()

        // Then
        val cooldownBase = ContinuousProfilingScheduler.CONTINUOUS_COOLDOWN_DURATION_MS
        assertThat(delayCaptor.lastValue)
            .isBetween((cooldownBase * 0.8).toLong(), (cooldownBase * 1.2).toLong())
    }

    @Test
    fun `M pause cooldown timer W onBackground() {cooldown running}`() {
        // Given
        testedScheduler.start(launchProfilingActive = true)
        testedScheduler.onAppLaunchProfilingComplete()

        // When
        testedScheduler.onBackground()

        // Then
        verify(mockFuture).cancel(false)
    }

    @Test
    fun `M resume cooldown with remaining time W onForeground() {paused cooldown}`() {
        // Given
        testedScheduler.start(launchProfilingActive = true)
        testedScheduler.onAppLaunchProfilingComplete()
        testedScheduler.onBackground()

        // When
        testedScheduler.onForeground()

        // Then
        assertThat(testedScheduler.state).isEqualTo(ContinuousProfilingScheduler.State.COOLDOWN)
    }

    @Test
    fun `M do nothing W onBackground() {scheduler not started}`() {
        // When
        testedScheduler.onBackground()

        // Then
        verify(mockProfiler, never()).stop(any())
        verify(mockFuture, never()).cancel(any())
    }

    @Test
    fun `M do nothing W onForeground() {scheduler not started}`() {
        // When
        testedScheduler.onForeground()

        // Then
        verify(mockProfiler, never()).start(any(), any(), any(), any(), any())
        verify(mockSchedulerExecutor, never()).schedule(any<Runnable>(), any(), any())
    }

    @Test
    fun `M ignore second background W onBackground() {already in grace period}`() {
        // Given
        openActiveWindow()
        testedScheduler.onBackground() // → GRACE_PERIOD

        // When
        testedScheduler.onBackground()

        // Then
        assertThat(testedScheduler.state).isEqualTo(ContinuousProfilingScheduler.State.GRACE_PERIOD)
        verify(mockFuture, times(1)).cancel(false)
    }

    @Test
    fun `M ignore foreground W onForeground() {state is ACTIVE}`() {
        // Given
        openActiveWindow()

        // When
        testedScheduler.onForeground()

        // Then
        assertThat(testedScheduler.state).isEqualTo(ContinuousProfilingScheduler.State.ACTIVE)
    }

    @Test
    fun `M ignore foreground W onForeground() {state is COOLDOWN}`() {
        // Given
        testedScheduler.start(launchProfilingActive = false)
        check(testedScheduler.state == ContinuousProfilingScheduler.State.COOLDOWN)

        // When
        testedScheduler.onForeground()

        // Then
        assertThat(testedScheduler.state).isEqualTo(ContinuousProfilingScheduler.State.COOLDOWN)
    }

    @Test
    fun `M re-enter grace period W onBackground() {after foreground resumed active window}`() {
        // Given
        openActiveWindow()
        testedScheduler.onBackground() // ACTIVE → GRACE_PERIOD
        testedScheduler.onForeground() // GRACE_PERIOD → ACTIVE

        // When
        testedScheduler.onBackground() // ACTIVE → GRACE_PERIOD again

        // Then
        assertThat(testedScheduler.state).isEqualTo(ContinuousProfilingScheduler.State.GRACE_PERIOD)
    }

    private fun openActiveWindow() {
        testedScheduler.rumSessionSampled = true
        testedScheduler.start(launchProfilingActive = true)
        val runnableCaptor = argumentCaptor<Runnable>()
        testedScheduler.onAppLaunchProfilingComplete()
        verify(mockSchedulerExecutor, atLeastOnce()).schedule(runnableCaptor.capture(), any(), any())

        // When — fire the cooldown runnable to trigger scheduleNextCycle
        runnableCaptor.lastValue.run()

        // Then
        assertThat(activeWindowStartedCount).isEqualTo(1)
    }

    // endregion
}
