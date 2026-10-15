/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition

import android.app.Application
import android.content.res.Resources
import android.graphics.Point
import android.graphics.Rect
import android.util.DisplayMetrics
import android.view.MotionEvent
import android.view.View
import android.view.Window
import com.datadog.android.api.InternalLogger
import com.datadog.android.internal.time.TimeProvider
import com.datadog.android.sessionreplay.TouchPrivacy
import com.datadog.android.sessionreplay.internal.TouchPrivacyManager
import com.datadog.android.sessionreplay.internal.recorder.callback.NoOpWindowCallback
import com.datadog.android.sessionreplay.internal.storage.RecordWriter
import com.datadog.android.sessionreplay.internal.utils.RumContextProvider
import com.datadog.android.sessionreplay.internal.utils.SessionReplayRumContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.clearInvocations
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

internal class CompositionWindowTouchInterceptorTest {

    @Test
    fun `M gate installed callback on captured window and scope W intercept`() {
        // Given
        val mockRoot: View = mock()
        val mockWindow: Window = mock()
        whenever(mockWindow.peekDecorView()).thenReturn(mockRoot)
        val fakeSource = ActiveWindowSource().apply { update(listOf(mockRoot)) }
        val mockPrivacyManager: TouchPrivacyManager = mock()
        val mockRumProvider: RumContextProvider = mock()
        whenever(mockRumProvider.getRumContext()).thenReturn(SessionReplayRumContext("app", "session", "view"))
        val testedInterceptor = buildInterceptor(mockPrivacyManager, fakeSource, mockRumProvider)
        testedInterceptor.intercept(listOf(mockWindow))
        val fakeCallback = argumentCaptor<Window.Callback>()
        verify(mockWindow).callback = fakeCallback.capture()
        val mockEvent: MotionEvent = mock()
        whenever(mockEvent.action).thenReturn(MotionEvent.ACTION_DOWN)

        // When
        fakeCallback.firstValue.dispatchTouchEvent(mockEvent)
        verify(mockPrivacyManager, never()).shouldRecordTouch(any(), any())
        fakeSource.publishTouchPrivacy(fakeSource.snapshot(), listOf(mockRoot), RumViewIdentityScope("view")) {}
        fakeCallback.firstValue.dispatchTouchEvent(mockEvent)

        // Then
        verify(mockPrivacyManager).shouldRecordTouch(any(), any())
        whenever(mockRumProvider.getRumContext()).thenReturn(SessionReplayRumContext("app", "session", "new"))
        fakeCallback.firstValue.dispatchTouchEvent(mockEvent)
        verify(mockPrivacyManager).shouldRecordTouch(any(), any())
        testedInterceptor.stop()
        assertThat(fakeSource.hasTouchPrivacy(mockRoot, RumViewIdentityScope("view"))).isFalse()
    }

    @Test
    fun `M wrap the window callback W intercept`() {
        // Given
        val mockWindow = mock<Window>()
        val testedInterceptor = buildInterceptor()

        // When
        testedInterceptor.intercept(listOf(mockWindow))

        // Then
        val captor = argumentCaptor<Window.Callback>()
        verify(mockWindow).callback = captor.capture()
        assertThat(captor.firstValue).isInstanceOf(CompositionWindowTouchCallback::class.java)
    }

    @Test
    fun `M wrap the existing callback as the delegate W intercept`() {
        // Given
        val mockExistingCallback = mock<Window.Callback>()
        val mockWindow = mock<Window>()
        whenever(mockWindow.callback).thenReturn(mockExistingCallback)
        val testedInterceptor = buildInterceptor()

        // When
        testedInterceptor.intercept(listOf(mockWindow))

        // Then
        val captor = argumentCaptor<Window.Callback>()
        verify(mockWindow).callback = captor.capture()
        val installed = captor.firstValue as CompositionWindowTouchCallback
        assertThat(installed.wrappedCallback).isSameAs(mockExistingCallback)
    }

