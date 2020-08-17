/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.instrumentation.gestures

import android.app.Activity
import android.view.View
import android.view.Window
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.isA
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.spy
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class DatadogGesturesTrackerTest {

    lateinit var testedTracker: DatadogGesturesTracker

    @Mock
    lateinit var mockActivity: Activity

    @Mock
    lateinit var mockWindow: Window

    @Mock
    lateinit var mockDecorView: View

    @Mock
    lateinit var mockGestureDetector: GesturesDetectorWrapper

    @BeforeEach
    fun `set up`() {
        testedTracker =
            DatadogGesturesTracker(emptyArray())
        whenever(mockActivity.window).thenReturn(mockWindow)
        whenever(mockWindow.decorView).thenReturn(mockDecorView)
    }

    @Test
    fun `will start tracking the activity`() {
        // When
        val spyTest = spy(testedTracker)
        doReturn(mockGestureDetector)
            .whenever(spyTest)
            .generateGestureDetector(mockActivity, mockDecorView)
        spyTest.startTracking(mockWindow, mockActivity)

        // Then
        verify(mockWindow).callback = isA<WindowCallbackWrapper>()
    }

    @Test
    fun `will stop tracking the activity`() {
        // Given
        whenever(mockWindow.callback)
            .thenReturn(
                WindowCallbackWrapper(
                    NoOpWindowCallback(),
                    mockGestureDetector
                )
            )

        // When
        testedTracker.stopTracking(mockWindow, mockActivity)

        // Then
        verify(mockWindow).callback = null
    }

    @Test
    fun `stop tracking the activity will restore the previous callback if was not null`() {
        // Given
        val previousCallback: Window.Callback = mock()
        whenever(mockWindow.callback)
            .thenReturn(
                WindowCallbackWrapper(
                    previousCallback,
                    mockGestureDetector
                )
            )

        // When
        testedTracker.stopTracking(mockWindow, mockActivity)

        // Then
        verify(mockWindow).callback = previousCallback
    }

    @Test
    fun `stop will do nothing if the activity was not tracked`() {
        // When
        testedTracker.stopTracking(mockWindow, mockActivity)

        // Then
        verify(mockWindow, never()).callback = any()
    }

    @Test
    fun `will not track an activity with no decor view`() {
        // Given
        whenever(mockWindow.decorView).thenReturn(null)

        // Then
        verify(mockWindow, never()).callback = any()
    }
}
