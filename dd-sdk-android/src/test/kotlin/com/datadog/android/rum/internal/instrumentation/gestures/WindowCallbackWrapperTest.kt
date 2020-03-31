/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.instrumentation.gestures

import android.view.MotionEvent
import android.view.Window
import androidx.core.view.GestureDetectorCompat
import com.datadog.android.utils.forge.Configurator
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.spy
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(
        MockitoExtension::class,
        ForgeExtension::class
    )
)
@ForgeConfiguration(Configurator::class)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class WindowCallbackWrapperTest {

    lateinit var underTest: WindowCallbackWrapper
    @Mock
    lateinit var mockCallback: Window.Callback
    @Mock
    lateinit var mockGestureDetector: GestureDetectorCompat

    @BeforeEach
    fun `set up`() {
        underTest = WindowCallbackWrapper(
            mockCallback,
            mockGestureDetector
        )
    }

    @Test
    fun `dispatchTouchEvent will delegate to wrapper`(forge: Forge) {
        // given
        val motionEvent: MotionEvent = mock()
        val spyTest = spy(underTest)
        val aBoolean = forge.aBool()
        whenever(mockCallback.dispatchTouchEvent(motionEvent)).thenReturn(aBoolean)
        doReturn(motionEvent).`when`(spyTest).copyEvent(motionEvent)

        // when
        val returnedValue = spyTest.dispatchTouchEvent(motionEvent)

        // then
        assertThat(returnedValue).isEqualTo(aBoolean)
        verify(mockCallback).dispatchTouchEvent(motionEvent)
    }

    @Test
    fun `dispatchTouchEvent will pass a copy of the event to the gesture detector`() {
        // given
        val motionEvent: MotionEvent = mock()
        val copyMotionEvent: MotionEvent = mock()
        val spyTest = spy(underTest)
        doReturn(copyMotionEvent).`when`(spyTest).copyEvent(motionEvent)

        // when
        spyTest.dispatchTouchEvent(motionEvent)

        // then
        verify(mockGestureDetector).onTouchEvent(copyMotionEvent)
        verify(copyMotionEvent).recycle()
    }
}
