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
import android.view.Window
import com.datadog.android.api.InternalLogger
import com.datadog.android.internal.time.TimeProvider
import com.datadog.android.sessionreplay.TouchPrivacy
import com.datadog.android.sessionreplay.internal.TouchPrivacyManager
import com.datadog.android.sessionreplay.internal.recorder.callback.NoOpWindowCallback
import com.datadog.android.sessionreplay.internal.storage.RecordWriter
import com.datadog.android.sessionreplay.internal.utils.RumContextProvider
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

    private fun buildInterceptor(touchPrivacyManager: TouchPrivacyManager = mock()) = CompositionWindowTouchInterceptor(
        appContext = mock<Application> { mockApplication ->
            val mockResources = mock<Resources> {
                whenever(it.displayMetrics).thenReturn(DisplayMetrics())
            }
            whenever(mockApplication.resources).thenReturn(mockResources)
        },
        recordWriter = mock<RecordWriter>(),
        timeProvider = mock<TimeProvider>(),
        rumContextProvider = mock<RumContextProvider>(),
        touchPrivacyManager = touchPrivacyManager,
        internalLogger = mock<InternalLogger>()
    )
}
