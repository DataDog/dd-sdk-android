/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal

import com.datadog.android.flags.FlagsStateListener
import com.datadog.android.flags.model.FlagsClientState
import com.datadog.android.internal.utils.DDCoreSubscription
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
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.concurrent.ExecutorService
import java.util.stream.Stream

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class FlagsStateManagerTest {

    @Mock
    lateinit var mockListener: FlagsStateListener

    @Mock
    lateinit var mockExecutorService: ExecutorService

    private lateinit var testedManager: FlagsStateManager

    @BeforeEach
    fun `set up`() {
        // Mock executor to run tasks synchronously for testing
        whenever(mockExecutorService.execute(any())).thenAnswer { invocation ->
            val runnable = invocation.getArgument<Runnable>(0)
            runnable.run()
        }

        testedManager = FlagsStateManager(
            subscription = DDCoreSubscription.create(),
            executorService = mockExecutorService
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
    fun `M notify listener W addListener() and notify`() {
        // Given
        testedManager.addListener(mockListener)

        // When
        testedManager.updateState(FlagsClientState.Ready)

        // Then
        inOrder(mockListener) {
            verify(mockListener).onStateChanged(FlagsClientState.NotReady) // Current state on add
            verify(mockListener).onStateChanged(FlagsClientState.Ready) // State update
        }
    }

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
        org.mockito.kotlin.verifyNoMoreInteractions(mockListener)
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
