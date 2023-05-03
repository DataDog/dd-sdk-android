/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal

import android.app.Activity
import android.app.Application
import android.view.View
import android.view.Window
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentManager.FragmentLifecycleCallbacks
import com.datadog.android.sessionreplay.SessionReplayPrivacy
import com.datadog.android.sessionreplay.internal.recorder.ViewOnDrawInterceptor
import com.datadog.android.sessionreplay.internal.recorder.WindowCallbackInterceptor
import com.datadog.android.sessionreplay.internal.recorder.callback.RecorderFragmentLifecycleCallback
import com.datadog.android.sessionreplay.internal.utils.RumContextProvider
import com.datadog.android.sessionreplay.internal.utils.TimeProvider
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
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class SessionReplayLifecycleCallbackTest {

    lateinit var testedCallback: SessionReplayLifecycleCallback

    @Mock
    private lateinit var mockWindowCallbackInterceptor: WindowCallbackInterceptor

    @Mock
    private lateinit var mockViewOnDrawInterceptor: ViewOnDrawInterceptor

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

    @Mock
    private lateinit var mockDecorView: View

    @BeforeEach
    fun `set up`() {
        whenever(mockActivity.window).thenReturn(mockWindow)
        whenever(mockWindow.decorView).thenReturn(mockDecorView)
        testedCallback = SessionReplayLifecycleCallback(
            mockRumContextProvider,
            fakePrivacy,
            mockRecordWriter,
            mockTimeProvider,
            mockRecordCallback
        )
        testedCallback.viewOnDrawInterceptor = mockViewOnDrawInterceptor
        testedCallback.windowCallbackInterceptor = mockWindowCallbackInterceptor
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
        verifyNoInteractions(mockActivity)
    }

    @Test
    fun `M intercept activity window callback W onActivityResumed()`() {
        // When
        testedCallback.onActivityResumed(mockActivity)

        // Then
        verify(mockWindowCallbackInterceptor).intercept(listOf(mockWindow), mockActivity)
    }

    @Test
    fun `M intercept activity decorView onDraw W onActivityResumed()`() {
        // When
        testedCallback.onActivityResumed(mockActivity)

        // Then
        verify(mockViewOnDrawInterceptor).intercept(listOf(mockDecorView), mockActivity)
    }

    @Test
    fun `M stop intercepting window callback W onActivityPaused() {activity previously resumed}`() {
        // Given
        testedCallback.onActivityResumed(mockActivity)

        // When
        testedCallback.onActivityPaused(mockActivity)

        // Then
        verify(mockWindowCallbackInterceptor).stopIntercepting(listOf(mockWindow))
    }

    @Test
    fun `M stop intercepting window callback W onActivityPaused() {not previously resumed}`() {
        // When
        testedCallback.onActivityPaused(mockActivity)

        // Then
        verify(mockWindowCallbackInterceptor).stopIntercepting(listOf(mockWindow))
    }

    @Test
    fun `M stop intercepting decorView onDraw W onActivityPaused(){activity previously resumed}`() {
        // Given
        testedCallback.onActivityResumed(mockActivity)

        // When
        testedCallback.onActivityPaused(mockActivity)

        // Then
        verify(mockViewOnDrawInterceptor).stopIntercepting(listOf(mockDecorView))
    }

    @Test
    fun `M stop intercepting decorView onDraw W onActivityPaused() {not previously resumed}`() {
        // When
        testedCallback.onActivityPaused(mockActivity)

        // Then
        verify(mockViewOnDrawInterceptor).stopIntercepting(listOf(mockDecorView))
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
        verify(mockWindowCallbackInterceptor).stopIntercepting()
        verify(mockViewOnDrawInterceptor).stopIntercepting()
    }
}