    @Test
    fun `M wrap a NoOpWindowCallback W intercept { window has no callback }`() {
        // Given
        val mockWindow = mock<Window>()
        whenever(mockWindow.callback).thenReturn(null)
        val testedInterceptor = buildInterceptor()

        // When
        testedInterceptor.intercept(listOf(mockWindow))

        // Then
        val captor = argumentCaptor<Window.Callback>()
        verify(mockWindow).callback = captor.capture()
        val installed = captor.firstValue as CompositionWindowTouchCallback
        assertThat(installed.wrappedCallback).isInstanceOf(NoOpWindowCallback::class.java)
    }

    @Test
    fun `M not rewrap a window W intercept { window already wrapped }`() {
        // Given
        val mockWindow = mock<Window>()
        val testedInterceptor = buildInterceptor()
        testedInterceptor.intercept(listOf(mockWindow))
        val captor = argumentCaptor<Window.Callback>()
        verify(mockWindow).callback = captor.capture()
        whenever(mockWindow.callback).thenReturn(captor.firstValue)

        // When
        testedInterceptor.intercept(listOf(mockWindow))

        // Then
        verify(mockWindow, times(1)).callback = any()
    }

    @Test
    fun `M restore the original callback W intercept { window drops out of the list }`() {
        // Given
        val mockExistingCallback = mock<Window.Callback>()
        val mockWindow = mock<Window>()
        whenever(mockWindow.callback).thenReturn(mockExistingCallback)
        val testedInterceptor = buildInterceptor()
        testedInterceptor.intercept(listOf(mockWindow))
        val captor = argumentCaptor<Window.Callback>()
        verify(mockWindow).callback = captor.capture()
        whenever(mockWindow.callback).thenReturn(captor.firstValue)

        // When
        testedInterceptor.intercept(emptyList())

        // Then
        verify(mockWindow).callback = mockExistingCallback
    }

    @Test
    fun `M clear the wrapped callback W intercept { window had no original callback }`() {
        // Given
        val mockWindow = mock<Window>()
        whenever(mockWindow.callback).thenReturn(null)
        val testedInterceptor = buildInterceptor()
        testedInterceptor.intercept(listOf(mockWindow))
        val captor = argumentCaptor<Window.Callback>()
        verify(mockWindow).callback = captor.capture()
        whenever(mockWindow.callback).thenReturn(captor.firstValue)

        // When
        testedInterceptor.intercept(emptyList())

        // Then
        verify(mockWindow).callback = null
    }

    @Test
    fun `M leave the window untouched W intercept { callback replaced since wrapping }`() {
        // Given
        val mockWindow = mock<Window>()
        val testedInterceptor = buildInterceptor()
        testedInterceptor.intercept(listOf(mockWindow))
        val someoneElsesCallback = mock<Window.Callback>()
        whenever(mockWindow.callback).thenReturn(someoneElsesCallback)
        clearInvocations(mockWindow)

        // When
        testedInterceptor.stop()

        // Then
        verify(mockWindow, never()).callback = any()
    }

