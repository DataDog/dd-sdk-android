package com.datadog.android.instrumentation.gestures

import android.app.Activity
import android.view.MotionEvent
import android.view.Window
import androidx.core.view.GestureDetectorCompat
import com.datadog.android.instrumentation.gesture.DatadogGesturesTracker
import com.datadog.android.instrumentation.gesture.NoOpWindowCallback
import com.datadog.android.instrumentation.gesture.WindowCallbackWrapper
import com.datadog.android.tracing.Tracer
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.isA
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.spy
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import datadog.opentracing.DDSpan
import datadog.opentracing.DDTracer
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

    lateinit var underTest: DatadogGesturesTracker
    @Mock
    lateinit var mockActivity: Activity
    @Mock
    lateinit var mockWindow: Window

    @Mock
    lateinit var mockRumTracer: Tracer
    @Mock
    lateinit var mockSpanBuilder: DDTracer.DDSpanBuilder
    @Mock
    lateinit var mockSpan: DDSpan

    @Mock
    lateinit var mockGestureDetector: GestureDetectorCompat

    @BeforeEach
    fun `set up`() {
        mockRumTracer.apply {
            whenever(buildSpan(DatadogGesturesTracker.UI_TAP_ACTION_EVENT))
                .thenReturn(mockSpanBuilder)
        }
        mockSpanBuilder.apply {
            whenever(start()).thenReturn(mockSpan)
        }
        underTest = DatadogGesturesTracker(mockRumTracer)
        whenever(mockActivity.window).thenReturn(mockWindow)
        whenever(mockWindow.decorView).thenReturn(mock())
    }

    @Test
    fun `will start tracking the activity`() {
        // when
        val spyTest = spy(underTest)
        doReturn(mockGestureDetector).whenever(spyTest).generateGestureDetector(mockActivity)
        spyTest.startTracking(mockActivity)

        // then
        verify(mockWindow).callback = isA<WindowCallbackWrapper>()
    }

    @Test
    fun `will stop tracking the activity`() {
        // given
        whenever(mockWindow.callback)
            .thenReturn(
                WindowCallbackWrapper(
                    NoOpWindowCallback(),
                    mockGestureDetector
                )
            )

        // when
        underTest.stopTracking(mockActivity)

        // then
        verify(mockWindow).callback = null
    }

    @Test
    fun `stop tracking the activity will restore the previous callback if was not null`() {
        // given
        val previousCallback: Window.Callback = mock()
        whenever(mockWindow.callback)
            .thenReturn(
                WindowCallbackWrapper(
                    previousCallback,
                    mockGestureDetector
                )
            )

        // when
        underTest.stopTracking(mockActivity)

        // then
        verify(mockWindow).callback = previousCallback
    }

    @Test
    fun `stop will do nothing if the activity was not tracked`() {
        // when
        underTest.stopTracking(mockActivity)

        // then
        verify(mockWindow, never()).callback = any()
    }

    @Test
    fun `will not track an activity with no decor view`() {
        // given
        whenever(mockWindow.decorView).thenReturn(null)

        // then
        verify(mockWindow, never()).callback = any()
    }

    @Test
    fun `when tap was detected will start and finish a span`() {
        // given
        val motionEvent: MotionEvent = mock()

        // when
        underTest.onSingleTapUp(motionEvent)

        // verify
        verify(mockRumTracer).buildSpan(DatadogGesturesTracker.UI_TAP_ACTION_EVENT)
        verify(mockSpan).finish(DatadogGesturesTracker.DEFAULT_EVENT_DURATION)
    }
}
