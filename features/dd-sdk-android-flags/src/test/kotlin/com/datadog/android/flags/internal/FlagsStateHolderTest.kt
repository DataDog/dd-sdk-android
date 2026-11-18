/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal

import com.datadog.android.flags.FlagsStateListener
import com.datadog.android.flags.model.FlagsClientState
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

internal class FlagsStateHolderTest {
    private val testedHolder = FlagsStateHolder.create<FlagsClientState, FlagsStateListener>(
        FlagsClientState.NotReady,
        FlagsStateListener::onStateChanged
    )
    private val fakeError = FlagsClientState.Error(null)
    private val observedStates = mutableListOf<FlagsClientState>()

    @Test
    fun `M deliver stale state W updateStateIfCurrent() {matching error}`() {
        // Given
        testedHolder.updateState(fakeError)
        addListener { observedStates.add(it) }

        // When
        testedHolder.updateStateIfCurrent(fakeError, FlagsClientState.Stale)

        // Then
        assertThat(testedHolder.currentState).isEqualTo(FlagsClientState.Stale)
        assertThat(observedStates).containsExactly(fakeError, FlagsClientState.Stale)
    }

    @Test
    fun `M skip stale state W updateStateIfCurrent() {newer request reconciles}`() {
        // Given
        testedHolder.updateState(FlagsClientState.Reconciling)
        addListener { observedStates.add(it) }

        // When
        testedHolder.updateStateIfCurrent(fakeError, FlagsClientState.Stale)

        // Then
        assertThat(testedHolder.currentState).isEqualTo(FlagsClientState.Reconciling)
        assertThat(observedStates).containsExactly(FlagsClientState.Reconciling)
    }

    @Test
    fun `M compare after earlier queued update W updateState() {reentrant recovery}`() {
        // Given
        addListener { observedStates.add(it) }
        addListener { state ->
            if (state == fakeError) {
                testedHolder.updateState(FlagsClientState.Reconciling)
                testedHolder.updateStateIfCurrent(fakeError, FlagsClientState.Stale)
            }
        }

        // When
        testedHolder.updateState(fakeError)

        // Then
        assertThat(testedHolder.currentState).isEqualTo(FlagsClientState.Reconciling)
        assertThat(observedStates).containsExactly(
            FlagsClientState.NotReady,
            fakeError,
            FlagsClientState.Reconciling
        )
    }

    @Test
    fun `M drain queued recovery and propagate exception W updateState()`() {
        // Given
        val fakeFailure = IllegalStateException("listener failed")
        addListener { observedStates.add(it) }
        addListener { state ->
            if (state == fakeError) {
                testedHolder.updateStateIfCurrent(fakeError, FlagsClientState.Stale)
                throw fakeFailure
            }
        }

        // When / Then
        assertThatThrownBy { testedHolder.updateState(fakeError) }.isSameAs(fakeFailure)
        assertThat(testedHolder.currentState).isEqualTo(FlagsClientState.Stale)
        assertThat(observedStates).containsExactly(FlagsClientState.NotReady, fakeError, FlagsClientState.Stale)
    }

    @Test
    fun `M deliver states in call order W updateState() { re-entrant update races a waiting thread }`() {
        // Given
        val deliveredStates = CopyOnWriteArrayList<FlagsClientState>()
        val worker = Worker { testedHolder.updateState(FlagsClientState.Reconciling) }

        addListener { newState ->
            deliveredStates.add(newState)
            if (newState == fakeError) {
                worker.releaseAndAwaitUpdateQueued()
                testedHolder.updateState(FlagsClientState.Ready)
            }
        }

        // When
        worker.start()
        testedHolder.updateState(fakeError)
        worker.join()

        // Then
        assertThat(deliveredStates).containsExactly(
            FlagsClientState.NotReady,
            fakeError,
            FlagsClientState.Reconciling,
            FlagsClientState.Ready
        )
        assertThat(testedHolder.currentState).isEqualTo(FlagsClientState.Ready)
    }

