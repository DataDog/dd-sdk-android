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
import com.datadog.android.sessionreplay.utils.TimeProvider
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.times
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

    @Mock
    lateinit var mockTimeProvider: TimeProvider

    @Mock
    lateinit var mockSnapshotProducer: SnapshotProducer

    @BeforeEach
    fun `set up`() {
        testedRecorder = ScreenRecorder(mockProcessor, mockSnapshotProducer, mockTimeProvider)
    }

    // region Tests

    @Test
    fun `M register the RecorderOnDrawListener W startRecording()`(forge: Forge) {
        // Given
        val mockViewTreeObserver: ViewTreeObserver = mock()
        val mockActivity = mockActivity(forge, mockViewTreeObserver)

        // When
        testedRecorder.startRecording(mockActivity)

        // Then
        val captor = argumentCaptor<ViewTreeObserver.OnDrawListener>()
        verify(mockViewTreeObserver).addOnDrawListener(captor.capture())
        assertThat(captor.firstValue).isInstanceOf(RecorderOnDrawListener::class.java)
    }

    @Test
    fun `M register the RecorderWindowCallback W startRecording()`(forge: Forge) {
        // Given
        val mockWindow: Window = mock()
        val mockDefaultCallback: Window.Callback = mock()
        val mockActivity = mockActivity(
            forge,
            window = mockWindow,
            defaultWindowCallback = mockDefaultCallback
        )

        // When
        testedRecorder.startRecording(mockActivity)

        // Then
        val captor = argumentCaptor<Window.Callback>()
        verify(mockWindow).callback = captor.capture()
        assertThat(captor.firstValue).isInstanceOf(RecorderWindowCallback::class.java)
        assertThat((captor.firstValue as RecorderWindowCallback).wrappedCallback)
            .isEqualTo(mockDefaultCallback)
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
        val mockActivity = mockActivity(forge, mockViewTreeObserver)
        testedRecorder.startRecording(mockActivity)

        // When
        testedRecorder.stopRecording(mockActivity)

        // Then
        val captor = argumentCaptor<ViewTreeObserver.OnDrawListener>()
        verify(mockViewTreeObserver).addOnDrawListener(captor.capture())
        verify(mockViewTreeObserver).removeOnDrawListener(captor.firstValue)
    }

    @Test
    fun `M remove the RecorderWindowCallback W stopRecording()`(forge: Forge) {
        // Given
        val mockWindow: Window = mock()
        val mockDefaultCallback: Window.Callback = mock()
        val mockActivity = mockActivity(
            forge,
            window = mockWindow,
            defaultWindowCallback = mockDefaultCallback
        )
        testedRecorder.startRecording(mockActivity)
        val startRecordingCapture = argumentCaptor<Window.Callback>()
        verify(mockWindow).callback = startRecordingCapture.capture()
        assertThat(startRecordingCapture.firstValue)
            .isInstanceOf(RecorderWindowCallback::class.java)
        whenever(mockWindow.callback).thenReturn(startRecordingCapture.firstValue)

        // When
        testedRecorder.stopRecording(mockActivity)

        // Then
        val stopRecordingCaptureTarget = argumentCaptor<Window.Callback>()
        verify(mockWindow, times(2)).callback = stopRecordingCaptureTarget.capture()
        assertThat(stopRecordingCaptureTarget.secondValue).isSameAs(mockDefaultCallback)
    }

    @Test
    fun `M clean the listeners the RecorderOnDrawListener W stopRecording()`(forge: Forge) {
        // Given
        val mockViewTreeObserver: ViewTreeObserver = mock()
        val mockActivity = mockActivity(forge, mockViewTreeObserver)
        testedRecorder.startRecording(mockActivity)

        // When
        testedRecorder.stopRecording(mockActivity)

        // Then
        assertThat(testedRecorder.drawListeners).isEmpty()
    }

    // endregion

    // region Internal

    private fun mockActivity(
        forge: Forge,
        viewTreeObserver: ViewTreeObserver = mock(),
        window: Window = mock(),
        defaultWindowCallback: Window.Callback = mock()
    ): Activity {
        val fakeDensity = forge.aFloat()
        val displayMetrics = DisplayMetrics().apply { density = fakeDensity }
        val mockResources: Resources = mock {
            whenever(it.displayMetrics).thenReturn(displayMetrics)
        }
        val mockDecorView: View = mock {
            whenever(it.viewTreeObserver).thenReturn(viewTreeObserver)
        }
        whenever(window.decorView).thenReturn(mockDecorView)
        val mockActivity: Activity = mock()
        whenever(mockActivity.resources).thenReturn(mockResources)
        whenever(mockActivity.window).thenReturn(window)
        whenever(mockActivity.window.callback).thenReturn(defaultWindowCallback)
        return mockActivity
    }

    // endregion
}
