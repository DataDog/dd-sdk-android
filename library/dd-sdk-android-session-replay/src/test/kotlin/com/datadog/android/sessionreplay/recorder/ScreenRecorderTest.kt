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
import com.datadog.android.sessionreplay.RecordCallback
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.processor.Processor
import com.datadog.android.sessionreplay.recorder.callback.NoOpWindowCallback
import com.datadog.android.sessionreplay.recorder.callback.RecorderWindowCallback
import com.datadog.android.sessionreplay.recorder.listener.WindowsOnDrawListener
import com.datadog.android.sessionreplay.utils.TimeProvider
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.inOrder
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyNoMoreInteractions
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
import java.util.LinkedList

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal class ScreenRecorderTest {

    lateinit var testedRecorder: ScreenRecorder

    @Mock
    lateinit var mockProcessor: Processor

    @Mock
    lateinit var mockTimeProvider: TimeProvider

    @Mock
    lateinit var mockSnapshotProducer: SnapshotProducer

    lateinit var fakeWindowsList: List<Window>

    lateinit var mockActivity: Activity

    @Mock
    lateinit var mockRecordCallback: RecordCallback

    @BeforeEach
    fun `set up`(forge: Forge) {
        mockActivity = forge.aMockedActivity()
        fakeWindowsList = forge.aMockedWindowsList()
        testedRecorder = ScreenRecorder(
            mockProcessor,
            mockSnapshotProducer,
            mockTimeProvider,
            mockRecordCallback
        )
    }

    // region OnDrawListener

    @Test
    fun `M register the OnDrawListener W startRecording()`() {
        // When
        testedRecorder.startRecording(fakeWindowsList, mockActivity)

        // Then
        val captor = argumentCaptor<ViewTreeObserver.OnDrawListener>()
        fakeWindowsList.forEach {
            verify(it.decorView.viewTreeObserver).addOnDrawListener(captor.capture())
        }
        captor.allValues.forEach { assertThat(it).isInstanceOf(WindowsOnDrawListener::class.java) }
    }

    @Test
    fun `M register one single listener instance W startRecording()`() {
        // When
        testedRecorder.startRecording(fakeWindowsList, mockActivity)

        // Then
        val captor = argumentCaptor<ViewTreeObserver.OnDrawListener>()
        fakeWindowsList.forEach {
            verify(it.decorView.viewTreeObserver).addOnDrawListener(captor.capture())
        }
        captor.allValues.reduce { acc, next ->
            assertThat(acc).isSameAs(next)
            next
        }
    }

    @Test
    fun `M unregister and clean the listeners W stopRecording(windows)`() {
        // Given
        testedRecorder.startRecording(fakeWindowsList, mockActivity)

        // When
        testedRecorder.stopRecording(fakeWindowsList)

        // Then
        fakeWindowsList.forEach {
            val captor = argumentCaptor<ViewTreeObserver.OnDrawListener>()
            verify(it.decorView.viewTreeObserver).addOnDrawListener(captor.capture())
            verify(it.decorView.viewTreeObserver).removeOnDrawListener(captor.firstValue)
        }
        assertThat(testedRecorder.windowsListeners).isEmpty()
    }

    @Test
    fun `M unregister and clean the listeners W stopRecording()`() {
        // Given
        testedRecorder.startRecording(fakeWindowsList, mockActivity)

        // When
        testedRecorder.stopRecording()

        // Then
        fakeWindowsList.forEach {
            val captor = argumentCaptor<ViewTreeObserver.OnDrawListener>()
            verify(it.decorView.viewTreeObserver).addOnDrawListener(captor.capture())
            verify(it.decorView.viewTreeObserver).removeOnDrawListener(captor.firstValue)
        }
        assertThat(testedRecorder.windowsListeners).isEmpty()
    }

    @Test
    fun `M unregister first and clean the listeners W startRecording()`() {
        // Given
        testedRecorder.startRecording(fakeWindowsList, mockActivity)

        // When
        testedRecorder.startRecording(fakeWindowsList, mockActivity)

        // Then
        fakeWindowsList.forEach {
            val captor = argumentCaptor<ViewTreeObserver.OnDrawListener>()
            it.decorView.viewTreeObserver.inOrder {
                verify().addOnDrawListener(captor.capture())
                verify().removeOnDrawListener(captor.firstValue)
                verify().addOnDrawListener(captor.capture())
            }
        }
    }

    @Test
    fun `M register listener startRecording() { more activities }`(forge: Forge) {
        // Given
        val fakeWindowsActivityPairs = forge.aList {
            aMockedWindowsList() to aMockedActivity()
        }

        // When
        fakeWindowsActivityPairs.forEach {
            testedRecorder.startRecording(it.first, it.second)
        }

        // Then
        fakeWindowsActivityPairs.map { it.first }.flatten().forEach {
            it.decorView.viewTreeObserver.inOrder {
                verify().addOnDrawListener(any())
            }
        }
    }

    // endregion

    // region WindowCallback

    @Test
    fun `M register the RecorderWindowCallback W startRecording()`() {
        // When
        testedRecorder.startRecording(fakeWindowsList, mockActivity)

        // Then
        fakeWindowsList.forEach {
            val captor = argumentCaptor<Window.Callback>()
            verify(it).callback = captor.capture()
            assertThat(captor.firstValue).isInstanceOf(RecorderWindowCallback::class.java)
            assertThat((captor.firstValue as RecorderWindowCallback).wrappedCallback)
                .isEqualTo(it.callback)
        }
    }

    @Test
    fun `M register the RecorderWindowCallback W startRecording{default callback is null}`() {
        // Given
        fakeWindowsList.forEach {
            whenever(it.callback).thenReturn(null)
        }

        // When
        testedRecorder.startRecording(fakeWindowsList, mockActivity)

        // Then
        fakeWindowsList.forEach {
            val captor = argumentCaptor<Window.Callback>()
            verify(it).callback = captor.capture()
            assertThat(captor.firstValue).isInstanceOf(RecorderWindowCallback::class.java)
            assertThat((captor.firstValue as RecorderWindowCallback).wrappedCallback)
                .isInstanceOf(NoOpWindowCallback::class.java)
        }
    }

    @Test
    fun `M remove the RecorderWindowCallback W stopRecording(windows){default is not null}`() {
        // Given
        val defaultCallbacks = LinkedList<Window.Callback>()
        fakeWindowsList.forEach {
            defaultCallbacks.add(it.callback)
        }
        testedRecorder.startRecording(fakeWindowsList, mockActivity)
        fakeWindowsList.forEach {
            defaultCallbacks.add(it.callback)
            val startRecordingCapture = argumentCaptor<Window.Callback>()
            verify(it).callback = startRecordingCapture.capture()
            assertThat(startRecordingCapture.firstValue)
                .isInstanceOf(RecorderWindowCallback::class.java)
            whenever(it.callback).thenReturn(startRecordingCapture.firstValue)
        }

        // When
        testedRecorder.stopRecording(fakeWindowsList)

        // Then
        fakeWindowsList.forEach {
            val stopRecordingCaptureTarget = argumentCaptor<Window.Callback>()
            verify(it, times(2)).callback = stopRecordingCaptureTarget.capture()
            assertThat(stopRecordingCaptureTarget.secondValue)
                .isSameAs(defaultCallbacks.removeFirst())
        }
    }

    @Test
    fun `M remove the RecorderWindowCallback W stopRecording(){default is not null}`() {
        // Given
        val defaultCallbacks = LinkedList<Window.Callback>()
        fakeWindowsList.forEach {
            defaultCallbacks.add(it.callback)
        }
        testedRecorder.startRecording(fakeWindowsList, mockActivity)
        fakeWindowsList.forEach {
            val startRecordingCapture = argumentCaptor<Window.Callback>()
            verify(it).callback = startRecordingCapture.capture()
            assertThat(startRecordingCapture.firstValue)
                .isInstanceOf(RecorderWindowCallback::class.java)
            whenever(it.callback).thenReturn(startRecordingCapture.firstValue)
        }

        // When
        testedRecorder.stopRecording()

        // Then
        fakeWindowsList.forEach {
            val stopRecordingCaptureTarget = argumentCaptor<Window.Callback>()
            verify(it, times(2)).callback = stopRecordingCaptureTarget.capture()
            assertThat(stopRecordingCaptureTarget.secondValue)
                .isSameAs(defaultCallbacks.removeFirst())
        }
    }

    @Test
    fun `M remove the RecorderWindowCallback W stopRecording(windows){default was null}`() {
        // Given
        fakeWindowsList.forEach {
            whenever(it.callback).thenReturn(null)
        }
        testedRecorder.startRecording(fakeWindowsList, mockActivity)
        fakeWindowsList.forEach {
            val startRecordingCapture = argumentCaptor<Window.Callback>()
            verify(it).callback = startRecordingCapture.capture()
            assertThat(startRecordingCapture.firstValue)
                .isInstanceOf(RecorderWindowCallback::class.java)
            whenever(it.callback).thenReturn(startRecordingCapture.firstValue)
        }

        // When
        testedRecorder.stopRecording(fakeWindowsList)

        // Then
        fakeWindowsList.forEach {
            val stopRecordingCaptureTarget = argumentCaptor<Window.Callback>()
            verify(it, times(2)).callback = stopRecordingCaptureTarget.capture()
            assertThat(stopRecordingCaptureTarget.secondValue).isNull()
        }
    }

    @Test
    fun `M remove the RecorderWindowCallback W stopRecording(){default was null}`() {
        // Given
        fakeWindowsList.forEach {
            whenever(it.callback).thenReturn(null)
        }
        testedRecorder.startRecording(fakeWindowsList, mockActivity)
        fakeWindowsList.forEach {
            val startRecordingCapture = argumentCaptor<Window.Callback>()
            verify(it).callback = startRecordingCapture.capture()
            assertThat(startRecordingCapture.firstValue)
                .isInstanceOf(RecorderWindowCallback::class.java)
            whenever(it.callback).thenReturn(startRecordingCapture.firstValue)
        }

        // When
        testedRecorder.stopRecording()

        // Then
        fakeWindowsList.forEach {
            val stopRecordingCaptureTarget = argumentCaptor<Window.Callback>()
            verify(it, times(2)).callback = stopRecordingCaptureTarget.capture()
            assertThat(stopRecordingCaptureTarget.secondValue).isNull()
        }
    }

    @Test
    fun `M do nothing W stopRecording(windows){window callback was not wrapped}`() {
        // When
        testedRecorder.stopRecording(fakeWindowsList)

        // Then
        fakeWindowsList.forEach {
            verify(it, never()).callback = any()
        }
    }

    @Test
    fun `M do nothing W stopRecording(){window callback was not wrapped and was null}`() {
        // When
        testedRecorder.stopRecording()

        // Then
        fakeWindowsList.forEach {
            verify(it, never()).callback = any()
        }
    }

    // endregion

    // region Record Callback

    @Test
    fun `M notify the callback W startRecording()`(forge: Forge) {
        // Given
        val mockWindows = forge.aMockedWindowsList()

        // When
        testedRecorder.startRecording(mockWindows, mockActivity)

        // Then
        verify(mockRecordCallback).onStartRecording()
        verifyNoMoreInteractions(mockRecordCallback)
    }

    @Test
    fun `M notify the callback W stopRecording(windows)`(forge: Forge) {
        // Given
        val mockWindows = forge.aMockedWindowsList()
        testedRecorder.startRecording(mockWindows, mockActivity)

        // When
        testedRecorder.stopRecording(mockWindows)

        // Then
        verify(mockRecordCallback).onStartRecording()
        verify(mockRecordCallback).onStopRecording()
        verifyNoMoreInteractions(mockRecordCallback)
    }

    @Test
    fun `M notify the callback W stopRecording`(forge: Forge) {
        // Given
        val mockWindows = forge.aMockedWindowsList()
        testedRecorder.startRecording(mockWindows, mockActivity)

        // When
        testedRecorder.stopRecording()

        // Then
        verify(mockRecordCallback).onStartRecording()
        verify(mockRecordCallback).onStopRecording()
        verifyNoMoreInteractions(mockRecordCallback)
    }

    // endregion

    // region Internal

    private fun Forge.aMockedActivity(): Activity {
        val mockActivity: Activity = mock()
        val fakeDensity = aPositiveFloat()
        val displayMetrics = DisplayMetrics().apply { density = fakeDensity }
        val mockResources: Resources = mock {
            whenever(it.displayMetrics).thenReturn(displayMetrics)
        }
        whenever(mockActivity.resources).thenReturn(mockResources)
        return mockActivity
    }

    private fun Forge.aMockedWindowsList(): List<Window> {
        return aList {
            mock {
                val mockDecorView: View = mock()
                whenever(mockDecorView.viewTreeObserver).thenReturn(mock())
                whenever(it.decorView).thenReturn(mockDecorView)
                whenever(it.callback).thenReturn(mock())
            }
        }
    }

    // endregion
}