    @Test
    fun `M report the failure to the thread that requested the update W updateState() { drained by another thread }`() {
        // Given
        val workerFailure = AtomicReference<Throwable>()
        val worker = Worker {
            runCatching { testedHolder.updateState(FlagsClientState.Reconciling) }
                .onFailure { workerFailure.set(it) }
        }
        val listenerFailure = RuntimeException("Third is not welcome")

        addListener { newState ->
            when (newState) {
                fakeError -> worker.releaseAndAwaitUpdateQueued()
                FlagsClientState.Reconciling -> throw listenerFailure
                else -> Unit
            }
        }

        // When
        worker.start()
        val drainingThreadFailure = runCatching { testedHolder.updateState(fakeError) }
            .exceptionOrNull()
        worker.join()

        // Then
        assertThat(drainingThreadFailure).isNull()
        assertThat(workerFailure.get()).isSameAs(listenerFailure)
    }

    @Test
    fun `M report the failure to the draining call W updateState() { re-entrant update fails }`() {
        // Given
        val listenerFailure = RuntimeException("Third is not welcome")
        addListener { newState ->
            when (newState) {
                fakeError -> testedHolder.updateState(FlagsClientState.Reconciling)
                FlagsClientState.Reconciling -> throw listenerFailure
                else -> Unit
            }
        }

        // When
        val failure = runCatching { testedHolder.updateState(fakeError) }.exceptionOrNull()

        // Then
        assertThat(failure).isSameAs(listenerFailure)
    }

    @Test
    fun `M propagate on the spot W updateState() { listener throws an Error }`() {
        // Given
        val receivedStates = CopyOnWriteArrayList<FlagsClientState>()
        val listenerFailure = OutOfMemoryError("the listener ran out of memory")
        addListener { newState ->
            receivedStates.add(newState)
            if (newState == fakeError) {
                testedHolder.updateState(FlagsClientState.Reconciling)
                throw listenerFailure
            }
        }

        // When
        val failure = runCatching { testedHolder.updateState(fakeError) }.exceptionOrNull()

        // Then
        assertThat(failure).isSameAs(listenerFailure)
        assertThat(receivedStates).containsExactly(FlagsClientState.NotReady, fakeError)
        assertThat(testedHolder.currentState).isEqualTo(fakeError)

        // When
        testedHolder.updateState(FlagsClientState.Ready)

        // Then
        assertThat(receivedStates).containsExactly(
            FlagsClientState.NotReady,
            fakeError,
            FlagsClientState.Reconciling,
            FlagsClientState.Ready
        )
        assertThat(testedHolder.currentState).isEqualTo(FlagsClientState.Ready)
    }

    private fun addListener(callback: (FlagsClientState) -> Unit) {
        testedHolder.addListener(object : FlagsStateListener {
            override fun onStateChanged(newState: FlagsClientState) = callback(newState)
        })
    }

    private class Worker(updateBlock: () -> Unit) {
        private val released = CountDownLatch(1)
        private val hasResumed = AtomicBoolean(false)
        private val thread = Thread {
            check(released.await(5, TimeUnit.SECONDS))
            hasResumed.set(true)
            updateBlock()
        }

        fun start() = thread.start()

        fun join() {
            thread.join(5_000)
            check(!thread.isAlive)
        }

        fun releaseAndAwaitUpdateQueued() {
            released.countDown()
            awaitCondition { hasResumed.get() }
            awaitCondition {
                thread.state in setOf(Thread.State.WAITING, Thread.State.TIMED_WAITING, Thread.State.BLOCKED)
            }
        }

        private fun awaitCondition(condition: () -> Boolean) {
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
            while (System.nanoTime() < deadline) {
                if (condition()) return
                Thread.yield()
            }
            throw AssertionError("worker did not reach the expected state")
        }
    }
}
