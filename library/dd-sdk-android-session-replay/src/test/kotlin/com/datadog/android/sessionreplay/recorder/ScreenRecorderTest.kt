/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.recorder

import android.app.Activity
import android.content.res.Resources
import android.util.DisplayMetrics
import android.view.View
import android.view.ViewTreeObserver
import android.view.Window
import com.datadog.android.sessionreplay.processor.Processor
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.Forge
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
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class ScreenRecorderTest {

    lateinit var testedRecorder: ScreenRecorder

    @Mock
    lateinit var mockProcessor: Processor

    @BeforeEach
    fun `set up`() {
        testedRecorder = ScreenRecorder(mockProcessor)
    }

    // region Tests

    @Test
    fun `M register the RecorderOnDrawListener W startRecording()`(forge: Forge) {
        // Given
        val mockViewTreeObserver: ViewTreeObserver = mock()
        val mockActivity = mockActivity(mockViewTreeObserver, forge)

        // When
        testedRecorder.startRecording(mockActivity)

        // Then
        val captor = argumentCaptor<ViewTreeObserver.OnDrawListener>()
        verify(mockViewTreeObserver).addOnDrawListener(captor.capture())
        assertThat(captor.firstValue).isInstanceOf(RecorderOnDrawListener::class.java)
    }

    @Test
    fun `M do nothing W startRecording() { activity window is null }`() {
        // Given
        val mockActivity: Activity = mock()

        // When
        testedRecorder.startRecording(mockActivity)

        // Then
        assertThat(testedRecorder.drawListeners).isEmpty()
    }

    @Test
    fun `M unregister the RecorderOnDrawListener W stopRecording()`(forge: Forge) {
        // Given
        val mockViewTreeObserver: ViewTreeObserver = mock()
        val mockActivity = mockActivity(mockViewTreeObserver, forge)
        testedRecorder.startRecording(mockActivity)

        // When
        testedRecorder.stopRecording(mockActivity)

        // Then
        val captor = argumentCaptor<ViewTreeObserver.OnDrawListener>()
        verify(mockViewTreeObserver).addOnDrawListener(captor.capture())
        verify(mockViewTreeObserver).removeOnDrawListener(captor.firstValue)
    }

    @Test
    fun `M clean the listeners the RecorderOnDrawListener W stopRecording()`(forge: Forge) {
        // Given
        val mockViewTreeObserver: ViewTreeObserver = mock()
        val mockActivity = mockActivity(mockViewTreeObserver, forge)
        testedRecorder.startRecording(mockActivity)

        // When
        testedRecorder.stopRecording(mockActivity)

        // Then
        assertThat(testedRecorder.drawListeners).isEmpty()
    }

    // endregion

    // region Internal

    private fun mockActivity(viewTreeObserver: ViewTreeObserver, forge: Forge): Activity {
        val fakeDensity = forge.aFloat()
        val displayMetrics = DisplayMetrics().apply { density = fakeDensity }
        val mockResources: Resources = mock {
            whenever(it.displayMetrics).thenReturn(displayMetrics)
        }
        val mockDecorView: View = mock {
            whenever(it.viewTreeObserver).thenReturn(viewTreeObserver)
        }
        val mockWindow: Window = mock {
            whenever(it.decorView).thenReturn(mockDecorView)
        }
        val mockActivity: Activity = mock()
        whenever(mockActivity.resources).thenReturn(mockResources)
        whenever(mockActivity.window).thenReturn(mockWindow)
        return mockActivity
    }

    // endregion
}
