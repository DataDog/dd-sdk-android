/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition

import android.app.Application
import android.os.Handler
import android.view.View
import android.view.ViewTreeObserver
import android.view.Window
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

internal class CompositionDrawListenerRetentionTest {

    @Test
    fun `M ignore callback W onDraw { source reference cleared }`() {
        // Given
        val mockRoot: View = mock()
        val mockChanges: CompositionChangeListener = mock()
        val testedListener = CompositionOnDrawListener(mockRoot, mockChanges)
        testedListener.viewReference.clear()

        // When
        testedListener.onDraw()

        // Then
        verifyNoInteractions(mockChanges)
    }

    @Test
    fun `M report the live source W onDraw`() {
        // Given
        val mockRoot: View = mock()
        val mockChanges: CompositionChangeListener = mock()
        val testedListener = CompositionOnDrawListener(mockRoot, mockChanges)

        // When
        testedListener.onDraw()

        // Then
        verify(mockChanges).onWindowsChanged(listOf(mockRoot))
    }

    @ParameterizedTest
    @CsvSource(
        "REFRESH, ALIVE",
        "REFRESH, DEAD",
        "REFRESH, TRANSFERRED",
        "REFRESH, REMOVAL_THROWS",
        "STOP, ALIVE",
        "STOP, DEAD",
        "STOP, TRANSFERRED",
        "STOP, REMOVAL_THROWS"
    )
    fun `M forget observer registration W cleanup`(fakeCleanup: Cleanup, fakeState: ObserverState) {
        // Given
        val mockObserver: ViewTreeObserver = mock()
        whenever(mockObserver.isAlive).thenReturn(true)
        val mockRoot: View = mock()
        whenever(mockRoot.viewTreeObserver).thenReturn(mockObserver)
        val fakeSource = ActiveWindowSource()
        val testedInterceptor = CompositionViewOnDrawInterceptor(fakeSource, {}, mock())
        testedInterceptor.intercept(listOf(mockRoot))
        val fakeListener = argumentCaptor<ViewTreeObserver.OnDrawListener>()
        verify(mockObserver).addOnDrawListener(fakeListener.capture())
        whenever(mockRoot.viewTreeObserver).thenThrow(AssertionError("Must use the live original observer"))
        val mockTransferredObserver: ViewTreeObserver = mock()
        whenever(mockTransferredObserver.isAlive).thenReturn(fakeState == ObserverState.TRANSFERRED)
        when (fakeState) {
            ObserverState.ALIVE -> Unit
            ObserverState.DEAD, ObserverState.TRANSFERRED -> {
                whenever(mockObserver.isAlive).thenReturn(false)
                doReturn(mockTransferredObserver).whenever(mockRoot).viewTreeObserver
            }
            ObserverState.REMOVAL_THROWS -> whenever(mockObserver.removeOnDrawListener(any()))
                .doThrow(IllegalStateException("Observer died during removal"))
        }

        // When
        when (fakeCleanup) {
            Cleanup.REFRESH -> testedInterceptor.intercept(emptyList())
            Cleanup.STOP -> testedInterceptor.stop()
        }

        // Then
        assertThat(fakeSource.currentWindows()).isEmpty()
        if (fakeState == ObserverState.DEAD || fakeState == ObserverState.TRANSFERRED) {
            verify(mockObserver, never()).removeOnDrawListener(any())
        } else {
            verify(mockObserver).removeOnDrawListener(fakeListener.firstValue)
        }
        if (fakeState == ObserverState.TRANSFERRED) {
            verify(mockTransferredObserver).removeOnDrawListener(fakeListener.firstValue)
        } else {
            verify(mockTransferredObserver, never()).removeOnDrawListener(any())
        }
        // A fresh registration proves cleanup forgot both the listener and its original observer,
        // including failure paths; no access to the interceptor's private map is needed.
        val mockNewObserver: ViewTreeObserver = mock()
        whenever(mockNewObserver.isAlive).thenReturn(true)
        doReturn(mockNewObserver).whenever(mockRoot).viewTreeObserver
        testedInterceptor.intercept(listOf(mockRoot))
        verify(mockNewObserver).addOnDrawListener(any())
        assertThat(fakeSource.currentWindows()).containsExactly(mockRoot)
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `M preserve live window exclusion W start { window removed while stopped }`(fakePreviouslyRecording: Boolean) {
        // Given
        val fakeSource = ActiveWindowSource()
        val mockRoot: View = mock()
        val mockWindow: Window = mock()
        whenever(mockWindow.peekDecorView()).thenReturn(mockRoot)
        val mockObserver: ViewTreeObserver = mock()
        whenever(mockObserver.isAlive).thenReturn(true)
        whenever(mockRoot.viewTreeObserver).thenReturn(mockObserver)
        val mockHandler: Handler = mock()
        doAnswer { it.getArgument<Runnable>(0).run(); true }.whenever(mockHandler).post(any())
        val testedLifecycle = AndroidSnapshotCaptureLifecycle(
            application = mock<Application>(),
            interceptor = CompositionViewOnDrawInterceptor(fakeSource, {}, mock()),
            touchInterceptor = mock(),
            internalLogger = mock(),
            uiHandler = mockHandler,
            windowProvider = { listOf(mockRoot) },
            windowFromDecorView = { mockWindow },
            isMainThreadWindow = { true }
        )
        if (fakePreviouslyRecording) {
            testedLifecycle.start()
            testedLifecycle.stop()
        }
        testedLifecycle.onWindowsRemoved(listOf(mockWindow))

        // When
        testedLifecycle.start()

        // Then
        assertThat(fakeSource.currentWindows()).isEmpty()
        verify(mockObserver, times(if (fakePreviouslyRecording) 1 else 0)).addOnDrawListener(any())
        // Tracking the same window again clears the exclusion without waiting for polling or GC.
        testedLifecycle.onWindowsAdded(listOf(mockWindow))
        assertThat(fakeSource.currentWindows()).containsExactly(mockRoot)
    }

    enum class Cleanup { REFRESH, STOP }
    enum class ObserverState { ALIVE, DEAD, TRANSFERRED, REMOVAL_THROWS }
}
