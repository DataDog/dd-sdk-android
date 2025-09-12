/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.monitor

import android.app.Activity
import android.app.ActivityManager
import android.os.Handler
import com.datadog.android.api.InternalLogger
import com.datadog.android.api.context.DatadogContext
import com.datadog.android.api.context.TimeInfo
import com.datadog.android.api.feature.EventWriteScope
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureScope
import com.datadog.android.api.storage.DataWriter
import com.datadog.android.core.InternalSdkCore
import com.datadog.android.core.feature.event.ThreadDump
import com.datadog.android.core.internal.net.FirstPartyHostHeaderTypeResolver
import com.datadog.android.internal.telemetry.InternalTelemetryEvent
import com.datadog.android.rum.DdRumContentProvider
import com.datadog.android.rum.ExperimentalRumApi
import com.datadog.android.rum.RumActionType
import com.datadog.android.rum.RumAttributes
import com.datadog.android.rum.RumErrorSource
import com.datadog.android.rum.RumPerformanceMetric
import com.datadog.android.rum.RumResourceKind
import com.datadog.android.rum.RumResourceMethod
import com.datadog.android.rum.RumSessionListener
import com.datadog.android.rum.RumSessionType
import com.datadog.android.rum.featureoperations.FailureReason
import com.datadog.android.rum.internal.RumErrorSourceType
import com.datadog.android.rum.internal.RumFeature
import com.datadog.android.rum.internal.debug.RumDebugListener
import com.datadog.android.rum.internal.domain.InfoProvider
import com.datadog.android.rum.internal.domain.RumContext
import com.datadog.android.rum.internal.domain.accessibility.AccessibilitySnapshotManager
import com.datadog.android.rum.internal.domain.battery.BatteryInfo
import com.datadog.android.rum.internal.domain.display.DisplayInfo
import com.datadog.android.rum.internal.domain.event.ResourceTiming
import com.datadog.android.rum.internal.domain.scope.RumApplicationScope
import com.datadog.android.rum.internal.domain.scope.RumRawEvent
import com.datadog.android.rum.internal.domain.scope.RumScopeKey
import com.datadog.android.rum.internal.domain.scope.RumSessionScope
import com.datadog.android.rum.internal.domain.scope.RumViewManagerScope
import com.datadog.android.rum.internal.domain.scope.RumViewScope
import com.datadog.android.rum.internal.domain.state.ViewUIPerformanceReport
import com.datadog.android.rum.internal.metric.SessionMetricDispatcher
import com.datadog.android.rum.internal.metric.slowframes.SlowFramesListener
import com.datadog.android.rum.internal.monitor.DatadogRumMonitor.Companion.FO_ERROR_INVALID_NAME
import com.datadog.android.rum.internal.monitor.DatadogRumMonitor.Companion.FO_ERROR_INVALID_OPERATION_KEY
import com.datadog.android.rum.internal.vitals.VitalMonitor
import com.datadog.android.rum.metric.interactiontonextview.LastInteractionIdentifier
import com.datadog.android.rum.metric.networksettled.InitialResourceIdentifier
import com.datadog.android.rum.model.ActionEvent
import com.datadog.android.rum.resource.ResourceId
import com.datadog.android.rum.utils.forge.Configurator
import com.datadog.android.rum.utils.verifyApiUsage
import com.datadog.android.rum.utils.verifyLog
import com.datadog.android.telemetry.internal.TelemetryEventHandler
import com.datadog.tools.unit.forge.aThrowable
import com.datadog.tools.unit.forge.exhaustiveAttributes
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.AdvancedForgery
import fr.xgouchet.elmyr.annotation.BoolForgery
import fr.xgouchet.elmyr.annotation.DoubleForgery
import fr.xgouchet.elmyr.annotation.FloatForgery
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.annotation.MapForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.annotation.StringForgeryType
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.same
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.Locale
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class DatadogRumMonitorTest {

    private lateinit var testedMonitor: DatadogRumMonitor

    @Mock
    lateinit var mockApplicationScope: RumApplicationScope

    @Mock
    lateinit var mockWriter: DataWriter<Any>

    @Mock
    lateinit var mockHandler: Handler

    @Mock
    lateinit var mockAccessibilitySnapshotManager: AccessibilitySnapshotManager

    @Mock
    lateinit var mockBatteryInfoProvider: InfoProvider<BatteryInfo>

    @Mock
    lateinit var mockDisplayInfoProvider: InfoProvider<DisplayInfo>

    @Mock
    lateinit var mockResolver: FirstPartyHostHeaderTypeResolver

    @Mock
    lateinit var mockCpuVitalMonitor: VitalMonitor

    @Mock
    lateinit var mockMemoryVitalMonitor: VitalMonitor

    @Mock
    lateinit var mockFrameRateVitalMonitor: VitalMonitor

    @Mock
    lateinit var mockSessionListener: RumSessionListener

    @Mock
    lateinit var mockTelemetryEventHandler: TelemetryEventHandler

    @Mock
    lateinit var mockSessionEndedMetricDispatcher: SessionMetricDispatcher

    @Mock
    lateinit var mockSdkCore: InternalSdkCore

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @Mock
    lateinit var mockExecutorService: ExecutorService

    @Mock
    lateinit var mockNetworkSettledResourceIdentifier: InitialResourceIdentifier

    @Mock
    lateinit var mockLastInteractionIdentifier: LastInteractionIdentifier

    @Mock
    lateinit var mockSlowFramesListener: SlowFramesListener

    @Mock
    lateinit var mockRumFeatureScope: FeatureScope

    @Mock
    lateinit var mockEventWriteScope: EventWriteScope

    @StringForgery(regex = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
    lateinit var fakeApplicationId: String

    lateinit var fakeAttributes: Map<String, Any?>

    @FloatForgery(min = 0f, max = 100f)
    var fakeSampleRate: Float = 0f

    @LongForgery(TIMESTAMP_MIN, TIMESTAMP_MAX)
    var fakeTimestamp: Long = 0L

    @BoolForgery
    var fakeBackgroundTrackingEnabled: Boolean = false

    @BoolForgery
    var fakeTrackFrustrations: Boolean = true

    @Forgery
    lateinit var fakeTimeInfo: TimeInfo

    @Forgery
    lateinit var fakeViewUIPerformanceReport: ViewUIPerformanceReport

    @Forgery
    lateinit var fakeDatadogContext: DatadogContext

    private var fakeRumSessionType: RumSessionType? = null

    @BeforeEach
    fun `set up`(forge: Forge) {
        whenever(mockExecutorService.execute(any<Runnable>())) doAnswer {
            it.getArgument<Runnable>(0).run()
        }
        whenever(mockExecutorService.submit(any<Callable<RumContext>>())) doAnswer {
            val rumContext = it.getArgument<Callable<RumContext>>(0).call()
            mock<Future<RumContext>>().apply { whenever(get()) doReturn rumContext }
        }

        whenever(mockSdkCore.internalLogger) doReturn mockInternalLogger
        whenever(mockSdkCore.time) doReturn fakeTimeInfo
        whenever(mockSlowFramesListener.resolveReport(any(), any(), any())) doReturn fakeViewUIPerformanceReport
        whenever(mockAccessibilitySnapshotManager.getIfChanged()) doReturn mock()

        whenever(mockSdkCore.getFeature(Feature.RUM_FEATURE_NAME)) doReturn mockRumFeatureScope

        whenever(
            mockRumFeatureScope.withWriteContext(eq(setOf(Feature.SESSION_REPLAY_FEATURE_NAME)), any())
        ) doAnswer {
            val callback = it.getArgument<(DatadogContext, EventWriteScope) -> Unit>(it.arguments.lastIndex)
            callback.invoke(fakeDatadogContext, mockEventWriteScope)
        }
        whenever(
            mockRumFeatureScope.getWriteContextSync(setOf(Feature.SESSION_REPLAY_FEATURE_NAME))
        ) doReturn (fakeDatadogContext to mockEventWriteScope)

        fakeAttributes = forge.exhaustiveAttributes()

        fakeRumSessionType = forge.aNullable { aValueFrom(RumSessionType::class.java) }

        testedMonitor = DatadogRumMonitor(
            applicationId = fakeApplicationId,
            sdkCore = mockSdkCore,
            sampleRate = fakeSampleRate,
            backgroundTrackingEnabled = fakeBackgroundTrackingEnabled,
            trackFrustrations = fakeTrackFrustrations,
            writer = mockWriter,
            handler = mockHandler,
            telemetryEventHandler = mockTelemetryEventHandler,
            sessionEndedMetricDispatcher = mockSessionEndedMetricDispatcher,
            firstPartyHostHeaderTypeResolver = mockResolver,
            cpuVitalMonitor = mockCpuVitalMonitor,
            memoryVitalMonitor = mockMemoryVitalMonitor,
            frameRateVitalMonitor = mockFrameRateVitalMonitor,
            sessionListener = mockSessionListener,
            executorService = mockExecutorService,
            initialResourceIdentifier = mockNetworkSettledResourceIdentifier,
            lastInteractionIdentifier = mockLastInteractionIdentifier,
            slowFramesListener = mockSlowFramesListener,
            rumSessionTypeOverride = fakeRumSessionType,
            accessibilitySnapshotManager = mockAccessibilitySnapshotManager,
            batteryInfoProvider = mockBatteryInfoProvider,
            displayInfoProvider = mockDisplayInfoProvider
        )
        testedMonitor.rootScope = mockApplicationScope
    }

    @Test
    fun `creates root scope`() {
        // Given
        testedMonitor = DatadogRumMonitor(
            applicationId = fakeApplicationId,
            sdkCore = mockSdkCore,
            sampleRate = fakeSampleRate,
            backgroundTrackingEnabled = fakeBackgroundTrackingEnabled,
            trackFrustrations = fakeTrackFrustrations,
            writer = mockWriter,
            handler = mockHandler,
            telemetryEventHandler = mockTelemetryEventHandler,
            sessionEndedMetricDispatcher = mockSessionEndedMetricDispatcher,
            firstPartyHostHeaderTypeResolver = mockResolver,
            cpuVitalMonitor = mockCpuVitalMonitor,
            memoryVitalMonitor = mockMemoryVitalMonitor,
            frameRateVitalMonitor = mockFrameRateVitalMonitor,
            sessionListener = mockSessionListener,
            executorService = mockExecutorService,
            initialResourceIdentifier = mockNetworkSettledResourceIdentifier,
            lastInteractionIdentifier = mockLastInteractionIdentifier,
            slowFramesListener = mockSlowFramesListener,
            rumSessionTypeOverride = fakeRumSessionType,
            accessibilitySnapshotManager = mockAccessibilitySnapshotManager,
            batteryInfoProvider = mockBatteryInfoProvider,
            displayInfoProvider = mockDisplayInfoProvider
        )

        // When
        val rootScope = testedMonitor.rootScope

        // Then
        assertThat(rootScope.sampleRate).isEqualTo(fakeSampleRate)
        assertThat(rootScope.backgroundTrackingEnabled).isEqualTo(fakeBackgroundTrackingEnabled)
    }

    @Test
    fun `M delegate event to rootScope W startView()`(
        @StringForgery(type = StringForgeryType.ASCII) key: String,
        @StringForgery name: String
    ) {
        // When
        testedMonitor.startView(key, name, fakeAttributes)

        // Then
        argumentCaptor<RumRawEvent> {
            verify(mockApplicationScope).handleEvent(
                capture(),
                same(fakeDatadogContext),
                same(mockEventWriteScope),
                same(mockWriter)
            )

            val event = firstValue as RumRawEvent.StartView
            assertThat(event.key).isEqualTo(RumScopeKey.from(key, name))
            assertThat(event.attributes).containsAllEntriesOf(fakeAttributes)
        }
        verifyNoMoreInteractions(mockWriter)
    }

    @Test
    fun `M send null for current session W getCurrentSessionId { no session started }`() {
        // Given
        val mockCallback = mock<(String?) -> Unit>()

        // When
        testedMonitor.getCurrentSessionId(mockCallback)

        // Then
        verify(mockCallback).invoke(null)
    }

    @Test
    fun `M send correct sessionId W getCurrentSessionId { session started, sampled in }`() {
        // Given
        testedMonitor = DatadogRumMonitor(
            applicationId = fakeApplicationId,
            sdkCore = mockSdkCore,
            sampleRate = 100.0f,
            backgroundTrackingEnabled = fakeBackgroundTrackingEnabled,
            trackFrustrations = fakeTrackFrustrations,
            writer = mockWriter,
            handler = mockHandler,
            telemetryEventHandler = mockTelemetryEventHandler,
            sessionEndedMetricDispatcher = mockSessionEndedMetricDispatcher,
            firstPartyHostHeaderTypeResolver = mockResolver,
            cpuVitalMonitor = mockCpuVitalMonitor,
            memoryVitalMonitor = mockMemoryVitalMonitor,
            frameRateVitalMonitor = mockFrameRateVitalMonitor,
            sessionListener = mockSessionListener,
            executorService = mockExecutorService,
            initialResourceIdentifier = mockNetworkSettledResourceIdentifier,
            lastInteractionIdentifier = mockLastInteractionIdentifier,
            slowFramesListener = mockSlowFramesListener,
            rumSessionTypeOverride = fakeRumSessionType,
            accessibilitySnapshotManager = mockAccessibilitySnapshotManager,
            batteryInfoProvider = mockBatteryInfoProvider,
            displayInfoProvider = mockDisplayInfoProvider
        )
        testedMonitor.start()
        val mockCallback = mock<(String?) -> Unit>()

        // When
        testedMonitor.getCurrentSessionId(mockCallback)

        // Then
        argumentCaptor<String> {
            verify(mockSessionListener).onSessionStarted(capture(), any())
            verify(mockCallback).invoke(firstValue)
            assertThat(firstValue).isNotNull()
        }
    }

    @Test
    fun `M send null sessionId W getCurrentSessionId { session started, sampled out }`() {
        // Given
        testedMonitor = DatadogRumMonitor(
            applicationId = fakeApplicationId,
            sdkCore = mockSdkCore,
            sampleRate = 0.0f,
            backgroundTrackingEnabled = fakeBackgroundTrackingEnabled,
            trackFrustrations = fakeTrackFrustrations,
            writer = mockWriter,
            handler = mockHandler,
            telemetryEventHandler = mockTelemetryEventHandler,
            sessionEndedMetricDispatcher = mockSessionEndedMetricDispatcher,
            firstPartyHostHeaderTypeResolver = mockResolver,
            cpuVitalMonitor = mockCpuVitalMonitor,
            memoryVitalMonitor = mockMemoryVitalMonitor,
            frameRateVitalMonitor = mockFrameRateVitalMonitor,
            sessionListener = mockSessionListener,
            executorService = mockExecutorService,
            initialResourceIdentifier = mockNetworkSettledResourceIdentifier,
            lastInteractionIdentifier = mockLastInteractionIdentifier,
            slowFramesListener = mockSlowFramesListener,
            rumSessionTypeOverride = fakeRumSessionType,
            accessibilitySnapshotManager = mockAccessibilitySnapshotManager,
            batteryInfoProvider = mockBatteryInfoProvider,
            displayInfoProvider = mockDisplayInfoProvider
        )
        testedMonitor.start()
        val mockCallback = mock<(String?) -> Unit>()

        // When
        testedMonitor.getCurrentSessionId(mockCallback)

        // Then
        verify(mockCallback).invoke(null)
    }

    @Test
    fun `M delegate event to rootScope W stopView()`(
        @StringForgery(type = StringForgeryType.ASCII) key: String
    ) {
        // When
        testedMonitor.stopView(key, fakeAttributes)

        // Then
        argumentCaptor<RumRawEvent> {
            verify(mockApplicationScope).handleEvent(
                capture(),
                same(fakeDatadogContext),
                same(mockEventWriteScope),
                same(mockWriter)
            )

            val event = firstValue as RumRawEvent.StopView
            assertThat(event.key).isEqualTo(RumScopeKey.from(key))
            assertThat(event.attributes).containsAllEntriesOf(fakeAttributes)
        }
        verifyNoMoreInteractions(mockWriter)
    }

    @Test
    fun `M delegate event to rootScope W addAction()`(
        @Forgery type: RumActionType,
        @StringForgery name: String
    ) {
        // When
        testedMonitor.addAction(type, name, fakeAttributes)

        // Then
        argumentCaptor<RumRawEvent> {
            verify(mockApplicationScope).handleEvent(
                capture(),
                same(fakeDatadogContext),
                same(mockEventWriteScope),
                same(mockWriter)
            )

            val event = firstValue as RumRawEvent.StartAction
            assertThat(event.type).isEqualTo(type)
            assertThat(event.name).isEqualTo(name)
            assertThat(event.waitForStop).isFalse
            assertThat(event.attributes).containsAllEntriesOf(fakeAttributes)
        }
        verifyNoMoreInteractions(mockWriter)
    }

    @Test
    fun `M delegate event to rootScope W startAction()`(
        @Forgery type: RumActionType,
        @StringForgery name: String
    ) {
        // When
        testedMonitor.startAction(type, name, fakeAttributes)

        // Then
        argumentCaptor<RumRawEvent> {
            verify(mockApplicationScope).handleEvent(
                capture(),
                same(fakeDatadogContext),
                same(mockEventWriteScope),
                same(mockWriter)
            )

            val event = firstValue as RumRawEvent.StartAction
            assertThat(event.type).isEqualTo(type)
            assertThat(event.name).isEqualTo(name)
            assertThat(event.waitForStop).isTrue
            assertThat(event.attributes).containsAllEntriesOf(fakeAttributes)
        }
        verifyNoMoreInteractions(mockWriter)
    }

    @Test
    fun `M delegate event to rootScope W stopAction()`(
        @Forgery type: RumActionType,
        @StringForgery name: String
    ) {
        // When
        testedMonitor.stopAction(type, name, fakeAttributes)

        // Then
        argumentCaptor<RumRawEvent> {
            verify(mockApplicationScope).handleEvent(
                capture(),
                same(fakeDatadogContext),
                same(mockEventWriteScope),
                same(mockWriter)
            )

            val event = firstValue as RumRawEvent.StopAction
            assertThat(event.type).isEqualTo(type)
            assertThat(event.name).isEqualTo(name)
            assertThat(event.attributes).containsAllEntriesOf(fakeAttributes)
        }
        verifyNoMoreInteractions(mockWriter)
    }

    @Test
    fun `M delegate event to rootScope W startResource()`(
        @StringForgery key: String,
        @Forgery method: RumResourceMethod,
        @StringForgery(regex = "http(s?)://[a-z]+\\.com/[a-z]+") url: String
    ) {
        // When
        testedMonitor.startResource(key, method, url, fakeAttributes)

        // Then
        argumentCaptor<RumRawEvent> {
            verify(mockApplicationScope).handleEvent(
                capture(),
                same(fakeDatadogContext),
                same(mockEventWriteScope),
                same(mockWriter)
            )

            val event = firstValue as RumRawEvent.StartResource
            assertThat(event.key).isEqualTo(key)
            assertThat(event.method).isEqualTo(method)
            assertThat(event.url).isEqualTo(url)
            assertThat(event.attributes).containsAllEntriesOf(fakeAttributes)
        }
        verifyNoMoreInteractions(mockWriter)
    }

    @Test
    fun `M delegate event to rootScope W stopResource()`(
        @StringForgery key: String,
        @IntForgery(200, 600) statusCode: Int,
        @LongForgery(0, 1024) size: Long,
        @Forgery kind: RumResourceKind
    ) {
        // When
        testedMonitor.stopResource(key, statusCode, size, kind, fakeAttributes)

        // Then
        argumentCaptor<RumRawEvent> {
            verify(mockApplicationScope).handleEvent(
                capture(),
                same(fakeDatadogContext),
                same(mockEventWriteScope),
                same(mockWriter)
            )

            val event = firstValue as RumRawEvent.StopResource
            assertThat(event.key).isEqualTo(key)
            assertThat(event.statusCode).isEqualTo(statusCode.toLong())
            assertThat(event.kind).isEqualTo(kind)
            assertThat(event.size).isEqualTo(size)
            assertThat(event.attributes).containsAllEntriesOf(fakeAttributes)
        }
        verifyNoMoreInteractions(mockWriter)
    }

    @Test
    fun `M delegate event to rootScope W stopResource() {without status code nor size}`(
        @StringForgery key: String,
        @Forgery kind: RumResourceKind
    ) {
        // When
        testedMonitor.stopResource(key, null, null, kind, fakeAttributes)

        // Then
        argumentCaptor<RumRawEvent> {
            verify(mockApplicationScope).handleEvent(
                capture(),
                same(fakeDatadogContext),
                same(mockEventWriteScope),
                same(mockWriter)
            )

            val event = firstValue as RumRawEvent.StopResource
            assertThat(event.key).isEqualTo(key)
            assertThat(event.statusCode).isNull()
            assertThat(event.kind).isEqualTo(kind)
            assertThat(event.size).isNull()
            assertThat(event.attributes).containsAllEntriesOf(fakeAttributes)
        }
        verifyNoMoreInteractions(mockWriter)
    }

    @Test
    fun `M delegate event to rootScope W stopResourceWithError() {throwable}`(
        @StringForgery key: String,
        @StringForgery message: String,
        @Forgery source: RumErrorSource,
        @IntForgery(200, 600) statusCode: Int,
        @Forgery throwable: Throwable
    ) {
        // When
        testedMonitor.stopResourceWithError(
            key,
            statusCode,
            message,
            source,
            throwable,
            fakeAttributes
        )

        // Then
        argumentCaptor<RumRawEvent> {
            verify(mockApplicationScope).handleEvent(
                capture(),
                same(fakeDatadogContext),
                same(mockEventWriteScope),
                same(mockWriter)
            )

            val event = firstValue as RumRawEvent.StopResourceWithError
            assertThat(event.key).isEqualTo(key)
            assertThat(event.statusCode).isEqualTo(statusCode.toLong())
            assertThat(event.message).isEqualTo(message)
            assertThat(event.source).isEqualTo(source)
            assertThat(event.throwable).isEqualTo(throwable)
            assertThat(event.attributes).containsAllEntriesOf(fakeAttributes)
        }
        verifyNoMoreInteractions(mockWriter)
    }

    @Test
    fun `M delegate event to rootScope W stopResourceWithError() {stacktrace}`(
        @StringForgery key: String,
        @StringForgery message: String,
        @Forgery source: RumErrorSource,
        @IntForgery(200, 600) statusCode: Int,
        @StringForgery(type = StringForgeryType.ASCII_EXTENDED) stackTrace: String,
        @StringForgery errorType: String
    ) {
        // When
        testedMonitor.stopResourceWithError(
            key,
            statusCode,
            message,
            source,
            stackTrace,
            errorType,
            fakeAttributes
        )

        // Then
        argumentCaptor<RumRawEvent> {
            verify(mockApplicationScope).handleEvent(
                capture(),
                same(fakeDatadogContext),
                same(mockEventWriteScope),
                same(mockWriter)
            )

            val event = firstValue as RumRawEvent.StopResourceWithStackTrace
            assertThat(event.key).isEqualTo(key)
            assertThat(event.statusCode).isEqualTo(statusCode.toLong())
            assertThat(event.message).isEqualTo(message)
            assertThat(event.source).isEqualTo(source)
            assertThat(event.stackTrace).isEqualTo(stackTrace)
            assertThat(event.errorType).isEqualTo(errorType)
            assertThat(event.attributes).containsAllEntriesOf(fakeAttributes)
        }
        verifyNoMoreInteractions(mockWriter)
    }

    @Test
    fun `M delegate event to rootScope W stopResourceWithError() {without status code}`(
        @StringForgery key: String,
        @StringForgery message: String,
        @Forgery source: RumErrorSource,
        @Forgery throwable: Throwable
    ) {
        // When
        testedMonitor.stopResourceWithError(key, null, message, source, throwable, fakeAttributes)

        // Then
        argumentCaptor<RumRawEvent> {
            verify(mockApplicationScope).handleEvent(
                capture(),
                same(fakeDatadogContext),
                same(mockEventWriteScope),
                same(mockWriter)
            )

            val event = firstValue as RumRawEvent.StopResourceWithError
            assertThat(event.key).isEqualTo(key)
            assertThat(event.statusCode).isNull()
            assertThat(event.message).isEqualTo(message)
            assertThat(event.source).isEqualTo(source)
            assertThat(event.throwable).isEqualTo(throwable)
            assertThat(event.attributes).containsAllEntriesOf(fakeAttributes)
        }
        verifyNoMoreInteractions(mockWriter)
    }

    @Test
    fun `M delegate event to rootScope W startResource()`(
        @Forgery key: ResourceId,
        @Forgery method: RumResourceMethod,
        @StringForgery(regex = "http(s?)://[a-z]+\\.com/[a-z]+") url: String
    ) {
        // When
        testedMonitor.startResource(key, method, url, fakeAttributes)

        // Then
        argumentCaptor<RumRawEvent> {
            verify(mockApplicationScope).handleEvent(
                capture(),
                same(fakeDatadogContext),
                same(mockEventWriteScope),
                same(mockWriter)
            )

            val event = firstValue as RumRawEvent.StartResource
            assertThat(event.key).isEqualTo(key)
            assertThat(event.method).isEqualTo(method)
            assertThat(event.url).isEqualTo(url)
            assertThat(event.attributes).containsAllEntriesOf(fakeAttributes)
        }
        verifyNoMoreInteractions(mockWriter)
    }

    @Test
    fun `M delegate event to rootScope W stopResource()`(
        @Forgery key: ResourceId,
        @IntForgery(200, 600) statusCode: Int,
        @LongForgery(0, 1024) size: Long,
        @Forgery kind: RumResourceKind
    ) {
        // When
        testedMonitor.stopResource(key, statusCode, size, kind, fakeAttributes)

        // Then
        argumentCaptor<RumRawEvent> {
            verify(mockApplicationScope).handleEvent(
                capture(),
                same(fakeDatadogContext),
                same(mockEventWriteScope),
                same(mockWriter)
            )

            val event = firstValue as RumRawEvent.StopResource
            assertThat(event.key).isEqualTo(key)
            assertThat(event.statusCode).isEqualTo(statusCode.toLong())
            assertThat(event.kind).isEqualTo(kind)
            assertThat(event.size).isEqualTo(size)
            assertThat(event.attributes).containsAllEntriesOf(fakeAttributes)
        }
        verifyNoMoreInteractions(mockWriter)
    }

    @Test
    fun `M delegate event to rootScope W stopResource() {without status code nor size}`(
        @Forgery key: ResourceId,
        @Forgery kind: RumResourceKind
    ) {
        // When
        testedMonitor.stopResource(key, null, null, kind, fakeAttributes)

        // Then
        argumentCaptor<RumRawEvent> {
            verify(mockApplicationScope).handleEvent(
                capture(),
                same(fakeDatadogContext),
                same(mockEventWriteScope),
                same(mockWriter)
            )

            val event = firstValue as RumRawEvent.StopResource
            assertThat(event.key).isEqualTo(key)
            assertThat(event.statusCode).isNull()
            assertThat(event.kind).isEqualTo(kind)
            assertThat(event.size).isNull()
            assertThat(event.attributes).containsAllEntriesOf(fakeAttributes)
        }
        verifyNoMoreInteractions(mockWriter)
    }

    @Test
    fun `M delegate event to rootScope W stopResourceWithError() {throwable}`(
        @Forgery key: ResourceId,
        @StringForgery message: String,
        @Forgery source: RumErrorSource,
        @IntForgery(200, 600) statusCode: Int,
        @Forgery throwable: Throwable
    ) {
        // When
        testedMonitor.stopResourceWithError(
            key,
            statusCode,
            message,
            source,
            throwable,
            fakeAttributes
        )

        // Then
        argumentCaptor<RumRawEvent> {
            verify(mockApplicationScope).handleEvent(
                capture(),
                same(fakeDatadogContext),
                same(mockEventWriteScope),
                same(mockWriter)
            )

            val event = firstValue as RumRawEvent.StopResourceWithError
            assertThat(event.key).isEqualTo(key)
            assertThat(event.statusCode).isEqualTo(statusCode.toLong())
            assertThat(event.message).isEqualTo(message)
            assertThat(event.source).isEqualTo(source)
            assertThat(event.throwable).isEqualTo(throwable)
            assertThat(event.attributes).containsAllEntriesOf(fakeAttributes)
        }
        verifyNoMoreInteractions(mockWriter)
    }

    @Test
    fun `M delegate event to rootScope W stopResourceWithError() {stacktrace}`(
        @Forgery key: ResourceId,
        @StringForgery message: String,
        @Forgery source: RumErrorSource,
        @IntForgery(200, 600) statusCode: Int,
        @StringForgery(type = StringForgeryType.ASCII_EXTENDED) stackTrace: String,
        @StringForgery errorType: String
    ) {
        // When
        testedMonitor.stopResourceWithError(
            key,
            statusCode,
            message,
            source,
            stackTrace,
            errorType,
            fakeAttributes
        )

        // Then
        argumentCaptor<RumRawEvent> {
            verify(mockApplicationScope).handleEvent(
                capture(),
                same(fakeDatadogContext),
                same(mockEventWriteScope),
                same(mockWriter)
            )

            val event = firstValue as RumRawEvent.StopResourceWithStackTrace
            assertThat(event.key).isEqualTo(key)
            assertThat(event.statusCode).isEqualTo(statusCode.toLong())
            assertThat(event.message).isEqualTo(message)
            assertThat(event.source).isEqualTo(source)
            assertThat(event.stackTrace).isEqualTo(stackTrace)
            assertThat(event.errorType).isEqualTo(errorType)
            assertThat(event.attributes).containsAllEntriesOf(fakeAttributes)
        }
        verifyNoMoreInteractions(mockWriter)
    }

    @Test
    fun `M delegate event to rootScope W stopResourceWithError() {without status code}`(
        @Forgery key: ResourceId,
        @StringForgery message: String,
        @Forgery source: RumErrorSource,
        @Forgery throwable: Throwable
    ) {
        // When
        testedMonitor.stopResourceWithError(key, null, message, source, throwable, fakeAttributes)

        // Then
        argumentCaptor<RumRawEvent> {
            verify(mockApplicationScope).handleEvent(
                capture(),
                same(fakeDatadogContext),
                same(mockEventWriteScope),
                same(mockWriter)
            )

            val event = firstValue as RumRawEvent.StopResourceWithError
            assertThat(event.key).isEqualTo(key)
            assertThat(event.statusCode).isNull()
            assertThat(event.message).isEqualTo(message)
            assertThat(event.source).isEqualTo(source)
            assertThat(event.throwable).isEqualTo(throwable)
            assertThat(event.attributes).containsAllEntriesOf(fakeAttributes)
        }
        verifyNoMoreInteractions(mockWriter)
    }

    @Test
    fun `M delegate event to rootScope W addError`(
        @StringForgery message: String,
        @Forgery source: RumErrorSource,
        @Forgery throwable: Throwable
    ) {
        // When
        testedMonitor.addError(message, source, throwable, fakeAttributes)

        // Then
        argumentCaptor<RumRawEvent> {
            verify(mockApplicationScope).handleEvent(
                capture(),
                same(fakeDatadogContext),
                same(mockEventWriteScope),
                same(mockWriter)
            )

            val event = firstValue as RumRawEvent.AddError
            assertThat(event.message).isEqualTo(message)
            assertThat(event.source).isEqualTo(source)
            assertThat(event.throwable).isEqualTo(throwable)
            assertThat(event.stacktrace).isNull()
            assertThat(event.isFatal).isFalse
            assertThat(event.sourceType).isEqualTo(RumErrorSourceType.ANDROID)
            assertThat(event.timeSinceAppStartNs).isNull()
            assertThat(event.attributes).containsAllEntriesOf(fakeAttributes)
        }
        verifyNoMoreInteractions(mockWriter)
    }

    @Test
    fun `M delegate event to rootScope W addErrorWithStacktrace`(
        @StringForgery message: String,
        @Forgery source: RumErrorSource,
        @StringForgery stacktrace: String
    ) {
        // When
        testedMonitor.addErrorWithStacktrace(message, source, stacktrace, fakeAttributes)

        // Then
        argumentCaptor<RumRawEvent> {
            verify(mockApplicationScope).handleEvent(
                capture(),
                same(fakeDatadogContext),
                same(mockEventWriteScope),
                same(mockWriter)
            )

            val event = firstValue as RumRawEvent.AddError
            assertThat(event.message).isEqualTo(message)
            assertThat(event.source).isEqualTo(source)
            assertThat(event.throwable).isNull()
            assertThat(event.stacktrace).isEqualTo(stacktrace)
            assertThat(event.isFatal).isFalse
            assertThat(event.sourceType).isEqualTo(RumErrorSourceType.ANDROID)
            assertThat(event.timeSinceAppStartNs).isNull()
            assertThat(event.attributes).containsAllEntriesOf(fakeAttributes)
        }
        verifyNoMoreInteractions(mockWriter)
    }

    @Test
    fun `M delegate event to rootScope W waitForResourceTiming()`(
        @StringForgery key: String
    ) {
        // When
        testedMonitor.waitForResourceTiming(key)

        // Then
        argumentCaptor<RumRawEvent> {
            verify(mockApplicationScope).handleEvent(
                capture(),
                same(fakeDatadogContext),
                same(mockEventWriteScope),
                same(mockWriter)
            )

            val event = firstValue
            check(event is RumRawEvent.WaitForResourceTiming)
            assertThat(event.key).isEqualTo(key)
        }
        verifyNoMoreInteractions(mockWriter)
    }

    @Test
    fun `M delegate event to rootScope W addResourceTiming()`(
        @StringForgery key: String,
        @Forgery timing: ResourceTiming
    ) {
        // When
        testedMonitor.addResourceTiming(key, timing)

        // Then
        argumentCaptor<RumRawEvent> {
            verify(mockApplicationScope).handleEvent(
                capture(),
                same(fakeDatadogContext),
                same(mockEventWriteScope),
                same(mockWriter)
            )

            val event = firstValue
            check(event is RumRawEvent.AddResourceTiming)
            assertThat(event.key).isEqualTo(key)
            assertThat(event.timing).isEqualTo(timing)
        }
        verifyNoMoreInteractions(mockWriter)
    }

    @Test
    fun `M delegate event to rootScope W addTiming()`(
        @StringForgery name: String
    ) {
        // When
        testedMonitor.addTiming(name)

        // Then
        argumentCaptor<RumRawEvent> {
            verify(mockApplicationScope).handleEvent(
                capture(),
                same(fakeDatadogContext),
                same(mockEventWriteScope),
                same(mockWriter)
            )

            val event = firstValue
            check(event is RumRawEvent.AddCustomTiming)
            assertThat(event.name).isEqualTo(name)
        }
        verifyNoMoreInteractions(mockWriter)
    }

    @Test
    @OptIn(ExperimentalRumApi::class)
    fun `M delegate event to rootScope W addViewLoadingTime()`(
        @BoolForgery fakeOverwrite: Boolean
    ) {
        testedMonitor.addViewLoadingTime(fakeOverwrite)

        argumentCaptor<RumRawEvent> {
            verify(mockApplicationScope).handleEvent(
                capture(),
                same(fakeDatadogContext),
                same(mockEventWriteScope),
                same(mockWriter)
            )
            val event = firstValue
            check(event is RumRawEvent.AddViewLoadingTime)
        }
        verifyNoMoreInteractions(mockWriter)
    }

    @Test
    fun `M delegate event to rootScope W addViewAttributes()`(
        @MapForgery(
            key = AdvancedForgery(string = [StringForgery(StringForgeryType.ALPHABETICAL)]),
            value = AdvancedForgery(string = [StringForgery()])
        ) fakeAttributes: Map<String, String>
    ) {
        testedMonitor.addViewAttributes(fakeAttributes)

        argumentCaptor<RumRawEvent> {
            verify(mockApplicationScope).handleEvent(
                capture(),
                same(fakeDatadogContext),
                same(mockEventWriteScope),
                same(mockWriter)
            )
            val event = firstValue
            check(event is RumRawEvent.AddViewAttributes)
            assertThat(event.attributes).isSameAs(fakeAttributes)
        }
        verifyNoMoreInteractions(mockWriter)
    }

    @Test
    fun `M delegate event to rootScope W removeViewAttributes()`(
        @StringForgery fakeAttributes: List<String>
    ) {
        testedMonitor.removeViewAttributes(fakeAttributes)

        argumentCaptor<RumRawEvent> {
            verify(mockApplicationScope).handleEvent(
                capture(),
                same(fakeDatadogContext),
                same(mockEventWriteScope),
                same(mockWriter)
            )
            val event = firstValue
            check(event is RumRawEvent.RemoveViewAttributes)
            assertThat(event.attributes).isSameAs(fakeAttributes)
        }
        verifyNoMoreInteractions(mockWriter)
    }

    @Test
    fun `M delegate event to rootScope on current thread W addCrash()`(
        @StringForgery message: String,
        @Forgery source: RumErrorSource,
        @Forgery throwable: Throwable,
        forge: Forge
    ) {
        // Given
        whenever(
            mockRumFeatureScope.getWriteContextSync(setOf(Feature.SESSION_REPLAY_FEATURE_NAME))
        ) doReturn (fakeDatadogContext to mockEventWriteScope)
        testedMonitor.drainExecutorService()
        val now = System.nanoTime()
        val appStartTimeNs = forge.aLong(min = 0L, max = now)
        whenever(mockSdkCore.appStartTimeNs) doReturn appStartTimeNs

        // When
        testedMonitor.addCrash(message, source, throwable, threads = emptyList())

        // Then
        argumentCaptor<RumRawEvent> {
            verify(mockApplicationScope).handleEvent(
                capture(),
                same(fakeDatadogContext),
                same(mockEventWriteScope),
                same(mockWriter)
            )

            val event = firstValue as RumRawEvent.AddError
            assertThat(event.message).isEqualTo(message)
            assertThat(event.source).isEqualTo(source)
            assertThat(event.throwable).isEqualTo(throwable)
            assertThat(event.isFatal).isTrue
            assertThat(event.sourceType).isEqualTo(RumErrorSourceType.ANDROID)
            assertThat(event.timeSinceAppStartNs).isEqualTo(event.eventTime.nanoTime - appStartTimeNs)
            assertThat(event.attributes).isEmpty()
        }
        verifyNoMoreInteractions(mockWriter)
    }

    @Test
    fun `M log warning W addCrash() { cannot get write context }`(
        @StringForgery message: String,
        @Forgery source: RumErrorSource,
        @Forgery throwable: Throwable
    ) {
        // Given
        whenever(mockRumFeatureScope.getWriteContextSync(setOf(Feature.SESSION_REPLAY_FEATURE_NAME))) doReturn null

        // When
        testedMonitor.addCrash(message, source, throwable, threads = emptyList())

        // Then
        mockInternalLogger.verifyLog(
            InternalLogger.Level.WARN,
            InternalLogger.Target.USER,
            DatadogRumMonitor.CANNOT_WRITE_CRASH_WRITE_CONTEXT_IS_NOT_AVAILABLE
        )
        verifyNoInteractions(mockApplicationScope, mockWriter)
    }

    @Test
    fun `M delegate event to rootScope W resetSession()`() {
        // When
        testedMonitor.resetSession()

        // Then
        argumentCaptor<RumRawEvent> {
            verify(mockApplicationScope).handleEvent(
                capture(),
                same(fakeDatadogContext),
                same(mockEventWriteScope),
                same(mockWriter)
            )

            val event = firstValue
            check(event is RumRawEvent.ResetSession)
        }
        verifyNoMoreInteractions(mockWriter)
    }

    @Test
    fun `M delegate event to rootScope W start() { application is in foreground }`() {
        // Given
        DdRumContentProvider.processImportance =
            ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND

        // When
        testedMonitor.start()

        // Then
        argumentCaptor<RumRawEvent> {
            verify(mockApplicationScope).handleEvent(
                capture(),
                same(fakeDatadogContext),
                same(mockEventWriteScope),
                same(mockWriter)
            )

            assertThat(firstValue).isInstanceOf(RumRawEvent.SdkInit::class.java)
            with(firstValue as RumRawEvent.SdkInit) {
                assertThat(isAppInForeground).isTrue()
            }
        }
        verifyNoMoreInteractions(mockWriter)
    }

    @Test
    fun `M delegate event to rootScope W start() { application is not in foreground }`(
        forge: Forge
    ) {
        // Given
        DdRumContentProvider.processImportance = forge.anElementFrom(
            ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND_SERVICE,
            ActivityManager.RunningAppProcessInfo.IMPORTANCE_TOP_SLEEPING,
            @Suppress("DEPRECATION")
            ActivityManager.RunningAppProcessInfo.IMPORTANCE_TOP_SLEEPING_PRE_28,
            ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE,
            ActivityManager.RunningAppProcessInfo.IMPORTANCE_PERCEPTIBLE,
            ActivityManager.RunningAppProcessInfo.IMPORTANCE_PERCEPTIBLE_PRE_26,
            ActivityManager.RunningAppProcessInfo.IMPORTANCE_CANT_SAVE_STATE,
            ActivityManager.RunningAppProcessInfo.IMPORTANCE_SERVICE,
            ActivityManager.RunningAppProcessInfo.IMPORTANCE_CACHED,
            ActivityManager.RunningAppProcessInfo.IMPORTANCE_GONE
        )

        // When
        testedMonitor.start()

        // Then
        argumentCaptor<RumRawEvent> {
            verify(mockApplicationScope).handleEvent(
                capture(),
                same(fakeDatadogContext),
                same(mockEventWriteScope),
                same(mockWriter)
            )

            assertThat(firstValue).isInstanceOf(RumRawEvent.SdkInit::class.java)
            with(firstValue as RumRawEvent.SdkInit) {
                assertThat(isAppInForeground).isFalse()
            }
        }
        verifyNoMoreInteractions(mockWriter)
    }

    @Test
    fun `M delegate event to rootScope with timestamp W startView()`(
        @StringForgery(type = StringForgeryType.ASCII) key: String,
        @StringForgery name: String
    ) {
        val attributes = fakeAttributes + (RumAttributes.INTERNAL_TIMESTAMP to fakeTimestamp)

        testedMonitor.startView(key, name, attributes)

        argumentCaptor<RumRawEvent> {
            verify(mockApplicationScope).handleEvent(
                capture(),
                same(fakeDatadogContext),
                same(mockEventWriteScope),
                same(mockWriter)
            )

            val event = firstValue as RumRawEvent.StartView
            assertThat(event.eventTime.timestamp).isEqualTo(fakeTimestamp)
            assertThat(event.key).isEqualTo(RumScopeKey.from(key, name))
            assertThat(event.attributes).containsAllEntriesOf(fakeAttributes)
        }
        verifyNoMoreInteractions(mockWriter)
    }

    @Test
    fun `M delegate event to rootScope with timestamp W stopView()`(
        @StringForgery(type = StringForgeryType.ASCII) key: String
    ) {
        val attributes = fakeAttributes + (RumAttributes.INTERNAL_TIMESTAMP to fakeTimestamp)

        testedMonitor.stopView(key, attributes)

        argumentCaptor<RumRawEvent> {
            verify(mockApplicationScope).handleEvent(
                capture(),
                same(fakeDatadogContext),
                same(mockEventWriteScope),
                same(mockWriter)
            )

            val event = firstValue as RumRawEvent.StopView
            assertThat(event.eventTime.timestamp).isEqualTo(fakeTimestamp)
            assertThat(event.key).isEqualTo(RumScopeKey.from(key))
            assertThat(event.attributes).containsAllEntriesOf(fakeAttributes)
        }
        verifyNoMoreInteractions(mockWriter)
    }

    @Test
    fun `M delegate event to rootScope with timestamp W addAction()`(
        @Forgery type: RumActionType,
        @StringForgery name: String
    ) {
        val attributes = fakeAttributes + (RumAttributes.INTERNAL_TIMESTAMP to fakeTimestamp)

        testedMonitor.addAction(type, name, attributes)

        argumentCaptor<RumRawEvent> {
            verify(mockApplicationScope).handleEvent(
                capture(),
                same(fakeDatadogContext),
                same(mockEventWriteScope),
                same(mockWriter)
            )

            val event = firstValue as RumRawEvent.StartAction
            assertThat(event.eventTime.timestamp).isEqualTo(fakeTimestamp)
            assertThat(event.type).isEqualTo(type)
            assertThat(event.name).isEqualTo(name)
            assertThat(event.waitForStop).isFalse
            assertThat(event.attributes).containsAllEntriesOf(fakeAttributes)
        }
        verifyNoMoreInteractions(mockWriter)
    }

    @Test
    fun `M delegate event to rootScope with timestamp W startAction()`(
        @Forgery type: RumActionType,
        @StringForgery name: String
    ) {
        val attributes = fakeAttributes + (RumAttributes.INTERNAL_TIMESTAMP to fakeTimestamp)

        testedMonitor.startAction(type, name, attributes)

        argumentCaptor<RumRawEvent> {
            verify(mockApplicationScope).handleEvent(
                capture(),
                same(fakeDatadogContext),
                same(mockEventWriteScope),
                same(mockWriter)
            )

            val event = firstValue as RumRawEvent.StartAction
            assertThat(event.eventTime.timestamp).isEqualTo(fakeTimestamp)
            assertThat(event.type).isEqualTo(type)
            assertThat(event.name).isEqualTo(name)
            assertThat(event.waitForStop).isTrue
            assertThat(event.attributes).containsAllEntriesOf(fakeAttributes)
        }
        verifyNoMoreInteractions(mockWriter)
    }

    @Test
    fun `M delegate event to rootScope with timestamp W stopAction()`(
        @Forgery type: RumActionType,
        @StringForgery name: String
    ) {
        val attributes = fakeAttributes + (RumAttributes.INTERNAL_TIMESTAMP to fakeTimestamp)

        testedMonitor.stopAction(type, name, attributes)

        argumentCaptor<RumRawEvent> {
            verify(mockApplicationScope).handleEvent(
                capture(),
                same(fakeDatadogContext),
                same(mockEventWriteScope),
                same(mockWriter)
            )

            val event = firstValue as RumRawEvent.StopAction
            assertThat(event.eventTime.timestamp).isEqualTo(fakeTimestamp)
            assertThat(event.type).isEqualTo(type)
            assertThat(event.name).isEqualTo(name)
            assertThat(event.attributes).containsAllEntriesOf(fakeAttributes)
        }
        verifyNoMoreInteractions(mockWriter)
    }

    @Test
    fun `M delegate event to rootScope with timestamp W startResource()`(
        @StringForgery key: String,
        @Forgery method: RumResourceMethod,
        @StringForgery(regex = "http(s?)://[a-z]+\\.com/[a-z]+") url: String
    ) {
        val attributes = fakeAttributes + (RumAttributes.INTERNAL_TIMESTAMP to fakeTimestamp)

        testedMonitor.startResource(key, method, url, attributes)

        argumentCaptor<RumRawEvent> {
            verify(mockApplicationScope).handleEvent(
                capture(),
                same(fakeDatadogContext),
                same(mockEventWriteScope),
                same(mockWriter)
            )

            val event = firstValue as RumRawEvent.StartResource
            assertThat(event.eventTime.timestamp).isEqualTo(fakeTimestamp)
            assertThat(event.key).isEqualTo(key)
            assertThat(event.method).isEqualTo(method)
            assertThat(event.url).isEqualTo(url)
            assertThat(event.attributes).containsAllEntriesOf(fakeAttributes)
        }
        verifyNoMoreInteractions(mockWriter)
    }

    @Test
    fun `M delegate event to rootScope with timestamp W stopResource()`(
        @StringForgery key: String,
        @IntForgery(200, 600) statusCode: Int,
        @LongForgery(0, 1024) size: Long,
        @Forgery kind: RumResourceKind
    ) {
        val attributes = fakeAttributes + (RumAttributes.INTERNAL_TIMESTAMP to fakeTimestamp)

        testedMonitor.stopResource(key, statusCode, size, kind, attributes)

        argumentCaptor<RumRawEvent> {
            verify(mockApplicationScope).handleEvent(
                capture(),
                same(fakeDatadogContext),
                same(mockEventWriteScope),
                same(mockWriter)
            )

            val event = firstValue as RumRawEvent.StopResource
            assertThat(event.eventTime.timestamp).isEqualTo(fakeTimestamp)
            assertThat(event.key).isEqualTo(key)
            assertThat(event.statusCode).isEqualTo(statusCode.toLong())
            assertThat(event.kind).isEqualTo(kind)
            assertThat(event.size).isEqualTo(size)
            assertThat(event.attributes).containsAllEntriesOf(fakeAttributes)
        }
        verifyNoMoreInteractions(mockWriter)
    }

    @Test
    fun `M delegate event to rootScope with all threads W addError`(
        @StringForgery message: String,
        @Forgery source: RumErrorSource,
        @Forgery throwable: Throwable,
        @Forgery allThreads: List<ThreadDump>
    ) {
        val attributes = fakeAttributes + (RumAttributes.INTERNAL_ALL_THREADS to allThreads)

        testedMonitor.addError(message, source, throwable, attributes)

        argumentCaptor<RumRawEvent> {
            verify(mockApplicationScope).handleEvent(
                capture(),
                same(fakeDatadogContext),
                same(mockEventWriteScope),
                same(mockWriter)
            )

            val event = firstValue as RumRawEvent.AddError
            assertThat(event.message).isEqualTo(message)
            assertThat(event.source).isEqualTo(source)
            assertThat(event.throwable).isEqualTo(throwable)
            assertThat(event.stacktrace).isNull()
            assertThat(event.isFatal).isFalse
            assertThat(event.sourceType).isEqualTo(RumErrorSourceType.ANDROID)
            assertThat(event.threads).isEqualTo(allThreads)
            assertThat(event.timeSinceAppStartNs).isNull()
            assertThat(event.attributes).containsExactlyEntriesOf(fakeAttributes)
        }
        verifyNoMoreInteractions(mockWriter)
    }

    @Test
    fun `M delegate event to rootScope with timestamp W addError`(
        @StringForgery message: String,
        @Forgery source: RumErrorSource,
        @Forgery throwable: Throwable
    ) {
        val attributes = fakeAttributes + (RumAttributes.INTERNAL_TIMESTAMP to fakeTimestamp)

        testedMonitor.addError(message, source, throwable, attributes)

        argumentCaptor<RumRawEvent> {
            verify(mockApplicationScope).handleEvent(
                capture(),
                same(fakeDatadogContext),
                same(mockEventWriteScope),
                same(mockWriter)
            )

            val event = firstValue as RumRawEvent.AddError
            assertThat(event.eventTime.timestamp).isEqualTo(fakeTimestamp)
            assertThat(event.message).isEqualTo(message)
            assertThat(event.source).isEqualTo(source)
            assertThat(event.throwable).isEqualTo(throwable)
            assertThat(event.stacktrace).isNull()
            assertThat(event.isFatal).isFalse
            assertThat(event.sourceType).isEqualTo(RumErrorSourceType.ANDROID)
            assertThat(event.timeSinceAppStartNs).isNull()
            assertThat(event.attributes).containsAllEntriesOf(fakeAttributes)
        }
        verifyNoMoreInteractions(mockWriter)
    }

    @Test
    fun `M delegate event to rootScope with timestamp W addErrorWithStacktrace`(
        @StringForgery message: String,
        @Forgery source: RumErrorSource,
        @StringForgery stacktrace: String
    ) {
        val attributes = fakeAttributes + (RumAttributes.INTERNAL_TIMESTAMP to fakeTimestamp)

        testedMonitor.addErrorWithStacktrace(message, source, stacktrace, attributes)

        argumentCaptor<RumRawEvent> {
            verify(mockApplicationScope).handleEvent(
                capture(),
                same(fakeDatadogContext),
                same(mockEventWriteScope),
                same(mockWriter)
            )

            val event = firstValue as RumRawEvent.AddError
            assertThat(event.eventTime.timestamp).isEqualTo(fakeTimestamp)
            assertThat(event.message).isEqualTo(message)
            assertThat(event.source).isEqualTo(source)
            assertThat(event.throwable).isNull()
            assertThat(event.stacktrace).isEqualTo(stacktrace)
            assertThat(event.isFatal).isFalse
            assertThat(event.sourceType).isEqualTo(RumErrorSourceType.ANDROID)
            assertThat(event.timeSinceAppStartNs).isNull()
            assertThat(event.attributes).containsAllEntriesOf(fakeAttributes)
        }
        verifyNoMoreInteractions(mockWriter)
    }

    @Test
    fun `M delegate event to rootScope with error type W addError`(
        @StringForgery message: String,
        @Forgery source: RumErrorSource,
        @Forgery throwable: Throwable,
        @StringForgery errorType: String
    ) {
        val fakeAttributesWithErrorType =
            fakeAttributes + (RumAttributes.INTERNAL_ERROR_TYPE to errorType)
        testedMonitor.addError(message, source, throwable, fakeAttributesWithErrorType)

        argumentCaptor<RumRawEvent> {
            verify(mockApplicationScope).handleEvent(
                capture(),
                same(fakeDatadogContext),
                same(mockEventWriteScope),
                same(mockWriter)
            )

            val event = firstValue as RumRawEvent.AddError
            assertThat(event.message).isEqualTo(message)
            assertThat(event.source).isEqualTo(source)
            assertThat(event.throwable).isEqualTo(throwable)
            assertThat(event.stacktrace).isNull()
            assertThat(event.isFatal).isFalse
            assertThat(event.type).isEqualTo(errorType)
            assertThat(event.sourceType).isEqualTo(RumErrorSourceType.ANDROID)
            assertThat(event.timeSinceAppStartNs).isNull()
            assertThat(event.attributes).containsAllEntriesOf(fakeAttributesWithErrorType)
        }
        verifyNoMoreInteractions(mockWriter)
    }

    @Test
    fun `M delegate event to rootScope W error type addErrorWithStacktrace`(
        @StringForgery message: String,
        @Forgery source: RumErrorSource,
        @StringForgery stacktrace: String,
        @StringForgery errorType: String
    ) {
        val fakeAttributesWithErrorType =
            fakeAttributes + (RumAttributes.INTERNAL_ERROR_TYPE to errorType)
        testedMonitor.addErrorWithStacktrace(
            message,
            source,
            stacktrace,
            fakeAttributesWithErrorType
        )

        argumentCaptor<RumRawEvent> {
            verify(mockApplicationScope).handleEvent(
                capture(),
                same(fakeDatadogContext),
                same(mockEventWriteScope),
                same(mockWriter)
            )

            val event = firstValue as RumRawEvent.AddError
            assertThat(event.message).isEqualTo(message)
            assertThat(event.source).isEqualTo(source)
            assertThat(event.throwable).isNull()
            assertThat(event.stacktrace).isEqualTo(stacktrace)
            assertThat(event.isFatal).isFalse
            assertThat(event.attributes).containsAllEntriesOf(fakeAttributesWithErrorType)
            assertThat(event.type).isEqualTo(errorType)
            assertThat(event.timeSinceAppStartNs).isNull()
            assertThat(event.sourceType).isEqualTo(RumErrorSourceType.ANDROID)
        }
        verifyNoMoreInteractions(mockWriter)
    }

    @RepeatedTest(10)
    fun `M delegate event to rootScope W error source type addErrorWithStacktrace`(
        @StringForgery message: String,
        @Forgery source: RumErrorSource,
        @StringForgery stacktrace: String,
        forge: Forge
    ) {
        val nonSupportedValue = forge.aString()
        val sourceTypeExpectations = mapOf(
            "android" to RumErrorSourceType.ANDROID,
            "browser" to RumErrorSourceType.BROWSER,
            "react-native" to RumErrorSourceType.REACT_NATIVE,
            "flutter" to RumErrorSourceType.FLUTTER,
            "ndk" to RumErrorSourceType.NDK,
            "ndk+il2cpp" to RumErrorSourceType.NDK_IL2CPP,
            nonSupportedValue to RumErrorSourceType.ANDROID,
            null to RumErrorSourceType.ANDROID
        )

        val sourceType = forge.anElementFrom(sourceTypeExpectations.keys)

        val fakeAttributesWithErrorSourceType =
            fakeAttributes + (RumAttributes.INTERNAL_ERROR_SOURCE_TYPE to sourceType)
        testedMonitor.addErrorWithStacktrace(
            message,
            source,
            stacktrace,
            fakeAttributesWithErrorSourceType
        )

        argumentCaptor<RumRawEvent> {
            verify(mockApplicationScope).handleEvent(
                capture(),
                same(fakeDatadogContext),
                same(mockEventWriteScope),
                same(mockWriter)
            )

            val event = firstValue as RumRawEvent.AddError
            assertThat(event.message).isEqualTo(message)
            assertThat(event.source).isEqualTo(source)
            assertThat(event.throwable).isNull()
            assertThat(event.stacktrace).isEqualTo(stacktrace)
            assertThat(event.isFatal).isFalse
            assertThat(event.sourceType).isEqualTo(sourceTypeExpectations[sourceType])
            assertThat(event.timeSinceAppStartNs).isNull()
            assertThat(event.attributes).containsAllEntriesOf(fakeAttributesWithErrorSourceType)
        }
        verifyNoMoreInteractions(mockWriter)
    }

    @Test
    fun `M delegate event to rootScope W addLongTask`(
        @LongForgery duration: Long,
        @StringForgery target: String
    ) {
        testedMonitor.addLongTask(duration, target)

        argumentCaptor<RumRawEvent> {
            verify(mockApplicationScope).handleEvent(
                capture(),
                same(fakeDatadogContext),
                same(mockEventWriteScope),
                same(mockWriter)
            )

            val event = firstValue as RumRawEvent.AddLongTask
            assertThat(event.durationNs).isEqualTo(duration)
        }
        verifyNoMoreInteractions(mockWriter)
    }

    @Test
    fun `M delegate event to rootScope W eventSent {action}`(
        @StringForgery viewId: String,
        @IntForgery(0) frustrationCount: Int,
        @LongForgery(0) eventEndTimestamp: Long,
        @Forgery actionType: ActionEvent.ActionEventActionType
    ) {
        testedMonitor.eventSent(viewId, StorageEvent.Action(frustrationCount, actionType, eventEndTimestamp))

        argumentCaptor<RumRawEvent> {
            verify(mockApplicationScope).handleEvent(
                capture(),
                same(fakeDatadogContext),
                same(mockEventWriteScope),
                same(mockWriter)
            )

            val event = firstValue as RumRawEvent.ActionSent
            assertThat(event.viewId).isEqualTo(viewId)
            assertThat(event.frustrationCount).isEqualTo(frustrationCount)
            assertThat(event.type).isEqualTo(actionType)
            assertThat(event.eventEndTimestampInNanos).isEqualTo(eventEndTimestamp)
        }
        verifyNoMoreInteractions(mockWriter)
    }

    @Test
    fun `M delegate event to rootScope W eventSent {resource}`(
        @StringForgery viewId: String,
        @StringForgery resourceId: String,
        @LongForgery(0) resourceEndTimestampInNanos: Long
    ) {
        testedMonitor.eventSent(viewId, StorageEvent.Resource(resourceId, resourceEndTimestampInNanos))

        argumentCaptor<RumRawEvent> {
            verify(mockApplicationScope).handleEvent(
                capture(),
                same(fakeDatadogContext),
                same(mockEventWriteScope),
                same(mockWriter)
            )

            val event = firstValue as RumRawEvent.ResourceSent
            assertThat(event.viewId).isEqualTo(viewId)
            assertThat(event.resourceId).isEqualTo(resourceId)
            assertThat(event.resourceEndTimestampInNanos).isEqualTo(resourceEndTimestampInNanos)
        }
        verifyNoMoreInteractions(mockWriter)
    }

    @Test
    fun `M delegate event to rootScope W eventSent {error}`(
        @StringForgery viewId: String
    ) {
        testedMonitor.eventSent(viewId, StorageEvent.Error())

        argumentCaptor<RumRawEvent> {
            verify(mockApplicationScope).handleEvent(
                capture(),
                same(fakeDatadogContext),
                same(mockEventWriteScope),
                same(mockWriter)
            )

            val event = firstValue as RumRawEvent.ErrorSent
            assertThat(event.viewId).isEqualTo(viewId)
        }
        verifyNoMoreInteractions(mockWriter)
    }

    @Test
    fun `M delegate event to rootScope W eventSent {longTask}`(
        @StringForgery viewId: String
    ) {
        testedMonitor.eventSent(viewId, StorageEvent.LongTask)

        argumentCaptor<RumRawEvent> {
            verify(mockApplicationScope).handleEvent(
                capture(),
                same(fakeDatadogContext),
                same(mockEventWriteScope),
                same(mockWriter)
            )

            val event = firstValue as RumRawEvent.LongTaskSent
            assertThat(event.viewId).isEqualTo(viewId)
            assertThat(event.isFrozenFrame).isFalse()
        }
        verifyNoMoreInteractions(mockWriter)
    }

    @Test
    fun `M delegate event to rootScope W eventSent {frozenFrame}`(
        @StringForgery viewId: String
    ) {
        testedMonitor.eventSent(viewId, StorageEvent.FrozenFrame)

        argumentCaptor<RumRawEvent> {
            verify(mockApplicationScope).handleEvent(
                capture(),
                same(fakeDatadogContext),
                same(mockEventWriteScope),
                same(mockWriter)
            )

            val event = firstValue as RumRawEvent.LongTaskSent
            assertThat(event.viewId).isEqualTo(viewId)
            assertThat(event.isFrozenFrame).isTrue()
        }
        verifyNoMoreInteractions(mockWriter)
    }

    @Test
    fun `M delegate event to rootScope W eventDropped {action}`(
        @StringForgery viewId: String,
        @IntForgery(0) frustrationCount: Int,
        @LongForgery(0) eventEndTimestamp: Long,
        @Forgery actionType: ActionEvent.ActionEventActionType
    ) {
        testedMonitor.eventDropped(viewId, StorageEvent.Action(frustrationCount, actionType, eventEndTimestamp))

        argumentCaptor<RumRawEvent> {
            verify(mockApplicationScope).handleEvent(
                capture(),
                same(fakeDatadogContext),
                same(mockEventWriteScope),
                same(mockWriter)
            )

            val event = firstValue as RumRawEvent.ActionDropped
            assertThat(event.viewId).isEqualTo(viewId)
        }
        verifyNoMoreInteractions(mockWriter)
    }

    @Test
    fun `M delegate event to rootScope W eventDropped {resource}`(
        @StringForgery viewId: String,
        @StringForgery resourceId: String,
        @LongForgery(0) resourceEndTimestampInNanos: Long
    ) {
        testedMonitor.eventDropped(viewId, StorageEvent.Resource(resourceId, resourceEndTimestampInNanos))

        argumentCaptor<RumRawEvent> {
            verify(mockApplicationScope).handleEvent(
                capture(),
                same(fakeDatadogContext),
                same(mockEventWriteScope),
                same(mockWriter)
            )

            val event = firstValue as RumRawEvent.ResourceDropped
            assertThat(event.viewId).isEqualTo(viewId)
            assertThat(event.resourceId).isEqualTo(resourceId)
        }
        verifyNoMoreInteractions(mockWriter)
    }

    @Test
    fun `M delegate event to rootScope W eventDropped {error}`(
        @StringForgery viewId: String
    ) {
        testedMonitor.eventDropped(viewId, StorageEvent.Error())

        argumentCaptor<RumRawEvent> {
            verify(mockApplicationScope).handleEvent(
                capture(),
                same(fakeDatadogContext),
                same(mockEventWriteScope),
                same(mockWriter)
            )

            val event = firstValue as RumRawEvent.ErrorDropped
            assertThat(event.viewId).isEqualTo(viewId)
        }
        verifyNoMoreInteractions(mockWriter)
    }

    @Test
    fun `M delegate event to rootScope W eventDropped {longTask}`(
        @StringForgery viewId: String
    ) {
        testedMonitor.eventDropped(viewId, StorageEvent.LongTask)

        argumentCaptor<RumRawEvent> {
            verify(mockApplicationScope).handleEvent(
                capture(),
                same(fakeDatadogContext),
                same(mockEventWriteScope),
                same(mockWriter)
            )

            val event = firstValue as RumRawEvent.LongTaskDropped
            assertThat(event.viewId).isEqualTo(viewId)
            assertThat(event.isFrozenFrame).isFalse()
        }
        verifyNoMoreInteractions(mockWriter)
    }

    @Test
    fun `M delegate event to rootScope W eventDropped {frozenFrame}`(
        @StringForgery viewId: String
    ) {
        testedMonitor.eventDropped(viewId, StorageEvent.FrozenFrame)

        argumentCaptor<RumRawEvent> {
            verify(mockApplicationScope).handleEvent(
                capture(),
                same(fakeDatadogContext),
                same(mockEventWriteScope),
                same(mockWriter)
            )

            val event = firstValue as RumRawEvent.LongTaskDropped
            assertThat(event.viewId).isEqualTo(viewId)
            assertThat(event.isFrozenFrame).isTrue()
        }
        verifyNoMoreInteractions(mockWriter)
    }

    @Test
    fun `M delegate event to rootScope W addFeatureFlagEvaluation`(
        @StringForgery name: String,
        @StringForgery value: String
    ) {
        testedMonitor.addFeatureFlagEvaluation(name, value)

        argumentCaptor<RumRawEvent> {
            verify(mockApplicationScope).handleEvent(
                capture(),
                same(fakeDatadogContext),
                same(mockEventWriteScope),
                same(mockWriter)
            )

            val event = firstValue as RumRawEvent.AddFeatureFlagEvaluation
            assertThat(event.name).isEqualTo(name)
            assertThat(event.value).isEqualTo(value)
        }
        verifyNoMoreInteractions(mockWriter)
    }

    @Test
    fun `M delegate event to rootScope W addFeatureFlagEvaluations`(
        @StringForgery name: String,
        @StringForgery value: String
    ) {
        val batch = mapOf(name to value)
        testedMonitor.addFeatureFlagEvaluations(batch)

        argumentCaptor<RumRawEvent> {
            verify(mockApplicationScope).handleEvent(
                capture(),
                same(fakeDatadogContext),
                same(mockEventWriteScope),
                same(mockWriter)
            )

            val event = firstValue as RumRawEvent.AddFeatureFlagEvaluations
            assertThat(event.featureFlags).isSameAs(batch)
        }
        verifyNoMoreInteractions(mockWriter)
    }

    @Test
    fun `sends keep alive event to rootScope regularly`() {
        argumentCaptor<Runnable> {
            inOrder(mockApplicationScope, mockWriter, mockHandler) {
                verify(mockHandler).postDelayed(capture(), eq(DatadogRumMonitor.KEEP_ALIVE_MS))
                verifyNoInteractions(mockApplicationScope)
                val runnable = firstValue
                runnable.run()
                verify(mockHandler).removeCallbacks(same(runnable))
                verify(mockApplicationScope).handleEvent(
                    argThat { this is RumRawEvent.KeepAlive },
                    same(fakeDatadogContext),
                    same(mockEventWriteScope),
                    same(mockWriter)
                )
                verify(mockHandler).postDelayed(same(runnable), eq(DatadogRumMonitor.KEEP_ALIVE_MS))
                verify(mockApplicationScope).activeSession
                verify(mockApplicationScope).getRumContext()
                verifyNoMoreInteractions()
            }
        }
    }

    @Test
    fun `delays keep alive runnable on other event`() {
        val mockEvent: RumRawEvent = mock()
        val runnable = testedMonitor.keepAliveRunnable

        testedMonitor.handleEvent(mockEvent)

        argumentCaptor<Runnable> {
            inOrder(mockApplicationScope, mockWriter, mockHandler) {
                verify(mockHandler).removeCallbacks(same(runnable))
                verify(mockApplicationScope).handleEvent(
                    same(mockEvent),
                    same(fakeDatadogContext),
                    same(mockEventWriteScope),
                    same(mockWriter)
                )
                verify(mockHandler).postDelayed(same(runnable), eq(DatadogRumMonitor.KEEP_ALIVE_MS))
                verify(mockApplicationScope).activeSession
                verify(mockApplicationScope).getRumContext()
                verifyNoMoreInteractions()
            }
        }
    }

    @Test
    fun `removes callback from handler on stopKeepAliveCallback`() {
        // When
        testedMonitor.stopKeepAliveCallback()

        // Then
        // initial post
        verify(mockHandler).postDelayed(any(), any())
        verify(mockHandler).removeCallbacks(same(testedMonitor.keepAliveRunnable))
        verifyNoMoreInteractions(mockHandler, mockWriter, mockApplicationScope)
    }

    @Test
    fun `M drain the executor queue W drainExecutorService()`(forge: Forge) {
        // Given
        val blockingQueue = LinkedBlockingQueue<Runnable>(forge.aList { mock() })
        val mockExecutor: ScheduledThreadPoolExecutor = mock()
        whenever(mockExecutor.queue).thenReturn(blockingQueue)
        testedMonitor = DatadogRumMonitor(
            applicationId = fakeApplicationId,
            sdkCore = mockSdkCore,
            sampleRate = fakeSampleRate,
            backgroundTrackingEnabled = fakeBackgroundTrackingEnabled,
            trackFrustrations = fakeTrackFrustrations,
            writer = mockWriter,
            handler = mockHandler,
            telemetryEventHandler = mockTelemetryEventHandler,
            sessionEndedMetricDispatcher = mockSessionEndedMetricDispatcher,
            firstPartyHostHeaderTypeResolver = mockResolver,
            cpuVitalMonitor = mockCpuVitalMonitor,
            memoryVitalMonitor = mockMemoryVitalMonitor,
            frameRateVitalMonitor = mockFrameRateVitalMonitor,
            sessionListener = mockSessionListener,
            executorService = mockExecutor,
            initialResourceIdentifier = mockNetworkSettledResourceIdentifier,
            lastInteractionIdentifier = mockLastInteractionIdentifier,
            slowFramesListener = mockSlowFramesListener,
            rumSessionTypeOverride = fakeRumSessionType,
            accessibilitySnapshotManager = mockAccessibilitySnapshotManager,
            batteryInfoProvider = mockBatteryInfoProvider,
            displayInfoProvider = mockDisplayInfoProvider
        )

        // When
        testedMonitor.drainExecutorService()

        // Then
        blockingQueue.forEach {
            verify(it).run()
        }
    }

    @Test
    fun `M shutdown with wait the persistence executor W drainExecutorService()`() {
        // Given
        val mockExecutorService: ExecutorService = mock()
        testedMonitor = DatadogRumMonitor(
            applicationId = fakeApplicationId,
            sdkCore = mockSdkCore,
            sampleRate = fakeSampleRate,
            backgroundTrackingEnabled = fakeBackgroundTrackingEnabled,
            trackFrustrations = fakeTrackFrustrations,
            writer = mockWriter,
            handler = mockHandler,
            telemetryEventHandler = mockTelemetryEventHandler,
            sessionEndedMetricDispatcher = mockSessionEndedMetricDispatcher,
            firstPartyHostHeaderTypeResolver = mockResolver,
            cpuVitalMonitor = mockCpuVitalMonitor,
            memoryVitalMonitor = mockMemoryVitalMonitor,
            frameRateVitalMonitor = mockFrameRateVitalMonitor,
            sessionListener = mockSessionListener,
            executorService = mockExecutorService,
            initialResourceIdentifier = mockNetworkSettledResourceIdentifier,
            lastInteractionIdentifier = mockLastInteractionIdentifier,
            slowFramesListener = mockSlowFramesListener,
            rumSessionTypeOverride = fakeRumSessionType,
            accessibilitySnapshotManager = mockAccessibilitySnapshotManager,
            batteryInfoProvider = mockBatteryInfoProvider,
            displayInfoProvider = mockDisplayInfoProvider
        )

        // When
        testedMonitor.drainExecutorService()

        // Then
        inOrder(mockExecutorService) {
            verify(mockExecutorService).shutdown()
            verify(mockExecutorService).awaitTermination(10, TimeUnit.SECONDS)
        }
    }

    @Test
    fun `M not schedule any task W executor service shutdown()`() {
        // Given
        val mockExecutorService: ExecutorService = mock()
        testedMonitor = DatadogRumMonitor(
            applicationId = fakeApplicationId,
            sdkCore = mockSdkCore,
            sampleRate = fakeSampleRate,
            backgroundTrackingEnabled = fakeBackgroundTrackingEnabled,
            trackFrustrations = fakeTrackFrustrations,
            writer = mockWriter,
            handler = mockHandler,
            telemetryEventHandler = mockTelemetryEventHandler,
            sessionEndedMetricDispatcher = mockSessionEndedMetricDispatcher,
            firstPartyHostHeaderTypeResolver = mockResolver,
            cpuVitalMonitor = mockCpuVitalMonitor,
            memoryVitalMonitor = mockMemoryVitalMonitor,
            frameRateVitalMonitor = mockFrameRateVitalMonitor,
            sessionListener = mockSessionListener,
            executorService = mockExecutorService,
            initialResourceIdentifier = mockNetworkSettledResourceIdentifier,
            lastInteractionIdentifier = mockLastInteractionIdentifier,
            slowFramesListener = mockSlowFramesListener,
            accessibilitySnapshotManager = mockAccessibilitySnapshotManager,
            batteryInfoProvider = mockBatteryInfoProvider,
            displayInfoProvider = mockDisplayInfoProvider,
            rumSessionTypeOverride = null
        )
        whenever(mockExecutorService.isShutdown).thenReturn(true)

        // When
        testedMonitor.handleEvent(mock())

        // Then
        verify(mockExecutorService, never()).execute(any())
    }

    @Test
    fun `M set debug listener W setDebugListener()`() {
        // Given
        val listener = mock<RumDebugListener>()

        // When
        testedMonitor.setDebugListener(listener)

        // Then
        assertThat(testedMonitor.debugListener).isNotNull
    }

    @Test
    fun `M notify debug listener with active RUM views W notifyDebugListenerWithState()`(
        forge: Forge
    ) {
        // Given
        val mockRumApplicationScope = mock<RumApplicationScope>()
        testedMonitor.rootScope = mockRumApplicationScope
        val mockSessionScope = mock<RumSessionScope>()
        val mockViewManagerScope = mock<RumViewManagerScope>()

        val listener = mock<RumDebugListener>()
        testedMonitor.debugListener = listener

        val viewScopes = forge.aList {
            mock<RumViewScope>().apply {
                whenever(getRumContext()) doReturn
                    RumContext(viewName = forge.aNullable { forge.anAlphaNumericalString() })

                whenever(isActive()) doReturn true
            }
        }
        val expectedViewNames = viewScopes.mapNotNull { it.getRumContext().viewName }
        whenever(mockRumApplicationScope.activeSession) doReturn mockSessionScope
        whenever(mockSessionScope.childScope) doReturn mockViewManagerScope
        whenever(mockViewManagerScope.childrenScopes)
            .thenReturn(viewScopes.toMutableList())

        // When
        testedMonitor.notifyDebugListenerWithState()

        // Then
        verify(listener).onReceiveRumActiveViews(expectedViewNames)
    }

    @Test
    fun `M notify debug listener with empty list W notifyDebugListenerWithState() {inactive}`(
        forge: Forge
    ) {
        // Given
        val mockRumApplicationScope = mock<RumApplicationScope>()
        testedMonitor.rootScope = mockRumApplicationScope
        val mockSessionScope = mock<RumSessionScope>()
        val mockViewManagerScope = mock<RumViewManagerScope>()

        val listener = mock<RumDebugListener>()
        testedMonitor.debugListener = listener
        val viewScopes = forge.aList {
            mock<RumViewScope>().apply {
                whenever(getRumContext()) doReturn
                    RumContext(viewName = forge.aNullable { forge.anAlphaNumericalString() })

                whenever(isActive()) doReturn false
            }
        }
        val expectedViewNames = emptyList<String>()
        whenever(mockRumApplicationScope.activeSession) doReturn mockSessionScope
        whenever(mockSessionScope.childScope) doReturn mockViewManagerScope
        whenever(mockViewManagerScope.childrenScopes)
            .thenReturn(viewScopes.toMutableList())

        // When
        testedMonitor.notifyDebugListenerWithState()

        // Then
        verify(listener).onReceiveRumActiveViews(expectedViewNames)
    }

    @Test
    fun `M not notify debug listener W notifyDebugListenerWithState(){no session scope}`() {
        // Given
        val mockRumApplicationScope = mock<RumApplicationScope>()
        testedMonitor.rootScope = mockRumApplicationScope

        val listener = mock<RumDebugListener>()
        testedMonitor.debugListener = listener

        whenever(mockRumApplicationScope.activeSession) doReturn null

        // When
        testedMonitor.notifyDebugListenerWithState()

        // Then
        verifyNoInteractions(listener)
    }

    @Test
    fun `M handle telemetry event W sendTelemetryEvent()`(@Forgery fakeInternalTelemetryEvent: InternalTelemetryEvent) {
        // When
        testedMonitor.sendTelemetryEvent(fakeInternalTelemetryEvent)

        // Then
        argumentCaptor<RumRawEvent.TelemetryEventWrapper> {
            verify(mockTelemetryEventHandler).handleEvent(
                capture(),
                eq(mockWriter)
            )
            assertThat(lastValue.event).isEqualTo(fakeInternalTelemetryEvent)
        }
    }

    @Test
    fun `M call enableJankStatsTracking on RUM feature W enableJankStatsTracking`() {
        // Given
        val mockActivity = mock<Activity>()
        val mockRumScope = mock<FeatureScope>()
        whenever(mockSdkCore.getFeature(Feature.RUM_FEATURE_NAME)) doReturn mockRumScope
        val mockRumFeature = mock<RumFeature>()
        whenever(mockRumScope.unwrap<RumFeature>()) doReturn mockRumFeature

        // When
        testedMonitor.enableJankStatsTracking(mockActivity)

        // Then
        verify(mockRumFeature).enableJankStatsTracking(mockActivity)
    }

    @Test
    fun `M call sessionEndedMetricDispatcher W addSessionReplaySkippedFrame`(
        @IntForgery(min = 0, max = 100) count: Int,
        @StringForgery(type = StringForgeryType.ASCII) key: String,
        @StringForgery name: String
    ) {
        val attributes = fakeAttributes + (RumAttributes.INTERNAL_TIMESTAMP to fakeTimestamp)

        testedMonitor.startView(key, name, attributes)
        // Given
        testedMonitor = DatadogRumMonitor(
            applicationId = fakeApplicationId,
            sdkCore = mockSdkCore,
            sampleRate = 100.0f,
            backgroundTrackingEnabled = fakeBackgroundTrackingEnabled,
            trackFrustrations = fakeTrackFrustrations,
            writer = mockWriter,
            handler = mockHandler,
            telemetryEventHandler = mockTelemetryEventHandler,
            sessionEndedMetricDispatcher = mockSessionEndedMetricDispatcher,
            firstPartyHostHeaderTypeResolver = mockResolver,
            cpuVitalMonitor = mockCpuVitalMonitor,
            memoryVitalMonitor = mockMemoryVitalMonitor,
            frameRateVitalMonitor = mockFrameRateVitalMonitor,
            sessionListener = mockSessionListener,
            executorService = mockExecutorService,
            initialResourceIdentifier = mockNetworkSettledResourceIdentifier,
            lastInteractionIdentifier = mockLastInteractionIdentifier,
            slowFramesListener = mockSlowFramesListener,
            rumSessionTypeOverride = fakeRumSessionType,
            accessibilitySnapshotManager = mockAccessibilitySnapshotManager,
            batteryInfoProvider = mockBatteryInfoProvider,
            displayInfoProvider = mockDisplayInfoProvider
        )
        testedMonitor.startView(key, name, attributes)
        // When
        repeat(count) {
            testedMonitor.addSessionReplaySkippedFrame()
        }

        // Then
        verify(mockSessionEndedMetricDispatcher, times(count)).onSessionReplaySkippedFrameTracked(any())
    }

    @Test
    fun `M delegate event to rootScope W sendWebViewEvent()`() {
        // When
        testedMonitor.sendWebViewEvent()

        // Then
        verify(mockApplicationScope).handleEvent(
            argThat { this is RumRawEvent.WebViewEvent },
            same(fakeDatadogContext),
            same(mockEventWriteScope),
            same(mockWriter)
        )
        verifyNoMoreInteractions(mockWriter)
    }

    @Test
    fun `M handle performance metric update W updatePerformanceMetric()`(
        forge: Forge
    ) {
        // Given
        val metric = forge.aValueFrom(RumPerformanceMetric::class.java)
        val value = forge.aDouble()

        // When
        testedMonitor.updatePerformanceMetric(metric, value)

        // Then
        argumentCaptor<RumRawEvent.UpdatePerformanceMetric> {
            verify(mockApplicationScope).handleEvent(
                capture(),
                same(fakeDatadogContext),
                same(mockEventWriteScope),
                same(mockWriter)
            )
            assertThat(lastValue.metric).isEqualTo(metric)
            assertThat(lastValue.value).isEqualTo(value)
        }
    }

    @Test
    fun `M handle external refresh rate update W updateExternalRefreshRate()`(
        forge: Forge
    ) {
        // Given
        val frameTimeSeconds = forge.aDouble(min = 0.001, max = 1.0)

        // When
        testedMonitor.updateExternalRefreshRate(frameTimeSeconds)
        Thread.sleep(PROCESSING_DELAY)

        // Then
        argumentCaptor<RumRawEvent.UpdateExternalRefreshRate> {
            verify(mockApplicationScope).handleEvent(
                capture(),
                any(),
                any<EventWriteScope>(),
                eq(mockWriter)
            )
            assertThat(lastValue.frameTimeSeconds).isEqualTo(frameTimeSeconds)
        }
    }

    @Test
    fun `M delegate internal view attributes W setInternalViewAttribute()`(
        forge: Forge
    ) {
        // Given
        val key = forge.aString()
        val value = forge.anInt()

        // When
        testedMonitor.setInternalViewAttribute(key, value)

        // Then
        argumentCaptor<RumRawEvent.SetInternalViewAttribute> {
            verify(mockApplicationScope).handleEvent(
                capture(),
                same(fakeDatadogContext),
                same(mockEventWriteScope),
                same(mockWriter)
            )
            assertThat(lastValue.key).isEqualTo(key)
            assertThat(lastValue.value).isEqualTo(value)
        }
    }

    @Test
    fun `M handle synthetics test attributes W setSyntheticsAttribute()`(
        @StringForgery fakeTestId: String,
        @StringForgery fakeResultId: String
    ) {
        // When
        testedMonitor.setSyntheticsAttribute(fakeTestId, fakeResultId)

        // Then
        argumentCaptor<RumRawEvent.SetSyntheticsTestAttribute> {
            verify(mockApplicationScope).handleEvent(
                capture(),
                same(fakeDatadogContext),
                same(mockEventWriteScope),
                same(mockWriter)
            )
            assertThat(lastValue.testId).isEqualTo(fakeTestId)
            assertThat(lastValue.resultId).isEqualTo(fakeResultId)
        }
    }

    @Test
    fun `M allow only one thread inside rootScope#handleEvent at the time W handleEvent()`(
        forge: Forge
    ) {
        // Given
        var isMethodOccupied = false
        val mockRootScope = mock<RumApplicationScope>().apply {
            whenever(handleEvent(any(), any(), any(), any())) doAnswer {
                if (isMethodOccupied) {
                    throw IllegalStateException(
                        "Only one thread should" +
                            " be allowed to enter rootScope at the time."
                    )
                }
                isMethodOccupied = true
                Thread.sleep(100)
                isMethodOccupied = false
                null
            }
        }
        testedMonitor.rootScope = mockRootScope
        // this is another executor, to imitate a bunch of external concurrent
        // calls to DatadogRumMonitor
        val executor = Executors.newFixedThreadPool(10)
        val futures = mutableListOf<Future<*>>()

        // When
        repeat(10) {
            futures += executor.submit {
                // we are not going to generate all set of the events, only AddError + fatal
                // which has a special handling + few simple others
                val event = forge.anElementFrom(
                    RumRawEvent.AddError(
                        message = forge.anAlphaNumericalString(),
                        source = forge.aValueFrom(RumErrorSource::class.java),
                        isFatal = true,
                        throwable = forge.aThrowable(),
                        stacktrace = forge.anAlphaNumericalString(),
                        threads = emptyList(),
                        attributes = emptyMap()
                    ),
                    RumRawEvent.StartAction(
                        type = forge.aValueFrom(RumActionType::class.java),
                        name = forge.anAlphaNumericalString(),
                        waitForStop = forge.aBool(),
                        attributes = emptyMap()
                    ),
                    RumRawEvent.StartView(
                        key = forge.getForgery(),
                        attributes = emptyMap()
                    )
                )
                testedMonitor.handleEvent(event)
            }
        }

        // Then
        assertDoesNotThrow {
            futures.forEach {
                // none of these should throw
                it.get()
            }
        }
    }

    @Test
    fun `M return map with attribute W addAttribute() + getAttributes()`(
        @StringForgery key: String,
        @StringForgery value: String
    ) {
        // Given
        testedMonitor.addAttribute(key, value)

        // When
        val result = testedMonitor.getAttributes()

        // Then
        assertThat(result).containsEntry(key, value)
    }

    @Test
    fun `M return map with updated attribute W addAttribute() twice + getAttributes()`(
        @StringForgery key: String,
        @StringForgery value: String,
        @DoubleForgery value2: Double
    ) {
        // Given
        testedMonitor.addAttribute(key, value)

        // When
        testedMonitor.addAttribute(key, value2)
        val result = testedMonitor.getAttributes()

        // Then
        assertThat(result).containsEntry(key, value2)
    }

    @Test
    fun `M return empty map W addAttribute() + addAttribute() {null value} + getAttributes()`(
        @StringForgery key: String,
        @StringForgery value: String
    ) {
        // Given
        testedMonitor.addAttribute(key, value)

        // When
        testedMonitor.addAttribute(key, null)
        val result = testedMonitor.getAttributes()

        // Then
        assertThat(result).isEmpty()
    }

    @Test
    fun `M return empty map W addAttribute() + removeAttribtue() + getAttributes()`(
        @StringForgery key: String,
        @StringForgery value: String
    ) {
        // Given
        testedMonitor.addAttribute(key, value)

        // When
        testedMonitor.removeAttribute(key)
        val result = testedMonitor.getAttributes()

        // Then
        assertThat(result).isEmpty()
    }

    @Test
    fun `M return empty map W addAttribute()++ + clearAttributes() + getAttributes()`(
        forge: Forge
    ) {
        // Given
        forge.exhaustiveAttributes().forEach { (k, v) ->
            testedMonitor.addAttribute(k, v)
        }

        // When
        testedMonitor.clearAttributes()
        val result = testedMonitor.getAttributes()

        // Then
        assertThat(result).isEmpty()
    }

    @Test
    fun `M enable RUM debugging W debug = true`() {
        // Given
        val mockRumScope = mock<FeatureScope>()
        whenever(mockSdkCore.getFeature(Feature.RUM_FEATURE_NAME)) doReturn mockRumScope
        val mockRumFeature = mock<RumFeature>()
        whenever(mockRumScope.unwrap<RumFeature>()) doReturn mockRumFeature

        // When
        testedMonitor.debug = true

        // Then
        verify(mockRumFeature).enableDebugging(testedMonitor)
    }

    @Test
    fun `M disable RUM debugging W debug = false`() {
        // Given
        val mockRumScope = mock<FeatureScope>()
        whenever(mockSdkCore.getFeature(Feature.RUM_FEATURE_NAME)) doReturn mockRumScope
        val mockRumFeature = mock<RumFeature>()
        whenever(mockRumScope.unwrap<RumFeature>()) doReturn mockRumFeature
        testedMonitor.debug = true

        // When
        testedMonitor.debug = false

        // Then
        verify(mockRumFeature).disableDebugging()
    }

    @Test
    fun `M log warn message W debug = true() { no RUM feature registered }`() {
        whenever(mockSdkCore.getFeature(Feature.RUM_FEATURE_NAME)) doReturn null

        // When
        testedMonitor.debug = true

        // Then
        mockInternalLogger.verifyLog(
            InternalLogger.Level.WARN,
            InternalLogger.Target.USER,
            DatadogRumMonitor.RUM_DEBUG_RUM_NOT_ENABLED_WARNING
        )
    }

    // region update feature context

    @Test
    fun `M update feature context W handleEvent() { active view exists }`(
        @Forgery fakeRumEvent: RumRawEvent,
        @Forgery fakeRumContext: RumContext
    ) {
        // Given
        val mockApplicationScope = mock<RumApplicationScope>()
        val mockSessionScope = mock<RumSessionScope>()
        val mockViewScope = mock<RumViewScope>()
        whenever(mockViewScope.getRumContext()) doReturn fakeRumContext
        whenever(mockSessionScope.activeView) doReturn mockViewScope
        whenever(mockApplicationScope.activeSession) doReturn mockSessionScope
        testedMonitor.rootScope = mockApplicationScope

        // When
        testedMonitor.handleEvent(fakeRumEvent)

        // Then
        argumentCaptor<(MutableMap<String, Any?>) -> Unit> {
            verify(mockSdkCore).updateFeatureContext(eq(Feature.RUM_FEATURE_NAME), any(), capture())
            val acc = mutableMapOf<String, Any?>()
            firstValue.invoke(acc)
            assertThat(acc).isEqualTo(fakeRumContext.toMap())
        }
    }

    @Test
    fun `M update feature context W handleEvent() { no active view, but active session }`(
        @Forgery fakeRumEvent: RumRawEvent,
        @Forgery fakeRumContext: RumContext
    ) {
        // Given
        val mockApplicationScope = mock<RumApplicationScope>()
        val mockSessionScope = mock<RumSessionScope>()
        whenever(mockSessionScope.activeView) doReturn null
        whenever(mockSessionScope.getRumContext()) doReturn fakeRumContext
        whenever(mockApplicationScope.activeSession) doReturn mockSessionScope
        testedMonitor.rootScope = mockApplicationScope

        // When
        testedMonitor.handleEvent(fakeRumEvent)

        // Then
        argumentCaptor<(MutableMap<String, Any?>) -> Unit> {
            verify(mockSdkCore).updateFeatureContext(eq(Feature.RUM_FEATURE_NAME), any(), capture())
            val acc = mutableMapOf<String, Any?>()
            firstValue.invoke(acc)
            assertThat(acc).isEqualTo(fakeRumContext.toMap())
        }
    }

    @Test
    fun `M update feature context W handleEvent() { no active session }`(
        @Forgery fakeRumEvent: RumRawEvent,
        @Forgery fakeRumContext: RumContext
    ) {
        // Given
        val mockApplicationScope = mock<RumApplicationScope>()
        whenever(mockApplicationScope.getRumContext()) doReturn fakeRumContext
        whenever(mockApplicationScope.activeSession) doReturn null
        testedMonitor.rootScope = mockApplicationScope

        // When
        testedMonitor.handleEvent(fakeRumEvent)

        // Then
        argumentCaptor<(MutableMap<String, Any?>) -> Unit> {
            verify(mockSdkCore).updateFeatureContext(eq(Feature.RUM_FEATURE_NAME), any(), capture())
            val acc = mutableMapOf<String, Any?>()
            firstValue.invoke(acc)
            assertThat(acc).isEqualTo(fakeRumContext.toMap())
        }
    }

    @Test
    fun `M not update feature context W handleEvent() { event processing failed }`(
        @Forgery fakeRumEvent: RumRawEvent
    ) {
        // Given
        val mockFeatureScope = mock<FeatureScope>()
        whenever(mockFeatureScope.getWriteContextSync(setOf(Feature.SESSION_REPLAY_FEATURE_NAME))) doReturn null
        whenever(mockSdkCore.getFeature(Feature.RUM_FEATURE_NAME)) doReturn mockFeatureScope
        whenever(mockExecutorService.submit(any<Callable<RumContext>>())) doAnswer {
            mock<Future<RumContext>>().apply { whenever(get()) doReturn null }
        }

        // When
        testedMonitor.handleEvent(fakeRumEvent)

        // Then
        verify(mockSdkCore, never()).updateFeatureContext(eq(Feature.RUM_FEATURE_NAME), any(), any())
    }

    // endregion

    @OptIn(ExperimentalRumApi::class)
    @Test
    fun `M produce StartFeatureOperation event W startFeatureOperation`(
        @StringForgery key: String,
        @StringForgery name: String,
        forge: Forge
    ) {
        val operationKey = forge.aNullable { key }
        val attributes = fakeAttributes + (RumAttributes.INTERNAL_TIMESTAMP to fakeTimestamp)

        assertMethodCallProducesValidEvent<RumRawEvent.StartFeatureOperation>(
            whenCalled = {
                testedMonitor.startFeatureOperation(name, operationKey, attributes)
            },
            then = { event ->
                assertThat(event.name).isEqualTo(name)
                assertThat(event.operationKey).isEqualTo(operationKey)
                assertThat(event.eventTime.timestamp).isEqualTo(fakeTimestamp)
                assertThat(event.attributes).containsAllEntriesOf(attributes)
            }
        )
    }

    @OptIn(ExperimentalRumApi::class)
    @Test
    fun `M produce StopFeatureOperation event W succeedFeatureOperation { successful }`(
        @StringForgery key: String,
        @StringForgery name: String,
        forge: Forge
    ) {
        val operationKey = forge.aNullable { key }
        val attributes = fakeAttributes + (RumAttributes.INTERNAL_TIMESTAMP to fakeTimestamp)

        assertMethodCallProducesValidEvent<RumRawEvent.StopFeatureOperation>(
            whenCalled = {
                testedMonitor.succeedFeatureOperation(name, operationKey, attributes)
            },
            then = { event ->
                assertThat(event.name).isEqualTo(name)
                assertThat(event.operationKey).isEqualTo(operationKey)
                assertThat(event.eventTime.timestamp).isEqualTo(fakeTimestamp)
                assertThat(event.attributes).containsAllEntriesOf(attributes)
                assertThat(event.failureReason).isNull()
            }
        )
    }

    @OptIn(ExperimentalRumApi::class)
    @Test
    fun `M produce StopFeatureOperation event W failFeatureOperation { failed }`(
        @StringForgery key: String,
        @StringForgery name: String,
        forge: Forge
    ) {
        val operationKey = forge.aNullable { key }
        val failureReason = forge.aValueFrom(FailureReason::class.java)
        val attributes = fakeAttributes + (RumAttributes.INTERNAL_TIMESTAMP to fakeTimestamp)

        assertMethodCallProducesValidEvent<RumRawEvent.StopFeatureOperation>(
            whenCalled = {
                testedMonitor.failFeatureOperation(name, operationKey, failureReason, attributes)
            },
            then = { event ->
                assertThat(event.name).isEqualTo(name)
                assertThat(event.operationKey).isEqualTo(operationKey)
                assertThat(event.eventTime.timestamp).isEqualTo(fakeTimestamp)
                assertThat(event.attributes).containsAllEntriesOf(attributes)
                assertThat(event.failureReason).isEqualTo(failureReason)
            }
        )
    }

    @OptIn(ExperimentalRumApi::class)
    @Test
    fun `M log user message W startFeatureOperation`(
        @StringForgery key: String,
        @StringForgery name: String,
        forge: Forge
    ) {
        // Given
        val operationKey = forge.aNullable { key }
        val attributes = fakeAttributes + (RumAttributes.INTERNAL_TIMESTAMP to fakeTimestamp)

        // When
        testedMonitor.startFeatureOperation(name, operationKey, attributes)

        // Then
        mockInternalLogger.verifyLog(
            InternalLogger.Level.DEBUG,
            InternalLogger.Target.USER,
            "Feature Operation `$name` (operationKey `$operationKey`) started."
        )
    }

    @OptIn(ExperimentalRumApi::class)
    @Test
    fun `M log user message W succeedFeatureOperation`(
        @StringForgery key: String,
        @StringForgery name: String,
        forge: Forge
    ) {
        // Given
        val operationKey = forge.aNullable { key }

        // When
        testedMonitor.succeedFeatureOperation(name, operationKey, fakeAttributes)

        // Then
        mockInternalLogger.verifyLog(
            InternalLogger.Level.DEBUG,
            InternalLogger.Target.USER,
            "Feature Operation `$name` (operationKey `$operationKey`) successfully ended."
        )
    }

    @OptIn(ExperimentalRumApi::class)
    @Test
    fun `M log user message W failFeatureOperation`(
        @StringForgery key: String,
        @StringForgery name: String,
        forge: Forge
    ) {
        // Given
        val operationKey = forge.aNullable { key }
        val failureReason = forge.aValueFrom(FailureReason::class.java)

        // When
        testedMonitor.failFeatureOperation(name, operationKey, failureReason, fakeAttributes)

        // Then
        mockInternalLogger.verifyLog(
            InternalLogger.Level.DEBUG,
            InternalLogger.Target.USER,
            "Feature Operation `$name` (operationKey `$operationKey`) unsuccessfully " +
                "ended with the following failure reason: $failureReason."
        )
    }

    @OptIn(ExperimentalRumApi::class)
    @Test
    fun `M log telemetry message W startFeatureOperation`(
        @StringForgery key: String,
        @StringForgery name: String,
        forge: Forge
    ) {
        // Given
        val operationKey = forge.aNullable { key }

        // When
        testedMonitor.startFeatureOperation(name, operationKey, fakeAttributes)

        // Then
        mockInternalLogger.verifyApiUsage(
            InternalTelemetryEvent.ApiUsage.AddOperationStepVital(
                InternalTelemetryEvent.ApiUsage.AddOperationStepVital.ActionType.START
            ),
            samplingRate = DEFAULT_API_USAGE_SAMPLING_RATE
        )
    }

    @OptIn(ExperimentalRumApi::class)
    @Test
    fun `M log telemetry message W succeedFeatureOperation`(
        @StringForgery key: String,
        @StringForgery name: String,
        forge: Forge
    ) {
        // Given
        val operationKey = forge.aNullable { key }

        // When
        testedMonitor.succeedFeatureOperation(name, operationKey, fakeAttributes)

        // Then
        mockInternalLogger.verifyApiUsage(
            InternalTelemetryEvent.ApiUsage.AddOperationStepVital(
                InternalTelemetryEvent.ApiUsage.AddOperationStepVital.ActionType.SUCCEED
            ),
            samplingRate = DEFAULT_API_USAGE_SAMPLING_RATE
        )
    }

    @OptIn(ExperimentalRumApi::class)
    @Test
    fun `M log telemetry message W failFeatureOperation`(
        @StringForgery key: String,
        @StringForgery name: String,
        forge: Forge
    ) {
        // Given
        val operationKey = forge.aNullable { key }
        val failureReason = forge.aValueFrom(FailureReason::class.java)

        // When
        testedMonitor.failFeatureOperation(name, operationKey, failureReason, fakeAttributes)

        // Then
        mockInternalLogger.verifyApiUsage(
            InternalTelemetryEvent.ApiUsage.AddOperationStepVital(
                InternalTelemetryEvent.ApiUsage.AddOperationStepVital.ActionType.FAIL
            ),
            samplingRate = DEFAULT_API_USAGE_SAMPLING_RATE
        )
    }

    @OptIn(ExperimentalRumApi::class)
    @Test
    fun `M log user message W startFeatureOperation { operation name is blank }`(
        @StringForgery(StringForgeryType.WHITESPACE) name: String
    ) {
        // When
        testedMonitor.startFeatureOperation(name, null, fakeAttributes)

        // Then
        mockInternalLogger.verifyLog(
            InternalLogger.Level.WARN,
            InternalLogger.Target.USER,
            FO_ERROR_INVALID_NAME.format(Locale.US, name)
        )

        verifyNoInteractions(mockApplicationScope)
        verifyNoMoreInteractions(mockInternalLogger)
    }

    @OptIn(ExperimentalRumApi::class)
    @Test
    fun `M log user message W startFeatureOperation { operation key is blank }`(
        @StringForgery name: String,
        @StringForgery(StringForgeryType.WHITESPACE) operationKey: String
    ) {
        // When
        testedMonitor.startFeatureOperation(name, operationKey, fakeAttributes)

        // Then
        mockInternalLogger.verifyLog(
            InternalLogger.Level.WARN,
            InternalLogger.Target.USER,
            FO_ERROR_INVALID_OPERATION_KEY.format(Locale.US, operationKey)
        )

        verifyNoInteractions(mockApplicationScope)
        verifyNoMoreInteractions(mockInternalLogger)
    }

    @OptIn(ExperimentalRumApi::class)
    @Test
    fun `M log user message W succeedFeatureOperation { operation name is blank }`(
        @StringForgery(StringForgeryType.WHITESPACE) name: String
    ) {
        // When
        testedMonitor.succeedFeatureOperation(name, null, fakeAttributes)

        // Then
        mockInternalLogger.verifyLog(
            InternalLogger.Level.WARN,
            InternalLogger.Target.USER,
            FO_ERROR_INVALID_NAME.format(Locale.US, name)
        )

        verifyNoInteractions(mockApplicationScope)
        verifyNoMoreInteractions(mockInternalLogger)
    }

    @OptIn(ExperimentalRumApi::class)
    @Test
    fun `M log user message W succeedFeatureOperation { operation key is blank }`(
        @StringForgery name: String,
        @StringForgery(StringForgeryType.WHITESPACE) operationKey: String
    ) {
        // When
        testedMonitor.succeedFeatureOperation(name, operationKey, fakeAttributes)

        // Then
        mockInternalLogger.verifyLog(
            InternalLogger.Level.WARN,
            InternalLogger.Target.USER,
            FO_ERROR_INVALID_OPERATION_KEY.format(Locale.US, operationKey)
        )

        verifyNoInteractions(mockApplicationScope)
        verifyNoMoreInteractions(mockInternalLogger)
    }

    @OptIn(ExperimentalRumApi::class)
    @Test
    fun `M log user message W failFeatureOperation { operation name is blank }`(
        @StringForgery(StringForgeryType.WHITESPACE) name: String,
        forge: Forge
    ) {
        val failureReason = forge.aValueFrom(FailureReason::class.java)

        // When
        testedMonitor.failFeatureOperation(name, null, failureReason, fakeAttributes)

        // Then
        mockInternalLogger.verifyLog(
            InternalLogger.Level.WARN,
            InternalLogger.Target.USER,
            FO_ERROR_INVALID_NAME.format(Locale.US, name)
        )

        verifyNoInteractions(mockApplicationScope)
        verifyNoMoreInteractions(mockInternalLogger)
    }

    @OptIn(ExperimentalRumApi::class)
    @Test
    fun `M log user message W failFeatureOperation { operation key is blank }`(
        @StringForgery name: String,
        @StringForgery(StringForgeryType.WHITESPACE) operationKey: String,
        forge: Forge
    ) {
        val failureReason = forge.aValueFrom(FailureReason::class.java)

        // When
        testedMonitor.failFeatureOperation(name, operationKey, failureReason, fakeAttributes)

        // Then
        mockInternalLogger.verifyLog(
            InternalLogger.Level.WARN,
            InternalLogger.Target.USER,
            FO_ERROR_INVALID_OPERATION_KEY.format(Locale.US, operationKey)
        )

        verifyNoInteractions(mockApplicationScope)
        verifyNoMoreInteractions(mockInternalLogger)
    }

    private inline fun <reified T : RumRawEvent> assertMethodCallProducesValidEvent(
        whenCalled: () -> Unit,
        then: (T) -> Unit
    ) {
        whenCalled()
        Thread.sleep(PROCESSING_DELAY)

        argumentCaptor<RumRawEvent> {
            verify(mockApplicationScope).handleEvent(
                capture(),
                eq(fakeDatadogContext),
                eq(mockEventWriteScope),
                same(mockWriter)
            )
            val event = firstValue as T
            assertThat(event.eventTime.timestamp).isEqualTo(fakeTimestamp)
            then(event)
        }

        verifyNoMoreInteractions(mockWriter)
    }

    companion object {
        const val TIMESTAMP_MIN = 1000000000000
        const val TIMESTAMP_MAX = 2000000000000
        const val PROCESSING_DELAY = 100L
        const val DEFAULT_API_USAGE_SAMPLING_RATE = 15f
    }
}
