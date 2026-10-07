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
import android.os.Handler
import android.view.View
import com.datadog.android.api.InternalLogger
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.core.metrics.PerformanceMetric
import com.datadog.android.core.metrics.TelemetryMetricType
import com.datadog.android.internal.time.TimeProvider
import com.datadog.android.sessionreplay.ImagePrivacy
import com.datadog.android.sessionreplay.TextAndInputPrivacy
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.internal.SessionReplayRumContextProvider
import com.datadog.android.sessionreplay.internal.SessionReplayRumContextProvider.Companion.RUM_APPLICATION_ID_CONTEXT_KEY
import com.datadog.android.sessionreplay.internal.SessionReplayRumContextProvider.Companion.RUM_SESSION_ID_CONTEXT_KEY
import com.datadog.android.sessionreplay.internal.SessionReplayRumContextProvider.Companion.RUM_VIEW_ID_CONTEXT_KEY
import com.datadog.android.sessionreplay.internal.TouchPrivacyManager
import com.datadog.android.sessionreplay.internal.async.RecordedDataQueueHandler
import com.datadog.android.sessionreplay.internal.async.RecordedDataQueueRefs
import com.datadog.android.sessionreplay.internal.async.SnapshotRecordedDataQueueItem
import com.datadog.android.sessionreplay.internal.processor.RumContextDataHandler
import com.datadog.android.sessionreplay.internal.recorder.Debouncer
import com.datadog.android.sessionreplay.internal.recorder.Node
import com.datadog.android.sessionreplay.internal.recorder.RecordingTimeBank
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
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal class WindowsOnDrawListenerTest {

    private lateinit var testedListener: WindowsOnDrawListener
    private var fakeCaptureAllowed = true

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

        whenever(mockDebouncer.debounce(any(), any())).then { it.getArgument<Runnable>(0).run() }

        testedListener = WindowsOnDrawListener(
            zOrderedDecorViews = fakeMockedDecorViews,
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
            isCaptureAllowed = { fakeCaptureAllowed }
        )
    }

    @Test
    fun `M resume without polling W context update {active RUM view disappears and returns}`() {
        // Given: use the real context, queue and scheduler to exercise failed snapshot attempts.
        val mockHandler = mock<Handler>()
        val mockTimeProvider = mock<TimeProvider>()
        var fakeClockMs = 0L
        var fakeDueMs = 0L
        var fakePendingCapture: Runnable? = null
        whenever(mockSdkCore.timeProvider).thenReturn(mockTimeProvider)
        whenever(mockTimeProvider.getDeviceElapsedTimeNanos()).thenAnswer { fakeClockMs * 1_000_000L }
        whenever(mockHandler.postDelayed(any(), any())).thenAnswer {
            assertThat(fakePendingCapture).isNull()
            fakePendingCapture = it.getArgument(0)
            fakeDueMs = fakeClockMs + it.getArgument<Long>(1)
            true
        }
        doAnswer { fakePendingCapture = null; null }.whenever(mockHandler).removeCallbacksAndMessages(null)
        fun advanceTo(targetMs: Long) {
            while (fakePendingCapture != null && fakeDueMs <= targetMs) {
                fakeClockMs = fakeDueMs
                val fakeCallback = checkNotNull(fakePendingCapture)
                fakePendingCapture = null
                fakeCallback.run()
            }
            fakeClockMs = targetMs
        }
        val fakeRumContextProvider = SessionReplayRumContextProvider(adaptiveCaptureSchedulingEnabled = true) {
            testedListener.scheduleCapture()
        }
        val fakeQueueHandler = RecordedDataQueueHandler(
            processor = mock(),
            rumContextDataHandler = RumContextDataHandler(fakeRumContextProvider, mockTimeProvider, mockInternalLogger),
            internalLogger = mockInternalLogger,
            executorService = mock(),
            recordedDataQueue = ConcurrentLinkedQueue(),
            timeProvider = mockTimeProvider
        )
        testedListener = WindowsOnDrawListener(
            zOrderedDecorViews = fakeMockedDecorViews,
            recordedDataQueueHandler = fakeQueueHandler,
            snapshotProducer = mockSnapshotProducer,
            textAndInputPrivacy = fakeTextAndInputPrivacy,
            imagePrivacy = fakeImagePrivacy,
            miscUtils = mockMiscUtils,
            sdkCore = mockSdkCore,
            dynamicOptimizationEnabled = true,
            adaptiveCaptureSchedulingEnabled = true,
            touchPrivacyManager = mockTouchPrivacyManager,
            debouncer = Debouncer(
                handler = mockHandler,
                sdkCore = mockSdkCore,
                timeBank = RecordingTimeBank(50),
                dynamicOptimizationEnabled = true,
                adaptiveCaptureSchedulingEnabled = true
            ),
            methodCallSamplingRate = 0f,
            rumContextProvider = fakeRumContextProvider
        )
        val fakeViewId = UUID.randomUUID().toString()
        val fakeContext = mutableMapOf<String, Any?>(
            RUM_APPLICATION_ID_CONTEXT_KEY to UUID.randomUUID().toString(),
            RUM_SESSION_ID_CONTEXT_KEY to UUID.randomUUID().toString(),
            RUM_VIEW_ID_CONTEXT_KEY to fakeViewId
        )
        fakeRumContextProvider.onContextUpdate(Feature.RUM_FEATURE_NAME, fakeContext)
        advanceTo(0L)
        assertThat(fakeQueueHandler.recordedDataQueue).hasSize(1)

        // When: a draw occurs between RUM views, then the UI remains idle.
        fakeContext.remove(RUM_VIEW_ID_CONTEXT_KEY)
        fakeRumContextProvider.onContextUpdate(Feature.RUM_FEATURE_NAME, fakeContext)
        testedListener.onDraw()
        advanceTo(1_000L)

        // Then: only one failed attempt, with no polling or traversal while context is invalid.
        verify(mockMiscUtils, times(2)).resolveSystemInformation(mockContext)
        verify(mockSnapshotProducer).beginSnapshot()
        assertThat(fakePendingCapture).isNull()
        assertThat(fakeQueueHandler.recordedDataQueue).hasSize(1)

        // Restoring even the same view resumes capture from the notification alone.
        fakeContext[RUM_VIEW_ID_CONTEXT_KEY] = fakeViewId
        fakeRumContextProvider.onContextUpdate(Feature.RUM_FEATURE_NAME, fakeContext)
        advanceTo(1_000L)
        verify(mockSnapshotProducer, times(2)).beginSnapshot()
        assertThat(fakeQueueHandler.recordedDataQueue).hasSize(2)
        assertThat(fakePendingCapture).isNull()
    }

    @Test
    fun `M reject dispatched snapshot W recording stops before callback runs`() {
        // Given
        val fakePendingCallbacks = mutableListOf<Runnable>()
        whenever(mockDebouncer.debounce(any(), any())).thenAnswer {
            fakePendingCallbacks.add(it.getArgument(0))
        }
        testedListener.onDraw()

        // When
        fakeCaptureAllowed = false
        fakePendingCallbacks.single().run()

        // Then
        verifyNoInteractions(mockSnapshotProducer, mockRecordedDataQueueHandler)
    }

    @Test
    fun `M reject explicit and draw captures W recording stopped before listener cleanup`() {
        // Given
        fakeCaptureAllowed = false

        // When
        testedListener.onDraw()
        val captured = testedListener.captureNow()

        // Then
        assertThat(captured).isFalse()
        verify(mockDebouncer).cancel()
        verifyNoInteractions(mockSnapshotProducer, mockRecordedDataQueueHandler)
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
    fun `M take and add to queue without debouncing W captureNow()`() {
        // Given
        // The debouncer is free to drop a debounced frame, which would lose the requested capture.
        whenever(mockRecordedDataQueueHandler.addSnapshotItem(any<SystemInformation>()))
            .thenReturn(fakeSnapshotQueueItem)

        // When
        val captured = testedListener.captureNow()

        // Then
        assertThat(captured).isTrue()
        verify(mockRecordedDataQueueHandler).addSnapshotItem(fakeSystemInformation)
        verifyNoInteractions(mockDebouncer)
    }

    @Test
    fun `M cancel pending work W cancelPendingCapture()`() {
        // When
        testedListener.cancelPendingCapture()

        // Then
        verify(mockDebouncer).cancel()
    }

    @Test
    fun `M schedule without budget delay W scheduleCapture()`() {
        // When
        testedListener.scheduleCapture()

        // Then
        verify(mockDebouncer).debounce(any(), eq(true))
    }

    @Test
    fun `M report no capture W captureNow() { no valid RUM context }`() {
        // Given
        whenever(mockRecordedDataQueueHandler.addSnapshotItem(any<SystemInformation>()))
            .thenReturn(null)

        // When
        val captured = testedListener.captureNow()

        // Then
        assertThat(captured).isFalse()
        verifyNoInteractions(mockSnapshotProducer)
    }

    @Test
    fun `M report no capture W captureNow() { windows lost the strong reference }`() {
        // Given
        testedListener.weakReferencedDecorViews.forEach { it.clear() }

        // When
        val captured = testedListener.captureNow()

        // Then
        assertThat(captured).isFalse()
        verifyNoInteractions(mockRecordedDataQueueHandler)
        verifyNoInteractions(mockSnapshotProducer)
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
    fun `M prepend hidden embedded node W onDraw { embedded view missing from snapshot }`() {
        // Given
        val hiddenEmbeddedNode = Node(wireframes = emptyList())
        whenever(mockRecordedDataQueueHandler.addSnapshotItem(any<SystemInformation>()))
            .thenReturn(fakeSnapshotQueueItem)
        whenever(mockSnapshotProducer.finishSnapshot()).thenReturn(hiddenEmbeddedNode)
        fakeSnapshotQueueItem.pendingJobs.set(0)

        // When
        testedListener.onDraw()

        // Then
        verify(mockSnapshotProducer).beginSnapshot()
        verify(mockSnapshotProducer).finishSnapshot()
        assertThat(fakeSnapshotQueueItem.nodes)
            .containsExactlyElementsOf(listOf(hiddenEmbeddedNode) + fakeWindowsSnapshots)
    }

    @Test
    fun `M do nothing W onDraw(){ windows are empty }`() {
        // When
        testedListener = WindowsOnDrawListener(
            zOrderedDecorViews = emptyList(),
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
        whenever(mockDebouncer.debounce(any(), any())) doAnswer {
            it.getArgument<Runnable>(0).run()
            Unit
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
            zOrderedDecorViews = fakeMockedDecorViews,
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
