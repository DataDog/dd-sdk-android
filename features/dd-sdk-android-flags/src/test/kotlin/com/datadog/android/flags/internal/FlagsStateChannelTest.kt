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
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.quality.Strictness

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class FlagsStateChannelTest {

    @Mock
    lateinit var mockListener: FlagsStateListener

    private lateinit var testedChannel: FlagsStateChannel

    @BeforeEach
    fun `set up`() {
        testedChannel = FlagsStateChannel(DDCoreSubscription.create())
    }

    // region notifyNotReady

    @Test
    fun `M notify listeners with NOT_READY W notifyNotReady()`() {
        // Given
        testedChannel.addListener(mockListener)

        // When
        testedChannel.notifyNotReady()

        // Then
        verify(mockListener).onStateChanged(FlagsClientState.NOT_READY, null)
    }

    // endregion

    // region notifyReady

    @Test
    fun `M notify listeners with READY W notifyReady()`() {
        // Given
        testedChannel.addListener(mockListener)

        // When
        testedChannel.notifyReady()

        // Then
        verify(mockListener).onStateChanged(FlagsClientState.READY, null)
    }

    // endregion

    // region notifyReconciling

    @Test
    fun `M notify listeners with RECONCILING W notifyReconciling()`() {
        // Given
        testedChannel.addListener(mockListener)

        // When
        testedChannel.notifyReconciling()

        // Then
        verify(mockListener).onStateChanged(FlagsClientState.RECONCILING, null)
    }

    // endregion

    // region notifyError

    @Test
    fun `M notify listeners with ERROR and null W notifyError() {no error provided}`() {
        // Given
        testedChannel.addListener(mockListener)

        // When
        testedChannel.notifyError()

        // Then
        verify(mockListener).onStateChanged(FlagsClientState.ERROR, null)
    }

    @Test
    fun `M notify listeners with ERROR and throwable W notifyError() {error provided}`() {
        // Given
        testedChannel.addListener(mockListener)
        val fakeError = RuntimeException("Test error")

        // When
        testedChannel.notifyError(fakeError)

        // Then
        verify(mockListener).onStateChanged(FlagsClientState.ERROR, fakeError)
    }

    // endregion

    // region addListener / removeListener

    @Test
    fun `M notify listener W addListener() and notify`() {
        // Given
        testedChannel.addListener(mockListener)

        // When
        testedChannel.notifyReady()

        // Then
        verify(mockListener).onStateChanged(FlagsClientState.READY, null)
    }

    @Test
    fun `M not notify listener W removeListener() and notify`() {
        // Given
        testedChannel.addListener(mockListener)
        testedChannel.removeListener(mockListener)

        // When
        testedChannel.notifyReady()

        // Then
        verifyNoInteractions(mockListener)
    }

    @Test
    fun `M notify all listeners W multiple listeners registered`() {
        // Given
        val mockListener2 = org.mockito.Mockito.mock(FlagsStateListener::class.java)
        testedChannel.addListener(mockListener)
        testedChannel.addListener(mockListener2)

        // When
        testedChannel.notifyReady()

        // Then
        verify(mockListener).onStateChanged(FlagsClientState.READY, null)
        verify(mockListener2).onStateChanged(FlagsClientState.READY, null)
    }

    @Test
    fun `M notify listeners in order W multiple state transitions`() {
        // Given
        testedChannel.addListener(mockListener)

        // When
        testedChannel.notifyNotReady()
        testedChannel.notifyReconciling()
        testedChannel.notifyReady()

        // Then
        val captor = argumentCaptor<FlagsClientState>()
        verify(mockListener, org.mockito.kotlin.times(3)).onStateChanged(captor.capture(), org.mockito.kotlin.any())

        assertThat(captor.allValues).containsExactly(
            FlagsClientState.NOT_READY,
            FlagsClientState.RECONCILING,
            FlagsClientState.READY
        )
    }

    // endregion
}
