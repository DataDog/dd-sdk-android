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
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.isA
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
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
    fun `M forget observer registration W intercept() + stop() { cleanup }`(
        fakeCleanup: Cleanup,
        fakeState: ObserverState
    ) {
        // Given
        val fakeFixture = ObserverFixture(fakeState)

        // When
        when (fakeCleanup) {
            Cleanup.REFRESH -> fakeFixture.testedInterceptor.intercept(emptyList())
            Cleanup.STOP -> fakeFixture.testedInterceptor.stop()
        }

        // Then
        assertThat(fakeFixture.source.currentWindows()).isEmpty()
        if (fakeState == ObserverState.DEAD || fakeState == ObserverState.TRANSFERRED) {
            verify(fakeFixture.mockObserver, never()).removeOnDrawListener(any())
        } else {
            verify(fakeFixture.mockObserver).removeOnDrawListener(fakeFixture.listener)
        }
        if (fakeState == ObserverState.TRANSFERRED) {
            verify(fakeFixture.mockTransferredObserver).removeOnDrawListener(fakeFixture.listener)
        } else {
            verify(fakeFixture.mockTransferredObserver, never()).removeOnDrawListener(any())
        }
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
    fun `M register a fresh observer W intercept() { previous registration cleaned up }`(
        fakeCleanup: Cleanup,
        fakeState: ObserverState
    ) {
        // Given
        val fakeFixture = ObserverFixture(fakeState)
        fakeFixture.cleanUp(fakeCleanup)
        val mockNewObserver: ViewTreeObserver = mock()
        whenever(mockNewObserver.isAlive).thenReturn(true)
        doReturn(mockNewObserver).whenever(fakeFixture.mockRoot).viewTreeObserver

        // When
        fakeFixture.testedInterceptor.intercept(listOf(fakeFixture.mockRoot))

        // Then
        verify(mockNewObserver).addOnDrawListener(isA())
        assertThat(fakeFixture.source.currentWindows()).containsExactly(fakeFixture.mockRoot)
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `M preserve live window exclusion W start { window removed while stopped }`(fakePreviouslyRecording: Boolean) {
        // Given
        val fakeFixture = ExcludedWindowFixture(fakePreviouslyRecording)

        // When
        fakeFixture.testedLifecycle.start()

        // Then
        assertThat(fakeFixture.source.currentWindows()).isEmpty()
        verify(fakeFixture.mockObserver, times(if (fakePreviouslyRecording) 1 else 0)).addOnDrawListener(isA())
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `M re-include an excluded window W onWindowsAdded() { recording restarted }`(fakePreviouslyRecording: Boolean) {
        // Given
        val fakeFixture = ExcludedWindowFixture(fakePreviouslyRecording)
        fakeFixture.testedLifecycle.start()

        // When
        fakeFixture.testedLifecycle.onWindowsAdded(listOf(fakeFixture.mockWindow))

        // Then
        assertThat(fakeFixture.source.currentWindows()).containsExactly(fakeFixture.mockRoot)
    }

    private class ObserverFixture(state: ObserverState) {
        val mockObserver: ViewTreeObserver = mock { on { isAlive } doReturn true }
        val mockRoot: View = mock { on { viewTreeObserver } doReturn mockObserver }
        val source = ActiveWindowSource()
        val testedInterceptor = CompositionViewOnDrawInterceptor(source, {}, mock())
        private val registeredListener = argumentCaptor<ViewTreeObserver.OnDrawListener>()
        val listener: ViewTreeObserver.OnDrawListener get() = registeredListener.firstValue
        val mockTransferredObserver: ViewTreeObserver = mock {
            on { isAlive } doReturn (state == ObserverState.TRANSFERRED)
        }

        init {
            testedInterceptor.intercept(listOf(mockRoot))
            verify(mockObserver).addOnDrawListener(registeredListener.capture())
            whenever(mockRoot.viewTreeObserver).thenThrow(AssertionError("Must use the live original observer"))
            when (state) {
                ObserverState.ALIVE -> Unit
                ObserverState.DEAD, ObserverState.TRANSFERRED -> {
                    whenever(mockObserver.isAlive).thenReturn(false)
                    doReturn(mockTransferredObserver).whenever(mockRoot).viewTreeObserver
                }
                ObserverState.REMOVAL_THROWS -> whenever(mockObserver.removeOnDrawListener(any()))
                    .doThrow(IllegalStateException("Observer died during removal"))
            }
        }

        fun cleanUp(cleanup: Cleanup) {
            when (cleanup) {
                Cleanup.REFRESH -> testedInterceptor.intercept(emptyList())
                Cleanup.STOP -> testedInterceptor.stop()
            }
        }
    }

    private class ExcludedWindowFixture(previouslyRecording: Boolean) {
        val source = ActiveWindowSource()
        val mockObserver: ViewTreeObserver = mock { on { isAlive } doReturn true }
        val mockRoot: View = mock { on { viewTreeObserver } doReturn mockObserver }
        val mockWindow: Window = mock { on { peekDecorView() } doReturn mockRoot }
        private val mockHandler: Handler = mock {
            on { post(any()) } doAnswer { invocation -> invocation.getArgument<Runnable>(0).run(); true }
        }
        val testedLifecycle = AndroidSnapshotCaptureLifecycle(
            application = mock<Application>(),
            interceptor = CompositionViewOnDrawInterceptor(source, {}, mock()),
            touchInterceptor = mock(),
            internalLogger = mock(),
            uiHandler = mockHandler,
            windowProvider = { listOf(mockRoot) },
            windowFromDecorView = { mockWindow },
            isMainThreadWindow = { true }
        )

        init {
            if (previouslyRecording) {
                testedLifecycle.start()
                testedLifecycle.stop()
            }
            testedLifecycle.onWindowsRemoved(listOf(mockWindow))
        }
    }

    enum class Cleanup { REFRESH, STOP }
    enum class ObserverState { ALIVE, DEAD, TRANSFERRED, REMOVAL_THROWS }
}
