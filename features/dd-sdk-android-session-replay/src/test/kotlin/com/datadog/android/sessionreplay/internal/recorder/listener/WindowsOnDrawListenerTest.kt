/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.listener

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.content.res.Resources.Theme
import android.view.View
import com.datadog.android.api.InternalLogger
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.core.metrics.PerformanceMetric
import com.datadog.android.core.metrics.TelemetryMetricType
import com.datadog.android.sessionreplay.ImagePrivacy
import com.datadog.android.sessionreplay.TextAndInputPrivacy
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.internal.TouchPrivacyManager
import com.datadog.android.sessionreplay.internal.async.RecordedDataQueueHandler
import com.datadog.android.sessionreplay.internal.async.RecordedDataQueueRefs
import com.datadog.android.sessionreplay.internal.async.SnapshotRecordedDataQueueItem
import com.datadog.android.sessionreplay.internal.recorder.CompositionTreeBuilder
import com.datadog.android.sessionreplay.internal.recorder.Debouncer
import com.datadog.android.sessionreplay.internal.recorder.Node
import com.datadog.android.sessionreplay.internal.recorder.SnapshotProducer
import com.datadog.android.sessionreplay.internal.utils.MiscUtils
import com.datadog.android.sessionreplay.internal.utils.RumContextProvider
import com.datadog.android.sessionreplay.internal.utils.SessionReplayRumContext
import com.datadog.android.sessionreplay.recorder.SystemInformation
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.BoolForgery
import fr.xgouchet.elmyr.annotation.FloatForgery
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.annotation.StringForgery
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
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal class WindowsOnDrawListenerTest {

    private lateinit var testedListener: WindowsOnDrawListener

    @Mock
    lateinit var mockDecorView: View

    private lateinit var mockResources: Resources
    private lateinit var configuration: Configuration

    @Mock
    lateinit var mockSnapshotProducer: SnapshotProducer

    @Mock
    lateinit var mockRecordedDataQueueHandler: RecordedDataQueueHandler

    @Mock
    lateinit var mockDebouncer: Debouncer

    @Mock
    lateinit var mockTouchPrivacyManager: TouchPrivacyManager

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @Mock
    lateinit var mockSdkCore: FeatureSdkCore

    @Mock
    lateinit var mockPerformanceMetric: PerformanceMetric

    @IntForgery(min = 0)
    var fakeDecorWidth: Int = 0

    @IntForgery(min = 0)
    var fakeDecorHeight: Int = 0
    private var fakeOrientation: Int = Configuration.ORIENTATION_UNDEFINED

    private lateinit var fakeMockedDecorViews: List<View>
    private lateinit var fakeWindowsSnapshots: List<Node>

    @Mock
    lateinit var mockTheme: Theme

    @Mock
    lateinit var mockMiscUtils: MiscUtils

    @Forgery
    lateinit var fakeSystemInformation: SystemInformation

    @Forgery
    lateinit var fakeSnapshotQueueItem: SnapshotRecordedDataQueueItem

    @Mock
    lateinit var mockContext: Context

    @Forgery
    lateinit var fakeTextAndInputPrivacy: TextAndInputPrivacy

    @Forgery
    lateinit var fakeImagePrivacy: ImagePrivacy

    @BoolForgery
    var fakeDynamicOptimizationEnabled: Boolean = false

    @FloatForgery
    var fakeMethodCallSamplingRate: Float = 0f

    @Mock
    lateinit var mockRumContextProvider: RumContextProvider

    @Mock
    lateinit var mockCompositionTreeBuilder: CompositionTreeBuilder

    @BeforeEach
    fun `set up`(forge: Forge) {
        whenever(mockSdkCore.internalLogger).thenReturn(mockInternalLogger)
        whenever(mockRumContextProvider.getRumContext()).thenReturn(SessionReplayRumContext())
        whenever(mockMiscUtils.resolveSystemInformation(mockContext))
            .thenReturn(fakeSystemInformation)
        fakeMockedDecorViews = forge.aMockedDecorViewList().onEach {
            whenever(it.context).thenReturn(mockContext)
        }
        fakeWindowsSnapshots = fakeMockedDecorViews.map { forge.getForgery() }
        whenever(mockContext.theme).thenReturn(mockTheme)
        fakeMockedDecorViews.forEachIndexed { index, decorView ->
            whenever(
                mockSnapshotProducer.produce(
                    rootView = eq(decorView),
                    systemInformation = eq(fakeSystemInformation),
                    textAndInputPrivacy = eq(fakeTextAndInputPrivacy),
                    imagePrivacy = eq(fakeImagePrivacy),
                    recordedDataQueueRefs = any(),
                    activeRumViewUrl = anyOrNull()
                )
            )
                .thenReturn(fakeWindowsSnapshots[index])
        }
        whenever(mockDecorView.width).thenReturn(fakeDecorWidth)
        whenever(mockDecorView.height).thenReturn(fakeDecorHeight)
        configuration = Configuration()
        fakeOrientation = forge.anElementFrom(
            Configuration
                .ORIENTATION_LANDSCAPE,
            Configuration.ORIENTATION_PORTRAIT
        )
        fakeDynamicOptimizationEnabled = forge.aBool()
        configuration.orientation = fakeOrientation
        mockResources = mock {
            whenever(it.configuration).thenReturn(configuration)
        }
        whenever(mockContext.resources).thenReturn(mockResources)

        whenever(mockDebouncer.debounce(any())).then { (it.arguments[0] as Runnable).run() }

        testedListener = WindowsOnDrawListener(
            decorViews = fakeMockedDecorViews,
            recordedDataQueueHandler = mockRecordedDataQueueHandler,
            snapshotProducer = mockSnapshotProducer,
            textAndInputPrivacy = fakeTextAndInputPrivacy,
            imagePrivacy = fakeImagePrivacy,
            debouncer = mockDebouncer,
            miscUtils = mockMiscUtils,
            sdkCore = mockSdkCore,
            methodCallSamplingRate = fakeMethodCallSamplingRate,
            dynamicOptimizationEnabled = fakeDynamicOptimizationEnabled,
            touchPrivacyManager = mockTouchPrivacyManager,
            rumContextProvider = mockRumContextProvider
        )
    }

    @Test
    fun `M take and add to queue W onDraw()`() {
        // Given
        whenever(mockRecordedDataQueueHandler.addSnapshotItem(any<SystemInformation>()))
            .thenReturn(fakeSnapshotQueueItem)

        // When
        testedListener.onDraw()

        // Then
        verify(mockRecordedDataQueueHandler).addSnapshotItem(fakeSystemInformation)
    }

    @Test
    fun `M update queue with correct nodes W onDraw()`() {
        // Given
        whenever(mockRecordedDataQueueHandler.addSnapshotItem(any<SystemInformation>()))
            .thenReturn(fakeSnapshotQueueItem)
        fakeSnapshotQueueItem.pendingJobs.set(0)

        // When
        testedListener.onDraw()

        // Then
        val argCaptor = argumentCaptor<RecordedDataQueueRefs>()
        verify(mockSnapshotProducer, times(fakeWindowsSnapshots.size)).produce(
            rootView = any(),
            systemInformation = any(),
            textAndInputPrivacy = eq(fakeTextAndInputPrivacy),
            imagePrivacy = eq(fakeImagePrivacy),
            recordedDataQueueRefs = argCaptor.capture(),
            activeRumViewUrl = anyOrNull()
        )
        assertThat(argCaptor.firstValue.recordedDataQueueItem).isEqualTo(fakeSnapshotQueueItem)
        verify(mockRecordedDataQueueHandler).tryToConsumeItems()
    }

    @Test
    fun `M do nothing W onDraw(){ windows are empty }`() {
        // When
        testedListener = WindowsOnDrawListener(
            decorViews = emptyList(),
            recordedDataQueueHandler = mockRecordedDataQueueHandler,
            snapshotProducer = mockSnapshotProducer,
            textAndInputPrivacy = fakeTextAndInputPrivacy,
            imagePrivacy = fakeImagePrivacy,
            debouncer = mockDebouncer,
            miscUtils = mockMiscUtils,
            sdkCore = mockSdkCore,
            methodCallSamplingRate = fakeMethodCallSamplingRate,
            dynamicOptimizationEnabled = fakeDynamicOptimizationEnabled,
            touchPrivacyManager = mockTouchPrivacyManager,
            rumContextProvider = mockRumContextProvider
        )
        testedListener.onDraw()

        // Then
        verifyNoInteractions(mockRecordedDataQueueHandler)
        verifyNoInteractions(mockSnapshotProducer)
    }

    @Test
    fun `M do nothing W onDraw(){ windows lost the strong reference }`() {
        // Given
        testedListener.weakReferencedDecorViews.forEach { it.clear() }

        // When
        testedListener.onDraw()

        // Then
        verify(mockRecordedDataQueueHandler, never()).tryToConsumeItems()
    }

    @Test
    fun `M do nothing W onDraw(){ no available view context }`() {
        // Given
        fakeMockedDecorViews.forEach { whenever(it.context).thenReturn(null) }

        // When
        testedListener.onDraw()

        // Then
        verify(mockRecordedDataQueueHandler, never()).tryToConsumeItems()
    }

    @Test
    fun `M call methodCall telemetry with true W onDraw() { has nodes }`() {
        // Given
        whenever(
            mockInternalLogger.startPerformanceMeasure(
                "com.datadog.android.sessionreplay.internal.recorder.listener.WindowsOnDrawListener",
                TelemetryMetricType.MethodCalled,
                fakeMethodCallSamplingRate,
                "Capture Record"
            )
        ).thenReturn(mockPerformanceMetric)
        whenever(mockDebouncer.debounce(any())) doAnswer {
            (it.arguments[0] as Runnable).run()
        }
        whenever(mockRecordedDataQueueHandler.addSnapshotItem(any<SystemInformation>()))
            .thenReturn(fakeSnapshotQueueItem)

        fakeSnapshotQueueItem.pendingJobs.set(0)

        // When
        testedListener.onDraw()

        // Then
        val booleanCaptor = argumentCaptor<Boolean>()
        verify(mockPerformanceMetric).stopAndSend(booleanCaptor.capture())
        assertThat(booleanCaptor.firstValue).isTrue()
    }

    @Test
    fun `M send methodCall telemetry with false W onDraw() { no nodes }`() {
        // Given
        whenever(
            mockInternalLogger.startPerformanceMeasure(
                "com.datadog.android.sessionreplay.internal.recorder.listener.WindowsOnDrawListener",
                TelemetryMetricType.MethodCalled,
                fakeMethodCallSamplingRate,
                "Capture Record"
            )
        ).thenReturn(mockPerformanceMetric)
        whenever(
            mockSnapshotProducer.produce(
                rootView = any(),
                systemInformation = any(),
                textAndInputPrivacy = any(),
                imagePrivacy = any(),
                recordedDataQueueRefs = any(),
                activeRumViewUrl = anyOrNull()
            )
        ).thenReturn(null)
        whenever(mockRecordedDataQueueHandler.addSnapshotItem(any<SystemInformation>()))
            .thenReturn(fakeSnapshotQueueItem)
        fakeSnapshotQueueItem.pendingJobs.set(0)

        // When
        testedListener.onDraw()

        // Then
        argumentCaptor<Boolean> {
            verify(mockPerformanceMetric).stopAndSend(capture())
            assertThat(firstValue).isFalse()
        }
    }

    @Test
    fun `M pass viewUrl from RumContextProvider W onDraw()`(
        @StringForgery fakeViewUrl: String
    ) {
        val mockRumContextProvider: RumContextProvider = mock {
            whenever(it.getRumContext()).thenReturn(
                SessionReplayRumContext(viewUrl = fakeViewUrl)
            )
        }
        val listenerWithProvider = WindowsOnDrawListener(
            decorViews = fakeMockedDecorViews,
            recordedDataQueueHandler = mockRecordedDataQueueHandler,
            snapshotProducer = mockSnapshotProducer,
            textAndInputPrivacy = fakeTextAndInputPrivacy,
            imagePrivacy = fakeImagePrivacy,
            debouncer = mockDebouncer,
            miscUtils = mockMiscUtils,
            sdkCore = mockSdkCore,
            methodCallSamplingRate = fakeMethodCallSamplingRate,
            dynamicOptimizationEnabled = fakeDynamicOptimizationEnabled,
            touchPrivacyManager = mockTouchPrivacyManager,
            rumContextProvider = mockRumContextProvider
        )
        whenever(mockRecordedDataQueueHandler.addSnapshotItem(any<SystemInformation>()))
            .thenReturn(fakeSnapshotQueueItem)
        fakeSnapshotQueueItem.pendingJobs.set(0)

        listenerWithProvider.onDraw()

        verify(mockSnapshotProducer, times(fakeMockedDecorViews.size)).produce(
            rootView = any(),
            systemInformation = any(),
            textAndInputPrivacy = eq(fakeTextAndInputPrivacy),
            imagePrivacy = eq(fakeImagePrivacy),
            recordedDataQueueRefs = any(),
            activeRumViewUrl = eq(fakeViewUrl)
        )
    }

    // region Composition tree pipeline — root view selection

    @Test
    fun `M build with the focused window last W onDraw() {composition tree pipeline, multiple windows}`() {
        // Given — the first window in the list has no focus (e.g. a backgrounded-but-visible
        // window, such as an Activity behind a dialog/sheet); the second one does. Both must be
        // captured and merged, with the focused one ordered last (renders on top).
        val mockBackgroundedView = aMockDecorView(hasWindowFocus = false)
        val mockFocusedView = aMockDecorView(hasWindowFocus = true)

        val listenerWithCompositionTree = WindowsOnDrawListener(
            decorViews = listOf(mockBackgroundedView, mockFocusedView),
            recordedDataQueueHandler = mockRecordedDataQueueHandler,
            snapshotProducer = mockSnapshotProducer,
            textAndInputPrivacy = fakeTextAndInputPrivacy,
            imagePrivacy = fakeImagePrivacy,
            debouncer = mockDebouncer,
            miscUtils = mockMiscUtils,
            sdkCore = mockSdkCore,
            methodCallSamplingRate = fakeMethodCallSamplingRate,
            dynamicOptimizationEnabled = fakeDynamicOptimizationEnabled,
            touchPrivacyManager = mockTouchPrivacyManager,
            rumContextProvider = mockRumContextProvider,
            compositionTreeBuilder = mockCompositionTreeBuilder
        )
        whenever(mockRecordedDataQueueHandler.addSnapshotItem(any<SystemInformation>()))
            .thenReturn(fakeSnapshotQueueItem)
        fakeSnapshotQueueItem.pendingJobs.set(0)
        whenever(
            mockCompositionTreeBuilder.build(any(), any(), any(), any(), any(), any())
        ).thenReturn(CompositionTreeBuilder.Output(null, emptyList()))

        // When
        listenerWithCompositionTree.onDraw()

        // Then
        verify(mockCompositionTreeBuilder).build(
            rootViews = eq(listOf(mockBackgroundedView, mockFocusedView)),
            systemInformation = any(),
            textAndInputPrivacy = eq(fakeTextAndInputPrivacy),
            imagePrivacy = eq(fakeImagePrivacy),
            recordedDataQueueRefs = any(),
            internalLogger = any()
        )
    }

    @Test
    fun `M keep original order W onDraw() {composition tree pipeline, no window has focus}`() {
        // Given — none of the windows report focus (e.g. a brief window-transition gap): the
        // stable sort by focus leaves relative order untouched rather than picking just one.
        val mockFirstView = aMockDecorView(hasWindowFocus = false)
        val mockSecondView = aMockDecorView(hasWindowFocus = false)

        val listenerWithCompositionTree = WindowsOnDrawListener(
            decorViews = listOf(mockFirstView, mockSecondView),
            recordedDataQueueHandler = mockRecordedDataQueueHandler,
            snapshotProducer = mockSnapshotProducer,
            textAndInputPrivacy = fakeTextAndInputPrivacy,
            imagePrivacy = fakeImagePrivacy,
            debouncer = mockDebouncer,
            miscUtils = mockMiscUtils,
            sdkCore = mockSdkCore,
            methodCallSamplingRate = fakeMethodCallSamplingRate,
            dynamicOptimizationEnabled = fakeDynamicOptimizationEnabled,
            touchPrivacyManager = mockTouchPrivacyManager,
            rumContextProvider = mockRumContextProvider,
            compositionTreeBuilder = mockCompositionTreeBuilder
        )
        whenever(mockRecordedDataQueueHandler.addSnapshotItem(any<SystemInformation>()))
            .thenReturn(fakeSnapshotQueueItem)
        fakeSnapshotQueueItem.pendingJobs.set(0)
        whenever(
            mockCompositionTreeBuilder.build(any(), any(), any(), any(), any(), any())
        ).thenReturn(CompositionTreeBuilder.Output(null, emptyList()))

        // When
        listenerWithCompositionTree.onDraw()

        // Then
        verify(mockCompositionTreeBuilder).build(
            rootViews = eq(listOf(mockFirstView, mockSecondView)),
            systemInformation = any(),
            textAndInputPrivacy = eq(fakeTextAndInputPrivacy),
            imagePrivacy = eq(fakeImagePrivacy),
            recordedDataQueueRefs = any(),
            internalLogger = any()
        )
    }

    @Test
    fun `M pass hidden windows through unfiltered W onDraw() {composition tree pipeline, only reorders}`() {
        // Given — visibility filtering is CompositionTreeBuilder's job (see its own tests),
        // not this listener's: it only reorders, it never drops a window from the list.
        val mockHiddenView = aMockDecorView(hasWindowFocus = false)
        val mockFocusedView = aMockDecorView(hasWindowFocus = true)
        whenever(mockHiddenView.isShown).thenReturn(false)

        val listenerWithCompositionTree = WindowsOnDrawListener(
            decorViews = listOf(mockHiddenView, mockFocusedView),
            recordedDataQueueHandler = mockRecordedDataQueueHandler,
            snapshotProducer = mockSnapshotProducer,
            textAndInputPrivacy = fakeTextAndInputPrivacy,
            imagePrivacy = fakeImagePrivacy,
            debouncer = mockDebouncer,
            miscUtils = mockMiscUtils,
            sdkCore = mockSdkCore,
            methodCallSamplingRate = fakeMethodCallSamplingRate,
            dynamicOptimizationEnabled = fakeDynamicOptimizationEnabled,
            touchPrivacyManager = mockTouchPrivacyManager,
            rumContextProvider = mockRumContextProvider,
            compositionTreeBuilder = mockCompositionTreeBuilder
        )
        whenever(mockRecordedDataQueueHandler.addSnapshotItem(any<SystemInformation>()))
            .thenReturn(fakeSnapshotQueueItem)
        fakeSnapshotQueueItem.pendingJobs.set(0)
        whenever(
            mockCompositionTreeBuilder.build(any(), any(), any(), any(), any(), any())
        ).thenReturn(CompositionTreeBuilder.Output(null, emptyList()))

        // When
        listenerWithCompositionTree.onDraw()

        // Then — mockHiddenView is still present, just reordered, not dropped
        verify(mockCompositionTreeBuilder).build(
            rootViews = eq(listOf(mockHiddenView, mockFocusedView)),
            systemInformation = any(),
            textAndInputPrivacy = eq(fakeTextAndInputPrivacy),
            imagePrivacy = eq(fakeImagePrivacy),
            recordedDataQueueRefs = any(),
            internalLogger = any()
        )
    }

    private fun aMockDecorView(hasWindowFocus: Boolean): View = mock {
        whenever(it.viewTreeObserver).thenReturn(mock())
        whenever(it.context).thenReturn(mockContext)
        whenever(it.hasWindowFocus()).thenReturn(hasWindowFocus)
    }

    // endregion

    // region Internal

    private fun Forge.aMockedDecorViewList(): List<View> {
        return aList {
            mock {
                whenever(it.viewTreeObserver).thenReturn(mock())
            }
        }
    }

    // endregion
}
