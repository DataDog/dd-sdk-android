package com.datadog.android.instrumentation.gestures

import android.app.Activity
import com.datadog.android.instrumentation.gesture.NoOpGesturesTracker
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
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
internal class NoOpGesturesTrackerTest {

    lateinit var underTest: NoOpGesturesTracker

    @Mock
    lateinit var mockActivity: Activity

    @BeforeEach
    fun `set up`() {
        underTest = NoOpGesturesTracker()
    }

    @Test
    fun `start tracking will do nothing`() {
        // when
        underTest.startTracking(mockActivity)

        // then
        verifyZeroInteractions(mockActivity)
    }

    @Test
    fun `stop tracking will do nothing`() {
        // when
        underTest.stopTracking(mockActivity)

        // then
        verifyZeroInteractions(mockActivity)
    }
}
