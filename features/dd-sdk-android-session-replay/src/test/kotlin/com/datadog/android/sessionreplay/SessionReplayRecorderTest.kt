/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay

import android.app.Application
import android.os.Handler
import android.view.View
import android.view.Window
import com.datadog.android.api.InternalLogger
import com.datadog.android.sessionreplay.internal.LifecycleCallback
import com.datadog.android.sessionreplay.internal.RecordWriter
import com.datadog.android.sessionreplay.internal.recorder.ViewOnDrawInterceptor
import com.datadog.android.sessionreplay.internal.recorder.WindowCallbackInterceptor
import com.datadog.android.sessionreplay.internal.recorder.WindowInspector
import com.datadog.android.sessionreplay.internal.utils.RumContextProvider
import com.datadog.android.sessionreplay.internal.utils.TimeProvider
import com.datadog.android.sessionreplay.utils.config.ApplicationContextTestConfiguration
import com.datadog.tools.unit.annotations.TestConfigurationsProvider
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.datadog.tools.unit.extensions.config.TestConfiguration
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(TestConfigurationExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class SessionReplayRecorderTest {

    @Mock
    private lateinit var mockViewOnDrawInterceptor: ViewOnDrawInterceptor

    @Mock
    private lateinit var mockRumContextProvider: RumContextProvider

    @Mock
    private lateinit var mockRecordWriter: RecordWriter

    @Forgery
    private lateinit var fakePrivacy: SessionReplayPrivacy

    @Mock
    private lateinit var mockTimeProvider: TimeProvider

    @Mock
    private lateinit var mockWindowCallbackInterceptor: WindowCallbackInterceptor

    @Mock
    lateinit var mockLifecycleCallback: LifecycleCallback

    @Mock
    lateinit var mockWindowInspector: WindowInspector

    @Mock
    lateinit var mockUiHandler: Handler

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    lateinit var fakeActiveWindows: List<Window>
    lateinit var fakeActiveWindowsDecorViews: List<View>

    lateinit var testedSessionReplayRecorder: SessionReplayRecorder

    @BeforeEach
    fun `set up`(forge: Forge) {
        fakeActiveWindows = forge.aList { mock() }
        fakeActiveWindowsDecorViews = fakeActiveWindows.map { mock() }
        whenever(mockLifecycleCallback.getCurrentWindows()).thenReturn(fakeActiveWindows)
        whenever(mockWindowInspector.getGlobalWindowViews(mockInternalLogger))
            .thenReturn(fakeActiveWindowsDecorViews)
        whenever(mockUiHandler.post(any())).then {
            it.getArgument<Runnable>(0).run()
            true
        }
        testedSessionReplayRecorder = SessionReplayRecorder(
            appContext.mockInstance,
            mockRumContextProvider,
            fakePrivacy,
            mockRecordWriter,
            mockTimeProvider,
            mock(),
            mock(),
            mockWindowInspector,
            mockWindowCallbackInterceptor,
            mockLifecycleCallback,
            mockViewOnDrawInterceptor,
            mock(),
            mockUiHandler,
            mockInternalLogger
        )
    }

    @Test
    fun `M register the lifecycle callback W registerCallbacks`() {
        // When
        testedSessionReplayRecorder.registerCallbacks()

        // Then
        verify(appContext.mockInstance).registerActivityLifecycleCallbacks(mockLifecycleCallback)
    }

    @Test
    fun `M unregister the lifecycle callback W unregisterCallbacks`() {
        // When
        testedSessionReplayRecorder.unregisterCallbacks()

        // Then
        verify(appContext.mockInstance).unregisterActivityLifecycleCallbacks(mockLifecycleCallback)
    }

    @Test
    fun `M intercept the active windows and decor view W resumeRecorders`() {
        // When
        testedSessionReplayRecorder.resumeRecorders()

        // Then
        verify(mockWindowCallbackInterceptor).intercept(fakeActiveWindows, appContext.mockInstance)
        verify(mockViewOnDrawInterceptor).intercept(
            fakeActiveWindowsDecorViews
        )
    }

    @Test
    fun `M stop intercepting the active windows and decor view W stopRecorders`() {
        // When
        testedSessionReplayRecorder.stopRecorders()

        // Then
        verify(mockViewOnDrawInterceptor).stopIntercepting()
        verify(mockWindowCallbackInterceptor).stopIntercepting()
    }

    @Test
    fun `M intercept the current windows and all active decors W onWindowsAdded{resumed}`(
        forge: Forge
    ) {
        // Given
        testedSessionReplayRecorder.resumeRecorders()
        val fakeAddedWindows = forge.aList { mock<Window>() }
        val fakeNewDecorViews = fakeAddedWindows.map { mock<View>() }
        whenever(mockWindowInspector.getGlobalWindowViews(mockInternalLogger))
            .thenReturn(fakeNewDecorViews)

        // When
        testedSessionReplayRecorder.onWindowsAdded(fakeAddedWindows)

        // Then
        verify(mockWindowCallbackInterceptor).intercept(fakeAddedWindows, appContext.mockInstance)
        verify(mockViewOnDrawInterceptor).intercept(fakeNewDecorViews)
    }

    @Test
    fun `M do nothing W onWindowsAdded{paused}`(
        forge: Forge
    ) {
        // Given
        testedSessionReplayRecorder.resumeRecorders()
        testedSessionReplayRecorder.stopRecorders()
        val fakeAddedWindows = forge.aList { mock<Window>() }
        val fakeNewDecorViews = fakeAddedWindows.map { mock<View>() }
        whenever(mockWindowInspector.getGlobalWindowViews(mockInternalLogger))
            .thenReturn(fakeNewDecorViews)

        // When
        testedSessionReplayRecorder.onWindowsAdded(fakeAddedWindows)

        // Then
        verify(mockWindowCallbackInterceptor, never())
            .intercept(fakeAddedWindows, appContext.mockInstance)
        verify(mockViewOnDrawInterceptor, never())
            .intercept(fakeNewDecorViews)
    }

    @Test
    fun `M do nothing W onWindowsAdded{just initialized}`(
        forge: Forge
    ) {
        // Given
        testedSessionReplayRecorder.resumeRecorders()
        testedSessionReplayRecorder.stopRecorders()
        val fakeAddedWindows = forge.aList { mock<Window>() }
        val fakeNewDecorViews = fakeAddedWindows.map { mock<View>() }
        whenever(mockWindowInspector.getGlobalWindowViews(mockInternalLogger))
            .thenReturn(fakeNewDecorViews)

        // When
        testedSessionReplayRecorder.onWindowsAdded(fakeAddedWindows)

        // Then
        verify(mockWindowCallbackInterceptor, never())
            .intercept(fakeAddedWindows, appContext.mockInstance)
        verify(mockViewOnDrawInterceptor, never())
            .intercept(fakeNewDecorViews)
    }

    @Test
    fun `M stop intercepting the current windows and refresh decors W onWindowsRemoved{paused}`(
        forge: Forge
    ) {
        // Given
        testedSessionReplayRecorder.resumeRecorders()
        val fakeAddedWindows = forge.aList { mock<Window>() }
        val fakeNewDecorViews = fakeAddedWindows.map { mock<View>() }
        whenever(mockWindowInspector.getGlobalWindowViews(mockInternalLogger))
            .thenReturn(fakeNewDecorViews)

        // When
        testedSessionReplayRecorder.onWindowsRemoved(fakeAddedWindows)

        // Then
        verify(mockWindowCallbackInterceptor).stopIntercepting(fakeAddedWindows)
        verify(mockViewOnDrawInterceptor).intercept(fakeNewDecorViews)
    }

    @Test
    fun `M do nothing W onWindowsRemoved{paused}`(
        forge: Forge
    ) {
        // Given
        testedSessionReplayRecorder.resumeRecorders()
        testedSessionReplayRecorder.stopRecorders()
        val fakeAddedWindows = forge.aList { mock<Window>() }
        val fakeNewDecorViews = fakeAddedWindows.map { mock<View>() }
        whenever(mockWindowInspector.getGlobalWindowViews(mockInternalLogger))
            .thenReturn(fakeNewDecorViews)

        // When
        testedSessionReplayRecorder.onWindowsRemoved(fakeAddedWindows)

        // Then
        verify(mockWindowCallbackInterceptor, never()).stopIntercepting(fakeAddedWindows)
        verify(mockViewOnDrawInterceptor, never())
            .intercept(fakeNewDecorViews)
    }

    @Test
    fun `M do nothing W onWindowsRemoved{just initialized}`(
        forge: Forge
    ) {
        // Given
        val fakeAddedWindows = forge.aList { mock<Window>() }
        val fakeNewDecorViews = fakeAddedWindows.map { mock<View>() }
        whenever(mockWindowInspector.getGlobalWindowViews(mockInternalLogger))
            .thenReturn(fakeNewDecorViews)

        // When
        testedSessionReplayRecorder.onWindowsRemoved(fakeAddedWindows)

        // Then
        verify(mockWindowCallbackInterceptor, never()).stopIntercepting(fakeAddedWindows)
        verify(mockViewOnDrawInterceptor, never())
            .intercept(fakeNewDecorViews)
    }

    companion object {
        val appContext = ApplicationContextTestConfiguration(Application::class.java)

        @TestConfigurationsProvider
        @JvmStatic
        fun getTestConfigurations(): List<TestConfiguration> {
            return listOf(appContext)
        }
    }
}
