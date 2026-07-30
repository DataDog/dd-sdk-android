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
import com.datadog.android.sessionreplay.internal.async.RecordedDataQueueHandler
import com.datadog.android.sessionreplay.internal.recorder.SessionReplayRecorder
import com.datadog.android.sessionreplay.internal.recorder.ViewOnDrawInterceptor
import com.datadog.android.sessionreplay.internal.recorder.WindowCallbackInterceptor
import com.datadog.android.sessionreplay.internal.recorder.WindowInspector
import com.datadog.android.sessionreplay.internal.recorder.resources.ResourceResolver
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

    @Forgery
    private lateinit var fakeTextAndInputPrivacy: TextAndInputPrivacy

    @Forgery
    private lateinit var fakeImagePrivacy: ImagePrivacy

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

    private lateinit var fakeActiveWindows: List<Window>
    private lateinit var fakeActiveWindowsDecorViews: List<View>
    private lateinit var testedSessionReplayRecorder: SessionReplayRecorder

    @Mock
    lateinit var mockRecordedDataQueueHandler: RecordedDataQueueHandler

    @Mock
    lateinit var mockResourceResolver: ResourceResolver

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
            appContext = appContext.mockInstance,
            textAndInputPrivacy = fakeTextAndInputPrivacy,
            imagePrivacy = fakeImagePrivacy,
            customOptionSelectorDetectors = mock(),
            windowInspector = mockWindowInspector,
            windowCallbackInterceptor = mockWindowCallbackInterceptor,
            sessionReplayLifecycleCallback = mockLifecycleCallback,
            viewOnDrawInterceptor = mockViewOnDrawInterceptor,
            recordedDataQueueHandler = mockRecordedDataQueueHandler,
            resourceResolver = mockResourceResolver,
            uiHandler = mockUiHandler,
            internalLogger = mockInternalLogger
        )
    }

    @Test
    fun `M also intercept an already-open untracked window W resumeRecorders`(forge: Forge) {
        // Given
        val mockDialogWindow: Window = mock()
        val mockDialogDecorView: View = mock {
            whenever(it.width).thenReturn(forge.aPositiveInt(strict = true))
            whenever(it.height).thenReturn(forge.aPositiveInt(strict = true))
        }
        whenever(mockWindowInspector.getGlobalWindowViews(mockInternalLogger))
            .thenReturn(fakeActiveWindowsDecorViews + mockDialogDecorView)
        testedSessionReplayRecorder = buildRecorderWith(
            windowFromDecorView = { view -> if (view == mockDialogDecorView) mockDialogWindow else null }
        )

        // When
        testedSessionReplayRecorder.resumeRecorders()

        // Then
        verify(mockWindowCallbackInterceptor).intercept(
            fakeActiveWindows + mockDialogWindow,
            appContext.mockInstance
        )
    }

    @Test
    fun `M not retry an already-known window W resumeRecorders {resolves to a tracked window}`(forge: Forge) {
        // Given
        val mockDecorView: View = mock {
            whenever(it.width).thenReturn(forge.aPositiveInt(strict = true))
            whenever(it.height).thenReturn(forge.aPositiveInt(strict = true))
        }
        whenever(mockWindowInspector.getGlobalWindowViews(mockInternalLogger))
            .thenReturn(fakeActiveWindowsDecorViews + mockDecorView)
        testedSessionReplayRecorder = buildRecorderWith(
            windowFromDecorView = { view -> if (view == mockDecorView) fakeActiveWindows.first() else null }
        )

        // When
        testedSessionReplayRecorder.resumeRecorders()

        // Then
        verify(mockWindowCallbackInterceptor).intercept(fakeActiveWindows, appContext.mockInstance)
    }

    @Test
    fun `M not resolve a zero-size untracked window W resumeRecorders`() {
        // Given
        val mockDialogWindow: Window = mock()
        val mockZeroSizeDecorView: View = mock()
        whenever(mockWindowInspector.getGlobalWindowViews(mockInternalLogger))
            .thenReturn(fakeActiveWindowsDecorViews + mockZeroSizeDecorView)
        testedSessionReplayRecorder = buildRecorderWith(
            windowFromDecorView = { view -> if (view == mockZeroSizeDecorView) mockDialogWindow else null }
        )

        // When
        testedSessionReplayRecorder.resumeRecorders()

        // Then
        verify(mockWindowCallbackInterceptor).intercept(fakeActiveWindows, appContext.mockInstance)
    }

    @Test
    fun `M not re-include an excluded paused window W resumeRecorders`(forge: Forge) {
        // Given
        val mockPausedWindow: Window = mock()
        val mockPausedDecorView: View = mock {
            whenever(it.width).thenReturn(forge.aPositiveInt(strict = true))
            whenever(it.height).thenReturn(forge.aPositiveInt(strict = true))
        }
        whenever(mockWindowCallbackInterceptor.isExcluded(mockPausedWindow)).thenReturn(true)
        whenever(mockWindowInspector.getGlobalWindowViews(mockInternalLogger))
            .thenReturn(fakeActiveWindowsDecorViews + mockPausedDecorView)
        testedSessionReplayRecorder = buildRecorderWith(
            windowFromDecorView = { view -> if (view == mockPausedDecorView) mockPausedWindow else null }
        )

        // When
        testedSessionReplayRecorder.resumeRecorders()

        // Then
        verify(mockWindowCallbackInterceptor).intercept(fakeActiveWindows, appContext.mockInstance)
    }

    @Test
    fun `M also intercept an already-open untracked window W onWindowsAdded{resumed}`(forge: Forge) {
        // Given
        val mockDialogWindow: Window = mock()
        val mockDialogDecorView: View = mock {
            whenever(it.width).thenReturn(forge.aPositiveInt(strict = true))
            whenever(it.height).thenReturn(forge.aPositiveInt(strict = true))
        }
        testedSessionReplayRecorder = buildRecorderWith(
            windowFromDecorView = { view -> if (view == mockDialogDecorView) mockDialogWindow else null }
        )
        testedSessionReplayRecorder.resumeRecorders()
        val fakeAddedWindows = forge.aList { mock<Window>() }
        val fakeNewDecorViews = fakeAddedWindows.map { mock<View>() }
        whenever(mockWindowInspector.getGlobalWindowViews(mockInternalLogger))
            .thenReturn(fakeNewDecorViews + mockDialogDecorView)

        // When
        testedSessionReplayRecorder.onWindowsAdded(fakeAddedWindows)

        // Then
        verify(mockWindowCallbackInterceptor).intercept(
            fakeAddedWindows + mockDialogWindow,
            appContext.mockInstance
        )
    }

    private fun buildRecorderWith(windowFromDecorView: (View) -> Window?): SessionReplayRecorder {
        return SessionReplayRecorder(
            appContext = appContext.mockInstance,
            textAndInputPrivacy = fakeTextAndInputPrivacy,
            imagePrivacy = fakeImagePrivacy,
            customOptionSelectorDetectors = mock(),
            windowInspector = mockWindowInspector,
            windowCallbackInterceptor = mockWindowCallbackInterceptor,
            sessionReplayLifecycleCallback = mockLifecycleCallback,
            viewOnDrawInterceptor = mockViewOnDrawInterceptor,
            recordedDataQueueHandler = mockRecordedDataQueueHandler,
            resourceResolver = mockResourceResolver,
            uiHandler = mockUiHandler,
            internalLogger = mockInternalLogger,
            windowFromDecorView = windowFromDecorView
        )
    }

    @Test
    fun `M register the lifecycle callback W registerCallbacks`() {
        // When
        testedSessionReplayRecorder.registerCallbacks()

        // Then
        verify(appContext.mockInstance).registerActivityLifecycleCallbacks(mockLifecycleCallback)
        verify(mockResourceResolver).registerCallbacks()
    }

    @Test
    fun `M unregister the lifecycle callback W unregisterCallbacks`() {
        // When
        testedSessionReplayRecorder.unregisterCallbacks()

        // Then
        verify(appContext.mockInstance).unregisterActivityLifecycleCallbacks(mockLifecycleCallback)
        verify(mockResourceResolver).unregisterCallbacks()
    }

    @Test
    fun `M intercept the active windows and decor view W resumeRecorders`() {
        // When
        testedSessionReplayRecorder.resumeRecorders()

        // Then
        verify(mockWindowCallbackInterceptor).intercept(fakeActiveWindows, appContext.mockInstance)
        verify(mockViewOnDrawInterceptor).intercept(
            decorViews = fakeActiveWindowsDecorViews,
            textAndInputPrivacy = fakeTextAndInputPrivacy,
            imagePrivacy = fakeImagePrivacy
        )
    }

    @Test
    fun `M request capture W requestCapture { recorder resumed }`() {
        // Given
        testedSessionReplayRecorder.resumeRecorders()

        // When
        testedSessionReplayRecorder.requestCapture()

        // Then
        verify(mockViewOnDrawInterceptor).requestCapture()
    }

    @Test
    fun `M not request capture W requestCapture { recorder stopped }`() {
        // When
        testedSessionReplayRecorder.requestCapture()

        // Then
        verify(mockViewOnDrawInterceptor, never()).requestCapture()
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
        verify(mockViewOnDrawInterceptor).intercept(fakeNewDecorViews, fakeTextAndInputPrivacy, fakeImagePrivacy)
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
            .intercept(fakeNewDecorViews, fakeTextAndInputPrivacy, fakeImagePrivacy)
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
            .intercept(fakeNewDecorViews, fakeTextAndInputPrivacy, fakeImagePrivacy)
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
        verify(mockViewOnDrawInterceptor).intercept(fakeNewDecorViews, fakeTextAndInputPrivacy, fakeImagePrivacy)
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
            .intercept(fakeNewDecorViews, fakeTextAndInputPrivacy, fakeImagePrivacy)
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
            .intercept(fakeNewDecorViews, fakeTextAndInputPrivacy, fakeImagePrivacy)
    }

    @Test
    fun `M delegate to recordedDataQueueHandler W stopProcessingRecords`() {
        // When
        testedSessionReplayRecorder.stopProcessingRecords()

        // Then
        verify(mockRecordedDataQueueHandler).clearAndStopProcessingQueue()
    }

    companion object {
        val appContext = ApplicationContextTestConfiguration(Application::class.java)

        @TestConfigurationsProvider
        @JvmStatic
        @Suppress("unused") // this is actually used
        fun getTestConfigurations(): List<TestConfiguration> {
            return listOf(appContext)
        }
    }
}
