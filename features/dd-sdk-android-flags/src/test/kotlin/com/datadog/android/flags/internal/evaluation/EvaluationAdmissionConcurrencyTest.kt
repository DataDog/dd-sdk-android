/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal.evaluation

import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.flags.FlagsStateListener
import com.datadog.android.flags.internal.FlagsStateManager
import com.datadog.android.flags.internal.repository.FlagsRepository
import com.datadog.android.flags.model.EvaluationContext
import com.datadog.android.flags.model.FlagsClientState
import com.datadog.android.internal.utils.DDCoreStateHolder
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.concurrent.CountDownLatch
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit

internal class EvaluationAdmissionConcurrencyTest {

    @Test
    fun `M complete competing requests W initial listener callback reenters admission`() {
        val mockSdkCore = mock<FeatureSdkCore>()
        whenever(mockSdkCore.internalLogger) doReturn mock()
        val mockRepository = mock<FlagsRepository>()
        val stateManager = FlagsStateManager(
            DDCoreStateHolder.create(
                initialState = FlagsClientState.NotReady,
                onStateChanged = FlagsStateListener::onStateChanged
            )
        )
        // No feature is registered: exercise synchronous admission without dispatching network work.
        val testedManager = EvaluationsManager(
            sdkCore = mockSdkCore,
            executorService = mock(),
            internalLogger = mockSdkCore.internalLogger,
            flagsRepository = mockRepository,
            assignmentsReader = mock(),
            precomputeMapper = mock(),
            flagStateManager = stateManager,
            initializationTimeoutMs = null,
            initializationTimeoutScheduler = { _, _ -> {} }
        )
        val fakeListenerContext = EvaluationContext("listener")
        val fakeCompetingContext = EvaluationContext("competing")
        val listenerEntered = CountDownLatch(1)
        val releaseListener = CountDownLatch(1)
        val competingStarted = CountDownLatch(1)
        val listener = object : FlagsStateListener {
            override fun onStateChanged(newState: FlagsClientState) {
                if (newState == FlagsClientState.NotReady) {
                    listenerEntered.countDown()
                    check(releaseListener.await(TIMEOUT_SECONDS, TimeUnit.SECONDS))
                    testedManager.updateEvaluationsForContext(fakeListenerContext)
                }
            }
        }
        val registrationResult = FutureTask { stateManager.addListener(listener) }
        val competingResult = FutureTask {
            competingStarted.countDown()
            testedManager.updateEvaluationsForContext(fakeCompetingContext)
        }
        // A regressed lock inversion must fail this test without keeping the test process alive.
        val registrationThread = Thread(registrationResult, "flags-listener-registration").apply { isDaemon = true }
        val competingThread = Thread(competingResult, "flags-competing-admission").apply { isDaemon = true }

        try {
            registrationThread.start()
            assertThat(listenerEntered.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue()
            competingThread.start()
            assertThat(competingStarted.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue()
            awaitLockContention(competingThread)

            // The competitor is already waiting for a lock held by registration. Reentry must
            // not wait for a different manager lock that the competitor acquired first.
            releaseListener.countDown()
            registrationResult.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            competingResult.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)

            verify(mockRepository).setRequestedContext(fakeListenerContext)
            verify(mockRepository).setRequestedContext(fakeCompetingContext)
            assertThat(stateManager.getCurrentState()).isEqualTo(FlagsClientState.Reconciling)
        } finally {
            releaseListener.countDown()
            registrationThread.interrupt()
            competingThread.interrupt()
            registrationThread.join(CLEANUP_WAIT_MS)
            competingThread.join(CLEANUP_WAIT_MS)
        }
    }

    private fun awaitLockContention(thread: Thread) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS)
        val waitingStates = setOf(Thread.State.BLOCKED, Thread.State.WAITING)
        while (thread.state !in waitingStates &&
            thread.isAlive && System.nanoTime() < deadline
        ) {
            Thread.yield()
        }
        assertThat(thread.state).isIn(Thread.State.BLOCKED, Thread.State.WAITING)
    }

    private companion object {
        const val TIMEOUT_SECONDS = 5L
        const val CLEANUP_WAIT_MS = 100L
    }
}
