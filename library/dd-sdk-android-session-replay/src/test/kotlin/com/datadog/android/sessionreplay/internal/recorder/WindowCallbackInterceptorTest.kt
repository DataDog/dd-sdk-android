/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder

import android.app.Activity
import android.content.res.Resources
import android.util.DisplayMetrics
import android.view.View
import android.view.Window
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.internal.async.RecordedDataQueueHandler
import com.datadog.android.sessionreplay.internal.recorder.callback.NoOpWindowCallback
import com.datadog.android.sessionreplay.internal.recorder.callback.RecorderWindowCallback
import com.datadog.android.sessionreplay.internal.utils.TimeProvider
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.times
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
import java.util.LinkedList

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal class WindowCallbackInterceptorTest {

    lateinit var testedInterceptor: WindowCallbackInterceptor

    @Mock
    lateinit var mockViewOnDrawInterceptor: ViewOnDrawInterceptor

    @Mock
    lateinit var mockRecordedDataQueueHandler: RecordedDataQueueHandler

    @Mock
    lateinit var mockTimeProvider: TimeProvider

    lateinit var fakeWindowsList: List<Window>

    lateinit var mockActivity: Activity

    @BeforeEach
    fun `set up`(forge: Forge) {
        mockActivity = forge.aMockedActivity()
        fakeWindowsList = forge.aMockedWindowsList()
        testedInterceptor = WindowCallbackInterceptor(
            mockRecordedDataQueueHandler,
            mockViewOnDrawInterceptor,
            mockTimeProvider
        )
    }

    @Test
    fun `M register the RecorderWindowCallback W intercept()`() {
        // When
        testedInterceptor.intercept(fakeWindowsList, mockActivity)

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
    fun `M register the RecorderWindowCallback W intercept{default callback is null}`() {
        // Given
        fakeWindowsList.forEach {
            whenever(it.callback).thenReturn(null)
        }

        // When
        testedInterceptor.intercept(fakeWindowsList, mockActivity)

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
    fun `M remove the RecorderWindowCallback W stopIntercepting(windows){default is not null}`() {
        // Given
        val defaultCallbacks = LinkedList<Window.Callback>()
        fakeWindowsList.forEach {
            defaultCallbacks.add(it.callback)
        }
        testedInterceptor.intercept(fakeWindowsList, mockActivity)
        fakeWindowsList.forEach {
            defaultCallbacks.add(it.callback)
            val startRecordingCapture = argumentCaptor<Window.Callback>()
            verify(it).callback = startRecordingCapture.capture()
            assertThat(startRecordingCapture.firstValue)
                .isInstanceOf(RecorderWindowCallback::class.java)
            whenever(it.callback).thenReturn(startRecordingCapture.firstValue)
        }

        // When
        testedInterceptor.stopIntercepting(fakeWindowsList)

        // Then
        fakeWindowsList.forEach {
            val stopRecordingCaptureTarget = argumentCaptor<Window.Callback>()
            verify(it, times(2)).callback = stopRecordingCaptureTarget.capture()
            assertThat(stopRecordingCaptureTarget.secondValue)
                .isSameAs(defaultCallbacks.removeFirst())
        }
    }

    @Test
    fun `M remove the RecorderWindowCallback W stopIntercepting(){default is not null}`() {
        // Given
        val defaultCallbacks = LinkedList<Window.Callback>()
        fakeWindowsList.forEach {
            defaultCallbacks.add(it.callback)
        }
        testedInterceptor.intercept(fakeWindowsList, mockActivity)
        fakeWindowsList.forEach {
            val startRecordingCapture = argumentCaptor<Window.Callback>()
            verify(it).callback = startRecordingCapture.capture()
            assertThat(startRecordingCapture.firstValue)
                .isInstanceOf(RecorderWindowCallback::class.java)
            whenever(it.callback).thenReturn(startRecordingCapture.firstValue)
        }

        // When
        testedInterceptor.stopIntercepting()

        // Then
        fakeWindowsList.forEach {
            val stopRecordingCaptureTarget = argumentCaptor<Window.Callback>()
            verify(it, times(2)).callback = stopRecordingCaptureTarget.capture()
            assertThat(stopRecordingCaptureTarget.secondValue)
                .isSameAs(defaultCallbacks.removeFirst())
        }
    }

    @Test
    fun `M remove the RecorderWindowCallback W stopIntercepting(windows){default was null}`() {
        // Given
        fakeWindowsList.forEach {
            whenever(it.callback).thenReturn(null)
        }
        testedInterceptor.intercept(fakeWindowsList, mockActivity)
        fakeWindowsList.forEach {
            val startRecordingCapture = argumentCaptor<Window.Callback>()
            verify(it).callback = startRecordingCapture.capture()
            assertThat(startRecordingCapture.firstValue)
                .isInstanceOf(RecorderWindowCallback::class.java)
            whenever(it.callback).thenReturn(startRecordingCapture.firstValue)
        }

        // When
        testedInterceptor.stopIntercepting(fakeWindowsList)

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
        testedInterceptor.intercept(fakeWindowsList, mockActivity)
        fakeWindowsList.forEach {
            val startRecordingCapture = argumentCaptor<Window.Callback>()
            verify(it).callback = startRecordingCapture.capture()
            assertThat(startRecordingCapture.firstValue)
                .isInstanceOf(RecorderWindowCallback::class.java)
            whenever(it.callback).thenReturn(startRecordingCapture.firstValue)
        }

        // When
        testedInterceptor.stopIntercepting()

        // Then
        fakeWindowsList.forEach {
            val stopRecordingCaptureTarget = argumentCaptor<Window.Callback>()
            verify(it, times(2)).callback = stopRecordingCaptureTarget.capture()
            assertThat(stopRecordingCaptureTarget.secondValue).isNull()
        }
    }

    @Test
    fun `M do nothing W stopIntercepting(windows){window callback was not wrapped}`() {
        // When
        testedInterceptor.stopIntercepting(fakeWindowsList)

        // Then
        fakeWindowsList.forEach {
            verify(it, never()).callback = any()
        }
    }

    @Test
    fun `M do nothing W stopIntercepting(){window callback was not wrapped and was null}`() {
        // When
        testedInterceptor.stopIntercepting()

        // Then
        fakeWindowsList.forEach {
            verify(it, never()).callback = any()
        }
    }

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
