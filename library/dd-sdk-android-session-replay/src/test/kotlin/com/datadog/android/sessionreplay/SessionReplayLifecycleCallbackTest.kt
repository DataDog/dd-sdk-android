/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay

import android.app.Activity
import android.app.Application
import android.view.Window
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentManager.FragmentLifecycleCallbacks
import com.datadog.android.sessionreplay.recorder.Recorder
import com.datadog.android.sessionreplay.recorder.callback.RecorderFragmentLifecycleCallback
import com.datadog.android.sessionreplay.utils.RumContextProvider
import com.datadog.android.sessionreplay.utils.TimeProvider
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.annotation.Forgery
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
internal class SessionReplayLifecycleCallbackTest {

    lateinit var testedCallback: SessionReplayLifecycleCallback

    @Mock
    private lateinit var mockRecoder: Recorder

    @Mock
    private lateinit var mockRumContextProvider: RumContextProvider

    @Mock
    private lateinit var mockRecordWriter: RecordWriter

    @Forgery
    private lateinit var fakePrivacy: SessionReplayPrivacy

    @Mock
    private lateinit var mockRecordCallback: RecordCallback

    @Mock
    private lateinit var mockTimeProvider: TimeProvider

    @Mock
    private lateinit var mockActivity: Activity

    @Mock
    private lateinit var mockWindow: Window

    @BeforeEach
    fun `set up`() {
        whenever(mockActivity.window).thenReturn(mockWindow)
        testedCallback = SessionReplayLifecycleCallback(
            mockRumContextProvider,
            fakePrivacy,
            mockRecordWriter,
            mockTimeProvider,
            mockRecordCallback
        )
        testedCallback.recorder = mockRecoder
    }

    @Test
    fun `M register fragment lifecycle W onActivityCreated(){FragmentActivity}`() {
        // Given
        val mockFragmentManager: FragmentManager = mock()
        val mockFragmentActivity: FragmentActivity = mock {
            whenever(it.supportFragmentManager).thenReturn(mockFragmentManager)
        }

        // When
        testedCallback.onActivityCreated(mockFragmentActivity, mock())

        // Then
        val argumentCaptor = argumentCaptor<FragmentLifecycleCallbacks>()
        verify(mockFragmentManager).registerFragmentLifecycleCallbacks(
            argumentCaptor.capture(),
            eq(true)
        )
        assertThat(argumentCaptor.firstValue)
            .isInstanceOf(RecorderFragmentLifecycleCallback::class.java)
    }

    @Test
    fun `M do nothing W onActivityCreated(){no FragmentActivity}`() {
        // When
        testedCallback.onActivityCreated(mockActivity, mock())

        // Then
        verifyZeroInteractions(mockActivity)
    }

    @Test
    fun `M start recording activity W onActivityResumed()`() {
        // When
        testedCallback.onActivityResumed(mockActivity)

        // Then
        verify(mockRecoder).startRecording(listOf(mockWindow), mockActivity)
    }

    @Test
    fun `M stop recording activity W onActivityPaused() { activity previously resumed }`() {
        // Given
        testedCallback.onActivityResumed(mockActivity)

        // When
        testedCallback.onActivityPaused(mockActivity)

        // Then
        verify(mockRecoder).stopRecording(listOf(mockWindow))
    }

    @Test
    fun `M stop recording activity W onActivityPaused() { activity not previously resumed }`() {
        // When
        testedCallback.onActivityPaused(mockActivity)

        // Then
        verify(mockRecoder).stopRecording(listOf(mockWindow))
    }

    @Test
    fun `M register lifecycle callback W register()`() {
        // Given
        val mockApplication: Application = mock()

        // When
        testedCallback.register(mockApplication)

        // Then
        verify(mockApplication).registerActivityLifecycleCallbacks(testedCallback)
    }

    @Test
    fun `M unregister lifecycle callback W unregisterAndStopRecorders()`() {
        // Given
        val mockApplication: Application = mock()

        // When
        testedCallback.unregisterAndStopRecorders(mockApplication)

        // Then
        verify(mockApplication).unregisterActivityLifecycleCallbacks(testedCallback)
    }

    @Test
    fun `M stop recording all resumed activities W unregisterAndStopRecorders()`() {
        // Given
        val mockApplication: Application = mock()
        val mockWindow1: Window = mock()
        val mockWindow2: Window = mock()
        val mockActivity1: Activity = mock {
            whenever(it.window).thenReturn(mockWindow1)
        }
        val mockActivity2: Activity = mock {
            whenever(it.window).thenReturn(mockWindow2)
        }
        testedCallback.onActivityResumed(mockActivity1)
        testedCallback.onActivityResumed(mockActivity2)

        // When
        testedCallback.unregisterAndStopRecorders(mockApplication)

        // Then
        verify(mockRecoder).stopRecording()
    }
}