    @Test
    fun `M restore every wrapped window W stop`() {
        // Given
        val mockWindowA = mock<Window>()
        val mockWindowB = mock<Window>()
        val testedInterceptor = buildInterceptor()
        testedInterceptor.intercept(listOf(mockWindowA, mockWindowB))
        val captorA = argumentCaptor<Window.Callback>()
        val captorB = argumentCaptor<Window.Callback>()
        verify(mockWindowA).callback = captorA.capture()
        verify(mockWindowB).callback = captorB.capture()
        whenever(mockWindowA.callback).thenReturn(captorA.firstValue)
        whenever(mockWindowB.callback).thenReturn(captorB.firstValue)

        // When
        testedInterceptor.stop()

        // Then
        verify(mockWindowA).callback = null
        verify(mockWindowB).callback = null
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `M discard previous screen overrides W stop and resume`(fakeHasWindow: Boolean) {
        // Given: a previously published SHOW area overrides the global HIDE policy.
        val fakePrivacyManager = TouchPrivacyManager(TouchPrivacy.HIDE)
        val stubArea: Rect = mock()
        whenever(stubArea.contains(any<Int>(), any<Int>())).thenReturn(true)
        fakePrivacyManager.replaceCurrentTouchOverrideAreas(mapOf(stubArea to TouchPrivacy.SHOW))
        val testedInterceptor = buildInterceptor(fakePrivacyManager)
        val fakeWindows = if (fakeHasWindow) listOf(mock<Window>()) else emptyList()
        testedInterceptor.intercept(fakeWindows)
        assertThat(fakePrivacyManager.shouldRecordTouch(Point())).isTrue()

        // When: interception resumes before a new snapshot has published any overrides.
        testedInterceptor.stop()
        testedInterceptor.intercept(fakeWindows)

        // Then: old SHOW regions no longer apply, but fresh overrides can still be published.
        assertThat(fakePrivacyManager.getCurrentOverrideAreas()).isEmpty()
        assertThat(fakePrivacyManager.shouldRecordTouch(Point())).isFalse()
        fakePrivacyManager.replaceCurrentTouchOverrideAreas(mapOf(stubArea to TouchPrivacy.SHOW))
        assertThat(fakePrivacyManager.shouldRecordTouch(Point())).isTrue()
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `M rewrap replaced callback once W intercept { replacement may delegate to old wrapper }`(
        fakeDelegatesToOldWrapper: Boolean
    ) {
        // Given
        val mockRoot: View = mock()
        val mockWindow: Window = mock()
        whenever(mockWindow.peekDecorView()).thenReturn(mockRoot)
        val fakeSource = ActiveWindowSource().apply { update(listOf(mockRoot)) }
        val mockPrivacyManager: TouchPrivacyManager = mock()
        val mockRumProvider: RumContextProvider = mock()
        whenever(mockRumProvider.getRumContext()).thenReturn(SessionReplayRumContext("app", "session", "view"))
        val testedInterceptor = buildInterceptor(mockPrivacyManager, fakeSource, mockRumProvider)
        testedInterceptor.intercept(listOf(mockWindow))
        val fakeCallbacks = argumentCaptor<Window.Callback>()
        verify(mockWindow).callback = fakeCallbacks.capture()
        val fakeOriginalWrapper = fakeCallbacks.firstValue
        val mockReplacement: Window.Callback = mock()
        if (fakeDelegatesToOldWrapper) {
            whenever(mockReplacement.dispatchTouchEvent(any())).thenAnswer {
                fakeOriginalWrapper.dispatchTouchEvent(it.getArgument(0))
            }
        }
        whenever(mockWindow.callback).thenReturn(mockReplacement)
        fakeSource.publishTouchPrivacy(fakeSource.snapshot(), listOf(mockRoot), RumViewIdentityScope("view")) {}

        // When
        testedInterceptor.intercept(listOf(mockWindow))
        verify(mockWindow, times(2)).callback = fakeCallbacks.capture()
        val fakeNewWrapper = fakeCallbacks.lastValue as CompositionWindowTouchCallback
        whenever(mockWindow.callback).thenReturn(fakeNewWrapper)
        testedInterceptor.intercept(listOf(mockWindow))
        val mockDown: MotionEvent = mock()
        whenever(mockDown.action).thenReturn(MotionEvent.ACTION_DOWN)
        fakeNewWrapper.dispatchTouchEvent(mockDown)

        // Then: preserve delegation, deactivate any nested old recorder and avoid repeated wrapping.
        assertThat(fakeNewWrapper.wrappedCallback).isSameAs(mockReplacement)
        verify(mockWindow, times(2)).callback = any()
        verify(mockPrivacyManager).shouldRecordTouch(any(), any())
        verify(mockReplacement).dispatchTouchEvent(mockDown)
        testedInterceptor.stop()
        verify(mockWindow).callback = mockReplacement
    }

    private fun buildInterceptor(
        touchPrivacyManager: TouchPrivacyManager = mock(),
        windowSource: ActiveWindowSource = ActiveWindowSource(),
        rumContextProvider: RumContextProvider = mock()
    ) = CompositionWindowTouchInterceptor(
        appContext = mock<Application> { mockApplication ->
            val mockResources = mock<Resources> {
                whenever(it.displayMetrics).thenReturn(DisplayMetrics())
            }
            whenever(mockApplication.resources).thenReturn(mockResources)
        },
        recordWriter = mock<RecordWriter>(),
        timeProvider = mock<TimeProvider>(),
        rumContextProvider = rumContextProvider,
        touchPrivacyManager = touchPrivacyManager,
        internalLogger = mock<InternalLogger>(),
        windowSource = windowSource
    )
}
