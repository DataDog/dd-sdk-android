/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.internal.utils

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertTimeoutPreemptively
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.quality.Strictness
import java.time.Duration
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class DDCoreStateHolderTest {

    @Mock
    lateinit var mockListener: TestStateListener

    private val testedHolder: DDCoreStateHolder<TestState, TestStateListener> = DDCoreStateHolder.create(
        initialState = TestState.Initial,
        onStateChanged = TestStateListener::onStateChanged
    )

    // region currentState

    @Test
    fun `M return initial state W currentState { no update }`() {
        // Then
        assertThat(testedHolder.currentState).isEqualTo(TestState.Initial)
    }

    @Test
    fun `M return latest state W currentState { after updateState }`() {
        // When
        testedHolder.updateState(TestState.Second)

        // Then
        assertThat(testedHolder.currentState).isEqualTo(TestState.Second)
    }

    // endregion

    // region updateState

    @Test
    fun `M notify listener with new state W updateState()`() {
        // Given
        testedHolder.addListener(mockListener)

        // When
        testedHolder.updateState(TestState.Second)

        // Then
        inOrder(mockListener) {
            verify(mockListener).onStateChanged(TestState.Initial) // initial state on add
            verify(mockListener).onStateChanged(TestState.Second)
        }
    }

    @Test
    fun `M notify listeners in order W updateState() { multiple transitions }`() {
        // Given
        testedHolder.addListener(mockListener)

        // When
        testedHolder.updateState(TestState.Second)
        testedHolder.updateState(TestState.Third)

        // Then
        inOrder(mockListener) {
            verify(mockListener).onStateChanged(TestState.Initial)
            verify(mockListener).onStateChanged(TestState.Second)
            verify(mockListener).onStateChanged(TestState.Third)
        }
    }

    @Test
    fun `M notify all listeners in registration order W updateState() { multiple listeners }`() {
        // Given
        val mockListener2 = mock<TestStateListener>()
        testedHolder.addListener(mockListener)
        testedHolder.addListener(mockListener2)

        // When
        testedHolder.updateState(TestState.Second)

        // Then
        inOrder(mockListener, mockListener2) {
            verify(mockListener).onStateChanged(TestState.Initial)
            verify(mockListener2).onStateChanged(TestState.Initial)
            verify(mockListener).onStateChanged(TestState.Second)
            verify(mockListener2).onStateChanged(TestState.Second)
        }
    }

    @Test
    fun `M notify every listener W updateState() { many listeners }`() {
        // Given
        val notificationCount = AtomicInteger(0)
        repeat(10) {
            testedHolder.addListener { newState ->
                if (newState == TestState.Second) notificationCount.incrementAndGet()
            }
        }

        // When
        testedHolder.updateState(TestState.Second)

        // Then
        assertThat(notificationCount.get()).isEqualTo(10)
        assertThat(testedHolder.currentState).isEqualTo(TestState.Second)
    }

    @Test
    fun `M stop notifying subsequent listeners W updateState() { listener throws }`() {
        // Given
        val executionOrder = mutableListOf<String>()
        testedHolder.addListener { if (it == TestState.Second) executionOrder.add("listener1") }
        testedHolder.addListener {
            if (it == TestState.Second) {
                executionOrder.add("listener2")
                throw RuntimeException("Listener 2 intentionally throws")
            }
        }
        testedHolder.addListener { if (it == TestState.Second) executionOrder.add("listener3") }

        // When
        var bubbledException: RuntimeException? = null
        try {
            testedHolder.updateState(TestState.Second)
        } catch (e: RuntimeException) {
            bubbledException = e
        }

        // Then
        assertThat(bubbledException).isNotNull()
        assertThat(testedHolder.currentState).isEqualTo(TestState.Second)
        assertThat(executionOrder).containsExactly("listener1", "listener2")
    }

    // endregion

    // region addListener / removeListener

    @Test
    fun `M notify listener with current state W addListener()`() {
        // Given
        testedHolder.updateState(TestState.Second)

        // When
        testedHolder.addListener(mockListener)

        // Then
        verify(mockListener).onStateChanged(TestState.Second)
        verifyNoMoreInteractions(mockListener)
    }

    @Test
    fun `M not notify listener W removeListener() + updateState()`() {
        // Given
        testedHolder.addListener(mockListener)
        verify(mockListener).onStateChanged(TestState.Initial)

        // When
        testedHolder.removeListener(mockListener)
        testedHolder.updateState(TestState.Second)

        // Then
        verifyNoMoreInteractions(mockListener)
    }

    // endregion

    // region re-entrancy

    @Test
    fun `M not deadlock W addListener() { callback calls updateState }`() {
        // Given
        val receivedStates = mutableListOf<TestState>()
        val reentrantListener = TestStateListener { newState ->
            receivedStates.add(newState)
            if (newState == TestState.Initial) {
                testedHolder.updateState(TestState.Second)
            }
        }

        // When
        assertTimeoutPreemptively(REENTRANCY_TIMEOUT) {
            testedHolder.addListener(reentrantListener)
        }

        // Then
        // The listener is registered before the initial notification, so it also sees the state
        // its own callback triggered.
        assertThat(receivedStates).containsExactly(TestState.Initial, TestState.Second)
        assertThat(testedHolder.currentState).isEqualTo(TestState.Second)
    }

    @Test
    fun `M deliver states in order to every listener W updateState() { callback calls updateState }`() {
        // Given
        val reentrantListenerStates = mutableListOf<TestState>()
        val otherListenerStates = mutableListOf<TestState>()

        testedHolder.addListener { newState ->
            reentrantListenerStates.add(newState)
            if (newState == TestState.Second) {
                testedHolder.updateState(TestState.Third)
            }
        }
        testedHolder.addListener { newState -> otherListenerStates.add(newState) }

        // When
        testedHolder.updateState(TestState.Second)

        // Then
        // The nested update must be queued behind the in-flight one, so that the second listener
        // is not handed Third before Second.
        assertThat(otherListenerStates)
            .containsExactly(TestState.Initial, TestState.Second, TestState.Third)
        assertThat(reentrantListenerStates)
            .containsExactly(TestState.Initial, TestState.Second, TestState.Third)
        assertThat(testedHolder.currentState).isEqualTo(TestState.Third)
    }

    @Test
    fun `M expose new state W currentState() { called from a callback of the same update }`() {
        // Given
        val observedInCallback = mutableListOf<TestState>()
        testedHolder.addListener { observedInCallback.add(testedHolder.currentState) }

        // When
        testedHolder.updateState(TestState.Second)

        // Then
        assertThat(observedInCallback).containsExactly(TestState.Initial, TestState.Second)
    }

    @Test
    fun `M deliver states in order W addListener() { during a queued notification }`() {
        // Given
        val lateListenerStates = mutableListOf<TestState>()
        val lateListener = TestStateListener { newState -> lateListenerStates.add(newState) }

        testedHolder.addListener { newState ->
            if (newState == TestState.Second) testedHolder.updateState(TestState.Third)
        }
        testedHolder.addListener { newState ->
            if (newState == TestState.Second) testedHolder.addListener(lateListener)
        }

        // When
        testedHolder.updateState(TestState.Second)

        // Then
        // The late listener joins where the delivery currently is (Second), and then receives the
        // state queued behind it (Third) - neither a duplicate nor a state out of order.
        assertThat(lateListenerStates).containsExactly(TestState.Second, TestState.Third)
        assertThat(testedHolder.currentState).isEqualTo(TestState.Third)
    }

    @Test
    fun `M notify a listener registered from a callback right away W addListener() { delivery in flight }`() {
        // Given
        val notifications = mutableListOf<String>()
        val lateListener = TestStateListener { notifications.add("C:$it") }
        testedHolder.addListener { newState ->
            notifications.add("A:$newState")
            if (newState == TestState.Second) testedHolder.addListener(lateListener)
        }
        testedHolder.addListener { newState -> notifications.add("B:$newState") }

        // When
        testedHolder.updateState(TestState.Second)

        // Then
        // C is handed its initial state synchronously by addListener, so it lands between A and B
        // rather than after B. This is registration, not a broadcast: it is deliberately not
        // ordered against the fan-out in flight, so that addListener never returns a listener that
        // has not been given a state yet.
        assertThat(notifications).containsExactly(
            "A:Initial",
            "B:Initial",
            "A:Second",
            "C:Second",
            "B:Second"
        )
    }

    @Test
    fun `M not deadlock W addListener() { callback calls addListener }`() {
        // Given
        val reentrantListener = TestStateListener { testedHolder.addListener(mockListener) }

        // When
        assertTimeoutPreemptively(REENTRANCY_TIMEOUT) {
            testedHolder.addListener(reentrantListener)
        }

        // Then
        verify(mockListener).onStateChanged(TestState.Initial)
    }

    @Test
    fun `M not deadlock W updateState() { callback calls addListener }`() {
        // Given
        testedHolder.addListener { newState ->
            if (newState == TestState.Second) testedHolder.addListener(mockListener)
        }

        // When
        assertTimeoutPreemptively(REENTRANCY_TIMEOUT) {
            testedHolder.updateState(TestState.Second)
        }

        // Then
        verify(mockListener).onStateChanged(TestState.Second)
    }

    // endregion

    // region concurrency

    @Test
    fun `M deliver states in call order W updateState() { re-entrant update races a waiting thread }`() {
        // Given
        val deliveredStates = CopyOnWriteArrayList<TestState>()
        val worker = Worker { testedHolder.updateState(TestState.Third) }

        testedHolder.addListener { newState ->
            deliveredStates.add(newState)
            if (newState == TestState.Second) {
                // Let the worker request Third and wait until it is queued, so that Third is
                // unambiguously requested before the re-entrant Fourth below.
                worker.releaseAndAwaitUpdateQueued()
                testedHolder.updateState(TestState.Fourth)
            }
        }

        // When
        worker.start()
        testedHolder.updateState(TestState.Second)
        worker.join()

        // Then
        // Third was requested before Fourth, even though Fourth was requested from a callback that
        // re-enters the lock the worker is waiting for.
        assertThat(deliveredStates).containsExactly(
            TestState.Initial,
            TestState.Second,
            TestState.Third,
            TestState.Fourth
        )
        assertThat(testedHolder.currentState).isEqualTo(TestState.Fourth)
    }

    @Test
    fun `M report the failure to the thread that requested the update W updateState() { drained by another thread }`() {
        // Given
        val workerFailure = AtomicReference<Throwable>()
        val worker = Worker {
            runCatching { testedHolder.updateState(TestState.Third) }
                .onFailure { workerFailure.set(it) }
        }
        val listenerFailure = RuntimeException("Third is not welcome")

        testedHolder.addListener { newState ->
            when (newState) {
                // The worker's update is queued while this thread is delivering Second, so this
                // thread is the one that ends up delivering Third...
                TestState.Second -> worker.releaseAndAwaitUpdateQueued()
                TestState.Third -> throw listenerFailure
                else -> Unit
            }
        }

        // When
        worker.start()
        // ...yet it must not be the one that is handed the failure.
        val drainingThreadFailure = runCatching { testedHolder.updateState(TestState.Second) }
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
        testedHolder.addListener { newState ->
            when (newState) {
                TestState.Second -> testedHolder.updateState(TestState.Third)
                TestState.Third -> throw listenerFailure
                else -> Unit
            }
        }

        // When
        // The re-entrant update returns before its delivery, so there is nobody else to report to.
        val failure = runCatching { testedHolder.updateState(TestState.Second) }.exceptionOrNull()

        // Then
        assertThat(failure).isSameAs(listenerFailure)
    }

    @Test
    fun `M propagate on the spot W updateState() { listener throws an Error }`() {
        // Given
        val receivedStates = CopyOnWriteArrayList<TestState>()
        val listenerFailure = OutOfMemoryError("the listener ran out of memory")
        testedHolder.addListener { newState ->
            receivedStates.add(newState)
            if (newState == TestState.Second) {
                // Queued behind the state whose delivery is about to blow up.
                testedHolder.updateState(TestState.Third)
                throw listenerFailure
            }
        }

        // When
        val failure = runCatching { testedHolder.updateState(TestState.Second) }.exceptionOrNull()

        // Then
        // An Error is not deferred the way an Exception is: it surfaces on the delivering thread...
        assertThat(failure).isSameAs(listenerFailure)
        assertThat(receivedStates).containsExactly(TestState.Initial, TestState.Second)
        assertThat(testedHolder.currentState).isEqualTo(TestState.Second)

        // When
        // ...and the state left in the queue is delivered by the next call to drain it.
        testedHolder.updateState(TestState.Fourth)

        // Then
        assertThat(receivedStates).containsExactly(
            TestState.Initial,
            TestState.Second,
            TestState.Third,
            TestState.Fourth
        )
        assertThat(testedHolder.currentState).isEqualTo(TestState.Fourth)
    }

    @Test
    fun `M block updateState W addListener() { slow listener notification }`() {
        // Given
        val receivedStates = CopyOnWriteArrayList<Pair<TestState, Long>>()
        val addListenerStarted = CountDownLatch(1)
        val addListenerSlowCallbackStarted = CountDownLatch(1)

        // Listener that is slow to process the initial state notification
        val slowListener = TestStateListener { newState ->
            receivedStates.add(newState to System.nanoTime())
            if (newState == TestState.Initial) {
                addListenerSlowCallbackStarted.countDown()
                // Hold the read lock by sleeping, blocking updateState's write lock
                Thread.sleep(SLOW_CALLBACK_DURATION_MS)
            }
        }

        // When
        val addListenerThread = Thread {
            addListenerStarted.countDown()
            testedHolder.addListener(slowListener)
        }
        val updateStateThread = Thread {
            addListenerSlowCallbackStarted.await()
            // Will block waiting for the write lock
            testedHolder.updateState(TestState.Second)
        }

        addListenerThread.start()
        addListenerStarted.await()
        updateStateThread.start()
        addListenerThread.join(THREAD_JOIN_TIMEOUT_MS)
        updateStateThread.join(THREAD_JOIN_TIMEOUT_MS)

        // Then
        assertThat(receivedStates.map { it.first })
            .containsExactly(TestState.Initial, TestState.Second)
        assertThat(receivedStates[1].second - receivedStates[0].second)
            .isGreaterThan(SLOW_CALLBACK_DURATION_MS * NANOS_IN_MILLI)
        assertThat(testedHolder.currentState).isEqualTo(TestState.Second)
    }

    @Test
    fun `M not notify listener after removeListener returns W concurrent updateState()`() {
        // Given
        val slowCallbackStarted = CountDownLatch(1)
        val removeListenerCompleted = CountDownLatch(1)
        val notificationsAfterRemove = CopyOnWriteArrayList<TestState>()

        val slowListener = TestStateListener { newState ->
            if (newState == TestState.Second) {
                slowCallbackStarted.countDown()
                // Hold the write lock by sleeping
                Thread.sleep(SLOW_CALLBACK_DURATION_MS)
            }
        }
        val trackingListener = TestStateListener { newState ->
            if (removeListenerCompleted.count == 0L) {
                notificationsAfterRemove.add(newState)
            }
        }
        testedHolder.addListener(slowListener)
        testedHolder.addListener(trackingListener)

        // When
        val updateStateStarted = CountDownLatch(1)
        val updateStateThread = Thread {
            updateStateStarted.countDown()
            testedHolder.updateState(TestState.Second)
        }
        val removeListenerThread = Thread {
            slowCallbackStarted.await()
            // Should block until updateState completes, because it needs the read lock
            testedHolder.removeListener(trackingListener)
            removeListenerCompleted.countDown()
        }

        updateStateThread.start()
        updateStateStarted.await()
        removeListenerThread.start()
        updateStateThread.join(THREAD_JOIN_TIMEOUT_MS)
        removeListenerThread.join(THREAD_JOIN_TIMEOUT_MS)

        // Then
        assertThat(notificationsAfterRemove).isEmpty()
        assertThat(testedHolder.currentState).isEqualTo(TestState.Second)
    }

    // endregion

    /**
     * A thread that runs [updateBlock] once released, and lets the releasing thread wait until the
     * update that block requests has been queued on the holder.
     */
    private class Worker(updateBlock: () -> Unit) {

        private val released = CountDownLatch(1)
        private val hasResumed = AtomicBoolean(false)
        private val thread = Thread {
            released.await()
            // Published *after* the latch wait returned: from here on the only thing this thread
            // can park on is the holder's lock, so a parked state can no longer be the latch.
            hasResumed.set(true)
            updateBlock()
        }

        fun start() {
            thread.start()
        }

        fun join() {
            thread.join(THREAD_JOIN_TIMEOUT_MS)
        }

        /**
         * Releases the worker and blocks until its update is queued on the holder.
         */
        fun releaseAndAwaitUpdateQueued() {
            released.countDown()
            awaitCondition("the worker never resumed from the latch") { hasResumed.get() }
            // updateState() queues its update before contending for the lock, so once the worker
            // is parked - which now can only be on that lock - the update is necessarily queued.
            awaitCondition("the worker never parked on the holder's lock") {
                thread.state in PARKED_STATES
            }
        }
    }

    companion object {

        private fun awaitCondition(failureMessage: String, condition: () -> Boolean) {
            val deadline = System.nanoTime() + THREAD_JOIN_TIMEOUT_MS * NANOS_IN_MILLI
            while (System.nanoTime() < deadline) {
                if (condition()) return
                Thread.yield()
            }
            throw AssertionError(failureMessage)
        }

        private val PARKED_STATES = setOf(
            Thread.State.WAITING,
            Thread.State.TIMED_WAITING,
            Thread.State.BLOCKED
        )
        private const val SLOW_CALLBACK_DURATION_MS = 50L
        private const val THREAD_JOIN_TIMEOUT_MS = 5000L
        private const val NANOS_IN_MILLI = 1_000_000L
        private val REENTRANCY_TIMEOUT: Duration = Duration.ofSeconds(5)
    }
}

internal enum class TestState {
    Initial,
    Second,
    Third,
    Fourth
}

internal fun interface TestStateListener {
    fun onStateChanged(newState: TestState)
}
