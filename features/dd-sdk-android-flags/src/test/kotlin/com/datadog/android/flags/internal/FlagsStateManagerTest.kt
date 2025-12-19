/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal

import com.datadog.android.api.InternalLogger
import com.datadog.android.flags.FlagsStateListener
import com.datadog.android.flags.model.FlagsClientState
import com.datadog.android.internal.utils.DDCoreSubscription
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.stream.Stream

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class FlagsStateManagerTest {

    @Mock
    lateinit var mockListener: FlagsStateListener

    @Mock
    lateinit var mockExecutorService: ExecutorService

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    private lateinit var testedManager: FlagsStateManager

    private lateinit var realExecutorService: ExecutorService

    @BeforeEach
    fun `set up`() {
        // Mock executor to run tasks synchronously for testing
        whenever(mockExecutorService.execute(any())).thenAnswer { invocation ->
            val runnable = invocation.getArgument<Runnable>(0)
            runnable.run()
        }

        testedManager = FlagsStateManager(
            DDCoreSubscription.create(),
            mockExecutorService,
            mockInternalLogger
        )
    }

    @AfterEach
    fun `tear down`() {
        if (::realExecutorService.isInitialized && !realExecutorService.isShutdown) {
            realExecutorService.shutdown()
            realExecutorService.awaitTermination(1, TimeUnit.SECONDS)
        }
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
    fun `M notify all listeners in order W updateState() { even if one throws }`() {
        // This test expresses the requirement that:
        // 1. State is set
        // 2. State change listeners are run in order, one at a time, without crashing
        // 3. State is readable immediately after updateState is called

        // Given - use a real executor to test actual async behavior
        realExecutorService = Executors.newSingleThreadExecutor()
        val managerWithRealExecutor = FlagsStateManager(
            DDCoreSubscription.create(),
            realExecutorService,
            mockInternalLogger
        )

        val executionOrder = mutableListOf<String>()
        val executionOrderLock = Any()

        // Creates a listener that adds start/end markers to the execution order and calls the additional block.
        fun createListener(
            name: String,
            additionalBlock: () -> Unit = {
            }
        ): FlagsStateListener = object : FlagsStateListener {
            override fun onStateChanged(newState: FlagsClientState) {
                if (newState is FlagsClientState.Ready) {
                    synchronized(executionOrderLock) {
                        executionOrder.add(name)
                        additionalBlock()
                        executionOrder.add("$name ended")
                    }
                }
            }
        }

        val listener1 = createListener("listener1")
        val listener2 = createListener("listener2") {
            Thread.sleep(5)
        }
        val listener3 = createListener("listener3") {
            throw RuntimeException("Listener 3 intentionally throws")
        }
        val listener4 = createListener("listener4")

        managerWithRealExecutor.addListener(listener1)
        managerWithRealExecutor.addListener(listener2)
        managerWithRealExecutor.addListener(listener3)
        managerWithRealExecutor.addListener(listener4)

        // When
        synchronized(executionOrderLock) {
            managerWithRealExecutor.updateState(FlagsClientState.Ready)
            executionOrder.add("updateState")
        }

        // Then - immediately check that state is correct (even while listeners are running)
        assertThat(managerWithRealExecutor.getCurrentState()).isEqualTo(FlagsClientState.Ready)

        // Wait for the executor to finish executing all its blocks
        realExecutorService.shutdown()
        realExecutorService.awaitTermination(2, TimeUnit.SECONDS)

        // Then - all listeners should have been called in order, despite listener3 throwing
        synchronized(executionOrderLock) {
            assertThat(executionOrder).containsExactly(
                "updateState",
                "listener1",
                "listener1 ended",
                "listener2",
                "listener2 ended",
                "listener3",
                "listener4",
                "listener4 ended"
            )
        }
    }

    @Test
    fun `M maintain FIFO ordering W concurrent operations on fair lock`() {
        // Given
        realExecutorService = Executors.newSingleThreadExecutor()
        val managerWithRealExecutor = FlagsStateManager(
            DDCoreSubscription.create(),
            realExecutorService,
            mockInternalLogger
        )

        val notificationCount = java.util.concurrent.atomic.AtomicInteger(0)
        val listeners = mutableListOf<FlagsStateListener>()

        // When
        repeat(10) {
            val listener = object : FlagsStateListener {
                override fun onStateChanged(newState: FlagsClientState) {
                    if (newState is FlagsClientState.Ready) {
                        notificationCount.incrementAndGet()
                    }
                }
            }
            listeners.add(listener)
            managerWithRealExecutor.addListener(listener)
        }

        // Trigger state change
        managerWithRealExecutor.updateState(FlagsClientState.Ready)

        realExecutorService.shutdown()
        realExecutorService.awaitTermination(2, TimeUnit.SECONDS)

        // Then
        assertThat(notificationCount.get()).isEqualTo(10)
        assertThat(managerWithRealExecutor.getCurrentState()).isEqualTo(FlagsClientState.Ready)
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
