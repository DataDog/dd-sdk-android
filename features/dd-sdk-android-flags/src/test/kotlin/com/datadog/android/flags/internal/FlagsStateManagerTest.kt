/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal

import com.datadog.android.flags.FlagsStateListener
import com.datadog.android.flags.model.FlagsClientState
import com.datadog.android.internal.utils.DDCoreSubscription
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.quality.Strictness
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
import java.util.stream.Stream

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class FlagsStateManagerTest {

    @Mock
    lateinit var mockListener: FlagsStateListener

    private lateinit var testedManager: FlagsStateManager

    @BeforeEach
    fun `set up`() {
        testedManager = FlagsStateManager(
            DDCoreSubscription.create()
        )
    }

    // region updateState

    @ParameterizedTest
    @MethodSource("allStates")
    fun `M notify listeners with state W updateState(state)`(state: FlagsClientState) {
        // Given
        testedManager.addListener(mockListener)

        // When
        testedManager.updateState(state)

        // Then
        if (state == FlagsClientState.NotReady) {
            // Special case: NotReady is both initial state and transition state
            verify(mockListener, times(2)).onStateChanged(FlagsClientState.NotReady)
        } else {
            inOrder(mockListener) {
                verify(mockListener).onStateChanged(FlagsClientState.NotReady) // Initial state on add
                verify(mockListener).onStateChanged(state) // State change
            }
        }
    }

    // endregion

    // region addListener / removeListener

    @Test
    fun `M not notify listener after removal W removeListener() and notify`() {
        // Given
        testedManager.addListener(mockListener)
        // Verify initial state was emitted
        verify(mockListener).onStateChanged(FlagsClientState.NotReady)

        testedManager.removeListener(mockListener)

        // When
        testedManager.updateState(FlagsClientState.Ready)

        // Then - no further notifications after removal
        verifyNoMoreInteractions(mockListener)
    }

    @Test
    fun `M notify all listeners W multiple listeners registered`() {
        // Given
        val mockListener2 = mock<FlagsStateListener>()
        testedManager.addListener(mockListener)
        testedManager.addListener(mockListener2)

        // When
        testedManager.updateState(FlagsClientState.Ready)

        // Then
        // Both listeners get current state on add, then Ready notification
        inOrder(mockListener, mockListener2) {
            verify(mockListener).onStateChanged(FlagsClientState.NotReady)
            verify(mockListener2).onStateChanged(FlagsClientState.NotReady)
            verify(mockListener).onStateChanged(FlagsClientState.Ready)
            verify(mockListener2).onStateChanged(FlagsClientState.Ready)
        }
    }

    @Test
    fun `M notify listeners in order W multiple state transitions`() {
        // Given
        testedManager.addListener(mockListener)

        // When
        testedManager.updateState(FlagsClientState.Reconciling)
        testedManager.updateState(FlagsClientState.Ready)

        // Then
        inOrder(mockListener) {
            verify(mockListener).onStateChanged(FlagsClientState.NotReady) // Initial on add
            verify(mockListener).onStateChanged(FlagsClientState.Reconciling) // Transition
            verify(mockListener).onStateChanged(FlagsClientState.Ready) // Transition
        }
    }

    @Test
    fun `M stop notifying subsequent listeners W updateState() { if listener throws }`() {
        // Given
        val executionOrder = mutableListOf<String>()

        val listener1 = object : FlagsStateListener {
            override fun onStateChanged(newState: FlagsClientState) {
                if (newState is FlagsClientState.Ready) {
                    executionOrder.add("listener1")
                }
            }
        }

        val listener2 = object : FlagsStateListener {
            override fun onStateChanged(newState: FlagsClientState) {
                if (newState is FlagsClientState.Ready) {
                    executionOrder.add("listener2")
                    throw RuntimeException("Listener 2 intentionally throws")
                }
            }
        }

        val listener3 = object : FlagsStateListener {
            override fun onStateChanged(newState: FlagsClientState) {
                if (newState is FlagsClientState.Ready) {
                    executionOrder.add("listener3")
                }
            }
        }

        testedManager.addListener(listener1)
        testedManager.addListener(listener2)
        testedManager.addListener(listener3)

        var bubbledException: RuntimeException? = null

        // When
        try {
            testedManager.updateState(FlagsClientState.Ready)
        } catch (e: RuntimeException) {
            // Expected exception from listener2
            bubbledException = e
        }

        // Then - state is set despite exception
        assertThat(testedManager.getCurrentState()).isEqualTo(FlagsClientState.Ready)

        // Then - exception was bubbled up
        assertThat(bubbledException).isNotNull()

        // Then - only listeners before the throwing listener were notified
        assertThat(executionOrder).containsExactly(
            "listener1",
            "listener2"
            // listener3 should NOT be in the list
        )
    }

    @Test
    fun `M notify all listeners W multiple listeners registered and state updated`() {
        // Given
        val notificationCount = AtomicInteger(0)
        val listeners = mutableListOf<FlagsStateListener>()

        repeat(10) {
            val listener = object : FlagsStateListener {
                override fun onStateChanged(newState: FlagsClientState) {
                    if (newState is FlagsClientState.Ready) {
                        notificationCount.incrementAndGet()
                    }
                }
            }
            listeners.add(listener)
            testedManager.addListener(listener)
        }

        // When
        testedManager.updateState(FlagsClientState.Ready)

        // Then
        assertThat(notificationCount.get()).isEqualTo(10)
        assertThat(testedManager.getCurrentState()).isEqualTo(FlagsClientState.Ready)
    }

    @Test
    fun `M block updateState calls W addListener() with slow listener notification`() {
        // Given
        val stateOld = FlagsClientState.NotReady
        val stateNew = FlagsClientState.Ready

        val receivedStates = mutableListOf<Pair<FlagsClientState, Long>>()
        val addListenerStarted = CountDownLatch(1)
        val addListenerSlowCallbackStarted = CountDownLatch(1)
        val updateStateCanProceed = CountDownLatch(1)

        // Listener that is slow to process the initial state notification
        val slowListener = object : FlagsStateListener {
            override fun onStateChanged(newState: FlagsClientState) {
                synchronized(receivedStates) {
                    receivedStates.add(newState to System.nanoTime())
                }

                // If this is the first notification (from addListener), be slow
                if (newState == stateOld) {
                    addListenerSlowCallbackStarted.countDown()
                    // Hold up the read lock by sleeping
                    Thread.sleep(10)
                    updateStateCanProceed.countDown()
                }
            }
        }

        // When - demonstrate that slow listener in addListener blocks updateState
        val addListenerThread = Thread {
            addListenerStarted.countDown()
            // This will:
            // 1. Acquire read lock
            // 2. Read currentState (NotReady)
            // 3. Call listener.onStateChanged(NotReady) - SLOW (sleeps 10ms)
            // 4. Add listener to subscription
            // 5. Release read lock
            testedManager.addListener(slowListener)
        }

        val updateStateThread = Thread {
            // Wait for addListener to start its slow callback
            addListenerSlowCallbackStarted.await()
            // Try to update state - this will BLOCK waiting for write lock
            // because addListener is still holding the read lock
            testedManager.updateState(stateNew)
        }

        // Start all threads
        addListenerThread.start()
        addListenerStarted.await() // Ensure addListener starts first
        updateStateThread.start()

        // Wait for all threads to complete
        addListenerThread.join(5000)
        updateStateThread.join(5000)

        // Then - verify the order of operations
        synchronized(receivedStates) {
            // Listener should have received all states in order
            assertThat(receivedStates.map { it.first }).containsExactly(
                stateOld, // From addListener's initial notification (slow)
                stateNew // From first updateState (blocked until addListener completes)
            )

            // Verify timing: the slow addListener callback completed before updateState calls
            val oldStateTime = receivedStates[0].second
            val newStateTime = receivedStates[1].second

            // updateState calls should have been delayed by at least the sleep time
            assertThat(newStateTime - oldStateTime).isGreaterThan(10_000_000) // ~10ms in nanoseconds

            // The states should be received in chronological order
            assertThat(oldStateTime).isLessThan(newStateTime)
        }

        // Verify final state
        assertThat(testedManager.getCurrentState()).isEqualTo(stateNew)
    }

    // endregion

    companion object {
        @JvmStatic
        fun allStates(): Stream<FlagsClientState> = Stream.of(
            FlagsClientState.NotReady,
            FlagsClientState.Ready,
            FlagsClientState.Reconciling,
            FlagsClientState.Stale,
            FlagsClientState.Error(null),
            FlagsClientState.Error(RuntimeException("Test error"))
        )
    }
}
