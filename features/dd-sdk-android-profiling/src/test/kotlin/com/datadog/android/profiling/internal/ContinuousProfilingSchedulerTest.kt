/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.profiling.internal

import android.app.Application
import com.datadog.android.api.InternalLogger
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.core.sampling.DeterministicSampler
import com.datadog.android.internal.sampling.SessionSamplingIdProvider
import com.datadog.android.internal.time.TimeProvider
import com.datadog.android.profiling.forge.Configurator
import com.datadog.android.profiling.internal.quota.QuotaResult
import fr.xgouchet.elmyr.Forge
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
import org.mockito.kotlin.isNull
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.UUID
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

    private lateinit var fakeSessionId: String

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

        // Make execute() run the runnable immediately so executeSafe-dispatched
        // lifecycle callbacks (onBackground/onForeground) execute synchronously.
        doAnswer { invocation ->
            (invocation.getArgument<Runnable>(0)).run()
            null
        }.whenever(mockSchedulerExecutor).execute(any<Runnable>())

        activeWindowStartedCount = 0
        fakeSessionId = UUID.randomUUID().toString()
        testedScheduler = ContinuousProfilingScheduler(
            profiler = mockProfiler,
            appContext = mockApplication,
            sdkCore = mockSdkCore,
            timeProvider = mockTimeProvider,
            sampleRate = 100f,
            onActiveWindowStarted = { activeWindowStartedCount++ }
        )
        testedScheduler.lastQuotaResult = QuotaResult.FAIL_OPEN
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
        testedScheduler.onRumSessionRenewed(sessionId = fakeSessionId, rumSessionSampleRate = 100f)
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
    fun `M defer extendLaunchSession W start() {continuousSampleRate positive, no session yet}`() {
        // When
        testedScheduler.start(launchProfilingActive = true)

        // Then
        verify(mockProfiler, never()).setExtendLaunchSession(true)
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
        testedScheduler.onRumSessionRenewed(sessionId = fakeSessionId, rumSessionSampleRate = 100f)
        testedScheduler.start(launchProfilingActive = true)

        // When
        testedScheduler.onAppLaunchProfilingComplete()

        // Then — profiling must not start until the cooldown fires
        verify(mockProfiler, never()).start(any(), any(), any(), any(), any())
    }

    @Test
    fun `M start profiling cycle after cooldown fires W onAppLaunchProfilingComplete()`() {
        // Given
        testedScheduler.onRumSessionRenewed(sessionId = fakeSessionId, rumSessionSampleRate = 100f)
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

    @Test
    fun `M clear active flag W active window end runnable fires`() {
        // Given — open an active window
        val runnableCaptor = argumentCaptor<Runnable>()
        whenever(
            mockSchedulerExecutor.schedule(runnableCaptor.capture(), any(), any<TimeUnit>())
        ) doReturn mockFuture
        testedScheduler.onRumSessionRenewed(sessionId = fakeSessionId, rumSessionSampleRate = 100f)
        testedScheduler.start(launchProfilingActive = true)
        testedScheduler.onAppLaunchProfilingComplete()
        // Fire the cooldown runnable to reach scheduleNextCycle and start the active window
        runnableCaptor.firstValue.run()
        assertThat(testedScheduler.isActive).isTrue()

        // When — the active window end runnable fires (before the Perfetto result callback lands)
        runnableCaptor.secondValue.run()

        // Then — buffering must stop immediately, without waiting for onActiveWindowEnded()
        assertThat(testedScheduler.isActive).isFalse()
        assertThat(testedScheduler.state).isEqualTo(ContinuousProfilingScheduler.State.COOLDOWN)
    }

    // endregion

    // region onRumSessionRenewed

    @Test
    fun `M not start profiler W scheduleNextCycle {rum session not sampled}`() {
        // Given
        testedScheduler.onRumSessionRenewed(sessionId = fakeSessionId, rumSessionSampleRate = 0f)
        val runnableCaptor = argumentCaptor<Runnable>()
        testedScheduler.start(launchProfilingActive = false)
        verify(mockSchedulerExecutor).schedule(runnableCaptor.capture(), any(), any())

        // When
        runnableCaptor.firstValue.run()

        // Then
        verify(mockProfiler, never()).start(any(), any(), any(), any(), any())
    }

    @Test
    fun `M not stop running profiler W onRumSessionRenewed {sessionSampleRate=0}`() {
        // Given
        testedScheduler.start(launchProfilingActive = false)

        // When
        testedScheduler.onRumSessionRenewed(sessionId = fakeSessionId, rumSessionSampleRate = 0f)

        // Then
        verify(mockProfiler, never()).stop(any())
    }

    @Test
    fun `M not stop active window W onRumSessionRenewed {mid-active-window, rate=0}`() {
        // Given — open the active window without going through onRumSessionRenewed, then the
        // renewal that follows samples the new session out with rate=0.
        openActiveWindow()

        // When
        testedScheduler.onRumSessionRenewed(sessionId = fakeSessionId, rumSessionSampleRate = 0f)

        // Then
        verify(mockProfiler, never()).stop(any())
        assertThat(testedScheduler.currentSessionSampled).isFalse()
    }

    @Test
    fun `M sample current session in W onRumSessionRenewed {100 percent rates}`() {
        // When
        testedScheduler.onRumSessionRenewed(sessionId = fakeSessionId, rumSessionSampleRate = 100f)

        // Then
        assertThat(testedScheduler.currentSessionSampled).isTrue()
        assertThat(testedScheduler.currentSessionId).isEqualTo(fakeSessionId)
    }

    @Test
    fun `M not sample current session in W onRumSessionRenewed {sessionSampleRate=0}`() {
        // When
        testedScheduler.onRumSessionRenewed(sessionId = fakeSessionId, rumSessionSampleRate = 0f)

        // Then
        assertThat(testedScheduler.currentSessionSampled).isFalse()
        assertThat(testedScheduler.currentSessionId).isEqualTo(fakeSessionId)
    }

    @Test
    fun `M not sample current session in W onRumSessionRenewed {continuousSampleRate=0}`() {
        // Given
        testedScheduler = ContinuousProfilingScheduler(
            profiler = mockProfiler,
            appContext = mockApplication,
            sdkCore = mockSdkCore,
            timeProvider = mockTimeProvider,
            sampleRate = 0f
        )

        // When
        testedScheduler.onRumSessionRenewed(sessionId = fakeSessionId, rumSessionSampleRate = 100f)

        // Then
        assertThat(testedScheduler.currentSessionSampled).isFalse()
    }

    @Test
    fun `M make deterministic decision W onRumSessionRenewed {same sessionId, same rates}`() {
        // When
        testedScheduler.onRumSessionRenewed(sessionId = fakeSessionId, rumSessionSampleRate = 50f)
        val firstDecision = testedScheduler.currentSessionSampled
        testedScheduler.onRumSessionRenewed(sessionId = fakeSessionId, rumSessionSampleRate = 50f)
        val secondDecision = testedScheduler.currentSessionSampled

        // Then
        assertThat(secondDecision).isEqualTo(firstDecision)
    }

    @Test
    fun `M apply multiplicative combined rate W onRumSessionRenewed`(forge: Forge) {
        // Given
        val fakeSessionRate = forge.aFloat(min = 0.1f, max = 100f)
        val fakeContinuousRate = forge.aFloat(min = 0.1f, max = 100f)
        val expectedEffectiveRate =
            (fakeSessionRate * fakeContinuousRate / 100f).coerceIn(0f, 100f)
        val expectedDecision = DeterministicSampler<String>(
            SessionSamplingIdProvider::provideId,
            expectedEffectiveRate
        ).sample(fakeSessionId)
        testedScheduler = ContinuousProfilingScheduler(
            profiler = mockProfiler,
            appContext = mockApplication,
            sdkCore = mockSdkCore,
            timeProvider = mockTimeProvider,
            sampleRate = fakeContinuousRate
        )

        // When
        testedScheduler.onRumSessionRenewed(
            sessionId = fakeSessionId,
            rumSessionSampleRate = fakeSessionRate
        )

        // Then
        assertThat(testedScheduler.currentSessionSampled).isEqualTo(expectedDecision)
    }

    @Test
    fun `M extend launch session W onRumSessionRenewed {first session sampled}`() {
        // Given
        testedScheduler.start(launchProfilingActive = true)

        // When
        testedScheduler.onRumSessionRenewed(sessionId = fakeSessionId, rumSessionSampleRate = 100f)

        // Then
        verify(mockProfiler).setExtendLaunchSession(true)
    }

    @Test
    fun `M not extend launch session W onRumSessionRenewed {session not sampled}`() {
        // Given
        testedScheduler.start(launchProfilingActive = true)

        // When
        testedScheduler.onRumSessionRenewed(sessionId = fakeSessionId, rumSessionSampleRate = 0f)

        // Then
        verify(mockProfiler, never()).setExtendLaunchSession(true)
    }

    @Test
    fun `M extend launch session only once W onRumSessionRenewed {multiple sampled renewals}`() {
        // Given
        testedScheduler.start(launchProfilingActive = true)

        // When
        repeat(3) {
            testedScheduler.onRumSessionRenewed(
                sessionId = fakeSessionId,
                rumSessionSampleRate = 100f
            )
        }

        // Then
        verify(mockProfiler, times(1)).setExtendLaunchSession(true)
    }

    // endregion

    // region quota gate

    @Test
    fun `M skip active window and stay in COOLDOWN W scheduleNextCycle {quota denied}`() {
        // Given
        testedScheduler.lastQuotaResult = QuotaResult.QUOTA_EXCEEDED
        testedScheduler.onRumSessionRenewed(sessionId = fakeSessionId, rumSessionSampleRate = 100f)
        testedScheduler.start(launchProfilingActive = true)
        val runnableCaptor = argumentCaptor<Runnable>()
        testedScheduler.onAppLaunchProfilingComplete()
        verify(mockSchedulerExecutor, atLeastOnce()).schedule(runnableCaptor.capture(), any(), any())

        // When
        runnableCaptor.firstValue.run()

        // Then
        verify(mockProfiler, never()).start(any(), any(), any(), any(), any())
        assertThat(testedScheduler.state).isEqualTo(ContinuousProfilingScheduler.State.COOLDOWN)
        assertThat(testedScheduler.isActive).isFalse()
        assertThat(activeWindowStartedCount).isEqualTo(0)
        val logCaptor = argumentCaptor<() -> String>()
        verify(mockInternalLogger, atLeastOnce()).log(
            eq(InternalLogger.Level.DEBUG),
            eq(InternalLogger.Target.USER),
            logCaptor.capture(),
            isNull(),
            eq(false),
            isNull()
        )
        assertThat(logCaptor.allValues.map { it.invoke() })
            .anyMatch { it.contains("quota denied") }
    }

    @Test
    fun `M skip active window and stay in COOLDOWN W scheduleNextCycle {quota decision not received}`() {
        // Given — no quota decision has arrived yet for the session
        testedScheduler.lastQuotaResult = null
        testedScheduler.onRumSessionRenewed(sessionId = fakeSessionId, rumSessionSampleRate = 100f)
        testedScheduler.start(launchProfilingActive = true)
        val runnableCaptor = argumentCaptor<Runnable>()
        testedScheduler.onAppLaunchProfilingComplete()
        verify(mockSchedulerExecutor, atLeastOnce()).schedule(runnableCaptor.capture(), any(), any())

        // When
        runnableCaptor.firstValue.run()

        // Then — the window is skipped until the decision lands
        verify(mockProfiler, never()).start(any(), any(), any(), any(), any())
        assertThat(testedScheduler.state).isEqualTo(ContinuousProfilingScheduler.State.COOLDOWN)
        assertThat(testedScheduler.isActive).isFalse()
        assertThat(activeWindowStartedCount).isEqualTo(0)
        val logCaptor = argumentCaptor<() -> String>()
        verify(mockInternalLogger, atLeastOnce()).log(
            eq(InternalLogger.Level.DEBUG),
            eq(InternalLogger.Target.USER),
            logCaptor.capture(),
            isNull(),
            eq(false),
            isNull()
        )
        assertThat(logCaptor.allValues.map { it.invoke() })
            .anyMatch { it.contains("awaiting quota decision") }
    }

    @Test
    fun `M start active window W scheduleNextCycle {quota allowed}`() {
        // Given
        testedScheduler.onRumSessionRenewed(sessionId = fakeSessionId, rumSessionSampleRate = 100f)
        testedScheduler.start(launchProfilingActive = true)
        val runnableCaptor = argumentCaptor<Runnable>()
        testedScheduler.onAppLaunchProfilingComplete()
        verify(mockSchedulerExecutor, atLeastOnce()).schedule(runnableCaptor.capture(), any(), any())

        // When
        runnableCaptor.firstValue.run()

        // Then
        verify(mockProfiler).start(
            appContext = eq(mockApplication),
            startReason = eq(ProfilingStartReason.CONTINUOUS),
            additionalAttributes = eq(emptyMap()),
            sdkInstanceNames = eq(setOf(fakeInstanceName)),
            durationMs = any()
        )
        assertThat(testedScheduler.isActive).isTrue()
    }

    // endregion

    // region kill-switch behavior

    @Test
    fun `M log kill-switch message W start() {continuousSampleRate=0}`() {
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
        assertThat(testedScheduler.isScheduling).isFalse()
        verify(mockProfiler).setExtendLaunchSession(false)
        verifyNoInteractions(mockSchedulerExecutor)
    }

    // region jitter

    @Test
    fun `M apply jitter within 20 percent W each active window starts`() {
        // Given
        val windowBase = ContinuousProfilingScheduler.CONTINUOUS_WINDOW_DURATION_MS
        val durationCaptor = argumentCaptor<Int>()

        testedScheduler.onRumSessionRenewed(sessionId = fakeSessionId, rumSessionSampleRate = 100f)
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
        testedScheduler.currentSessionSampled = true
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
