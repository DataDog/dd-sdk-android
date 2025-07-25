/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain.scope

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.context.DatadogContext
import com.datadog.android.api.context.NetworkInfo
import com.datadog.android.api.context.TimeInfo
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureScope
import com.datadog.android.api.storage.DataWriter
import com.datadog.android.api.storage.EventBatchWriter
import com.datadog.android.api.storage.EventType
import com.datadog.android.core.InternalSdkCore
import com.datadog.android.core.feature.event.ThreadDump
import com.datadog.android.core.internal.net.FirstPartyHostHeaderTypeResolver
import com.datadog.android.internal.telemetry.InternalTelemetryEvent
import com.datadog.android.internal.utils.loggableStackTrace
import com.datadog.android.rum.RumActionType
import com.datadog.android.rum.RumAttributes
import com.datadog.android.rum.RumErrorSource
import com.datadog.android.rum.RumPerformanceMetric
import com.datadog.android.rum.RumResourceMethod
import com.datadog.android.rum.RumSessionType
import com.datadog.android.rum.assertj.ActionEventAssert.Companion.assertThat
import com.datadog.android.rum.assertj.ErrorEventAssert.Companion.assertThat
import com.datadog.android.rum.assertj.LongTaskEventAssert.Companion.assertThat
import com.datadog.android.rum.assertj.ViewEventAssert.Companion.assertThat
import com.datadog.android.rum.internal.FeaturesContextResolver
import com.datadog.android.rum.internal.RumErrorSourceType
import com.datadog.android.rum.internal.anr.ANRException
import com.datadog.android.rum.internal.collections.toEvictingQueue
import com.datadog.android.rum.internal.domain.RumContext
import com.datadog.android.rum.internal.domain.Time
import com.datadog.android.rum.internal.domain.accessibility.AccessibilityReader
import com.datadog.android.rum.internal.domain.state.SlowFrameRecord
import com.datadog.android.rum.internal.domain.state.ViewUIPerformanceReport
import com.datadog.android.rum.internal.metric.NoValueReason
import com.datadog.android.rum.internal.metric.SessionMetricDispatcher
import com.datadog.android.rum.internal.metric.ViewEndedMetricDispatcher
import com.datadog.android.rum.internal.metric.ViewInitializationMetricsConfig
import com.datadog.android.rum.internal.metric.ViewInitializationMetricsState
import com.datadog.android.rum.internal.metric.ViewMetricDispatcher
import com.datadog.android.rum.internal.metric.interactiontonextview.InteractionToNextViewMetricResolver
import com.datadog.android.rum.internal.metric.interactiontonextview.InternalInteractionContext
import com.datadog.android.rum.internal.metric.networksettled.InternalResourceContext
import com.datadog.android.rum.internal.metric.networksettled.NetworkSettledMetricResolver
import com.datadog.android.rum.internal.metric.slowframes.SlowFramesListener
import com.datadog.android.rum.internal.monitor.AdvancedRumMonitor
import com.datadog.android.rum.internal.monitor.StorageEvent
import com.datadog.android.rum.internal.toAction
import com.datadog.android.rum.internal.toError
import com.datadog.android.rum.internal.toLongTask
import com.datadog.android.rum.internal.toView
import com.datadog.android.rum.internal.vitals.VitalInfo
import com.datadog.android.rum.internal.vitals.VitalListener
import com.datadog.android.rum.internal.vitals.VitalMonitor
import com.datadog.android.rum.model.ActionEvent
import com.datadog.android.rum.model.ErrorEvent
import com.datadog.android.rum.model.LongTaskEvent
import com.datadog.android.rum.model.ViewEvent
import com.datadog.android.rum.utils.config.GlobalRumMonitorTestConfiguration
import com.datadog.android.rum.utils.forge.Configurator
import com.datadog.android.rum.utils.verifyApiUsage
import com.datadog.android.rum.utils.verifyLog
import com.datadog.tools.unit.annotations.TestConfigurationsProvider
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.datadog.tools.unit.extensions.config.TestConfiguration
import com.datadog.tools.unit.forge.aFilteredMap
import com.datadog.tools.unit.forge.anException
import com.datadog.tools.unit.forge.exhaustiveAttributes
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.BoolForgery
import fr.xgouchet.elmyr.annotation.DoubleForgery
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.annotation.StringForgeryType
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assumptions.assumeFalse
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doReturnConsecutively
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.isA
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.Arrays
import java.util.Locale
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.min

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(TestConfigurationExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class RumViewScopeTest {

    lateinit var testedScope: RumViewScope

    @Mock
    lateinit var mockParentScope: RumScope

    @Mock
    lateinit var mockChildScope: RumScope

    @Mock
    lateinit var mockActionScope: RumActionScope

    @Mock
    lateinit var mockWriter: DataWriter<Any>

    @Mock
    lateinit var mockResolver: FirstPartyHostHeaderTypeResolver

    @Mock
    lateinit var mockCpuVitalMonitor: VitalMonitor

    @Mock
    lateinit var mockAccessibilityReader: AccessibilityReader

    @Mock
    lateinit var mockMemoryVitalMonitor: VitalMonitor

    @Mock
    lateinit var mockFrameRateVitalMonitor: VitalMonitor

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @Mock
    lateinit var mockRumFeatureScope: FeatureScope

    @Mock
    lateinit var mockEventBatchWriter: EventBatchWriter

    @StringForgery(regex = "[a-f0-9]{8}-([a-f0-9]{4}-){3}[a-f0-9]{12}")
    lateinit var fakeActionId: String

    lateinit var fakeUrl: String

    @Forgery
    lateinit var fakeKey: RumScopeKey

    lateinit var fakeAttributes: Map<String, Any?>

    @Forgery
    lateinit var fakeParentContext: RumContext

    lateinit var fakeEventTime: Time

    lateinit var fakeEvent: RumRawEvent

    @Forgery
    lateinit var fakeTimeInfoAtScopeStart: TimeInfo

    @Forgery
    lateinit var fakeNetworkInfoAtScopeStart: NetworkInfo

    @Forgery
    lateinit var fakeDatadogContext: DatadogContext

    var fakeSourceViewEvent: ViewEvent.ViewEventSource? = null
    var fakeSourceErrorEvent: ErrorEvent.ErrorEventSource? = null
    var fakeSourceActionEvent: ActionEvent.ActionEventSource? = null
    var fakeSourceLongTaskEvent: LongTaskEvent.LongTaskEventSource? = null

    @BoolForgery
    var fakeHasReplay: Boolean = false

    @LongForgery(min = 0)
    var fakeReplayRecordsCount: Long = 0

    @Mock
    lateinit var mockFeaturesContextResolver: FeaturesContextResolver

    @Mock
    lateinit var mockViewChangedListener: RumViewChangedListener

    @Mock
    private lateinit var mockSessionEndedMetricDispatcher: SessionMetricDispatcher

    @Mock
    private lateinit var mockNetworkSettledMetricResolver: NetworkSettledMetricResolver

    @Mock
    private lateinit var mockInteractionToNextViewMetricResolver: InteractionToNextViewMetricResolver

    @Mock
    private lateinit var mockViewEndedMetricDispatcher: ViewEndedMetricDispatcher

    @Mock
    private lateinit var mockViewUIPerformanceReport: ViewUIPerformanceReport

    @Mock
    lateinit var mockSlowFramesListener: SlowFramesListener

    @Forgery
    private lateinit var fakeTnsState: ViewInitializationMetricsState

    @Forgery
    private lateinit var fakeInvState: ViewInitializationMetricsState

    private var fakeNetworkSettledMetricValue: Long? = null
    private var fakeInteractionToNextViewMetricValue: Long? = null

    @BoolForgery
    var fakeTrackFrustrations: Boolean = true

    @LongForgery(min = 1L)
    var fakeViewDurationNs: Long = 0

    lateinit var fakeReplayStats: ViewEvent.ReplayStats

    private var fakeSampleRate: Float = 0.0f

    @DoubleForgery(min = 0.0, max = 1.0)
    var fakeSlownessRate: Double = 0.0

    @DoubleForgery(min = 0.0, max = 1.0)
    private var fakeFreezeRate: Double = 0.0
    private lateinit var fakeSlowRecords: List<ViewEvent.SlowFrame>

    private var fakeRumSessionType: RumSessionType? = null

    @BeforeEach
    fun `set up`(forge: Forge) {
        fakeRumSessionType = forge.aNullable { aValueFrom(RumSessionType::class.java) }
        fakeNetworkSettledMetricValue = forge.aNullable { aPositiveLong() }
        fakeInteractionToNextViewMetricValue = forge.aNullable { aPositiveLong() }
        whenever(mockNetworkSettledMetricResolver.resolveMetric()) doReturn fakeNetworkSettledMetricValue
        whenever(mockNetworkSettledMetricResolver.getState()) doReturn fakeTnsState
        whenever(mockInteractionToNextViewMetricResolver.getState(any())) doReturn fakeInvState
        whenever(mockInteractionToNextViewMetricResolver.resolveMetric(any())) doReturn
            fakeInteractionToNextViewMetricValue
        val isValidSource = forge.aBool()

        val fakeSource = if (isValidSource) {
            forge.anElementFrom(
                ViewEvent.ViewEventSource.values().map { it.toJson().asString }
            )
        } else {
            forge.anAlphabeticalString()
        }

        fakeSourceViewEvent = if (isValidSource) {
            ViewEvent.ViewEventSource.fromJson(fakeSource)
        } else {
            null
        }
        fakeSourceErrorEvent = if (isValidSource) {
            ErrorEvent.ErrorEventSource.fromJson(fakeSource)
        } else {
            null
        }
        fakeSourceActionEvent = if (isValidSource) {
            ActionEvent.ActionEventSource.fromJson(fakeSource)
        } else {
            null
        }
        fakeSourceLongTaskEvent = if (isValidSource) {
            LongTaskEvent.LongTaskEventSource.fromJson(fakeSource)
        } else {
            null
        }

        fakeDatadogContext = fakeDatadogContext.copy(
            source = fakeSource
        )

        fakeParentContext = fakeParentContext.copy(syntheticsTestId = null, syntheticsResultId = null)

        val fakeOffset = -forge.aLong(1000, 50000)
        val fakeTimestamp = System.currentTimeMillis() + fakeOffset
        val fakeNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(fakeOffset)
        val maxLimit = max(Long.MAX_VALUE - fakeTimestamp, Long.MAX_VALUE)
        val minLimit = min(-fakeTimestamp, maxLimit)
        fakeSampleRate = forge.aFloat(min = 0.0f, max = 100.0f)

        fakeDatadogContext = fakeDatadogContext.copy(
            time = fakeTimeInfoAtScopeStart.copy(
                serverTimeOffsetMs = forge.aLong(min = minLimit, max = maxLimit)
            )
        )
        fakeEventTime = Time(fakeTimestamp, fakeNanos)
        fakeAttributes = forge.exhaustiveAttributes()
        fakeKey = forge.getForgery()
        fakeEvent = mockEvent()
        fakeUrl = fakeKey.url.replace('.', '/')

        fakeSlowRecords = forge.aList {
            ViewEvent.SlowFrame(
                start = aLong(min = 0, max = MAX_DURATION_VALUE_NS),
                duration = aLong(min = 0, max = MAX_DURATION_VALUE_NS)
            )
        }
        whenever(mockViewUIPerformanceReport.freezeFramesRate(any())) doReturn fakeFreezeRate
        whenever(mockViewUIPerformanceReport.slowFramesRate(any())) doReturn fakeSlownessRate
        whenever(mockViewUIPerformanceReport.slowFramesRecords) doReturn fakeSlowRecords
            .map {
                SlowFrameRecord(
                    startTimestampNs = fakeEventTime.nanoTime + it.start,
                    durationNs = it.duration
                )
            }
            .toEvictingQueue()

        whenever(mockSlowFramesListener.resolveReport(any(), any(), any())) doReturn mockViewUIPerformanceReport
        whenever(mockParentScope.getRumContext()) doReturn fakeParentContext
        whenever(mockChildScope.handleEvent(any(), any())) doReturn mockChildScope
        whenever(mockActionScope.handleEvent(any(), any())) doReturn mockActionScope
        whenever(mockActionScope.actionId) doReturn fakeActionId
        whenever(rumMonitor.mockSdkCore.getFeature(Feature.RUM_FEATURE_NAME)) doReturn mockRumFeatureScope
        whenever(rumMonitor.mockSdkCore.time) doReturn fakeTimeInfoAtScopeStart
        whenever(rumMonitor.mockSdkCore.networkInfo) doReturn fakeNetworkInfoAtScopeStart
        whenever(rumMonitor.mockSdkCore.internalLogger) doReturn mockInternalLogger
        whenever(mockRumFeatureScope.withWriteContext(any(), any())) doAnswer {
            val callback = it.getArgument<(DatadogContext, EventBatchWriter) -> Unit>(1)
            callback.invoke(fakeDatadogContext, mockEventBatchWriter)
        }
        whenever(mockWriter.write(eq(mockEventBatchWriter), any(), eq(EventType.DEFAULT))) doReturn true
        fakeReplayStats = ViewEvent.ReplayStats(recordsCount = fakeReplayRecordsCount)
        testedScope = newRumViewScope(trackFrustrations = true)
        mockSessionReplayContext(testedScope)
    }

    // region Context

    @Test
    fun `M return valid RumContext W getRumContext()`() {
        // When
        val context = testedScope.getRumContext()

        // Then
        assertThat(context.actionId).isNull()
        assertThat(context.viewId).isEqualTo(testedScope.viewId)
        assertThat(context.viewName).isEqualTo(fakeKey.name)
        assertThat(context.viewUrl).isEqualTo(fakeUrl)
        assertThat(context.applicationId).isEqualTo(fakeParentContext.applicationId)
        assertThat(context.sessionId).isEqualTo(fakeParentContext.sessionId)
    }

    @Test
    fun `M return active actionId W getRumContext() with child ActionScope`() {
        // Given
        testedScope.activeActionScope = mockActionScope

        // When
        val context = testedScope.getRumContext()

        // Then
        assertThat(context.actionId).isEqualTo(fakeActionId)
        assertThat(context.viewId).isEqualTo(testedScope.viewId)
        assertThat(context.viewName).isEqualTo(fakeKey.name)
        assertThat(context.viewUrl).isEqualTo(fakeUrl)
        assertThat(context.applicationId).isEqualTo(fakeParentContext.applicationId)
        assertThat(context.sessionId).isEqualTo(fakeParentContext.sessionId)
    }

    @Test
    fun `M update RUM feature context W init()`() {
        argumentCaptor<(MutableMap<String, Any?>) -> Unit> {
            verify(rumMonitor.mockSdkCore)
                .updateFeatureContext(eq(Feature.RUM_FEATURE_NAME), capture())

            val rumContext = mutableMapOf<String, Any?>()
            firstValue.invoke(rumContext)

            assertThat(RumContext.fromFeatureContext(rumContext))
                .isEqualTo(testedScope.getRumContext())
        }
    }

    @Test
    fun `M update the context with the viewType W initializing`(forge: Forge) {
        // Given
        val fakeViewEventType = forge.aValueFrom(RumViewType::class.java)

        // When
        testedScope = newRumViewScope(type = fakeViewEventType)

        // Then
        argumentCaptor<(MutableMap<String, Any?>) -> Unit> {
            verify(rumMonitor.mockSdkCore, times(2)).updateFeatureContext(
                eq(Feature.RUM_FEATURE_NAME),
                capture()
            )

            val rumContext = mutableMapOf<String, Any?>()
            allValues.fold(rumContext) { acc, function ->
                function.invoke(acc)
                acc
            }
            assertThat(rumContext["view_type"]).isEqualTo(fakeViewEventType.asString)
        }
    }

    @Test
    fun `M update the feature context with the view timestamp offset W initializing`() {
        argumentCaptor<(MutableMap<String, Any?>) -> Unit> {
            verify(rumMonitor.mockSdkCore).updateFeatureContext(
                eq(Feature.RUM_FEATURE_NAME),
                capture()
            )

            val rumContext = mutableMapOf<String, Any?>()
            lastValue.invoke(rumContext)
            assertThat(rumContext[RumContext.VIEW_TIMESTAMP_OFFSET])
                .isEqualTo(fakeTimeInfoAtScopeStart.serverTimeOffsetMs)
        }
    }

    @Test
    fun `M update the feature context with the view timestamp W initializing`() {
        argumentCaptor<(MutableMap<String, Any?>) -> Unit> {
            verify(rumMonitor.mockSdkCore).updateFeatureContext(
                eq(Feature.RUM_FEATURE_NAME),
                capture()
            )

            val rumContext = mutableMapOf<String, Any?>()
            lastValue.invoke(rumContext)
            assertThat(rumContext[RumContext.VIEW_TIMESTAMP])
                .isEqualTo(fakeEventTime.timestamp + fakeTimeInfoAtScopeStart.serverTimeOffsetMs)
        }
    }

    @Test
    fun `M reset the hasReplay attribute in feature context with the view timestamp W initializing`() {
        argumentCaptor<(MutableMap<String, Any?>) -> Unit> {
            verify(rumMonitor.mockSdkCore).updateFeatureContext(
                eq(Feature.RUM_FEATURE_NAME),
                capture()
            )

            val rumContext = mutableMapOf<String, Any?>()
            lastValue.invoke(rumContext)
            assertThat(rumContext[RumContext.HAS_REPLAY] as Boolean).isFalse()
        }
    }

    @Test
    fun `M update the context with viewType NONE W handleEvent(StopView)`(
        forge: Forge
    ) {
        // Given
        val attributes = forge.exhaustiveAttributes(excludedKeys = fakeAttributes.keys)

        // When
        testedScope.handleEvent(
            RumRawEvent.StopView(fakeKey, attributes),
            mockWriter
        )

        // Then
        argumentCaptor<(MutableMap<String, Any?>) -> Unit> {
            verify(rumMonitor.mockSdkCore, times(3)).updateFeatureContext(
                eq(Feature.RUM_FEATURE_NAME),
                capture()
            )

            val rumContext = mutableMapOf<String, Any?>()
            allValues.fold(rumContext) { acc, function ->
                function.invoke(acc)
                acc
            }
            assertThat(rumContext["view_type"]).isEqualTo(RumViewType.NONE.asString)
        }
    }

    @Test
    fun `M keep the resolved hasReplay value in the context W handleEvent(StopView)`(
        forge: Forge
    ) {
        // Given
        val attributes = forge.exhaustiveAttributes(excludedKeys = fakeAttributes.keys)

        // When
        testedScope.handleEvent(
            RumRawEvent.StopView(fakeKey, attributes),
            mockWriter
        )

        // Then
        argumentCaptor<(MutableMap<String, Any?>) -> Unit> {
            verify(rumMonitor.mockSdkCore, times(3)).updateFeatureContext(
                eq(Feature.RUM_FEATURE_NAME),
                capture()
            )

            val rumContext = mutableMapOf<String, Any?>()
            allValues.fold(rumContext) { acc, function ->
                function.invoke(acc)
                acc
            }
            assertThat(rumContext[RumContext.HAS_REPLAY] as Boolean).isEqualTo(fakeHasReplay)
        }
    }

    @Test
    fun `M keep the viewTimestamp value in the context W handleEvent(StopView)`(
        forge: Forge
    ) {
        // Given
        val attributes = forge.exhaustiveAttributes(excludedKeys = fakeAttributes.keys)

        // When
        testedScope.handleEvent(
            RumRawEvent.StopView(fakeKey, attributes),
            mockWriter
        )

        // Then
        argumentCaptor<(MutableMap<String, Any?>) -> Unit> {
            verify(rumMonitor.mockSdkCore, times(3)).updateFeatureContext(
                eq(Feature.RUM_FEATURE_NAME),
                capture()
            )

            val rumContext = mutableMapOf<String, Any?>()
            allValues.fold(rumContext) { acc, function ->
                function.invoke(acc)
                acc
            }
            assertThat(rumContext[RumContext.VIEW_TIMESTAMP] as Long)
                .isEqualTo(fakeEventTime.timestamp + fakeTimeInfoAtScopeStart.serverTimeOffsetMs)
        }
    }

    @Test
    fun `M not update the context with viewType NONE W handleEvent(StopView) { unknown key }`(
        forge: Forge
    ) {
        // Given
        val attributes = forge.exhaustiveAttributes(excludedKeys = fakeAttributes.keys)

        // When
        testedScope.handleEvent(
            RumRawEvent.StopView(forge.getForgery(), attributes),
            mockWriter
        )

        // Then
        argumentCaptor<(MutableMap<String, Any?>) -> Unit> {
            verify(rumMonitor.mockSdkCore).updateFeatureContext(
                eq(Feature.RUM_FEATURE_NAME),
                capture()
            )
            val rumContext = mutableMapOf<String, Any?>()
            lastValue.invoke(rumContext)
            assertThat(rumContext["view_type"]).isEqualTo(RumViewType.FOREGROUND.asString)
        }
    }

    @Test
    fun `M not update the context W handleEvent(StopView) { cur vs glob view ids don't match }`(
        forge: Forge
    ) {
        // Given
        val expectedViewType = forge.aValueFrom(RumViewType::class.java)

        // need to create this one, because RUM context is updated in the constructor
        val anotherScope = newRumViewScope(type = expectedViewType)

        // When
        testedScope.handleEvent(
            RumRawEvent.StopView(fakeKey, emptyMap()),
            mockWriter
        )

        // Then
        argumentCaptor<(MutableMap<String, Any?>) -> Unit> {
            // A scope init + B scope init + A scope stop
            verify(rumMonitor.mockSdkCore, times(4)).updateFeatureContext(
                eq(Feature.RUM_FEATURE_NAME),
                capture()
            )

            val rumContext = mutableMapOf<String, Any?>()

            allValues.fold(rumContext) { acc, function ->
                function.invoke(acc)
                acc
            }

            assertThat(rumContext["view_type"]).isEqualTo(expectedViewType.asString)
            assertThat(rumContext["view_name"]).isEqualTo(anotherScope.getRumContext().viewName)
            assertThat(rumContext["view_id"]).isEqualTo(anotherScope.getRumContext().viewId)
            assertThat(rumContext["view_url"]).isEqualTo(anotherScope.getRumContext().viewUrl)
            assertThat(rumContext["action_id"]).isEqualTo(anotherScope.getRumContext().actionId)
        }
        mockInternalLogger.verifyLog(
            InternalLogger.Level.DEBUG,
            InternalLogger.Target.MAINTAINER,
            RumViewScope.RUM_CONTEXT_UPDATE_IGNORED_AT_STOP_VIEW_MESSAGE
        )
    }

    @Test
    fun `M update the context W handleEvent(StopView) { new session }`(
        forge: Forge
    ) {
        // Given
        val currentContext = testedScope.getRumContext()

        val fakeNewSessionContext: RumContext = forge.getForgery()
        whenever(mockParentScope.getRumContext()) doReturn fakeNewSessionContext

        assumeTrue { currentContext.sessionId != fakeNewSessionContext.sessionId }

        // When
        testedScope.handleEvent(
            RumRawEvent.StopView(fakeKey, emptyMap()),
            mockWriter
        )

        // Then
        argumentCaptor<(MutableMap<String, Any?>) -> Unit> {
            verify(rumMonitor.mockSdkCore, times(3)).updateFeatureContext(
                eq(Feature.RUM_FEATURE_NAME),
                capture()
            )
            val rumContext = mutableMapOf<String, Any?>()

            allValues.fold(rumContext) { acc, function ->
                function.invoke(acc)
                acc
            }

            assertThat(rumContext["view_type"]).isEqualTo(RumViewType.NONE.asString)
            assertThat(rumContext["view_name"]).isNull()
            assertThat(rumContext["view_id"]).isNull()
            assertThat(rumContext["view_url"]).isNull()
            assertThat(rumContext["action_id"]).isNull()
        }
    }

    @Test
    fun `M not update the context W handleEvent() { action completes after view stopped }`(
        @StringForgery actionName: String,
        @Forgery rumActionType: RumActionType
    ) {
        // Given
        testedScope.activeActionScope = mockChildScope
        val stopActionEvent = RumRawEvent.StopAction(rumActionType, actionName, emptyMap())
        whenever(mockChildScope.handleEvent(stopActionEvent, mockWriter)) doReturn null

        // When
        testedScope.handleEvent(
            RumRawEvent.StopView(fakeKey, emptyMap()),
            mockWriter
        )
        testedScope.handleEvent(
            stopActionEvent,
            mockWriter
        )

        // Then
        argumentCaptor<(MutableMap<String, Any?>) -> Unit> {
            // scope init + stop view + stop action
            verify(rumMonitor.mockSdkCore, times(4)).updateFeatureContext(
                eq(Feature.RUM_FEATURE_NAME),
                capture()
            )

            val rumContext = mutableMapOf<String, Any?>()
            allValues.fold(rumContext) { acc, function ->
                function.invoke(acc)
                acc
            }

            assertThat(rumContext["view_type"]).isEqualTo(RumViewType.NONE.asString)
            assertThat(rumContext["view_name"]).isNull()
            assertThat(rumContext["view_id"]).isNull()
            assertThat(rumContext["view_url"]).isNull()
            assertThat(rumContext["action_id"]).isNull()
        }

        mockInternalLogger.verifyLog(
            InternalLogger.Level.DEBUG,
            InternalLogger.Target.MAINTAINER,
            RumViewScope.RUM_CONTEXT_UPDATE_IGNORED_AT_ACTION_UPDATE_MESSAGE
        )
    }

    // endregion

    // region isActive

    @Test
    fun `M return true W isActive() {not stopped}`() {
        // Given
        testedScope.stopped = false

        // When
        val isActive = testedScope.isActive()

        // Then
        assertThat(isActive).isTrue()
    }

    @Test
    fun `M return false W isActive() {stopped}`() {
        // Given
        testedScope.stopped = true

        // When
        val isActive = testedScope.isActive()

        // Then
        assertThat(isActive).isFalse()
    }

    // endregion

    // region View

    @ParameterizedTest
    @EnumSource(
        value = RumViewType::class,
        names = ["NONE"],
        mode = EnumSource.Mode.EXCLUDE
    )
    fun `M not update the viewType to NONE W handleEvent(StartView) { on active view }`(
        viewType: RumViewType,
        @Forgery key: RumScopeKey
    ) {
        // Given
        testedScope.handleEvent(
            RumRawEvent.StopView(fakeKey, emptyMap()),
            mockWriter
        )
        newRumViewScope(type = viewType)

        // When
        testedScope.handleEvent(
            RumRawEvent.StartView(key, emptyMap()),
            mockWriter
        )

        // Then
        argumentCaptor<(MutableMap<String, Any?>) -> Unit> {
            // A scope init + onStopView + B scope init
            verify(rumMonitor.mockSdkCore, times(4)).updateFeatureContext(
                eq(Feature.RUM_FEATURE_NAME),
                capture()
            )
            val rumContext = mutableMapOf<String, Any?>()
            allValues.fold(rumContext) { acc, function ->
                function.invoke(acc)
                acc
            }
            assertThat(rumContext["view_type"]).isEqualTo(viewType.asString)
        }
    }

    @ParameterizedTest
    @EnumSource(
        value = RumViewType::class,
        names = ["NONE"],
        mode = EnumSource.Mode.EXCLUDE
    )
    fun `M not update the viewType to NONE W handleEvent(StopView) {already stopped, active view}`(
        viewType: RumViewType,
        @Forgery key: RumScopeKey
    ) {
        // Given
        testedScope.handleEvent(
            RumRawEvent.StopView(fakeKey, emptyMap()),
            mockWriter
        )
        newRumViewScope(key = key, type = viewType)

        // When
        testedScope.handleEvent(
            RumRawEvent.StopView(key, emptyMap()),
            mockWriter
        )

        // Then
        argumentCaptor<(MutableMap<String, Any?>) -> Unit> {
            // A scope init + A scope stop + B scope init
            verify(rumMonitor.mockSdkCore, times(4)).updateFeatureContext(
                eq(Feature.RUM_FEATURE_NAME),
                capture()
            )
            val rumContext = mutableMapOf<String, Any?>()
            allValues.fold(rumContext) { acc, function ->
                function.invoke(acc)
                acc
            }
            assertThat(rumContext["view_type"]).isEqualTo(viewType.asString)
        }
    }

    @Test
    fun `M do nothing W handleEvent(StartView) on stopped view`(
        @Forgery key: RumScopeKey
    ) {
        // Given
        testedScope.stopped = true

        // When
        val result = testedScope.handleEvent(
            RumRawEvent.StartView(key, emptyMap()),
            mockWriter
        )

        // Then
        verifyNoInteractions(mockWriter)
        assertThat(result).isNull()
    }

    @Test
    fun `M send event W handleEvent(StartView) on active view`(
        @Forgery key: RumScopeKey
    ) {
        // When
        val result = testedScope.handleEvent(
            RumRawEvent.StartView(key, emptyMap()),
            mockWriter
        )

        // Then
        argumentCaptor<ViewEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))
            assertThat(lastValue).apply {
                hasTimestamp(resolveExpectedTimestamp(fakeEventTime.timestamp))
                hasName(fakeKey.name)
                hasUrl(fakeUrl)
                hasDurationGreaterThan(1)
                hasVersion(2)
                hasErrorCount(0)
                hasCrashCount(0)
                hasResourceCount(0)
                hasActionCount(0)
                hasFrustrationCount(0)
                hasLongTaskCount(0)
                hasFrozenFrameCount(0)
                hasCpuMetric(null)
                hasMemoryMetric(null, null)
                hasRefreshRateMetric(null, null)
                isActive(false)
                isSlowRendered(false)
                hasNoCustomTimings()
                hasUserInfo(fakeDatadogContext.userInfo)
                hasAccountInfo(fakeDatadogContext.accountInfo)
                hasViewId(testedScope.viewId)
                hasApplicationId(fakeParentContext.applicationId)
                hasSessionId(fakeParentContext.sessionId)
                hasSessionType(fakeRumSessionType?.toView() ?: ViewEvent.ViewEventSessionType.USER)
                hasNoSyntheticsTest()
                hasStartReason(fakeParentContext.sessionStartReason)
                hasReplay(fakeHasReplay)
                hasReplayStats(fakeReplayStats)
                hasSource(fakeSourceViewEvent)
                containsExactlyContextAttributes(fakeAttributes)
                hasDeviceInfo(
                    fakeDatadogContext.deviceInfo.deviceName,
                    fakeDatadogContext.deviceInfo.deviceModel,
                    fakeDatadogContext.deviceInfo.deviceBrand,
                    fakeDatadogContext.deviceInfo.deviceType.toViewSchemaType(),
                    fakeDatadogContext.deviceInfo.architecture
                )
                hasOsInfo(
                    fakeDatadogContext.deviceInfo.osName,
                    fakeDatadogContext.deviceInfo.osVersion,
                    fakeDatadogContext.deviceInfo.osMajorVersion
                )
                hasSlownessInfo(
                    fakeSlowRecords,
                    fakeSlownessRate,
                    fakeFreezeRate
                )
                hasConnectivityInfo(fakeDatadogContext.networkInfo)
                hasServiceName(fakeDatadogContext.service)
                hasVersion(fakeDatadogContext.version)
                hasSessionActive(fakeParentContext.isSessionActive)
                hasSampleRate(fakeSampleRate)
            }
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isNull()
    }

    @Test
    fun `M send event once W handleEvent(StartView) twice on active view`(
        @Forgery key: RumScopeKey,
        @Forgery key2: RumScopeKey
    ) {
        // When
        val result = testedScope.handleEvent(
            RumRawEvent.StartView(key, emptyMap()),
            mockWriter
        )
        val result2 = testedScope.handleEvent(
            RumRawEvent.StartView(key2, emptyMap()),
            mockWriter
        )

        // Then
        argumentCaptor<ViewEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))
            assertThat(lastValue)
                .apply {
                    hasTimestamp(resolveExpectedTimestamp(fakeEventTime.timestamp))
                    hasName(fakeKey.name)
                    hasUrl(fakeUrl)
                    hasDurationGreaterThan(1)
                    hasVersion(2)
                    hasErrorCount(0)
                    hasCrashCount(0)
                    hasResourceCount(0)
                    hasActionCount(0)
                    hasFrustrationCount(0)
                    hasLongTaskCount(0)
                    hasFrozenFrameCount(0)
                    hasCpuMetric(null)
                    hasMemoryMetric(null, null)
                    hasRefreshRateMetric(null, null)
                    isActive(false)
                    isSlowRendered(false)
                    hasNoCustomTimings()
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasAccountInfo(fakeDatadogContext.accountInfo)
                    hasViewId(testedScope.viewId)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasSessionType(fakeRumSessionType?.toView() ?: ViewEvent.ViewEventSessionType.USER)
                    hasNoSyntheticsTest()
                    containsExactlyContextAttributes(fakeAttributes)
                    hasStartReason(fakeParentContext.sessionStartReason)
                    hasReplay(fakeHasReplay)
                    hasReplayStats(fakeReplayStats)
                    hasSource(fakeSourceViewEvent)
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toViewSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasSlownessInfo(
                        fakeSlowRecords,
                        fakeSlownessRate,
                        fakeFreezeRate
                    )
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasSessionActive(fakeParentContext.isSessionActive)
                    hasSampleRate(fakeSampleRate)
                }
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isNull()
        assertThat(result2).isNull()
    }

    @Test
    fun `M send event W handleEvent(StopView) on active view`(
        forge: Forge
    ) {
        // Given
        val attributes = forge.exhaustiveAttributes(excludedKeys = fakeAttributes.keys)
        val expectedAttributes = mutableMapOf<String, Any?>()
        expectedAttributes.putAll(fakeAttributes)
        expectedAttributes.putAll(attributes)

        // When
        val result = testedScope.handleEvent(
            RumRawEvent.StopView(fakeKey, attributes),
            mockWriter
        )

        // Then
        argumentCaptor<ViewEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))
            assertThat(lastValue)
                .apply {
                    hasTimestamp(resolveExpectedTimestamp(fakeEventTime.timestamp))
                    hasName(fakeKey.name)
                    hasUrl(fakeUrl)
                    hasDurationGreaterThan(1)
                    hasVersion(2)
                    hasErrorCount(0)
                    hasCrashCount(0)
                    hasResourceCount(0)
                    hasActionCount(0)
                    hasFrustrationCount(0)
                    hasLongTaskCount(0)
                    hasFrozenFrameCount(0)
                    hasCpuMetric(null)
                    hasMemoryMetric(null, null)
                    hasRefreshRateMetric(null, null)
                    isActive(false)
                    isSlowRendered(false)
                    hasNoCustomTimings()
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasAccountInfo(fakeDatadogContext.accountInfo)
                    hasViewId(testedScope.viewId)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasSessionType(fakeRumSessionType?.toView() ?: ViewEvent.ViewEventSessionType.USER)
                    hasNoSyntheticsTest()
                    containsExactlyContextAttributes(expectedAttributes)
                    hasStartReason(fakeParentContext.sessionStartReason)
                    hasReplay(fakeHasReplay)
                    hasReplayStats(fakeReplayStats)
                    hasSource(fakeSourceViewEvent)
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toViewSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasSlownessInfo(
                        fakeSlowRecords,
                        fakeSlownessRate,
                        fakeFreezeRate
                    )
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasSessionActive(fakeParentContext.isSessionActive)
                    hasSampleRate(fakeSampleRate)
                }
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isNull()
    }

    @Test
    fun `M send event W handleEvent(StopView) on active view { pending attributes are negative }`(
        forge: Forge
    ) {
        // Given
        testedScope.pendingActionCount = forge.aLong(min = 0, max = 100) * (-1)
        testedScope.pendingResourceCount = forge.aLong(min = 0, max = 100) * (-1)
        testedScope.pendingErrorCount = forge.aLong(min = 0, max = 100) * (-1)
        testedScope.pendingLongTaskCount = forge.aLong(min = 0, max = 100) * (-1)

        // we limit it to 100 to avoid overflow and when we add those and end up with a positive
        // number
        val attributes = forge.exhaustiveAttributes(excludedKeys = fakeAttributes.keys)
        val expectedAttributes = mutableMapOf<String, Any?>()
        expectedAttributes.putAll(fakeAttributes)
        expectedAttributes.putAll(attributes)

        // When
        val result = testedScope.handleEvent(
            RumRawEvent.StopView(fakeKey, attributes),
            mockWriter
        )

        // Then
        argumentCaptor<ViewEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))
            assertThat(lastValue)
                .apply {
                    hasTimestamp(resolveExpectedTimestamp(fakeEventTime.timestamp))
                    hasName(fakeKey.name)
                    hasUrl(fakeUrl)
                    hasDurationGreaterThan(1)
                    hasVersion(2)
                    hasErrorCount(0)
                    hasCrashCount(0)
                    hasResourceCount(0)
                    hasActionCount(0)
                    hasFrustrationCount(0)
                    hasLongTaskCount(0)
                    hasFrozenFrameCount(0)
                    hasCpuMetric(null)
                    hasMemoryMetric(null, null)
                    hasRefreshRateMetric(null, null)
                    isActive(false)
                    isSlowRendered(false)
                    hasNoCustomTimings()
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasAccountInfo(fakeDatadogContext.accountInfo)
                    hasViewId(testedScope.viewId)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasSessionType(fakeRumSessionType?.toView() ?: ViewEvent.ViewEventSessionType.USER)
                    hasNoSyntheticsTest()
                    hasStartReason(fakeParentContext.sessionStartReason)
                    containsExactlyContextAttributes(expectedAttributes)
                    hasReplay(fakeHasReplay)
                    hasReplayStats(fakeReplayStats)
                    hasSource(fakeSourceViewEvent)
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toViewSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasSlownessInfo(
                        fakeSlowRecords,
                        fakeSlownessRate,
                        fakeFreezeRate
                    )
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasSessionActive(fakeParentContext.isSessionActive)
                    hasSampleRate(fakeSampleRate)
                }
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isNull()
    }

    @Test
    fun `M send event W handleEvent(StopView) on active view { pending attributes are positive }`(
        forge: Forge
    ) {
        // Given
        testedScope.pendingActionCount = forge.aLong(min = 0, max = 100)
        testedScope.pendingResourceCount = forge.aLong(min = 0, max = 100)
        testedScope.pendingErrorCount = forge.aLong(min = 0, max = 100)
        testedScope.pendingLongTaskCount = forge.aLong(min = 0, max = 100)
        val attributes = forge.exhaustiveAttributes(excludedKeys = fakeAttributes.keys)
        val expectedAttributes = mutableMapOf<String, Any?>()
        expectedAttributes.putAll(fakeAttributes)
        expectedAttributes.putAll(attributes)

        // When
        val result = testedScope.handleEvent(
            RumRawEvent.StopView(fakeKey, attributes),
            mockWriter
        )

        // Then
        argumentCaptor<ViewEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))
            assertThat(lastValue)
                .apply {
                    hasTimestamp(resolveExpectedTimestamp(fakeEventTime.timestamp))
                    hasName(fakeKey.name)
                    hasUrl(fakeUrl)
                    hasDurationGreaterThan(1)
                    hasVersion(2)
                    hasErrorCount(0)
                    hasCrashCount(0)
                    hasResourceCount(0)
                    hasActionCount(0)
                    hasFrustrationCount(0)
                    hasLongTaskCount(0)
                    hasFrozenFrameCount(0)
                    hasCpuMetric(null)
                    hasMemoryMetric(null, null)
                    hasRefreshRateMetric(null, null)
                    isActive(true)
                    isSlowRendered(false)
                    hasNoCustomTimings()
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasAccountInfo(fakeDatadogContext.accountInfo)
                    hasViewId(testedScope.viewId)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasSessionType(fakeRumSessionType?.toView() ?: ViewEvent.ViewEventSessionType.USER)
                    hasNoSyntheticsTest()
                    hasStartReason(fakeParentContext.sessionStartReason)
                    containsExactlyContextAttributes(expectedAttributes)
                    hasReplay(fakeHasReplay)
                    hasReplayStats(fakeReplayStats)
                    hasSource(fakeSourceViewEvent)
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toViewSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasSlownessInfo(fakeSlowRecords)
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasSessionActive(fakeParentContext.isSessionActive)
                    hasSampleRate(fakeSampleRate)
                }
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `M send event W handleEvent(StopView) on active view { still has ongoing resources }`(
        forge: Forge,
        @StringForgery key: String
    ) {
        // Given
        val mockResourceScope: RumScope = mock()
        whenever(mockResourceScope.handleEvent(any(), any())) doReturn mockResourceScope
        testedScope.activeResourceScopes[key] = mockResourceScope
        val attributes = forge.exhaustiveAttributes(excludedKeys = fakeAttributes.keys)
        val expectedAttributes = mutableMapOf<String, Any?>()
        expectedAttributes.putAll(fakeAttributes)
        expectedAttributes.putAll(attributes)

        // When
        val result = testedScope.handleEvent(
            RumRawEvent.StopView(fakeKey, attributes),
            mockWriter
        )

        // Then
        argumentCaptor<ViewEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))
            assertThat(lastValue)
                .apply {
                    hasTimestamp(resolveExpectedTimestamp(fakeEventTime.timestamp))
                    hasName(fakeKey.name)
                    hasUrl(fakeUrl)
                    hasDurationGreaterThan(1)
                    hasVersion(2)
                    hasErrorCount(0)
                    hasCrashCount(0)
                    hasResourceCount(0)
                    hasActionCount(0)
                    hasFrustrationCount(0)
                    hasLongTaskCount(0)
                    hasFrozenFrameCount(0)
                    hasCpuMetric(null)
                    hasMemoryMetric(null, null)
                    hasRefreshRateMetric(null, null)
                    isActive(true)
                    isSlowRendered(false)
                    hasNoCustomTimings()
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasAccountInfo(fakeDatadogContext.accountInfo)
                    hasViewId(testedScope.viewId)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasSessionType(fakeRumSessionType?.toView() ?: ViewEvent.ViewEventSessionType.USER)
                    hasNoSyntheticsTest()
                    hasStartReason(fakeParentContext.sessionStartReason)
                    containsExactlyContextAttributes(expectedAttributes)
                    hasReplay(fakeHasReplay)
                    hasReplayStats(fakeReplayStats)
                    hasSource(fakeSourceViewEvent)
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toViewSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasSlownessInfo(fakeSlowRecords)
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasSessionActive(fakeParentContext.isSessionActive)
                    hasSampleRate(fakeSampleRate)
                }
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `M send event with user extra attributes W handleEvent(StopView) on active view`() {
        // When
        val result = testedScope.handleEvent(
            RumRawEvent.StopView(fakeKey, emptyMap()),
            mockWriter
        )

        // Then
        argumentCaptor<ViewEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))
            assertThat(lastValue)
                .apply {
                    hasTimestamp(resolveExpectedTimestamp(fakeEventTime.timestamp))
                    hasName(fakeKey.name)
                    hasUrl(fakeUrl)
                    hasDurationGreaterThan(1)
                    hasVersion(2)
                    hasErrorCount(0)
                    hasCrashCount(0)
                    hasResourceCount(0)
                    hasActionCount(0)
                    hasFrustrationCount(0)
                    hasLongTaskCount(0)
                    hasFrozenFrameCount(0)
                    hasCpuMetric(null)
                    hasMemoryMetric(null, null)
                    hasRefreshRateMetric(null, null)
                    isActive(false)
                    isSlowRendered(false)
                    hasNoCustomTimings()
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasAccountInfo(fakeDatadogContext.accountInfo)
                    hasViewId(testedScope.viewId)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasSessionType(fakeRumSessionType?.toView() ?: ViewEvent.ViewEventSessionType.USER)
                    hasNoSyntheticsTest()
                    hasStartReason(fakeParentContext.sessionStartReason)
                    hasReplay(fakeHasReplay)
                    hasReplayStats(fakeReplayStats)
                    hasSource(fakeSourceViewEvent)
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toViewSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasSlownessInfo(
                        fakeSlowRecords,
                        fakeSlownessRate,
                        fakeFreezeRate
                    )
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasSessionActive(fakeParentContext.isSessionActive)
                    hasSampleRate(fakeSampleRate)
                }
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isNull()
    }

    @Test
    fun `M send event with updated global attributes W handleEvent(StopView) on active view`(
        forge: Forge
    ) {
        // Given
        val fakeGlobalAttributes = forge.aFilteredMap(excludedKeys = fakeAttributes.keys) {
            anHexadecimalString() to anAsciiString()
        }
        val expectedAttributes = mutableMapOf<String, Any?>()
        expectedAttributes.putAll(fakeAttributes)
        expectedAttributes.putAll(emptyMap())
        whenever(rumMonitor.mockInstance.getAttributes()) doReturn fakeGlobalAttributes

        testedScope = newRumViewScope()
        mockSessionReplayContext(testedScope)
        whenever(rumMonitor.mockInstance.getAttributes()) doReturn emptyMap()

        // When
        val result = testedScope.handleEvent(
            RumRawEvent.StopView(fakeKey, emptyMap()),
            mockWriter
        )

        // Then
        argumentCaptor<ViewEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))
            assertThat(lastValue)
                .apply {
                    hasTimestamp(resolveExpectedTimestamp(fakeEventTime.timestamp))
                    hasName(fakeKey.name)
                    hasUrl(fakeUrl)
                    hasDurationGreaterThan(1)
                    hasVersion(2)
                    hasErrorCount(0)
                    hasCrashCount(0)
                    hasResourceCount(0)
                    hasActionCount(0)
                    hasFrustrationCount(0)
                    hasLongTaskCount(0)
                    hasFrozenFrameCount(0)
                    hasCpuMetric(null)
                    hasMemoryMetric(null, null)
                    hasRefreshRateMetric(null, null)
                    isActive(false)
                    isSlowRendered(false)
                    hasNoCustomTimings()
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasAccountInfo(fakeDatadogContext.accountInfo)
                    hasViewId(testedScope.viewId)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasSessionType(fakeRumSessionType?.toView() ?: ViewEvent.ViewEventSessionType.USER)
                    hasNoSyntheticsTest()
                    hasStartReason(fakeParentContext.sessionStartReason)
                    hasReplay(fakeHasReplay)
                    hasReplayStats(fakeReplayStats)
                    containsExactlyContextAttributes(expectedAttributes)
                    hasSource(fakeSourceViewEvent)
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toViewSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasSlownessInfo(
                        fakeSlowRecords,
                        fakeSlownessRate,
                        fakeFreezeRate
                    )
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasSessionActive(fakeParentContext.isSessionActive)
                    hasSampleRate(fakeSampleRate)
                }
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isNull()
    }

    @Test
    fun `M send event with global attributes W handleEvent(StopView) on active view`(
        forge: Forge
    ) {
        // Given
        val fakeGlobalAttributes = forge.aFilteredMap(excludedKeys = fakeAttributes.keys) {
            anHexadecimalString() to anAsciiString()
        }
        val expectedAttributes = mutableMapOf<String, Any?>()
        expectedAttributes.putAll(fakeAttributes)
        expectedAttributes.putAll(fakeGlobalAttributes)
        whenever(rumMonitor.mockInstance.getAttributes()) doReturn fakeGlobalAttributes
        testedScope = newRumViewScope()
        mockSessionReplayContext(testedScope)

        // When
        val result = testedScope.handleEvent(
            RumRawEvent.StopView(fakeKey, emptyMap()),
            mockWriter
        )

        // Then
        argumentCaptor<ViewEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))
            assertThat(lastValue)
                .apply {
                    hasTimestamp(resolveExpectedTimestamp(fakeEventTime.timestamp))
                    hasName(fakeKey.name)
                    hasUrl(fakeUrl)
                    hasDurationGreaterThan(1)
                    hasVersion(2)
                    hasErrorCount(0)
                    hasCrashCount(0)
                    hasResourceCount(0)
                    hasActionCount(0)
                    hasFrustrationCount(0)
                    hasLongTaskCount(0)
                    hasFrozenFrameCount(0)
                    hasCpuMetric(null)
                    hasMemoryMetric(null, null)
                    hasRefreshRateMetric(null, null)
                    isActive(false)
                    isSlowRendered(false)
                    hasNoCustomTimings()
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasAccountInfo(fakeDatadogContext.accountInfo)
                    hasViewId(testedScope.viewId)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasSessionType(fakeRumSessionType?.toView() ?: ViewEvent.ViewEventSessionType.USER)
                    hasNoSyntheticsTest()
                    hasStartReason(fakeParentContext.sessionStartReason)
                    hasReplay(fakeHasReplay)
                    hasReplayStats(fakeReplayStats)
                    containsExactlyContextAttributes(expectedAttributes)
                    hasSource(fakeSourceViewEvent)
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toViewSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasSlownessInfo(
                        fakeSlowRecords,
                        fakeSlownessRate,
                        fakeFreezeRate
                    )
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasSessionActive(fakeParentContext.isSessionActive)
                    hasSampleRate(fakeSampleRate)
                }
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isNull()
    }

    @Test
    fun `M not take into account global attribute removal W handleEvent(StopView) on active view`(
        forge: Forge
    ) {
        // Given
        val fakeGlobalAttributeKey = forge.anAlphabeticalString()
        val fakeGlobalAttributeValue = forge.anAlphabeticalString()
        whenever(rumMonitor.mockInstance.getAttributes())
            .doReturn(mapOf(fakeGlobalAttributeKey to fakeGlobalAttributeValue))

        testedScope = newRumViewScope()
        mockSessionReplayContext(testedScope)
        val expectedAttributes = mutableMapOf<String, Any?>()
        expectedAttributes.putAll(fakeAttributes)
        expectedAttributes.put(fakeGlobalAttributeKey, fakeGlobalAttributeValue)

        // When

        val result = testedScope.handleEvent(
            RumRawEvent.StopView(fakeKey, emptyMap()),
            mockWriter
        )

        // Then
        argumentCaptor<ViewEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))
            assertThat(lastValue)
                .apply {
                    hasTimestamp(resolveExpectedTimestamp(fakeEventTime.timestamp))
                    hasName(fakeKey.name)
                    hasUrl(fakeUrl)
                    hasDurationGreaterThan(1)
                    hasVersion(2)
                    hasErrorCount(0)
                    hasCrashCount(0)
                    hasResourceCount(0)
                    hasActionCount(0)
                    hasFrustrationCount(0)
                    hasLongTaskCount(0)
                    hasFrozenFrameCount(0)
                    hasCpuMetric(null)
                    hasMemoryMetric(null, null)
                    hasRefreshRateMetric(null, null)
                    isActive(false)
                    isSlowRendered(false)
                    hasNoCustomTimings()
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasAccountInfo(fakeDatadogContext.accountInfo)
                    hasViewId(testedScope.viewId)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasSessionType(fakeRumSessionType?.toView() ?: ViewEvent.ViewEventSessionType.USER)
                    hasNoSyntheticsTest()
                    hasStartReason(fakeParentContext.sessionStartReason)
                    hasReplay(fakeHasReplay)
                    hasReplayStats(fakeReplayStats)
                    containsExactlyContextAttributes(expectedAttributes)
                    hasSource(fakeSourceViewEvent)
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toViewSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasSlownessInfo(
                        fakeSlowRecords,
                        fakeSlownessRate,
                        fakeFreezeRate
                    )
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasSessionActive(fakeParentContext.isSessionActive)
                    hasSampleRate(fakeSampleRate)
                }
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isNull()
    }

    @Test
    fun `M take into account global attribute update W handleEvent(StopView) on active view`(
        forge: Forge
    ) {
        // Given
        val fakeGlobalAttributeKey = forge.anAlphabeticalString()
        val fakeGlobalAttributeValue = forge.anAlphabeticalString()
        val fakeGlobalAttributeNewValue =
            fakeGlobalAttributeValue + forge.anAlphabeticalString(size = 2)
        whenever(rumMonitor.mockInstance.getAttributes())
            .doReturn(mapOf(fakeGlobalAttributeKey to fakeGlobalAttributeValue))

        testedScope = newRumViewScope()
        mockSessionReplayContext(testedScope)
        val expectedAttributes = mutableMapOf<String, Any?>()
        expectedAttributes.putAll(fakeAttributes)
        expectedAttributes[fakeGlobalAttributeKey] = fakeGlobalAttributeNewValue
        whenever(rumMonitor.mockInstance.getAttributes())
            .doReturn(mapOf(fakeGlobalAttributeKey to fakeGlobalAttributeNewValue))

        // When
        val result = testedScope.handleEvent(
            RumRawEvent.StopView(fakeKey, emptyMap()),
            mockWriter
        )

        // Then
        argumentCaptor<ViewEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))
            assertThat(lastValue)
                .apply {
                    hasTimestamp(resolveExpectedTimestamp(fakeEventTime.timestamp))
                    hasName(fakeKey.name)
                    hasUrl(fakeUrl)
                    hasDurationGreaterThan(1)
                    hasVersion(2)
                    hasErrorCount(0)
                    hasCrashCount(0)
                    hasResourceCount(0)
                    hasActionCount(0)
                    hasFrustrationCount(0)
                    hasLongTaskCount(0)
                    hasFrozenFrameCount(0)
                    hasCpuMetric(null)
                    hasMemoryMetric(null, null)
                    hasRefreshRateMetric(null, null)
                    isActive(false)
                    isSlowRendered(false)
                    hasNoCustomTimings()
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasAccountInfo(fakeDatadogContext.accountInfo)
                    hasViewId(testedScope.viewId)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasSessionType(fakeRumSessionType?.toView() ?: ViewEvent.ViewEventSessionType.USER)
                    hasNoSyntheticsTest()
                    hasStartReason(fakeParentContext.sessionStartReason)
                    hasReplay(fakeHasReplay)
                    hasReplayStats(fakeReplayStats)
                    containsExactlyContextAttributes(expectedAttributes)
                    hasSource(fakeSourceViewEvent)
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toViewSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasSlownessInfo(
                        fakeSlowRecords,
                        fakeSlownessRate,
                        fakeFreezeRate
                    )
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasSessionActive(fakeParentContext.isSessionActive)
                    hasSampleRate(fakeSampleRate)
                }
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isNull()
    }

    @Test
    fun `M send event once W handleEvent(StopView) twice on active view`(
        forge: Forge
    ) {
        // Given
        val attributes = forge.exhaustiveAttributes(excludedKeys = fakeAttributes.keys)
        val expectedAttributes = mutableMapOf<String, Any?>()
        expectedAttributes.putAll(fakeAttributes)
        expectedAttributes.putAll(attributes)

        // When
        val result = testedScope.handleEvent(
            RumRawEvent.StopView(fakeKey, attributes),
            mockWriter
        )
        val result2 = testedScope.handleEvent(
            RumRawEvent.StopView(fakeKey, attributes),
            mockWriter
        )

        // Then
        argumentCaptor<ViewEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))
            assertThat(lastValue)
                .apply {
                    hasTimestamp(resolveExpectedTimestamp(fakeEventTime.timestamp))
                    hasName(fakeKey.name)
                    hasUrl(fakeUrl)
                    hasDurationGreaterThan(1)
                    hasVersion(2)
                    hasErrorCount(0)
                    hasCrashCount(0)
                    hasResourceCount(0)
                    hasActionCount(0)
                    hasFrustrationCount(0)
                    hasLongTaskCount(0)
                    hasFrozenFrameCount(0)
                    hasCpuMetric(null)
                    hasMemoryMetric(null, null)
                    hasRefreshRateMetric(null, null)
                    isActive(false)
                    isSlowRendered(false)
                    hasNoCustomTimings()
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasAccountInfo(fakeDatadogContext.accountInfo)
                    hasViewId(testedScope.viewId)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasSessionType(fakeRumSessionType?.toView() ?: ViewEvent.ViewEventSessionType.USER)
                    hasNoSyntheticsTest()
                    hasStartReason(fakeParentContext.sessionStartReason)
                    hasReplay(fakeHasReplay)
                    hasReplayStats(fakeReplayStats)
                    containsExactlyContextAttributes(expectedAttributes)
                    hasSource(fakeSourceViewEvent)
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toViewSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasSlownessInfo(
                        fakeSlowRecords,
                        fakeSlownessRate,
                        fakeFreezeRate
                    )
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasSessionActive(fakeParentContext.isSessionActive)
                    hasSampleRate(fakeSampleRate)
                }
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isNull()
        assertThat(result2).isNull()
    }

    @Test
    fun `M returns not null W handleEvent(StopView) and a resource is still active`(
        @StringForgery key: String,
        forge: Forge
    ) {
        // Given
        testedScope.activeResourceScopes.put(key, mockChildScope)
        val attributes = forge.exhaustiveAttributes(excludedKeys = fakeAttributes.keys)
        val expectedAttributes = mutableMapOf<String, Any?>()
        expectedAttributes.putAll(fakeAttributes)
        expectedAttributes.putAll(attributes)

        // When
        val result = testedScope.handleEvent(
            RumRawEvent.StopView(fakeKey, attributes),
            mockWriter
        )

        // Then
        argumentCaptor<ViewEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))
            assertThat(lastValue)
                .apply {
                    hasTimestamp(resolveExpectedTimestamp(fakeEventTime.timestamp))
                    hasName(fakeKey.name)
                    hasUrl(fakeUrl)
                    hasDurationGreaterThan(1)
                    hasVersion(2)
                    hasErrorCount(0)
                    hasCrashCount(0)
                    hasResourceCount(0)
                    hasActionCount(0)
                    hasFrustrationCount(0)
                    hasLongTaskCount(0)
                    hasFrozenFrameCount(0)
                    hasCpuMetric(null)
                    hasMemoryMetric(null, null)
                    hasRefreshRateMetric(null, null)
                    isActive(true)
                    isSlowRendered(false)
                    hasNoCustomTimings()
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasAccountInfo(fakeDatadogContext.accountInfo)
                    hasViewId(testedScope.viewId)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasSessionType(fakeRumSessionType?.toView() ?: ViewEvent.ViewEventSessionType.USER)
                    hasNoSyntheticsTest()
                    hasStartReason(fakeParentContext.sessionStartReason)
                    hasReplay(fakeHasReplay)
                    hasReplayStats(fakeReplayStats)
                    containsExactlyContextAttributes(expectedAttributes)
                    hasSource(fakeSourceViewEvent)
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toViewSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasSlownessInfo(fakeSlowRecords)
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasSessionActive(fakeParentContext.isSessionActive)
                    hasSampleRate(fakeSampleRate)
                }
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `M do nothing W handleEvent(StopView) on active view without matching key`(
        @Forgery key: RumScopeKey,
        forge: Forge
    ) {
        // Given
        val attributes = forge.exhaustiveAttributes(excludedKeys = fakeAttributes.keys)
        val expectedAttributes = mutableMapOf<String, Any?>()
        expectedAttributes.putAll(fakeAttributes)
        expectedAttributes.putAll(attributes)

        // When
        val result = testedScope.handleEvent(
            RumRawEvent.StopView(key, attributes),
            mockWriter
        )

        // Then
        verifyNoInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `M do nothing W handleEvent(StopView) on stopped view`(
        forge: Forge
    ) {
        // Given
        val attributes = forge.exhaustiveAttributes(excludedKeys = fakeAttributes.keys)
        val expectedAttributes = mutableMapOf<String, Any?>()
        expectedAttributes.putAll(fakeAttributes)
        expectedAttributes.putAll(attributes)
        testedScope.stopped = true

        // When
        val result = testedScope.handleEvent(
            RumRawEvent.StopView(fakeKey, attributes),
            mockWriter
        )

        // Then
        verifyNoInteractions(mockWriter)
        assertThat(result).isNull()
    }

    @Test
    fun `M send event W handleEvent(ErrorSent) on active view`(
        @LongForgery(1) pending: Long
    ) {
        // Given
        fakeEvent = RumRawEvent.ErrorSent(testedScope.viewId)
        testedScope.pendingErrorCount = pending

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        argumentCaptor<ViewEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))
            assertThat(lastValue)
                .apply {
                    hasTimestamp(resolveExpectedTimestamp(fakeEventTime.timestamp))
                    hasName(fakeKey.name)
                    hasUrl(fakeUrl)
                    hasDurationGreaterThan(1)
                    hasVersion(2)
                    hasErrorCount(1)
                    hasCrashCount(0)
                    hasResourceCount(0)
                    hasActionCount(0)
                    hasFrustrationCount(0)
                    hasLongTaskCount(0)
                    hasFrozenFrameCount(0)
                    hasCpuMetric(null)
                    hasMemoryMetric(null, null)
                    hasRefreshRateMetric(null, null)
                    isActive(true)
                    isSlowRendered(false)
                    hasNoCustomTimings()
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasAccountInfo(fakeDatadogContext.accountInfo)
                    hasViewId(testedScope.viewId)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasSessionType(fakeRumSessionType?.toView() ?: ViewEvent.ViewEventSessionType.USER)
                    hasNoSyntheticsTest()
                    hasStartReason(fakeParentContext.sessionStartReason)
                    hasReplay(fakeHasReplay)
                    hasReplayStats(fakeReplayStats)
                    containsExactlyContextAttributes(fakeAttributes)
                    hasSource(fakeSourceViewEvent)
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toViewSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasSlownessInfo(fakeSlowRecords)
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasSessionActive(fakeParentContext.isSessionActive)
                    hasSampleRate(fakeSampleRate)
                }
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
        assertThat(testedScope.pendingErrorCount).isEqualTo(pending - 1)
    }

    @Test
    fun `M do nothing W handleEvent(ErrorSent) on active view {unknown viewId}`(
        @Forgery viewUuid: UUID,
        @LongForgery(1) pending: Long
    ) {
        // Given
        val viewId = viewUuid.toString()
        assumeTrue(viewId != testedScope.viewId)
        fakeEvent = RumRawEvent.ErrorSent(viewId)
        testedScope.pendingErrorCount = pending

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        verifyNoInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
        assertThat(testedScope.pendingErrorCount).isEqualTo(pending)
    }

    @Test
    fun `M send event W handleEvent(ResourceSent) on active view`(
        @LongForgery(1) pending: Long,
        forge: Forge

    ) {
        // Given
        testedScope.pendingResourceCount = pending
        val fakeResourceEvent = forge.getForgery<RumRawEvent.ResourceSent>().copy(viewId = testedScope.viewId)

        // When
        val result = testedScope.handleEvent(fakeResourceEvent, mockWriter)

        // Then
        argumentCaptor<ViewEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))
            assertThat(lastValue)
                .apply {
                    hasTimestamp(resolveExpectedTimestamp(fakeEventTime.timestamp))
                    hasName(fakeKey.name)
                    hasUrl(fakeUrl)
                    hasDurationGreaterThan(1)
                    hasVersion(2)
                    hasErrorCount(0)
                    hasCrashCount(0)
                    hasResourceCount(1)
                    hasActionCount(0)
                    hasFrustrationCount(0)
                    hasLongTaskCount(0)
                    hasFrozenFrameCount(0)
                    hasCpuMetric(null)
                    hasMemoryMetric(null, null)
                    hasRefreshRateMetric(null, null)
                    isActive(true)
                    isSlowRendered(false)
                    hasNoCustomTimings()
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasAccountInfo(fakeDatadogContext.accountInfo)
                    hasViewId(testedScope.viewId)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasSessionType(fakeRumSessionType?.toView() ?: ViewEvent.ViewEventSessionType.USER)
                    hasNoSyntheticsTest()
                    hasStartReason(fakeParentContext.sessionStartReason)
                    hasReplay(fakeHasReplay)
                    hasReplayStats(fakeReplayStats)
                    containsExactlyContextAttributes(fakeAttributes)
                    hasSource(fakeSourceViewEvent)
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toViewSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasSlownessInfo(fakeSlowRecords)
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasSessionActive(fakeParentContext.isSessionActive)
                    hasSampleRate(fakeSampleRate)
                }
        }
        verifyNoMoreInteractions(mockWriter)
        verify(mockNetworkSettledMetricResolver).resourceWasStopped(
            InternalResourceContext(
                fakeResourceEvent.resourceId,
                fakeResourceEvent.resourceEndTimestampInNanos
            )
        )
        assertThat(result).isSameAs(testedScope)
        assertThat(testedScope.pendingResourceCount).isEqualTo(pending - 1)
    }

    @Test
    fun `M do nothing W handleEvent(ResourceSent) on active view {unknown viewId}`(
        @Forgery viewUuid: UUID,
        @LongForgery(1) pending: Long,
        forge: Forge
    ) {
        // Given
        testedScope.pendingResourceCount = pending
        val viewId = viewUuid.toString()
        assumeTrue(viewId != testedScope.viewId)
        fakeEvent = forge.getForgery<RumRawEvent.ResourceSent>().copy(viewId = viewId)

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        verifyNoInteractions(mockWriter)
        verify(mockNetworkSettledMetricResolver, never()).resourceWasStopped(any())
        assertThat(result).isSameAs(testedScope)
        assertThat(testedScope.pendingResourceCount).isEqualTo(pending)
    }

    @Test
    fun `M send event W handleEvent(ActionSent) on active view`(
        @LongForgery(1) pending: Long,
        @IntForgery(0) frustrationCount: Int,
        @Forgery actionType: ActionEvent.ActionEventActionType,
        @LongForgery(0) actionEventTimestamp: Long
    ) {
        // Given
        fakeEvent = RumRawEvent.ActionSent(testedScope.viewId, frustrationCount, actionType, actionEventTimestamp)
        testedScope.pendingActionCount = pending

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        argumentCaptor<ViewEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))
            assertThat(lastValue)
                .apply {
                    hasTimestamp(resolveExpectedTimestamp(fakeEventTime.timestamp))
                    hasName(fakeKey.name)
                    hasUrl(fakeUrl)
                    hasDurationGreaterThan(1)
                    hasVersion(2)
                    hasErrorCount(0)
                    hasCrashCount(0)
                    hasResourceCount(0)
                    hasActionCount(1)
                    hasFrustrationCount(frustrationCount.toLong())
                    hasLongTaskCount(0)
                    hasFrozenFrameCount(0)
                    hasCpuMetric(null)
                    hasMemoryMetric(null, null)
                    hasRefreshRateMetric(null, null)
                    isActive(true)
                    isSlowRendered(false)
                    hasNoCustomTimings()
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasAccountInfo(fakeDatadogContext.accountInfo)
                    hasViewId(testedScope.viewId)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasSessionType(fakeRumSessionType?.toView() ?: ViewEvent.ViewEventSessionType.USER)
                    hasNoSyntheticsTest()
                    hasStartReason(fakeParentContext.sessionStartReason)
                    hasReplay(fakeHasReplay)
                    hasReplayStats(fakeReplayStats)
                    containsExactlyContextAttributes(fakeAttributes)
                    hasSource(fakeSourceViewEvent)
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toViewSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasSlownessInfo(fakeSlowRecords)
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasSessionActive(fakeParentContext.isSessionActive)
                    hasSampleRate(fakeSampleRate)
                }
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
        assertThat(testedScope.pendingActionCount).isEqualTo(pending - 1)
    }

    @Test
    fun `M do nothing W handleEvent(ActionSent) on active view {unknown viewId}`(
        @Forgery viewUuid: UUID,
        @LongForgery(1) pending: Long,
        @IntForgery(0) frustrationCount: Int,
        @Forgery actionType: ActionEvent.ActionEventActionType,
        @LongForgery(0) actionEventTimestamp: Long
    ) {
        // Given
        val viewId = viewUuid.toString()
        assumeTrue(viewId != testedScope.viewId)
        fakeEvent = RumRawEvent.ActionSent(viewId, frustrationCount, actionType, actionEventTimestamp)
        testedScope.pendingActionCount = pending

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        verifyNoInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
        assertThat(testedScope.pendingActionCount).isEqualTo(pending)
    }

    @Test
    fun `M send event W handleEvent(LongTaskSent) on active view {not frozen}`(
        @LongForgery(1) pendingLongTask: Long,
        @LongForgery(1) pendingFrozenFrame: Long
    ) {
        // Given
        fakeEvent = RumRawEvent.LongTaskSent(testedScope.viewId)
        testedScope.pendingLongTaskCount = pendingLongTask
        testedScope.pendingFrozenFrameCount = pendingFrozenFrame

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        argumentCaptor<ViewEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))
            assertThat(lastValue)
                .apply {
                    hasTimestamp(resolveExpectedTimestamp(fakeEventTime.timestamp))
                    hasName(fakeKey.name)
                    hasUrl(fakeUrl)
                    hasDurationGreaterThan(1)
                    hasVersion(2)
                    hasErrorCount(0)
                    hasCrashCount(0)
                    hasResourceCount(0)
                    hasActionCount(0)
                    hasFrustrationCount(0)
                    hasLongTaskCount(1)
                    hasFrozenFrameCount(0)
                    hasCpuMetric(null)
                    hasMemoryMetric(null, null)
                    hasRefreshRateMetric(null, null)
                    isActive(true)
                    isSlowRendered(false)
                    hasNoCustomTimings()
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasAccountInfo(fakeDatadogContext.accountInfo)
                    hasViewId(testedScope.viewId)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasSessionType(fakeRumSessionType?.toView() ?: ViewEvent.ViewEventSessionType.USER)
                    hasNoSyntheticsTest()
                    hasStartReason(fakeParentContext.sessionStartReason)
                    hasReplay(fakeHasReplay)
                    hasReplayStats(fakeReplayStats)
                    containsExactlyContextAttributes(fakeAttributes)
                    hasSource(fakeSourceViewEvent)
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toViewSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasSlownessInfo(fakeSlowRecords)
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasSessionActive(fakeParentContext.isSessionActive)
                    hasSampleRate(fakeSampleRate)
                }
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
        assertThat(testedScope.pendingLongTaskCount).isEqualTo(pendingLongTask - 1)
        assertThat(testedScope.pendingFrozenFrameCount).isEqualTo(pendingFrozenFrame)
    }

    @Test
    fun `M send event W handleEvent(LongTaskSent) on active view {frozen}`(
        @LongForgery(1) pendingLongTask: Long,
        @LongForgery(1) pendingFrozenFrame: Long
    ) {
        // Given
        fakeEvent = RumRawEvent.LongTaskSent(testedScope.viewId, true)
        testedScope.pendingLongTaskCount = pendingLongTask
        testedScope.pendingFrozenFrameCount = pendingFrozenFrame

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        argumentCaptor<ViewEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))
            assertThat(lastValue)
                .apply {
                    hasTimestamp(resolveExpectedTimestamp(fakeEventTime.timestamp))
                    hasName(fakeKey.name)
                    hasUrl(fakeUrl)
                    hasDurationGreaterThan(1)
                    hasVersion(2)
                    hasErrorCount(0)
                    hasCrashCount(0)
                    hasResourceCount(0)
                    hasActionCount(0)
                    hasFrustrationCount(0)
                    hasLongTaskCount(1)
                    hasFrozenFrameCount(1)
                    hasCpuMetric(null)
                    hasMemoryMetric(null, null)
                    hasRefreshRateMetric(null, null)
                    isActive(true)
                    isSlowRendered(false)
                    hasNoCustomTimings()
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasAccountInfo(fakeDatadogContext.accountInfo)
                    hasViewId(testedScope.viewId)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasSessionType(fakeRumSessionType?.toView() ?: ViewEvent.ViewEventSessionType.USER)
                    hasNoSyntheticsTest()
                    hasStartReason(fakeParentContext.sessionStartReason)
                    hasReplay(fakeHasReplay)
                    hasReplayStats(fakeReplayStats)
                    containsExactlyContextAttributes(fakeAttributes)
                    hasSource(fakeSourceViewEvent)
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toViewSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasSlownessInfo(fakeSlowRecords)
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasSessionActive(fakeParentContext.isSessionActive)
                    hasSampleRate(fakeSampleRate)
                }
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
        assertThat(testedScope.pendingLongTaskCount).isEqualTo(pendingLongTask - 1)
        assertThat(testedScope.pendingFrozenFrameCount).isEqualTo(pendingFrozenFrame - 1)
    }

    @Test
    fun `M do nothing W handleEvent(LongTaskSent) on active view {unknown viewId}`(
        @Forgery viewUuid: UUID,
        @LongForgery(1) pending: Long
    ) {
        // Given
        val viewId = viewUuid.toString()
        assumeTrue(viewId != testedScope.viewId)
        fakeEvent = RumRawEvent.LongTaskSent(viewId)
        testedScope.pendingLongTaskCount = pending

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        verifyNoInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
        assertThat(testedScope.pendingLongTaskCount).isEqualTo(pending)
    }

    @Test
    fun `M send event with global attributes W handleEvent(ApplicationStarted) on active view`(
        @LongForgery(0) duration: Long,
        forge: Forge
    ) {
        // Given
        val eventTime = Time()
        fakeEvent = RumRawEvent.ApplicationStarted(eventTime, duration)
        val attributes = forgeGlobalAttributes(forge, fakeAttributes)
        whenever(rumMonitor.mockInstance.getAttributes()) doReturn attributes

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        argumentCaptor<ActionEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))
            assertThat(firstValue)
                .apply {
                    hasNonNullId()
                    hasTimestamp(testedScope.eventTimestamp)
                    hasType(ActionEvent.ActionEventActionType.APPLICATION_START)
                    hasNoTarget()
                    hasDuration(duration)
                    hasResourceCount(0)
                    hasErrorCount(0)
                    hasCrashCount(0)
                    hasLongTaskCount(0)
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasAccountInfo(fakeDatadogContext.accountInfo)
                    hasView(testedScope.viewId, testedScope.key.name, testedScope.url)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasSessionType(fakeRumSessionType?.toAction() ?: ActionEvent.ActionEventSessionType.USER)
                    hasNoSyntheticsTest()
                    hasStartReason(fakeParentContext.sessionStartReason)
                    hasReplay(false)
                    hasSource(fakeSourceActionEvent)
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toActionSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    containsExactlyContextAttributes(attributes)
                    hasSampleRate(fakeSampleRate)
                }
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `M send event with synthetics info W handleEvent(ApplicationStarted) on active view`(
        @LongForgery(0) duration: Long,
        @StringForgery fakeTestId: String,
        @StringForgery fakeResultId: String,
        forge: Forge
    ) {
        // Given
        val eventTime = Time()
        fakeEvent = RumRawEvent.ApplicationStarted(eventTime, duration)
        val attributes = forgeGlobalAttributes(forge, fakeAttributes)
        whenever(rumMonitor.mockInstance.getAttributes()) doReturn attributes
        fakeParentContext = fakeParentContext.copy(
            syntheticsTestId = fakeTestId,
            syntheticsResultId = fakeResultId
        )
        whenever(mockParentScope.getRumContext()) doReturn fakeParentContext

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        argumentCaptor<ActionEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))
            assertThat(firstValue)
                .apply {
                    hasNonNullId()
                    hasTimestamp(testedScope.eventTimestamp)
                    hasType(ActionEvent.ActionEventActionType.APPLICATION_START)
                    hasNoTarget()
                    hasDuration(duration)
                    hasResourceCount(0)
                    hasErrorCount(0)
                    hasCrashCount(0)
                    hasLongTaskCount(0)
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasAccountInfo(fakeDatadogContext.accountInfo)
                    hasView(testedScope.viewId, testedScope.key.name, testedScope.url)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasSessionType(fakeRumSessionType?.toAction() ?: ActionEvent.ActionEventSessionType.SYNTHETICS)
                    hasSyntheticsTest(fakeTestId, fakeResultId)
                    hasStartReason(fakeParentContext.sessionStartReason)
                    hasReplay(false)
                    hasSource(fakeSourceActionEvent)
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toActionSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    containsExactlyContextAttributes(attributes)
                    hasSampleRate(fakeSampleRate)
                }
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `M send event W handleEvent(ErrorSent) on stopped view`(
        @LongForgery(1L, 500_000_000L) durationNs: Long
    ) {
        // Given
        testedScope.stopped = true
        testedScope.stoppedNanos = fakeEventTime.nanoTime + durationNs
        testedScope.pendingErrorCount = 1
        fakeEvent = RumRawEvent.ErrorSent(testedScope.viewId)

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        argumentCaptor<ViewEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))
            assertThat(lastValue)
                .apply {
                    hasTimestamp(resolveExpectedTimestamp(fakeEventTime.timestamp))
                    hasName(fakeKey.name)
                    hasUrl(fakeUrl)
                    hasDuration(durationNs)
                    hasVersion(2)
                    hasErrorCount(1)
                    hasCrashCount(0)
                    hasResourceCount(0)
                    hasActionCount(0)
                    hasFrustrationCount(0)
                    hasLongTaskCount(0)
                    hasFrozenFrameCount(0)
                    hasCpuMetric(null)
                    hasMemoryMetric(null, null)
                    hasRefreshRateMetric(null, null)
                    isActive(false)
                    isSlowRendered(false)
                    hasNoCustomTimings()
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasAccountInfo(fakeDatadogContext.accountInfo)
                    hasViewId(testedScope.viewId)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasSessionType(fakeRumSessionType?.toView() ?: ViewEvent.ViewEventSessionType.USER)
                    hasNoSyntheticsTest()
                    hasStartReason(fakeParentContext.sessionStartReason)
                    hasReplay(fakeHasReplay)
                    hasReplayStats(fakeReplayStats)
                    containsExactlyContextAttributes(fakeAttributes)
                    hasSource(fakeSourceViewEvent)
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toViewSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasSlownessInfo(
                        fakeSlowRecords,
                        fakeSlownessRate,
                        fakeFreezeRate
                    )
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasSessionActive(fakeParentContext.isSessionActive)
                    hasSampleRate(fakeSampleRate)
                }
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isNull()
        assertThat(testedScope.pendingErrorCount).isEqualTo(0)
    }

    @Test
    fun `M do nothing W handleEvent(ErrorSent) on stopped view {unknown viewId}`(
        @Forgery viewUuid: UUID,
        @LongForgery(1) pending: Long
    ) {
        // Given
        testedScope.stopped = true
        testedScope.pendingErrorCount = pending
        val viewId = viewUuid.toString()
        assumeTrue(viewId != testedScope.viewId)
        fakeEvent = RumRawEvent.ErrorSent(viewId)

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        verifyNoInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
        assertThat(testedScope.pendingErrorCount).isEqualTo(pending)
    }

    @Test
    fun `M send event W handleEvent(ResourceSent) on stopped view`(
        forge: Forge,
        @LongForgery(0L, 500_000_000L) durationNs: Long
    ) {
        // Given
        testedScope.stopped = true
        testedScope.stoppedNanos = fakeEventTime.nanoTime + durationNs
        testedScope.pendingResourceCount = 1
        val fakeResourceSent = forge.getForgery<RumRawEvent.ResourceSent>().copy(viewId = testedScope.viewId)

        // When
        val result = testedScope.handleEvent(fakeResourceSent, mockWriter)

        // Then
        argumentCaptor<ViewEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))
            assertThat(lastValue)
                .apply {
                    hasTimestamp(resolveExpectedTimestamp(fakeEventTime.timestamp))
                    hasName(fakeKey.name)
                    hasUrl(fakeUrl)
                    hasDuration(durationNs)
                    hasVersion(2)
                    hasErrorCount(0)
                    hasCrashCount(0)
                    hasResourceCount(1)
                    hasActionCount(0)
                    hasFrustrationCount(0)
                    hasLongTaskCount(0)
                    hasFrozenFrameCount(0)
                    hasCpuMetric(null)
                    hasMemoryMetric(null, null)
                    hasRefreshRateMetric(null, null)
                    isActive(false)
                    isSlowRendered(false)
                    hasNoCustomTimings()
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasAccountInfo(fakeDatadogContext.accountInfo)
                    hasViewId(testedScope.viewId)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasSessionType(fakeRumSessionType?.toView() ?: ViewEvent.ViewEventSessionType.USER)
                    hasNoSyntheticsTest()
                    hasStartReason(fakeParentContext.sessionStartReason)
                    hasReplay(fakeHasReplay)
                    hasReplayStats(fakeReplayStats)
                    containsExactlyContextAttributes(fakeAttributes)
                    hasSource(fakeSourceViewEvent)
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toViewSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasSlownessInfo(
                        fakeSlowRecords,
                        fakeSlownessRate,
                        fakeFreezeRate
                    )
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasSessionActive(fakeParentContext.isSessionActive)
                    hasSampleRate(fakeSampleRate)
                }
        }
        verifyNoMoreInteractions(mockWriter)
        verify(mockNetworkSettledMetricResolver).resourceWasStopped(
            InternalResourceContext(
                resourceId = fakeResourceSent.resourceId,
                eventCreatedAtNanos = fakeResourceSent.resourceEndTimestampInNanos
            )
        )
        assertThat(result).isNull()
        assertThat(testedScope.pendingResourceCount).isEqualTo(0)
    }

    @Test
    fun `M do nothing W handleEvent(ResourceSent) on stopped view {unknown viewId}`(
        @Forgery viewUuid: UUID,
        @LongForgery(1) pending: Long,
        forge: Forge
    ) {
        // Given
        testedScope.stopped = true
        testedScope.pendingResourceCount = pending
        val viewId = viewUuid.toString()
        assumeTrue(viewId != testedScope.viewId)
        fakeEvent = forge.getForgery<RumRawEvent.ResourceSent>().copy(viewId = viewId)

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        verifyNoInteractions(mockWriter)
        verify(mockNetworkSettledMetricResolver, never()).resourceWasStopped(any())
        assertThat(result).isSameAs(testedScope)
        assertThat(testedScope.pendingResourceCount).isEqualTo(pending)
    }

    @Test
    fun `M send event W handleEvent(ActionSent) on stopped view`(
        @IntForgery(0) frustrationCount: Int,
        @Forgery actionType: ActionEvent.ActionEventActionType,
        @LongForgery(0L) actionEventTimestamp: Long,
        @LongForgery(0L, 500_000_000L) durationNs: Long
    ) {
        // Given
        testedScope.stopped = true
        testedScope.stoppedNanos = fakeEventTime.nanoTime + durationNs
        testedScope.pendingActionCount = 1
        fakeEvent = RumRawEvent.ActionSent(testedScope.viewId, frustrationCount, actionType, actionEventTimestamp)

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        argumentCaptor<ViewEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))
            assertThat(lastValue)
                .apply {
                    hasTimestamp(resolveExpectedTimestamp(fakeEventTime.timestamp))
                    hasName(fakeKey.name)
                    hasUrl(fakeUrl)
                    hasDurationGreaterThan(1)
                    hasVersion(2)
                    hasErrorCount(0)
                    hasCrashCount(0)
                    hasResourceCount(0)
                    hasActionCount(1)
                    hasFrustrationCount(frustrationCount.toLong())
                    hasLongTaskCount(0)
                    hasFrozenFrameCount(0)
                    hasCpuMetric(null)
                    hasMemoryMetric(null, null)
                    hasRefreshRateMetric(null, null)
                    isActive(false)
                    isSlowRendered(false)
                    hasNoCustomTimings()
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasAccountInfo(fakeDatadogContext.accountInfo)
                    hasViewId(testedScope.viewId)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasSessionType(fakeRumSessionType?.toView() ?: ViewEvent.ViewEventSessionType.USER)
                    hasNoSyntheticsTest()
                    hasStartReason(fakeParentContext.sessionStartReason)
                    hasReplay(fakeHasReplay)
                    hasReplayStats(fakeReplayStats)
                    containsExactlyContextAttributes(fakeAttributes)
                    hasSource(fakeSourceViewEvent)
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toViewSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasSlownessInfo(
                        fakeSlowRecords,
                        fakeSlownessRate,
                        fakeFreezeRate
                    )
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasSessionActive(fakeParentContext.isSessionActive)
                    hasSampleRate(fakeSampleRate)
                }
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isNull()
        assertThat(testedScope.pendingActionCount).isEqualTo(0)
    }

    @Test
    fun `M do nothing W handleEvent(ActionSent) on stopped view {unknown viewId}`(
        @Forgery viewUuid: UUID,
        @LongForgery(1) pending: Long,
        @IntForgery(0) frustrationCount: Int,
        @Forgery actionType: ActionEvent.ActionEventActionType,
        @LongForgery(0) actionEventTimestamp: Long
    ) {
        // Given
        testedScope.stopped = true
        testedScope.pendingActionCount = pending
        val viewId = viewUuid.toString()
        assumeTrue(viewId != testedScope.viewId)
        fakeEvent = RumRawEvent.ActionSent(viewId, frustrationCount, actionType, actionEventTimestamp)

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        verifyNoInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
        assertThat(testedScope.pendingActionCount).isEqualTo(pending)
    }

    @Test
    fun `M send event W handleEvent(LongTaskSent) on stopped view`(
        @LongForgery(0L, 500_000_000L) durationNs: Long
    ) {
        // Given
        testedScope.stopped = true
        testedScope.stoppedNanos = fakeEventTime.nanoTime + durationNs
        testedScope.pendingLongTaskCount = 1
        fakeEvent = RumRawEvent.LongTaskSent(testedScope.viewId)

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        argumentCaptor<ViewEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))
            assertThat(lastValue)
                .apply {
                    hasTimestamp(resolveExpectedTimestamp(fakeEventTime.timestamp))
                    hasName(fakeKey.name)
                    hasUrl(fakeUrl)
                    hasDurationGreaterThan(1)
                    hasVersion(2)
                    hasErrorCount(0)
                    hasCrashCount(0)
                    hasResourceCount(0)
                    hasActionCount(0)
                    hasFrustrationCount(0)
                    hasLongTaskCount(1)
                    hasFrozenFrameCount(0)
                    hasCpuMetric(null)
                    hasMemoryMetric(null, null)
                    hasRefreshRateMetric(null, null)
                    isActive(false)
                    isSlowRendered(false)
                    hasNoCustomTimings()
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasAccountInfo(fakeDatadogContext.accountInfo)
                    hasViewId(testedScope.viewId)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasSessionType(fakeRumSessionType?.toView() ?: ViewEvent.ViewEventSessionType.USER)
                    hasNoSyntheticsTest()
                    hasStartReason(fakeParentContext.sessionStartReason)
                    hasReplay(fakeHasReplay)
                    hasReplayStats(fakeReplayStats)
                    containsExactlyContextAttributes(fakeAttributes)
                    hasSource(fakeSourceViewEvent)
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toViewSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasSlownessInfo(
                        fakeSlowRecords,
                        fakeSlownessRate,
                        fakeFreezeRate
                    )
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasSessionActive(fakeParentContext.isSessionActive)
                    hasSampleRate(fakeSampleRate)
                }
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isNull()
        assertThat(testedScope.pendingLongTaskCount).isEqualTo(0)
    }

    @Test
    fun `M do nothing W handleEvent(LongTaskSent) on stopped view {unknown viewId}`(
        @Forgery viewUuid: UUID,
        @LongForgery(1) pending: Long
    ) {
        // Given
        testedScope.stopped = true
        testedScope.pendingLongTaskCount = pending
        val viewId = viewUuid.toString()
        assumeTrue(viewId != testedScope.viewId)
        fakeEvent = RumRawEvent.LongTaskSent(viewId)

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        verifyNoInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
        assertThat(testedScope.pendingLongTaskCount).isEqualTo(pending)
    }

    @Test
    fun `M close the scope W handleEvent(ActionSent) on stopped view { ApplicationStarted }`(
        @LongForgery(0) duration: Long,
        @IntForgery(0) frustrationCount: Int,
        @Forgery actionType: ActionEvent.ActionEventActionType,
        @LongForgery(0) actionEventTimestamp: Long
    ) {
        // Given
        testedScope.stopped = true
        val eventTime = Time()
        fakeEvent = RumRawEvent.ApplicationStarted(eventTime, duration)
        val fakeActionSent =
            RumRawEvent.ActionSent(testedScope.viewId, frustrationCount, actionType, actionEventTimestamp)

        // When
        testedScope.handleEvent(fakeEvent, mockWriter)
        val result = testedScope.handleEvent(fakeActionSent, mockWriter)

        // Then
        argumentCaptor<Any> {
            verify(mockWriter, times(2)).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))
            assertThat(firstValue as ActionEvent)
                .apply {
                    hasNonNullId()
                    hasTimestamp(testedScope.eventTimestamp)
                    hasType(ActionEvent.ActionEventActionType.APPLICATION_START)
                    hasNoTarget()
                    hasDuration(duration)
                    hasResourceCount(0)
                    hasErrorCount(0)
                    hasCrashCount(0)
                    hasLongTaskCount(0)
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasAccountInfo(fakeDatadogContext.accountInfo)
                    hasView(testedScope.viewId, testedScope.key.name, testedScope.url)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasSessionType(fakeRumSessionType?.toAction() ?: ActionEvent.ActionEventSessionType.USER)
                    hasNoSyntheticsTest()
                    hasStartReason(fakeParentContext.sessionStartReason)
                    hasReplay(false)
                    hasSource(fakeSourceActionEvent)
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toActionSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                }
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isNull()
    }

    @Test
    fun `M close the scope W handleEvent(ActionDropped) on stopped view { ApplicationStarted }`(
        @LongForgery(0) duration: Long
    ) {
        // Given
        testedScope.stopped = true
        val eventTime = Time()
        fakeEvent = RumRawEvent.ApplicationStarted(eventTime, duration)
        val fakeActionSent = RumRawEvent.ActionDropped(testedScope.viewId)

        // When
        testedScope.handleEvent(fakeEvent, mockWriter)
        val result = testedScope.handleEvent(fakeActionSent, mockWriter)

        // Then
        argumentCaptor<ActionEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))
            assertThat(firstValue)
                .apply {
                    hasNonNullId()
                    hasTimestamp(testedScope.eventTimestamp)
                    hasType(ActionEvent.ActionEventActionType.APPLICATION_START)
                    hasNoTarget()
                    hasDuration(duration)
                    hasResourceCount(0)
                    hasErrorCount(0)
                    hasCrashCount(0)
                    hasLongTaskCount(0)
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasAccountInfo(fakeDatadogContext.accountInfo)
                    hasView(testedScope.viewId, testedScope.key.name, testedScope.url)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasSessionType(fakeRumSessionType?.toAction() ?: ActionEvent.ActionEventSessionType.USER)
                    hasNoSyntheticsTest()
                    hasStartReason(fakeParentContext.sessionStartReason)
                    hasReplay(false)
                    hasSource(fakeSourceActionEvent)
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toActionSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasSampleRate(fakeSampleRate)
                }
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isNull()
    }

    @Test
    fun `M do nothing W handleEvent(KeepAlive) on stopped view`() {
        // Given
        testedScope.stopped = true

        // When
        val result = testedScope.handleEvent(
            RumRawEvent.KeepAlive(),
            mockWriter
        )

        // Then
        verifyNoInteractions(mockWriter)
        assertThat(result).isNull()
    }

    @Test
    fun `M send event W handleEvent(KeepAlive) on active view`() {
        // When
        val result = testedScope.handleEvent(
            RumRawEvent.KeepAlive(),
            mockWriter
        )

        // Then
        argumentCaptor<ViewEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))
            assertThat(lastValue)
                .apply {
                    hasTimestamp(resolveExpectedTimestamp(fakeEventTime.timestamp))
                    hasName(fakeKey.name)
                    hasUrl(fakeUrl)
                    hasDurationGreaterThan(1)
                    hasVersion(2)
                    hasErrorCount(0)
                    hasCrashCount(0)
                    hasResourceCount(0)
                    hasActionCount(0)
                    hasFrustrationCount(0)
                    hasLongTaskCount(0)
                    hasFrozenFrameCount(0)
                    hasCpuMetric(null)
                    hasMemoryMetric(null, null)
                    hasRefreshRateMetric(null, null)
                    isActive(true)
                    isSlowRendered(false)
                    hasNoCustomTimings()
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasAccountInfo(fakeDatadogContext.accountInfo)
                    hasViewId(testedScope.viewId)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasSessionType(fakeRumSessionType?.toView() ?: ViewEvent.ViewEventSessionType.USER)
                    hasNoSyntheticsTest()
                    hasStartReason(fakeParentContext.sessionStartReason)
                    hasReplay(fakeHasReplay)
                    hasReplayStats(fakeReplayStats)
                    containsExactlyContextAttributes(fakeAttributes)
                    hasSource(fakeSourceViewEvent)
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toViewSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasSlownessInfo(fakeSlowRecords)
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasSessionActive(fakeParentContext.isSessionActive)
                    hasSampleRate(fakeSampleRate)
                }
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `M returns null W handleEvent(any) on stopped view {no pending event}`() {
        // Given
        testedScope.stopped = true
        fakeEvent = mock()

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        verifyNoInteractions(mockWriter)
        assertThat(result).isNull()
    }

    @Test
    fun `M remove the hasReplay entry W handleEvent(any) on stopped view {no pending event}`(
        forge: Forge
    ) {
        // Given
        val argumentCaptor = argumentCaptor<(MutableMap<String, Any?>) -> Unit>()
        val fakeSessionReplayContext = forge.exhaustiveAttributes()
            .apply { put(testedScope.viewId, forge.aBool()) }
        testedScope.stopped = true
        fakeEvent = mock()

        // When
        testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        verify(rumMonitor.mockSdkCore).updateFeatureContext(
            eq(Feature.SESSION_REPLAY_FEATURE_NAME),
            argumentCaptor.capture()
        )
        argumentCaptor.firstValue.invoke(fakeSessionReplayContext)
        assertThat(fakeSessionReplayContext).doesNotContainKey(testedScope.viewId)
    }

    @Test
    fun `M returns self W handleEvent(any) on stopped view {pending action event}`(
        @LongForgery(1, 32) pendingEvents: Long
    ) {
        // Given
        testedScope.stopped = true
        testedScope.pendingActionCount = pendingEvents
        fakeEvent = mock()

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        verifyNoInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
        verify(rumMonitor.mockSdkCore, never()).updateFeatureContext(
            eq(Feature.SESSION_REPLAY_FEATURE_NAME),
            any()
        )
    }

    @Test
    fun `M returns self W handleEvent(any) on stopped view {pending resource event}`(
        @LongForgery(1, 32) pendingEvents: Long
    ) {
        // Given
        testedScope.stopped = true
        testedScope.pendingResourceCount = pendingEvents
        fakeEvent = mock()

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        verifyNoInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
        verify(rumMonitor.mockSdkCore, never()).updateFeatureContext(
            eq(Feature.SESSION_REPLAY_FEATURE_NAME),
            any()
        )
    }

    @Test
    fun `M returns self W handleEvent(any) on stopped view {pending error event}`(
        @LongForgery(1, 32) pendingEvents: Long
    ) {
        // Given
        testedScope.stopped = true
        testedScope.pendingErrorCount = pendingEvents
        fakeEvent = mock()

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        verifyNoInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
        verify(rumMonitor.mockSdkCore, never()).updateFeatureContext(
            eq(Feature.SESSION_REPLAY_FEATURE_NAME),
            any()
        )
    }

    @Test
    fun `M returns self W handleEvent(any) on stopped view {pending long task event}`(
        @LongForgery(1, 32) pendingEvents: Long
    ) {
        // Given
        testedScope.stopped = true
        testedScope.pendingLongTaskCount = pendingEvents
        fakeEvent = mock()

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        verifyNoInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
        verify(rumMonitor.mockSdkCore, never()).updateFeatureContext(
            eq(Feature.SESSION_REPLAY_FEATURE_NAME),
            any()
        )
    }

    @Test
    fun `M send event with synthetics info W handleEvent(StopView) on active view`(
        @StringForgery fakeTestId: String,
        @StringForgery fakeResultId: String,
        forge: Forge
    ) {
        // Given
        val attributes = forge.exhaustiveAttributes(excludedKeys = fakeAttributes.keys)
        val expectedAttributes = mutableMapOf<String, Any?>()
        expectedAttributes.putAll(fakeAttributes)
        expectedAttributes.putAll(attributes)
        fakeParentContext = fakeParentContext.copy(
            syntheticsTestId = fakeTestId,
            syntheticsResultId = fakeResultId
        )
        whenever(mockParentScope.getRumContext()) doReturn fakeParentContext

        // When
        val result = testedScope.handleEvent(
            RumRawEvent.StopView(fakeKey, attributes),
            mockWriter
        )

        // Then
        argumentCaptor<ViewEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))
            assertThat(lastValue)
                .apply {
                    hasTimestamp(resolveExpectedTimestamp(fakeEventTime.timestamp))
                    hasName(fakeKey.name)
                    hasUrl(fakeUrl)
                    hasDurationGreaterThan(1)
                    hasVersion(2)
                    hasErrorCount(0)
                    hasCrashCount(0)
                    hasResourceCount(0)
                    hasActionCount(0)
                    hasFrustrationCount(0)
                    hasLongTaskCount(0)
                    hasFrozenFrameCount(0)
                    hasCpuMetric(null)
                    hasMemoryMetric(null, null)
                    hasRefreshRateMetric(null, null)
                    isActive(false)
                    isSlowRendered(false)
                    hasNoCustomTimings()
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasAccountInfo(fakeDatadogContext.accountInfo)
                    hasViewId(testedScope.viewId)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasSessionType(fakeRumSessionType?.toView() ?: ViewEvent.ViewEventSessionType.SYNTHETICS)
                    hasSyntheticsTest(fakeTestId, fakeResultId)
                    containsExactlyContextAttributes(expectedAttributes)
                    hasStartReason(fakeParentContext.sessionStartReason)
                    hasReplay(fakeHasReplay)
                    hasReplayStats(fakeReplayStats)
                    hasSource(fakeSourceViewEvent)
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toViewSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasSlownessInfo(
                        fakeSlowRecords,
                        fakeSlownessRate,
                        fakeFreezeRate
                    )
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasSessionActive(fakeParentContext.isSessionActive)
                    hasSampleRate(fakeSampleRate)
                }
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isNull()
    }

    // endregion

    // region Action

    @Test
    fun `M create ActionScope W handleEvent(StartAction)`(
        @Forgery type: RumActionType,
        @StringForgery name: String,
        @BoolForgery waitForStop: Boolean,
        forge: Forge
    ) {
        // Given
        val attributes = forge.exhaustiveAttributes(excludedKeys = fakeAttributes.keys)

        // When
        val fakeStartActionEvent = RumRawEvent.StartAction(type, name, waitForStop, attributes)
        val result = testedScope.handleEvent(
            fakeStartActionEvent,
            mockWriter
        )

        // Then
        verifyNoInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
        assertThat(testedScope.activeActionScope).isInstanceOf(RumActionScope::class.java)
        val actionScope = testedScope.activeActionScope as RumActionScope
        assertThat(actionScope.name).isEqualTo(name)
        assertThat(actionScope.eventTimestamp)
            .isEqualTo(resolveExpectedTimestamp(fakeStartActionEvent.eventTime.timestamp))
        assertThat(actionScope.waitForStop).isEqualTo(waitForStop)
        assertThat(actionScope.attributes).containsAllEntriesOf(attributes)
        assertThat(actionScope.parentScope).isSameAs(testedScope)
        assertThat(actionScope.sampleRate).isCloseTo(fakeSampleRate, Assertions.offset(0.001f))
    }

    @Test
    fun `M update the RumContext in GlobalRum W ActionScope created`(
        @Forgery type: RumActionType,
        @StringForgery name: String,
        @BoolForgery waitForStop: Boolean,
        forge: Forge
    ) {
        // Given
        val attributes = forge.exhaustiveAttributes(excludedKeys = fakeAttributes.keys)

        // When
        val fakeStartActionEvent = RumRawEvent.StartAction(type, name, waitForStop, attributes)
        testedScope.handleEvent(
            fakeStartActionEvent,
            mockWriter
        )

        // Then
        argumentCaptor<(Map<String, Any?>) -> Unit> {
            verify(rumMonitor.mockSdkCore, times(2)).updateFeatureContext(
                eq(Feature.RUM_FEATURE_NAME),
                capture()
            )
            val rumContext = mutableMapOf<String, Any?>()
            allValues.fold(rumContext) { acc, function ->
                function.invoke(acc)
                acc
            }
            assertThat(rumContext["action_id"])
                .isEqualTo((testedScope.activeActionScope as RumActionScope).actionId)
        }
    }

    @ParameterizedTest
    @EnumSource(RumActionType::class, names = ["CUSTOM"], mode = EnumSource.Mode.EXCLUDE)
    fun `M do nothing + log warning W handleEvent(StartAction+!CUSTOM)+active child ActionScope`(
        actionType: RumActionType,
        @StringForgery name: String,
        @BoolForgery waitForStop: Boolean,
        forge: Forge
    ) {
        // Given
        val attributes = forge.exhaustiveAttributes(excludedKeys = fakeAttributes.keys)
        testedScope.activeActionScope = mockChildScope
        fakeEvent = RumRawEvent.StartAction(actionType, name, waitForStop, attributes)
        whenever(mockChildScope.handleEvent(fakeEvent, mockWriter)) doReturn mockChildScope

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        verify(mockChildScope).handleEvent(fakeEvent, mockWriter)
        verifyNoInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
        assertThat(testedScope.activeActionScope).isSameAs(mockChildScope)

        mockInternalLogger.verifyLog(
            InternalLogger.Level.WARN,
            InternalLogger.Target.USER,
            RumViewScope.ACTION_DROPPED_WARNING.format(
                Locale.US,
                (fakeEvent as RumRawEvent.StartAction).type,
                (fakeEvent as RumRawEvent.StartAction).name
            )
        )
    }

    @Test
    fun `M do nothing + log warning W handleEvent(StartAction+CUSTOM+cont) + child ActionScope`(
        @StringForgery name: String,
        forge: Forge
    ) {
        // Given
        val attributes = forge.exhaustiveAttributes(excludedKeys = fakeAttributes.keys)
        testedScope.activeActionScope = mockChildScope
        fakeEvent =
            RumRawEvent.StartAction(RumActionType.CUSTOM, name, waitForStop = true, attributes)
        whenever(mockChildScope.handleEvent(fakeEvent, mockWriter)) doReturn mockChildScope

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        verify(mockChildScope).handleEvent(fakeEvent, mockWriter)
        verifyNoInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
        assertThat(testedScope.activeActionScope).isSameAs(mockChildScope)

        mockInternalLogger.verifyLog(
            InternalLogger.Level.WARN,
            InternalLogger.Target.USER,
            RumViewScope.ACTION_DROPPED_WARNING.format(
                Locale.US,
                (fakeEvent as RumRawEvent.StartAction).type,
                (fakeEvent as RumRawEvent.StartAction).name
            )
        )
    }

    @Test
    fun `M send action W handleEvent(StartAction+CUSTOM+instant) + active child ActionScope`(
        @StringForgery name: String,
        forge: Forge
    ) {
        // Given
        val attributes = forge.exhaustiveAttributes(excludedKeys = fakeAttributes.keys)
        testedScope.activeActionScope = mockChildScope
        fakeEvent = RumRawEvent.StartAction(RumActionType.CUSTOM, name, false, attributes)
        whenever(mockChildScope.handleEvent(fakeEvent, mockWriter)) doReturn mockChildScope

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        argumentCaptor<ActionEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))
            assertThat(firstValue)
                .apply {
                    hasNonNullId()
                    hasType(RumActionType.CUSTOM)
                    hasTargetName(name)
                    hasDuration(1)
                    hasResourceCount(0)
                    hasErrorCount(0)
                    hasCrashCount(0)
                    hasLongTaskCount(0)
                    hasView(testedScope.getRumContext())
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasAccountInfo(fakeDatadogContext.accountInfo)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasSessionType(fakeRumSessionType?.toAction() ?: ActionEvent.ActionEventSessionType.USER)
                    hasNoSyntheticsTest()
                    hasStartReason(fakeParentContext.sessionStartReason)
                    hasReplay(fakeHasReplay)
                    containsExactlyContextAttributes(attributes)
                    hasSource(fakeSourceActionEvent)
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toActionSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasConnectivityInfo(fakeNetworkInfoAtScopeStart)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasSampleRate(fakeSampleRate)
                }
        }
        verifyNoMoreInteractions(mockWriter)

        assertThat(result).isSameAs(testedScope)
        assertThat(testedScope.activeActionScope).isSameAs(mockChildScope)
        assertThat(testedScope.pendingActionCount).isEqualTo(1)
    }

    @Test
    fun `M send action with synthetics W handleEvent(StartAction+CUSTOM+instant) + active child ActionScope`(
        @StringForgery name: String,
        @StringForgery fakeTestId: String,
        @StringForgery fakeResultId: String,
        forge: Forge
    ) {
        // Given
        val attributes = forge.exhaustiveAttributes(excludedKeys = fakeAttributes.keys)
        testedScope.activeActionScope = mockChildScope
        fakeEvent = RumRawEvent.StartAction(RumActionType.CUSTOM, name, false, attributes)
        whenever(mockChildScope.handleEvent(fakeEvent, mockWriter)) doReturn mockChildScope
        fakeParentContext = fakeParentContext.copy(
            syntheticsTestId = fakeTestId,
            syntheticsResultId = fakeResultId
        )
        whenever(mockParentScope.getRumContext()) doReturn fakeParentContext

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        argumentCaptor<ActionEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))
            assertThat(firstValue)
                .apply {
                    hasNonNullId()
                    hasType(RumActionType.CUSTOM)
                    hasTargetName(name)
                    hasDuration(1)
                    hasResourceCount(0)
                    hasErrorCount(0)
                    hasCrashCount(0)
                    hasLongTaskCount(0)
                    hasView(testedScope.getRumContext())
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasAccountInfo(fakeDatadogContext.accountInfo)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasSessionType(fakeRumSessionType?.toAction() ?: ActionEvent.ActionEventSessionType.SYNTHETICS)
                    hasSyntheticsTest(fakeTestId, fakeResultId)
                    hasStartReason(fakeParentContext.sessionStartReason)
                    hasReplay(fakeHasReplay)
                    containsExactlyContextAttributes(attributes)
                    hasSource(fakeSourceActionEvent)
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toActionSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasConnectivityInfo(fakeNetworkInfoAtScopeStart)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasSampleRate(fakeSampleRate)
                }
        }
        verifyNoMoreInteractions(mockWriter)

        assertThat(result).isSameAs(testedScope)
        assertThat(testedScope.activeActionScope).isSameAs(mockChildScope)
        assertThat(testedScope.pendingActionCount).isEqualTo(1)
    }

    @Test
    fun `M do nothing W handleEvent(StartAction) on stopped view`(
        @Forgery type: RumActionType,
        @StringForgery name: String,
        @BoolForgery waitForStop: Boolean,
        forge: Forge
    ) {
        // Given
        val attributes = forge.exhaustiveAttributes(excludedKeys = fakeAttributes.keys)
        testedScope.stopped = true
        fakeEvent = RumRawEvent.StartAction(type, name, waitForStop, attributes)

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        verifyNoInteractions(mockWriter)
        assertThat(result).isNull()
        assertThat(testedScope.activeActionScope).isNull()
    }

    @Test
    fun `M send event to child ActionScope W handleEvent(StartView) on active view`() {
        // Given
        testedScope.activeActionScope = mockChildScope

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        verify(mockChildScope).handleEvent(fakeEvent, mockWriter)
        verifyNoInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `M send event to child ActionScope W handleEvent() on stopped view`() {
        // Given
        testedScope.stopped = true
        testedScope.activeActionScope = mockChildScope

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        verify(mockChildScope).handleEvent(fakeEvent, mockWriter)
        verifyNoInteractions(mockWriter)
        assertThat(result).isNull()
    }

    @Test
    fun `M remove child ActionScope W handleEvent() returns null`() {
        // Given
        testedScope.activeActionScope = mockChildScope
        whenever(mockChildScope.handleEvent(fakeEvent, mockWriter)) doReturn null

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        verify(mockChildScope).handleEvent(fakeEvent, mockWriter)
        verifyNoInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
        assertThat(testedScope.activeActionScope).isNull()
    }

    @Test
    fun `M update the RumContext in GlobalRum when removing the ActionScope`() {
        // Given
        testedScope.activeActionScope = mockChildScope
        whenever(mockChildScope.handleEvent(fakeEvent, mockWriter)) doReturn null

        // When
        testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        argumentCaptor<(Map<String, Any?>) -> Unit> {
            verify(rumMonitor.mockSdkCore, times(2)).updateFeatureContext(
                eq(Feature.RUM_FEATURE_NAME),
                capture()
            )
            val rumContext = mutableMapOf<String, Any?>()
            allValues.fold(rumContext) { acc, function ->
                function.invoke(acc)
                acc
            }
            assertThat(rumContext["action_id"]).isNull()
        }
    }

    @Test
    fun `M wait for pending W handleEvent(StartAction) on active view`(
        @Forgery type: RumActionType,
        @StringForgery name: String,
        @BoolForgery waitForStop: Boolean
    ) {
        // Given
        testedScope.activeActionScope = null
        testedScope.pendingActionCount = 0
        fakeEvent = RumRawEvent.StartAction(type, name, waitForStop, emptyMap())

        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        assertThat(testedScope.pendingActionCount).isEqualTo(1)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `M wait for pending W handleEvent(ApplicationStarted) on active view`(
        @LongForgery(0) duration: Long
    ) {
        // Given
        val eventTime = Time()
        fakeEvent = RumRawEvent.ApplicationStarted(eventTime, duration)
        testedScope.activeActionScope = null
        testedScope.pendingActionCount = 0

        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        assertThat(testedScope.pendingActionCount).isEqualTo(1)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `M decrease pending Action W handleEvent(ActionDropped) on active view`(
        @LongForgery(1) pending: Long
    ) {
        // Given
        testedScope.pendingActionCount = pending
        fakeEvent = RumRawEvent.ActionDropped(testedScope.viewId)

        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        assertThat(testedScope.pendingActionCount).isEqualTo(pending - 1)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `M decrease pending Action W handleEvent(ActionDropped) on stopped view`() {
        // Given
        testedScope.pendingActionCount = 1
        fakeEvent = RumRawEvent.ActionDropped(testedScope.viewId)
        testedScope.stopped = true

        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        assertThat(testedScope.pendingActionCount).isEqualTo(0)
        assertThat(result).isNull()
    }

    @Test
    fun `M do nothing W handleEvent(ActionDropped) on active view {unknown viewId}`(
        @LongForgery(1) pending: Long,
        @Forgery viewUuid: UUID
    ) {
        // Given
        val viewId = viewUuid.toString()
        assumeTrue(viewId != testedScope.viewId)
        testedScope.pendingActionCount = pending
        fakeEvent = RumRawEvent.ActionDropped(viewId)

        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        assertThat(testedScope.pendingActionCount).isEqualTo(pending)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `M do nothing W handleEvent(ActionDropped) on stopped view {unknown viewId}`(
        @LongForgery(1) pending: Long,
        @Forgery viewUuid: UUID
    ) {
        // Given
        val viewId = viewUuid.toString()
        assumeTrue(viewId != testedScope.viewId)
        testedScope.pendingActionCount = pending
        fakeEvent = RumRawEvent.ActionDropped(viewId)
        testedScope.stopped = true

        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        assertThat(testedScope.pendingActionCount).isEqualTo(pending)
        assertThat(result).isSameAs(testedScope)
    }

    // endregion

    // region Resource

    @Test
    fun `M create ResourceScope W handleEvent(StartResource)`(
        @StringForgery key: String,
        @Forgery method: RumResourceMethod,
        @StringForgery(regex = "http(s?)://[a-z]+\\.com/[a-z]+") url: String,
        forge: Forge
    ) {
        // Given
        val attributes = forge.exhaustiveAttributes(excludedKeys = fakeAttributes.keys)

        // When
        val fakeEvent = RumRawEvent.StartResource(key, url, method, attributes)
        val result = testedScope.handleEvent(
            fakeEvent,
            mockWriter
        )

        // Then
        verifyNoInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
        assertThat(testedScope.activeResourceScopes).isNotEmpty()
        val entry = testedScope.activeResourceScopes.entries.first()
        assertThat(entry.key).isEqualTo(key)
        assertThat(entry.value).isInstanceOf(RumResourceScope::class.java)
        val resourceScope = entry.value as RumResourceScope
        assertThat(resourceScope.parentScope).isSameAs(testedScope)
        assertThat(resourceScope.attributes).containsAllEntriesOf(attributes)
        assertThat(resourceScope.key).isSameAs(key)
        assertThat(resourceScope.url).isEqualTo(url)
        assertThat(resourceScope.method).isSameAs(method)
        assertThat(resourceScope.eventTimestamp)
            .isEqualTo(resolveExpectedTimestamp(fakeEvent.eventTime.timestamp))
        assertThat(resourceScope.firstPartyHostHeaderTypeResolver).isSameAs(mockResolver)
        assertThat(resourceScope.sampleRate).isCloseTo(fakeSampleRate, Assertions.offset(0.001f))
    }

    @Test
    fun `M create ResourceScope with active actionId W handleEvent(StartResource)`(
        @StringForgery key: String,
        @Forgery method: RumResourceMethod,
        @StringForgery(regex = "http(s?)://[a-z]+\\.com/[a-z]+") url: String,
        forge: Forge
    ) {
        // Given
        testedScope.activeActionScope = mockActionScope
        val attributes = forge.exhaustiveAttributes(excludedKeys = fakeAttributes.keys)
        fakeEvent = RumRawEvent.StartResource(key, url, method, attributes)

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        verify(mockActionScope).handleEvent(fakeEvent, mockWriter)
        verifyNoInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
        assertThat(testedScope.activeResourceScopes).isNotEmpty()
        val entry = testedScope.activeResourceScopes.entries.first()
        assertThat(entry.key).isEqualTo(key)
        val resourceScope = entry.value as RumResourceScope
        assertThat(resourceScope.parentScope).isSameAs(testedScope)
        assertThat(resourceScope.attributes).containsAllEntriesOf(attributes)
        assertThat(resourceScope.key).isSameAs(key)
        assertThat(resourceScope.url).isEqualTo(url)
        assertThat(resourceScope.method).isSameAs(method)
        assertThat(resourceScope.eventTimestamp)
            .isEqualTo(resolveExpectedTimestamp(fakeEvent.eventTime.timestamp))
        assertThat(resourceScope.firstPartyHostHeaderTypeResolver).isSameAs(mockResolver)
        assertThat(resourceScope.sampleRate).isCloseTo(fakeSampleRate, Assertions.offset(0.001f))
    }

    @Test
    fun `M send event to children ResourceScopes W handleEvent(StartView) on active view`(
        @StringForgery key: String
    ) {
        // Given
        testedScope.activeResourceScopes[key] = mockChildScope
        whenever(mockChildScope.handleEvent(fakeEvent, mockWriter)) doReturn mockChildScope

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        verify(mockChildScope).handleEvent(fakeEvent, mockWriter)
        verifyNoInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `M send event to children ResourceScopes W handleEvent(StartView) on stopped view`(
        @StringForgery key: String
    ) {
        // Given
        testedScope.stopped = true
        testedScope.activeResourceScopes[key] = mockChildScope
        whenever(mockChildScope.handleEvent(fakeEvent, mockWriter)) doReturn mockChildScope

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        verify(mockChildScope).handleEvent(fakeEvent, mockWriter)
        verifyNoInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `M remove child ResourceScope W handleEvent() returns null`(
        @StringForgery key: String
    ) {
        // Given
        testedScope.activeResourceScopes[key] = mockChildScope
        whenever(mockChildScope.handleEvent(fakeEvent, mockWriter)) doReturn null

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        verify(mockChildScope).handleEvent(fakeEvent, mockWriter)
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
        assertThat(testedScope.activeResourceScopes).isEmpty()
    }

    @Test
    fun `M wait for pending Resource W handleEvent(StartResource) on active view`(
        @StringForgery key: String,
        @Forgery method: RumResourceMethod,
        @StringForgery(regex = "http(s?)://[a-z]+\\.com/[a-z]+") url: String
    ) {
        // Given
        testedScope.pendingResourceCount = 0
        fakeEvent = RumRawEvent.StartResource(key, url, method, emptyMap())

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        assertThat(testedScope.pendingResourceCount).isEqualTo(1)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `M decrease pending Resource W handleEvent(ResourceDropped) on active view`(
        @LongForgery(1) pending: Long,
        forge: Forge
    ) {
        // Given
        testedScope.pendingResourceCount = pending
        val fakeResourceDrooped = forge.getForgery<RumRawEvent.ResourceDropped>().copy(testedScope.viewId)

        // When
        val result = testedScope.handleEvent(fakeResourceDrooped, mockWriter)

        // Then
        assertThat(testedScope.pendingResourceCount).isEqualTo(pending - 1)
        assertThat(result).isSameAs(testedScope)
        verify(mockNetworkSettledMetricResolver).resourceWasDropped(fakeResourceDrooped.resourceId)
    }

    @Test
    fun `M decrease pending Resource W handleEvent(ResourceDropped) on stopped view`(
        forge: Forge
    ) {
        // Given
        testedScope.pendingResourceCount = 1
        val fakeResourceDropped = forge.getForgery<RumRawEvent.ResourceDropped>().copy(viewId = testedScope.viewId)
        testedScope.stopped = true

        // When
        val result = testedScope.handleEvent(fakeResourceDropped, mockWriter)

        // Then
        assertThat(testedScope.pendingResourceCount).isEqualTo(0)
        assertThat(result).isNull()
        verify(mockNetworkSettledMetricResolver).resourceWasDropped(fakeResourceDropped.resourceId)
    }

    @Test
    fun `M do nothing W handleEvent(ResourceDropped) on active view {unknown viewId}`(
        @LongForgery(1) pending: Long,
        @Forgery viewUuid: UUID,
        forge: Forge
    ) {
        // Given
        val viewId = viewUuid.toString()
        assumeTrue(viewId != testedScope.viewId)
        testedScope.pendingResourceCount = pending
        fakeEvent = forge.getForgery<RumRawEvent.ResourceDropped>().copy(viewId = viewId)

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        assertThat(testedScope.pendingResourceCount).isEqualTo(pending)
        assertThat(result).isSameAs(testedScope)
        verify(mockNetworkSettledMetricResolver, never()).resourceWasDropped(any())
    }

    @Test
    fun `M do nothing W handleEvent(ResourceDropped) on stopped view {unknown viewId}`(
        @LongForgery(1) pending: Long,
        @Forgery viewUuid: UUID,
        forge: Forge
    ) {
        // Given
        val viewId = viewUuid.toString()
        assumeTrue(viewId != testedScope.viewId)
        testedScope.pendingResourceCount = pending
        fakeEvent = forge.getForgery<RumRawEvent.ResourceDropped>().copy(viewId = viewId)
        testedScope.stopped = true

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        assertThat(testedScope.pendingResourceCount).isEqualTo(pending)
        assertThat(result).isSameAs(testedScope)
        verify(mockNetworkSettledMetricResolver, never()).resourceWasDropped(any())
    }

    @Test
    fun `M convert pending resource to error W handleEvent() {resource stopped by error}`(
        @LongForgery(1) pendingResources: Long,
        @LongForgery(min = 0, max = Long.MAX_VALUE - 1) pendingErrors: Long,
        forge: Forge
    ) {
        // Given
        testedScope.pendingErrorCount = pendingErrors
        testedScope.pendingResourceCount = pendingResources
        val fakeEvent = forge.stopResourceWithErrorEvent()
        testedScope.activeResourceScopes[fakeEvent.key] = mock()

        // When
        testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        assertThat(testedScope.pendingResourceCount).isEqualTo(pendingResources - 1)
        assertThat(testedScope.pendingErrorCount).isEqualTo(pendingErrors + 1)
    }

    @Test
    fun `M convert pending resource to error W handleEvent() {resource stopped by error with stacktrace}`(
        @LongForgery(1) pendingResources: Long,
        @LongForgery(min = 0, max = Long.MAX_VALUE - 1) pendingErrors: Long,
        forge: Forge
    ) {
        // Given
        testedScope.pendingErrorCount = pendingErrors
        testedScope.pendingResourceCount = pendingResources
        val fakeEvent = forge.stopResourceWithStacktraceEvent()
        testedScope.activeResourceScopes[fakeEvent.key] = mock()

        // When
        testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        assertThat(testedScope.pendingResourceCount).isEqualTo(pendingResources - 1)
        assertThat(testedScope.pendingErrorCount).isEqualTo(pendingErrors + 1)
    }

    // endregion

    // region Error

    @Test
    fun `M send event W handleEvent(AddError) on active view`(
        @StringForgery message: String,
        @Forgery source: RumErrorSource,
        @Forgery throwable: Throwable,
        @StringForgery stacktrace: String,
        forge: Forge
    ) {
        // Given
        testedScope.activeActionScope = mockActionScope
        val attributes = forge.exhaustiveAttributes(excludedKeys = fakeAttributes.keys)
        fakeEvent = RumRawEvent.AddError(
            message,
            source,
            throwable,
            stacktrace,
            isFatal = false,
            threads = emptyList(),
            attributes = attributes
        )

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        val expectedMessage = "$message: ${throwable.message}"
        argumentCaptor<ErrorEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))

            assertThat(firstValue)
                .apply {
                    hasTimestamp(resolveExpectedTimestamp(fakeEvent.eventTime.timestamp))
                    hasMessage(expectedMessage)
                    hasErrorSource(source)
                    hasErrorCategory(ErrorEvent.Category.EXCEPTION)
                    hasTimeSinceAppStart(null)
                    hasStackTrace(stacktrace)
                    isCrash(false)
                    hasNoThreads()
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasAccountInfo(fakeDatadogContext.accountInfo)
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasView(testedScope.viewId, testedScope.key.name, testedScope.url)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasActionId(fakeActionId)
                    hasStartReason(fakeParentContext.sessionStartReason)
                    hasReplay(fakeHasReplay)
                    containsExactlyContextAttributes(attributes)
                    hasSessionType(fakeRumSessionType?.toError() ?: ErrorEvent.ErrorEventSessionType.USER)
                    hasNoSyntheticsTest()
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toErrorSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasSampleRate(fakeSampleRate)
                    hasBuildId(fakeDatadogContext.appBuildId)
                }
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `M send event with synthetics info W handleEvent(AddError) on active view`(
        @StringForgery fakeTestId: String,
        @StringForgery fakeResultId: String,
        @StringForgery message: String,
        @Forgery source: RumErrorSource,
        @Forgery throwable: Throwable,
        @StringForgery stacktrace: String,
        forge: Forge
    ) {
        // Given
        testedScope.activeActionScope = mockActionScope
        val attributes = forge.exhaustiveAttributes(excludedKeys = fakeAttributes.keys)
        fakeEvent = RumRawEvent.AddError(
            message,
            source,
            throwable,
            stacktrace,
            isFatal = false,
            threads = emptyList(),
            attributes = attributes
        )
        fakeParentContext = fakeParentContext.copy(
            syntheticsTestId = fakeTestId,
            syntheticsResultId = fakeResultId
        )
        whenever(mockParentScope.getRumContext()) doReturn fakeParentContext

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        val expectedMessage = "$message: ${throwable.message}"
        argumentCaptor<ErrorEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))

            assertThat(firstValue)
                .apply {
                    hasTimestamp(resolveExpectedTimestamp(fakeEvent.eventTime.timestamp))
                    hasMessage(expectedMessage)
                    hasErrorSource(source)
                    hasErrorCategory(ErrorEvent.Category.EXCEPTION)
                    hasTimeSinceAppStart(null)
                    hasStackTrace(stacktrace)
                    isCrash(false)
                    hasNoThreads()
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasAccountInfo(fakeDatadogContext.accountInfo)
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasView(testedScope.viewId, testedScope.key.name, testedScope.url)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasActionId(fakeActionId)
                    hasStartReason(fakeParentContext.sessionStartReason)
                    hasReplay(fakeHasReplay)
                    containsExactlyContextAttributes(attributes)
                    hasSessionType(fakeRumSessionType?.toError() ?: ErrorEvent.ErrorEventSessionType.SYNTHETICS)
                    hasSyntheticsTest(fakeTestId, fakeResultId)
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toErrorSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasSampleRate(fakeSampleRate)
                    hasBuildId(fakeDatadogContext.appBuildId)
                }
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `M send event W handleEvent(AddError) on active view {throwable_message == null}`(
        @StringForgery message: String,
        @Forgery source: RumErrorSource,
        @StringForgery stacktrace: String,
        forge: Forge
    ) {
        // Given
        val throwable = RuntimeException()
        testedScope.activeActionScope = mockActionScope
        val attributes = forge.exhaustiveAttributes(excludedKeys = fakeAttributes.keys)
        fakeEvent = RumRawEvent.AddError(
            message,
            source,
            throwable,
            stacktrace,
            isFatal = false,
            threads = emptyList(),
            attributes = attributes
        )

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        argumentCaptor<ErrorEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))

            assertThat(firstValue)
                .apply {
                    hasTimestamp(resolveExpectedTimestamp(fakeEvent.eventTime.timestamp))
                    hasMessage(message)
                    hasErrorSource(source)
                    hasErrorCategory(ErrorEvent.Category.EXCEPTION)
                    hasTimeSinceAppStart(null)
                    hasStackTrace(stacktrace)
                    isCrash(false)
                    hasNoThreads()
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasAccountInfo(fakeDatadogContext.accountInfo)
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasView(testedScope.viewId, testedScope.key.name, testedScope.url)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasActionId(fakeActionId)
                    hasStartReason(fakeParentContext.sessionStartReason)
                    hasReplay(fakeHasReplay)
                    containsExactlyContextAttributes(attributes)
                    hasSource(fakeSourceErrorEvent)
                    hasSessionType(fakeRumSessionType?.toError() ?: ErrorEvent.ErrorEventSessionType.USER)
                    hasNoSyntheticsTest()
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toErrorSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasSampleRate(fakeSampleRate)
                }
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `M send event W handleEvent(AddError) on active view {throwable is ANR}`(
        @StringForgery message: String,
        @Forgery source: RumErrorSource,
        @StringForgery stacktrace: String,
        forge: Forge
    ) {
        // Given
        val throwable = ANRException(Thread.currentThread())
        testedScope.activeActionScope = mockActionScope
        val attributes = forge.exhaustiveAttributes(excludedKeys = fakeAttributes.keys)
        fakeEvent = RumRawEvent.AddError(
            message,
            source,
            throwable,
            stacktrace,
            isFatal = false,
            threads = emptyList(),
            attributes = attributes
        )

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        argumentCaptor<ErrorEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))

            assertThat(firstValue)
                .apply {
                    hasTimestamp(resolveExpectedTimestamp(fakeEvent.eventTime.timestamp))
                    hasMessage(message)
                    hasErrorSource(source)
                    hasErrorCategory(ErrorEvent.Category.ANR)
                    hasTimeSinceAppStart(null)
                    hasStackTrace(stacktrace)
                    isCrash(false)
                    hasNoThreads()
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasAccountInfo(fakeDatadogContext.accountInfo)
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasView(testedScope.viewId, testedScope.key.name, testedScope.url)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasActionId(fakeActionId)
                    hasStartReason(fakeParentContext.sessionStartReason)
                    hasReplay(fakeHasReplay)
                    containsExactlyContextAttributes(attributes)
                    hasSource(fakeSourceErrorEvent)
                    hasSessionType(fakeRumSessionType?.toError() ?: ErrorEvent.ErrorEventSessionType.USER)
                    hasNoSyntheticsTest()
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toErrorSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasSampleRate(fakeSampleRate)
                    hasBuildId(fakeDatadogContext.appBuildId)
                }
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `M send event W handleEvent(AddError) on active view {throwable_message == blank}`(
        @StringForgery message: String,
        @Forgery source: RumErrorSource,
        @StringForgery(StringForgeryType.WHITESPACE) blankMessage: String,
        @StringForgery stacktrace: String,
        forge: Forge
    ) {
        // Given
        val throwable = RuntimeException(blankMessage)
        testedScope.activeActionScope = mockActionScope
        val attributes = forge.exhaustiveAttributes(excludedKeys = fakeAttributes.keys)
        fakeEvent = RumRawEvent.AddError(
            message,
            source,
            throwable,
            stacktrace,
            isFatal = false,
            threads = emptyList(),
            attributes = attributes
        )

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        argumentCaptor<ErrorEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))

            assertThat(firstValue)
                .apply {
                    hasTimestamp(resolveExpectedTimestamp(fakeEvent.eventTime.timestamp))
                    hasMessage(message)
                    hasErrorSource(source)
                    hasErrorCategory(ErrorEvent.Category.EXCEPTION)
                    hasTimeSinceAppStart(null)
                    hasStackTrace(stacktrace)
                    isCrash(false)
                    hasNoThreads()
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasAccountInfo(fakeDatadogContext.accountInfo)
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasView(testedScope.viewId, testedScope.key.name, testedScope.url)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasActionId(fakeActionId)
                    hasStartReason(fakeParentContext.sessionStartReason)
                    hasReplay(fakeHasReplay)
                    containsExactlyContextAttributes(attributes)
                    hasSource(fakeSourceErrorEvent)
                    hasSessionType(fakeRumSessionType?.toError() ?: ErrorEvent.ErrorEventSessionType.USER)
                    hasNoSyntheticsTest()
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toErrorSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasSampleRate(fakeSampleRate)
                    hasBuildId(fakeDatadogContext.appBuildId)
                }
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `M send event W handleEvent(AddError) on active view {message = throwable_message}`(
        @Forgery source: RumErrorSource,
        @Forgery throwable: Throwable,
        @StringForgery stacktrace: String,
        forge: Forge
    ) {
        // Given
        testedScope.activeActionScope = mockActionScope
        val attributes = forge.exhaustiveAttributes(excludedKeys = fakeAttributes.keys)
        val throwableMessage = throwable.message
        check(!throwableMessage.isNullOrBlank()) {
            "Expected throwable to have a non null, non blank message"
        }
        fakeEvent = RumRawEvent.AddError(
            throwableMessage,
            source,
            throwable,
            stacktrace,
            isFatal = false,
            threads = emptyList(),
            attributes = attributes
        )

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        argumentCaptor<ErrorEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))

            assertThat(firstValue)
                .apply {
                    hasTimestamp(resolveExpectedTimestamp(fakeEvent.eventTime.timestamp))
                    hasMessage(throwableMessage)
                    hasErrorSource(source)
                    hasErrorCategory(ErrorEvent.Category.EXCEPTION)
                    hasTimeSinceAppStart(null)
                    hasStackTrace(stacktrace)
                    isCrash(false)
                    hasNoThreads()
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasAccountInfo(fakeDatadogContext.accountInfo)
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasView(testedScope.viewId, testedScope.key.name, testedScope.url)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasActionId(fakeActionId)
                    hasStartReason(fakeParentContext.sessionStartReason)
                    hasReplay(fakeHasReplay)
                    containsExactlyContextAttributes(attributes)
                    hasSource(fakeSourceErrorEvent)
                    hasSessionType(fakeRumSessionType?.toError() ?: ErrorEvent.ErrorEventSessionType.USER)
                    hasNoSyntheticsTest()
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toErrorSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasSampleRate(fakeSampleRate)
                    hasBuildId(fakeDatadogContext.appBuildId)
                }
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `M send event W AddError {throwable=null}`(
        @StringForgery message: String,
        @Forgery source: RumErrorSource,
        @StringForgery stacktrace: String,
        forge: Forge
    ) {
        testedScope.activeActionScope = mockActionScope
        val attributes = forge.exhaustiveAttributes(excludedKeys = fakeAttributes.keys)
        fakeEvent = RumRawEvent.AddError(
            message,
            source,
            throwable = null,
            stacktrace,
            isFatal = false,
            threads = emptyList(),
            attributes = attributes
        )

        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        argumentCaptor<ErrorEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))

            assertThat(firstValue)
                .apply {
                    hasTimestamp(resolveExpectedTimestamp(fakeEvent.eventTime.timestamp))
                    hasMessage(message)
                    hasErrorSource(source)
                    hasStackTrace(stacktrace)
                    hasErrorCategory(ErrorEvent.Category.EXCEPTION)
                    hasTimeSinceAppStart(null)
                    isCrash(false)
                    hasNoThreads()
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasAccountInfo(fakeDatadogContext.accountInfo)
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasView(testedScope.viewId, testedScope.key.name, testedScope.url)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasActionId(fakeActionId)
                    hasStartReason(fakeParentContext.sessionStartReason)
                    hasReplay(fakeHasReplay)
                    containsExactlyContextAttributes(attributes)
                    hasSource(fakeSourceErrorEvent)
                    hasSessionType(fakeRumSessionType?.toError() ?: ErrorEvent.ErrorEventSessionType.USER)
                    hasNoSyntheticsTest()
                    hasSampleRate(fakeSampleRate)
                    hasBuildId(fakeDatadogContext.appBuildId)
                }
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `M send event W AddError {stacktrace=null}`(
        @StringForgery message: String,
        @Forgery source: RumErrorSource,
        @Forgery throwable: Throwable,
        @Forgery sourceType: RumErrorSourceType,
        forge: Forge
    ) {
        testedScope.activeActionScope = mockActionScope
        val attributes = forge.exhaustiveAttributes(excludedKeys = fakeAttributes.keys)
        fakeEvent = RumRawEvent.AddError(
            message,
            source,
            throwable,
            stacktrace = null,
            isFatal = false,
            threads = emptyList(),
            attributes = attributes,
            sourceType = sourceType
        )

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        val expectedMessage = "$message: ${throwable.message}"
        argumentCaptor<ErrorEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))

            assertThat(firstValue)
                .apply {
                    hasTimestamp(resolveExpectedTimestamp(fakeEvent.eventTime.timestamp))
                    hasMessage(expectedMessage)
                    hasErrorSource(source)
                    hasErrorCategory(ErrorEvent.Category.EXCEPTION)
                    hasTimeSinceAppStart(null)
                    hasStackTrace(throwable.loggableStackTrace())
                    isCrash(false)
                    hasNoThreads()
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasAccountInfo(fakeDatadogContext.accountInfo)
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasView(testedScope.viewId, testedScope.key.name, testedScope.url)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasActionId(fakeActionId)
                    hasErrorType(throwable.javaClass.canonicalName)
                    hasErrorSourceType(sourceType.toSchemaSourceType())
                    hasSessionType(fakeRumSessionType?.toError() ?: ErrorEvent.ErrorEventSessionType.USER)
                    hasNoSyntheticsTest()
                    hasStartReason(fakeParentContext.sessionStartReason)
                    hasReplay(fakeHasReplay)
                    containsExactlyContextAttributes(attributes)
                    hasSource(fakeSourceErrorEvent)
                    hasSessionType(fakeRumSessionType?.toError() ?: ErrorEvent.ErrorEventSessionType.USER)
                    hasNoSyntheticsTest()
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toErrorSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasSampleRate(fakeSampleRate)
                    hasBuildId(fakeDatadogContext.appBuildId)
                }
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `M send event W handleEvent(AddError) {throwable=null, stacktrace=null, fatal=false}`(
        @StringForgery message: String,
        @Forgery source: RumErrorSource,
        @Forgery sourceType: RumErrorSourceType,
        forge: Forge
    ) {
        // Given
        testedScope.activeActionScope = mockActionScope
        val attributes = forge.exhaustiveAttributes(excludedKeys = fakeAttributes.keys)
        fakeEvent = RumRawEvent.AddError(
            message,
            source,
            throwable = null,
            stacktrace = null,
            isFatal = false,
            threads = emptyList(),
            attributes = attributes,
            sourceType = sourceType
        )

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        argumentCaptor<ErrorEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))

            assertThat(firstValue)
                .apply {
                    hasTimestamp(resolveExpectedTimestamp(fakeEvent.eventTime.timestamp))
                    hasMessage(message)
                    hasErrorSource(source)
                    hasErrorCategory(null)
                    hasTimeSinceAppStart(null)
                    hasStackTrace(null)
                    isCrash(false)
                    hasNoThreads()
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasAccountInfo(fakeDatadogContext.accountInfo)
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasView(testedScope.viewId, testedScope.key.name, testedScope.url)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasActionId(fakeActionId)
                    hasErrorType(null)
                    hasErrorSourceType(sourceType.toSchemaSourceType())
                    hasSessionType(fakeRumSessionType?.toError() ?: ErrorEvent.ErrorEventSessionType.USER)
                    hasNoSyntheticsTest()
                    hasStartReason(fakeParentContext.sessionStartReason)
                    hasReplay(fakeHasReplay)
                    containsExactlyContextAttributes(attributes)
                    hasSource(fakeSourceErrorEvent)
                    hasSessionType(fakeRumSessionType?.toError() ?: ErrorEvent.ErrorEventSessionType.USER)
                    hasNoSyntheticsTest()
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toErrorSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasSampleRate(fakeSampleRate)
                    hasBuildId(fakeDatadogContext.appBuildId)
                }
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `M send event W handleEvent(AddError) {throwable=null, stacktrace=null, fatal=true}`(
        @StringForgery message: String,
        @Forgery source: RumErrorSource,
        @Forgery sourceType: RumErrorSourceType,
        @Forgery threads: List<ThreadDump>,
        @LongForgery timeSinceAppStart: Long,
        forge: Forge
    ) {
        // Given
        testedScope.activeActionScope = mockActionScope
        val attributes = forge.exhaustiveAttributes(excludedKeys = fakeAttributes.keys)
        fakeEvent = RumRawEvent.AddError(
            message,
            source,
            throwable = null,
            stacktrace = null,
            isFatal = true,
            threads = threads,
            attributes = attributes,
            sourceType = sourceType,
            timeSinceAppStartNs = timeSinceAppStart
        )

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        argumentCaptor<Any> {
            verify(mockWriter, times(2)).write(eq(mockEventBatchWriter), capture(), eq(EventType.CRASH))
            assertThat(firstValue as ErrorEvent)
                .apply {
                    hasTimestamp(resolveExpectedTimestamp(fakeEvent.eventTime.timestamp))
                    hasMessage(message)
                    hasErrorSource(source)
                    hasStackTrace(null)
                    isCrash(true)
                    hasThreads(threads)
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasAccountInfo(fakeDatadogContext.accountInfo)
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasView(testedScope.viewId, testedScope.key.name, testedScope.url)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasActionId(fakeActionId)
                    hasErrorType(null)
                    hasErrorSourceType(sourceType.toSchemaSourceType())
                    hasErrorCategory(null)
                    hasTimeSinceAppStart(TimeUnit.NANOSECONDS.toMillis(timeSinceAppStart))
                    hasSessionType(fakeRumSessionType?.toError() ?: ErrorEvent.ErrorEventSessionType.USER)
                    hasNoSyntheticsTest()
                    hasStartReason(fakeParentContext.sessionStartReason)
                    hasReplay(fakeHasReplay)
                    containsExactlyContextAttributes(attributes)
                    hasSource(fakeSourceErrorEvent)
                    hasSessionType(fakeRumSessionType?.toError() ?: ErrorEvent.ErrorEventSessionType.USER)
                    hasNoSyntheticsTest()
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toErrorSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasBuildId(fakeDatadogContext.appBuildId)
                }
            assertThat(lastValue as ViewEvent)
                .apply {
                    hasTimestamp(resolveExpectedTimestamp(fakeEventTime.timestamp))
                    hasName(fakeKey.name)
                    hasUrl(fakeUrl)
                    hasDurationGreaterThan(1)
                    hasLoadingTime(null)
                    hasNetworkSettledTime(fakeNetworkSettledMetricValue)
                    hasInteractionToNextViewTime(fakeInteractionToNextViewMetricValue)
                    hasLoadingType(null)
                    hasVersion(2)
                    hasErrorCount(1)
                    hasCrashCount(1)
                    hasResourceCount(0)
                    hasActionCount(0)
                    hasFrustrationCount(0)
                    hasLongTaskCount(0)
                    hasFrozenFrameCount(0)
                    hasCpuMetric(null)
                    hasMemoryMetric(null, null)
                    hasRefreshRateMetric(null, null)
                    isActive(true)
                    isSlowRendered(false)
                    hasNoCustomTimings()
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasAccountInfo(fakeDatadogContext.accountInfo)
                    hasViewId(testedScope.viewId)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasSessionType(fakeRumSessionType?.toView() ?: ViewEvent.ViewEventSessionType.USER)
                    hasNoSyntheticsTest()
                    hasStartReason(fakeParentContext.sessionStartReason)
                    hasReplay(fakeHasReplay)
                    hasReplayStats(fakeReplayStats)
                    containsExactlyContextAttributes(fakeAttributes)
                    hasSource(fakeSourceViewEvent)
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toViewSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasSessionActive(fakeParentContext.isSessionActive)
                }
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `M send event W handleEvent(AddError) on active view { error fingerprint attribute }`(
        @Forgery source: RumErrorSource,
        @Forgery throwable: Throwable,
        @StringForgery stacktrace: String,
        @StringForgery fingerprint: String,
        forge: Forge
    ) {
        // Given
        testedScope.activeActionScope = mockActionScope
        val mockAttributes = forge.exhaustiveAttributes(excludedKeys = fakeAttributes.keys)
        val fullAttributes = mockAttributes.toMutableMap().apply {
            put(RumAttributes.ERROR_FINGERPRINT, fingerprint)
        }
        val throwableMessage = throwable.message
        check(!throwableMessage.isNullOrBlank()) {
            "Expected throwable to have a non null, non blank message"
        }
        fakeEvent = RumRawEvent.AddError(
            throwableMessage,
            source,
            throwable,
            stacktrace,
            isFatal = false,
            threads = emptyList(),
            attributes = fullAttributes
        )

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        argumentCaptor<ErrorEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))

            assertThat(firstValue)
                .apply {
                    hasTimestamp(resolveExpectedTimestamp(fakeEvent.eventTime.timestamp))
                    hasMessage(throwableMessage)
                    hasErrorSource(source)
                    hasStackTrace(stacktrace)
                    hasErrorFingerprint(fingerprint)
                    isCrash(false)
                    hasNoThreads()
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasAccountInfo(fakeDatadogContext.accountInfo)
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasView(testedScope.viewId, testedScope.key.name, testedScope.url)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasActionId(fakeActionId)
                    hasStartReason(fakeParentContext.sessionStartReason)
                    hasReplay(fakeHasReplay)
                    containsExactlyContextAttributes(mockAttributes)
                    hasSource(fakeSourceErrorEvent)
                    hasSessionType(fakeRumSessionType?.toError() ?: ErrorEvent.ErrorEventSessionType.USER)
                    hasNoSyntheticsTest()
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toErrorSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasSampleRate(fakeSampleRate)
                }
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `M send event with global attributes W handleEvent(AddError)`(
        @StringForgery message: String,
        @Forgery source: RumErrorSource,
        @Forgery throwable: Throwable,
        @Forgery sourceType: RumErrorSourceType,
        forge: Forge
    ) {
        // Given
        testedScope.activeActionScope = mockActionScope
        fakeEvent = RumRawEvent.AddError(
            message,
            source,
            throwable,
            stacktrace = null,
            isFatal = false,
            threads = emptyList(),
            attributes = emptyMap(),
            sourceType = sourceType
        )
        val attributes = forgeGlobalAttributes(forge, fakeAttributes)
        whenever(rumMonitor.mockInstance.getAttributes()) doReturn attributes

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        val expectedMessage = "$message: ${throwable.message}"
        argumentCaptor<ErrorEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))

            assertThat(firstValue)
                .apply {
                    hasTimestamp(resolveExpectedTimestamp(fakeEvent.eventTime.timestamp))
                    hasMessage(expectedMessage)
                    hasErrorSource(source)
                    hasErrorCategory(ErrorEvent.Category.EXCEPTION)
                    hasTimeSinceAppStart(null)
                    hasStackTrace(throwable.loggableStackTrace())
                    isCrash(false)
                    hasNoThreads()
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasAccountInfo(fakeDatadogContext.accountInfo)
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasView(testedScope.viewId, testedScope.key.name, testedScope.url)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasActionId(fakeActionId)
                    hasErrorType(throwable.javaClass.canonicalName)
                    hasErrorSourceType(sourceType.toSchemaSourceType())
                    hasSessionType(fakeRumSessionType?.toError() ?: ErrorEvent.ErrorEventSessionType.USER)
                    hasNoSyntheticsTest()
                    hasStartReason(fakeParentContext.sessionStartReason)
                    hasReplay(fakeHasReplay)
                    containsExactlyContextAttributes(attributes)
                    hasSource(fakeSourceErrorEvent)
                    hasSessionType(fakeRumSessionType?.toError() ?: ErrorEvent.ErrorEventSessionType.USER)
                    hasNoSyntheticsTest()
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toErrorSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasSampleRate(fakeSampleRate)
                    hasBuildId(fakeDatadogContext.appBuildId)
                }
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `M send event W handleEvent(AddError) {isFatal=true}`(
        @StringForgery message: String,
        @Forgery source: RumErrorSource,
        @Forgery throwable: Throwable,
        @Forgery sourceType: RumErrorSourceType,
        @Forgery threads: List<ThreadDump>,
        @LongForgery timeSinceAppStart: Long,
        forge: Forge
    ) {
        // Given
        testedScope.activeActionScope = mockActionScope
        val attributes = forge.exhaustiveAttributes(excludedKeys = fakeAttributes.keys)
        fakeEvent = RumRawEvent.AddError(
            message,
            source,
            throwable,
            stacktrace = null,
            isFatal = true,
            threads = threads,
            attributes = attributes,
            sourceType = sourceType,
            timeSinceAppStartNs = timeSinceAppStart
        )

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        val expectedMessage = "$message: ${throwable.message}"
        argumentCaptor<Any> {
            verify(mockWriter, times(2)).write(eq(mockEventBatchWriter), capture(), eq(EventType.CRASH))
            assertThat(firstValue as ErrorEvent)
                .apply {
                    hasTimestamp(resolveExpectedTimestamp(fakeEvent.eventTime.timestamp))
                    hasMessage(expectedMessage)
                    hasErrorSource(source)
                    hasStackTrace(throwable.loggableStackTrace())
                    isCrash(true)
                    hasThreads(threads)
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasAccountInfo(fakeDatadogContext.accountInfo)
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasView(testedScope.viewId, testedScope.key.name, testedScope.url)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasActionId(fakeActionId)
                    hasErrorType(throwable.javaClass.canonicalName)
                    hasErrorSourceType(sourceType.toSchemaSourceType())
                    hasErrorCategory(ErrorEvent.Category.EXCEPTION)
                    hasTimeSinceAppStart(TimeUnit.NANOSECONDS.toMillis(timeSinceAppStart))
                    hasSessionType(fakeRumSessionType?.toError() ?: ErrorEvent.ErrorEventSessionType.USER)
                    hasNoSyntheticsTest()
                    hasStartReason(fakeParentContext.sessionStartReason)
                    hasReplay(fakeHasReplay)
                    containsExactlyContextAttributes(attributes)
                    hasSource(fakeSourceErrorEvent)
                    hasSessionType(fakeRumSessionType?.toError() ?: ErrorEvent.ErrorEventSessionType.USER)
                    hasNoSyntheticsTest()
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toErrorSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasBuildId(fakeDatadogContext.appBuildId)
                }
            assertThat(lastValue as ViewEvent)
                .apply {
                    hasTimestamp(resolveExpectedTimestamp(fakeEventTime.timestamp))
                    hasName(fakeKey.name)
                    hasUrl(fakeUrl)
                    hasDurationGreaterThan(1)
                    hasLoadingTime(null)
                    hasNetworkSettledTime(fakeNetworkSettledMetricValue)
                    hasInteractionToNextViewTime(fakeInteractionToNextViewMetricValue)
                    hasLoadingType(null)
                    hasVersion(2)
                    hasErrorCount(1)
                    hasCrashCount(1)
                    hasResourceCount(0)
                    hasActionCount(0)
                    hasFrustrationCount(0)
                    hasLongTaskCount(0)
                    hasFrozenFrameCount(0)
                    hasCpuMetric(null)
                    hasMemoryMetric(null, null)
                    hasRefreshRateMetric(null, null)
                    isActive(true)
                    isSlowRendered(false)
                    hasNoCustomTimings()
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasAccountInfo(fakeDatadogContext.accountInfo)
                    hasViewId(testedScope.viewId)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasSessionType(fakeRumSessionType?.toView() ?: ViewEvent.ViewEventSessionType.USER)
                    hasNoSyntheticsTest()
                    hasStartReason(fakeParentContext.sessionStartReason)
                    hasReplay(fakeHasReplay)
                    hasReplayStats(fakeReplayStats)
                    containsExactlyContextAttributes(fakeAttributes)
                    hasSource(fakeSourceViewEvent)
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toViewSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasSessionActive(fakeParentContext.isSessionActive)
                }
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `M send event W handleEvent(AddError) {internal is_crash=true}`(
        @StringForgery message: String,
        @Forgery source: RumErrorSource,
        @Forgery throwable: Throwable,
        @Forgery sourceType: RumErrorSourceType,
        forge: Forge
    ) {
        // Given
        testedScope.activeActionScope = mockActionScope
        val attributes = forge.exhaustiveAttributes(excludedKeys = fakeAttributes.keys)
        val attributesWithCrash = attributes.toMutableMap()
        attributesWithCrash["_dd.error.is_crash"] = true
        fakeEvent = RumRawEvent.AddError(
            message,
            source,
            throwable,
            stacktrace = null,
            isFatal = false,
            // empty list, because _dd.error.is_crash is coming from Cross Platform, this not a native crash
            threads = emptyList(),
            attributes = attributesWithCrash,
            sourceType = sourceType
        )

        // Sending a second crash should not trigger a view update
        val fakeNativeCrashEvent = RumRawEvent.AddError(
            message,
            source,
            throwable,
            stacktrace = null,
            isFatal = true,
            threads = emptyList(),
            attributes = attributes,
            sourceType = sourceType,
            timeSinceAppStartNs = forge.aPositiveLong()
        )

        // When
        val result = testedScope
            .handleEvent(fakeEvent, mockWriter)
            ?.handleEvent(fakeNativeCrashEvent, mockWriter)

        // Then
        val expectedMessage = "$message: ${throwable.message}"
        argumentCaptor<Any> {
            verify(mockWriter, times(2)).write(eq(mockEventBatchWriter), capture(), eq(EventType.CRASH))
            assertThat(firstValue as ErrorEvent)
                .apply {
                    hasTimestamp(resolveExpectedTimestamp(fakeEvent.eventTime.timestamp))
                    hasMessage(expectedMessage)
                    hasErrorSource(source)
                    hasStackTrace(throwable.loggableStackTrace())
                    isCrash(true)
                    hasNoThreads()
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasAccountInfo(fakeDatadogContext.accountInfo)
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasView(testedScope.viewId, testedScope.key.name, testedScope.url)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasActionId(fakeActionId)
                    hasErrorType(throwable.javaClass.canonicalName)
                    hasErrorSourceType(sourceType.toSchemaSourceType())
                    hasErrorCategory(ErrorEvent.Category.EXCEPTION)
                    // since this crash is coming externally (from cross-platform), expectation is to have it provided
                    // as an attribute from there as well
                    hasTimeSinceAppStart(null)
                    hasSessionType(fakeRumSessionType?.toError() ?: ErrorEvent.ErrorEventSessionType.USER)
                    hasNoSyntheticsTest()
                    hasStartReason(fakeParentContext.sessionStartReason)
                    hasReplay(fakeHasReplay)
                    containsExactlyContextAttributes(attributes)
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toErrorSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasBuildId(fakeDatadogContext.appBuildId)
                }
            assertThat(lastValue as ViewEvent)
                .apply {
                    hasTimestamp(resolveExpectedTimestamp(fakeEventTime.timestamp))
                    hasName(fakeKey.name)
                    hasUrl(fakeUrl)
                    hasDurationGreaterThan(1)
                    hasLoadingTime(null)
                    hasNetworkSettledTime(fakeNetworkSettledMetricValue)
                    hasInteractionToNextViewTime(fakeInteractionToNextViewMetricValue)
                    hasLoadingType(null)
                    hasVersion(2)
                    hasErrorCount(1)
                    hasCrashCount(1)
                    hasResourceCount(0)
                    hasActionCount(0)
                    hasFrustrationCount(0)
                    hasLongTaskCount(0)
                    hasFrozenFrameCount(0)
                    hasCpuMetric(null)
                    hasMemoryMetric(null, null)
                    hasRefreshRateMetric(null, null)
                    isActive(true)
                    isSlowRendered(false)
                    hasNoCustomTimings()
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasAccountInfo(fakeDatadogContext.accountInfo)
                    hasViewId(testedScope.viewId)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasSessionType(fakeRumSessionType?.toView() ?: ViewEvent.ViewEventSessionType.USER)
                    hasNoSyntheticsTest()
                    hasStartReason(fakeParentContext.sessionStartReason)
                    hasReplay(fakeHasReplay)
                    hasReplayStats(fakeReplayStats)
                    containsExactlyContextAttributes(fakeAttributes)
                    hasSource(fakeSourceViewEvent)
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toViewSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasSessionActive(fakeParentContext.isSessionActive)
                }
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `M send event W handleEvent(AddError) {internal is_crash=false}`(
        @StringForgery message: String,
        @Forgery source: RumErrorSource,
        @Forgery throwable: Throwable,
        @Forgery sourceType: RumErrorSourceType,
        forge: Forge
    ) {
        // Given
        testedScope.activeActionScope = mockActionScope
        val attributes = forge.exhaustiveAttributes(excludedKeys = fakeAttributes.keys)
        val attributesWithCrash = attributes.toMutableMap()
        attributesWithCrash["_dd.error.is_crash"] = false
        fakeEvent = RumRawEvent.AddError(
            message,
            source,
            throwable,
            stacktrace = null,
            isFatal = false,
            threads = emptyList(),
            attributes = attributesWithCrash,
            sourceType = sourceType
        )

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        val expectedMessage = "$message: ${throwable.message}"
        argumentCaptor<Any> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))
            assertThat(firstValue as ErrorEvent)
                .apply {
                    hasTimestamp(resolveExpectedTimestamp(fakeEvent.eventTime.timestamp))
                    hasMessage(expectedMessage)
                    hasErrorSource(source)
                    hasStackTrace(throwable.loggableStackTrace())
                    isCrash(false)
                    hasNoThreads()
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasAccountInfo(fakeDatadogContext.accountInfo)
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasView(testedScope.viewId, testedScope.key.name, testedScope.url)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasActionId(fakeActionId)
                    hasErrorType(throwable.javaClass.canonicalName)
                    hasErrorSourceType(sourceType.toSchemaSourceType())
                    hasErrorCategory(ErrorEvent.Category.EXCEPTION)
                    hasTimeSinceAppStart(null)
                    hasSessionType(fakeRumSessionType?.toError() ?: ErrorEvent.ErrorEventSessionType.USER)
                    hasNoSyntheticsTest()
                    hasStartReason(fakeParentContext.sessionStartReason)
                    hasReplay(fakeHasReplay)
                    containsExactlyContextAttributes(attributes)
                    hasSource(fakeSourceErrorEvent)
                    hasSessionType(fakeRumSessionType?.toError() ?: ErrorEvent.ErrorEventSessionType.USER)
                    hasNoSyntheticsTest()
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toErrorSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasBuildId(fakeDatadogContext.appBuildId)
                }
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `M send event W handleEvent(AddError) {custom error type}`(
        @StringForgery message: String,
        @Forgery source: RumErrorSource,
        @Forgery throwable: Throwable,
        @StringForgery errorType: String,
        @Forgery sourceType: RumErrorSourceType,
        forge: Forge
    ) {
        // Given
        testedScope.activeActionScope = mockActionScope
        val attributes = forge.exhaustiveAttributes(excludedKeys = fakeAttributes.keys)
        fakeEvent = RumRawEvent.AddError(
            message,
            source,
            throwable,
            stacktrace = null,
            isFatal = false,
            threads = emptyList(),
            attributes = attributes,
            type = errorType,
            sourceType = sourceType
        )

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        val expectedMessage = "$message: ${throwable.message}"
        argumentCaptor<ErrorEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))

            assertThat(firstValue)
                .apply {
                    hasTimestamp(resolveExpectedTimestamp(fakeEvent.eventTime.timestamp))
                    hasMessage(expectedMessage)
                    hasErrorSource(source)
                    hasErrorCategory(ErrorEvent.Category.EXCEPTION)
                    hasTimeSinceAppStart(null)
                    hasStackTrace(throwable.loggableStackTrace())
                    isCrash(false)
                    hasNoThreads()
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasAccountInfo(fakeDatadogContext.accountInfo)
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasView(testedScope.viewId, testedScope.key.name, testedScope.url)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasActionId(fakeActionId)
                    hasErrorType(errorType)
                    hasErrorSourceType(sourceType.toSchemaSourceType())
                    hasSessionType(fakeRumSessionType?.toError() ?: ErrorEvent.ErrorEventSessionType.USER)
                    hasNoSyntheticsTest()
                    hasStartReason(fakeParentContext.sessionStartReason)
                    hasReplay(fakeHasReplay)
                    containsExactlyContextAttributes(attributes)
                    hasSource(fakeSourceErrorEvent)
                    hasSessionType(fakeRumSessionType?.toError() ?: ErrorEvent.ErrorEventSessionType.USER)
                    hasNoSyntheticsTest()
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toErrorSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasSampleRate(fakeSampleRate)
                    hasBuildId(fakeDatadogContext.appBuildId)
                }
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `M send event with global attributes W handleEvent(AddError) {isFatal=true}`(
        @StringForgery message: String,
        @Forgery source: RumErrorSource,
        @Forgery throwable: Throwable,
        @Forgery sourceType: RumErrorSourceType,
        @Forgery threads: List<ThreadDump>,
        @LongForgery timeSinceAppStart: Long,
        forge: Forge
    ) {
        // Given
        testedScope.activeActionScope = mockActionScope
        fakeEvent = RumRawEvent.AddError(
            message,
            source,
            throwable,
            stacktrace = null,
            isFatal = true,
            threads = threads,
            attributes = emptyMap(),
            sourceType = sourceType,
            timeSinceAppStartNs = timeSinceAppStart
        )
        val attributes = forgeGlobalAttributes(forge, fakeAttributes)
        whenever(rumMonitor.mockInstance.getAttributes()) doReturn attributes

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        val expectedMessage = "$message: ${throwable.message}"
        argumentCaptor<Any> {
            verify(mockWriter, times(2)).write(eq(mockEventBatchWriter), capture(), eq(EventType.CRASH))
            assertThat(firstValue as ErrorEvent)
                .apply {
                    hasTimestamp(resolveExpectedTimestamp(fakeEvent.eventTime.timestamp))
                    hasMessage(expectedMessage)
                    hasErrorSource(source)
                    hasStackTrace(throwable.loggableStackTrace())
                    isCrash(true)
                    hasThreads(threads)
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasAccountInfo(fakeDatadogContext.accountInfo)
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasView(testedScope.viewId, testedScope.key.name, testedScope.url)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasActionId(fakeActionId)
                    hasErrorType(throwable.javaClass.canonicalName)
                    hasErrorSourceType(sourceType.toSchemaSourceType())
                    hasErrorCategory(ErrorEvent.Category.EXCEPTION)
                    hasTimeSinceAppStart(TimeUnit.NANOSECONDS.toMillis(timeSinceAppStart))
                    hasSessionType(fakeRumSessionType?.toError() ?: ErrorEvent.ErrorEventSessionType.USER)
                    hasNoSyntheticsTest()
                    hasStartReason(fakeParentContext.sessionStartReason)
                    hasReplay(fakeHasReplay)
                    containsExactlyContextAttributes(attributes)
                    hasSource(fakeSourceErrorEvent)
                    hasSessionType(fakeRumSessionType?.toError() ?: ErrorEvent.ErrorEventSessionType.USER)
                    hasNoSyntheticsTest()
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toErrorSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasBuildId(fakeDatadogContext.appBuildId)
                }
            assertThat(lastValue as ViewEvent)
                .apply {
                    hasTimestamp(resolveExpectedTimestamp(fakeEventTime.timestamp))
                    hasName(fakeKey.name)
                    hasUrl(fakeUrl)
                    hasDurationGreaterThan(1)
                    hasLoadingTime(null)
                    hasNetworkSettledTime(fakeNetworkSettledMetricValue)
                    hasInteractionToNextViewTime(fakeInteractionToNextViewMetricValue)
                    hasLoadingType(null)
                    hasVersion(2)
                    hasErrorCount(1)
                    hasCrashCount(1)
                    hasResourceCount(0)
                    hasActionCount(0)
                    hasFrustrationCount(0)
                    hasLongTaskCount(0)
                    hasFrozenFrameCount(0)
                    hasCpuMetric(null)
                    hasMemoryMetric(null, null)
                    hasRefreshRateMetric(null, null)
                    isActive(true)
                    isSlowRendered(false)
                    hasNoCustomTimings()
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasAccountInfo(fakeDatadogContext.accountInfo)
                    hasViewId(testedScope.viewId)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasSessionType(fakeRumSessionType?.toView() ?: ViewEvent.ViewEventSessionType.USER)
                    hasNoSyntheticsTest()
                    hasStartReason(fakeParentContext.sessionStartReason)
                    hasReplay(fakeHasReplay)
                    hasReplayStats(fakeReplayStats)
                    containsExactlyContextAttributes(fakeAttributes + attributes)
                    hasSource(fakeSourceViewEvent)
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toViewSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasSessionActive(fakeParentContext.isSessionActive)
                }
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `M do nothing W handleEvent(AddError) on stopped view {throwable}`(
        @StringForgery message: String,
        @Forgery source: RumErrorSource,
        @Forgery throwable: Throwable,
        @BoolForgery fatal: Boolean,
        forge: Forge
    ) {
        // Given
        testedScope.activeActionScope = mockActionScope
        val attributes = forge.exhaustiveAttributes(excludedKeys = fakeAttributes.keys)
        fakeEvent = RumRawEvent.AddError(
            message,
            source,
            throwable,
            stacktrace = null,
            isFatal = fatal,
            threads = emptyList(),
            attributes = attributes
        )
        testedScope.stopped = true

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        verifyNoInteractions(mockWriter)
        assertThat(result).isNull()
    }

    @Test
    fun `M do nothing W handleEvent(AddError) on stopped view {stacktrace}`(
        @StringForgery message: String,
        @Forgery source: RumErrorSource,
        @StringForgery stacktrace: String,
        @BoolForgery fatal: Boolean,
        forge: Forge
    ) {
        // Given
        testedScope.activeActionScope = mockActionScope
        val attributes = forge.exhaustiveAttributes(excludedKeys = fakeAttributes.keys)
        fakeEvent = RumRawEvent.AddError(
            message,
            source,
            throwable = null,
            stacktrace,
            fatal,
            threads = emptyList(),
            attributes = attributes
        )
        testedScope.stopped = true

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        verifyNoInteractions(mockWriter)
        assertThat(result).isNull()
    }

    @Test
    fun `M wait for pending Error W handleEvent(AddError) on active view {fatal=false}`(
        @StringForgery message: String,
        @Forgery source: RumErrorSource,
        @StringForgery stacktrace: String
    ) {
        // Given
        testedScope.pendingErrorCount = 0
        fakeEvent = RumRawEvent.AddError(
            message,
            source,
            throwable = null,
            stacktrace,
            isFatal = false,
            threads = emptyList(),
            attributes = emptyMap()
        )

        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        assertThat(testedScope.pendingErrorCount).isEqualTo(1)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `M not wait for pending Error W handleEvent(AddError) on active view {fatal=true}`(
        @StringForgery message: String,
        @Forgery source: RumErrorSource,
        @StringForgery stacktrace: String
    ) {
        // Given
        testedScope.pendingErrorCount = 0
        fakeEvent = RumRawEvent.AddError(
            message,
            source,
            throwable = null,
            stacktrace,
            isFatal = true,
            threads = emptyList(),
            attributes = emptyMap()
        )

        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        assertThat(testedScope.pendingErrorCount).isEqualTo(0)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `M decrease pending Error W handleEvent(ErrorDropped) on active view`(
        @LongForgery(1) pending: Long
    ) {
        // Given
        testedScope.pendingErrorCount = pending
        fakeEvent = RumRawEvent.ErrorDropped(testedScope.viewId)

        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        assertThat(testedScope.pendingErrorCount).isEqualTo(pending - 1)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `M decrease pending Error W handleEvent(ErrorDropped) on stopped view`() {
        // Given
        testedScope.pendingErrorCount = 1
        fakeEvent = RumRawEvent.ErrorDropped(testedScope.viewId)
        testedScope.stopped = true

        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        assertThat(testedScope.pendingErrorCount).isEqualTo(0)
        assertThat(result).isNull()
    }

    @Test
    fun `M do nothing W handleEvent(ErrorDropped) on active view {unknown viewId}`(
        @LongForgery(1) pending: Long,
        @Forgery viewUuid: UUID
    ) {
        // Given
        val viewId = viewUuid.toString()
        assumeTrue(viewId != testedScope.viewId)
        testedScope.pendingErrorCount = pending
        fakeEvent = RumRawEvent.ErrorDropped(viewId)

        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        assertThat(testedScope.pendingErrorCount).isEqualTo(pending)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `M do nothing W handleEvent(ErrorDropped) on stopped view {unknown viewId}`(
        @LongForgery(1) pending: Long,
        @Forgery viewUuid: UUID
    ) {
        // Given
        val viewId = viewUuid.toString()
        assumeTrue(viewId != testedScope.viewId)
        testedScope.pendingErrorCount = pending
        fakeEvent = RumRawEvent.ErrorDropped(viewId)
        testedScope.stopped = true

        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        assertThat(testedScope.pendingErrorCount).isEqualTo(pending)
        assertThat(result).isSameAs(testedScope)
    }

    // endregion

    // region Long Task

    @Test
    fun `M send event W handleEvent(AddLongTask) on active view {not frozen}`(
        @LongForgery(0L, 700_000_000L) durationNs: Long,
        @StringForgery target: String
    ) {
        // Given
        testedScope.activeActionScope = null
        fakeEvent = RumRawEvent.AddLongTask(durationNs, target)
        val durationMs = TimeUnit.NANOSECONDS.toMillis(durationNs)

        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        argumentCaptor<LongTaskEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))

            assertThat(firstValue)
                .apply {
                    hasTimestamp(
                        resolveExpectedTimestamp(fakeEvent.eventTime.timestamp) - durationMs
                    )
                    hasDuration(durationNs)
                    isFrozenFrame(false)
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasAccountInfo(fakeDatadogContext.accountInfo)
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasView(testedScope.viewId, testedScope.url)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasStartReason(fakeParentContext.sessionStartReason)
                    hasSessionType(fakeRumSessionType?.toLongTask() ?: LongTaskEvent.LongTaskEventSessionType.USER)
                    hasNoSyntheticsTest()
                    hasReplay(fakeHasReplay)
                    hasSource(fakeSourceLongTaskEvent)
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toLongTaskSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasSampleRate(fakeSampleRate)
                }
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `M send event W handleEvent(AddLongTask) on active view {frozen}`(
        @LongForgery(700_000_000L, 10_000_000_000L) durationNs: Long,
        @StringForgery target: String
    ) {
        // Given
        testedScope.activeActionScope = null
        fakeEvent = RumRawEvent.AddLongTask(durationNs, target)
        val durationMs = TimeUnit.NANOSECONDS.toMillis(durationNs)

        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        argumentCaptor<LongTaskEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))

            assertThat(firstValue)
                .apply {
                    hasTimestamp(
                        resolveExpectedTimestamp(fakeEvent.eventTime.timestamp) - durationMs
                    )
                    hasDuration(durationNs)
                    isFrozenFrame(true)
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasAccountInfo(fakeDatadogContext.accountInfo)
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasView(testedScope.viewId, testedScope.url)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasStartReason(fakeParentContext.sessionStartReason)
                    hasSessionType(fakeRumSessionType?.toLongTask() ?: LongTaskEvent.LongTaskEventSessionType.USER)
                    hasNoSyntheticsTest()
                    hasReplay(fakeHasReplay)
                    hasSource(fakeSourceLongTaskEvent)
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toLongTaskSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasSampleRate(fakeSampleRate)
                }
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `M send event synthetics info W handleEvent(AddLongTask) on active view {not frozen}`(
        @StringForgery fakeTestId: String,
        @StringForgery fakeResultId: String,
        @LongForgery(0L, 700_000_000L) durationNs: Long,
        @StringForgery target: String
    ) {
        // Given
        testedScope.activeActionScope = null
        fakeEvent = RumRawEvent.AddLongTask(durationNs, target)
        val durationMs = TimeUnit.NANOSECONDS.toMillis(durationNs)
        fakeParentContext = fakeParentContext.copy(
            syntheticsTestId = fakeTestId,
            syntheticsResultId = fakeResultId
        )
        whenever(mockParentScope.getRumContext()) doReturn fakeParentContext

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        argumentCaptor<LongTaskEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))

            assertThat(firstValue)
                .apply {
                    hasTimestamp(
                        resolveExpectedTimestamp(fakeEvent.eventTime.timestamp) - durationMs
                    )
                    hasDuration(durationNs)
                    isFrozenFrame(false)
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasAccountInfo(fakeDatadogContext.accountInfo)
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasView(testedScope.viewId, testedScope.url)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasStartReason(fakeParentContext.sessionStartReason)
                    hasSessionType(
                        fakeRumSessionType?.toLongTask() ?: LongTaskEvent.LongTaskEventSessionType.SYNTHETICS
                    )
                    hasSyntheticsTest(fakeTestId, fakeResultId)
                    hasReplay(fakeHasReplay)
                    hasSource(fakeSourceLongTaskEvent)
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toLongTaskSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasSampleRate(fakeSampleRate)
                }
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `M send event with synthetics info W handleEvent(AddLongTask) on active view {frozen}`(
        @StringForgery fakeTestId: String,
        @StringForgery fakeResultId: String,
        @LongForgery(700_000_000L, 10_000_000_000L) durationNs: Long,
        @StringForgery target: String
    ) {
        // Given
        testedScope.activeActionScope = null
        fakeEvent = RumRawEvent.AddLongTask(durationNs, target)
        val durationMs = TimeUnit.NANOSECONDS.toMillis(durationNs)
        fakeParentContext = fakeParentContext.copy(
            syntheticsTestId = fakeTestId,
            syntheticsResultId = fakeResultId
        )
        whenever(mockParentScope.getRumContext()) doReturn fakeParentContext

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        argumentCaptor<LongTaskEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))

            assertThat(firstValue)
                .apply {
                    hasTimestamp(
                        resolveExpectedTimestamp(fakeEvent.eventTime.timestamp) - durationMs
                    )
                    hasDuration(durationNs)
                    isFrozenFrame(true)
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasAccountInfo(fakeDatadogContext.accountInfo)
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasView(testedScope.viewId, testedScope.url)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasStartReason(fakeParentContext.sessionStartReason)
                    hasSessionType(
                        fakeRumSessionType?.toLongTask() ?: LongTaskEvent.LongTaskEventSessionType.SYNTHETICS
                    )
                    hasSyntheticsTest(fakeTestId, fakeResultId)
                    hasReplay(fakeHasReplay)
                    hasSource(fakeSourceLongTaskEvent)
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toLongTaskSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasSampleRate(fakeSampleRate)
                }
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `M send event with global attributes W handleEvent(AddLongTask) {not frozen}`(
        @LongForgery(0L, 700_000_000L) durationNs: Long,
        @StringForgery target: String,
        forge: Forge
    ) {
        // Given
        testedScope.activeActionScope = mockActionScope
        val fakeLongTaskEvent = RumRawEvent.AddLongTask(durationNs, target)
        val attributes = forgeGlobalAttributes(forge, fakeAttributes)
        val durationMs = TimeUnit.NANOSECONDS.toMillis(durationNs)
        whenever(rumMonitor.mockInstance.getAttributes()) doReturn attributes
        val expectedAttributes = attributes + mapOf(
            RumAttributes.LONG_TASK_TARGET to fakeLongTaskEvent.target
        )

        // When
        val result = testedScope.handleEvent(fakeLongTaskEvent, mockWriter)

        // Then
        val expectedTimestamp =
            resolveExpectedTimestamp(fakeLongTaskEvent.eventTime.timestamp) - durationMs
        argumentCaptor<LongTaskEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))

            assertThat(firstValue)
                .apply {
                    hasTimestamp(expectedTimestamp)
                    hasDuration(durationNs)
                    isFrozenFrame(false)
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasAccountInfo(fakeDatadogContext.accountInfo)
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasView(testedScope.viewId, testedScope.url)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasActionId(fakeActionId)
                    hasStartReason(fakeParentContext.sessionStartReason)
                    hasSessionType(fakeRumSessionType?.toLongTask() ?: LongTaskEvent.LongTaskEventSessionType.USER)
                    hasNoSyntheticsTest()
                    hasReplay(fakeHasReplay)
                    containsExactlyContextAttributes(expectedAttributes)
                    hasSource(fakeSourceLongTaskEvent)
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toLongTaskSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasSampleRate(fakeSampleRate)
                }
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `M send event with global attributes W handleEvent(AddLongTask) {frozen}`(
        @LongForgery(700_000_000L, 10_000_000_000L) durationNs: Long,
        @StringForgery target: String,
        forge: Forge
    ) {
        // Given
        testedScope.activeActionScope = mockActionScope
        val fakeLongTaskEvent = RumRawEvent.AddLongTask(durationNs, target)
        val attributes = forgeGlobalAttributes(forge, fakeAttributes)
        val durationMs = TimeUnit.NANOSECONDS.toMillis(durationNs)
        whenever(rumMonitor.mockInstance.getAttributes()) doReturn attributes
        val expectedAttributes = attributes + mapOf(
            RumAttributes.LONG_TASK_TARGET to fakeLongTaskEvent.target
        )

        // When
        val result = testedScope.handleEvent(fakeLongTaskEvent, mockWriter)

        // Then
        argumentCaptor<LongTaskEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))

            assertThat(firstValue)
                .apply {
                    hasTimestamp(
                        resolveExpectedTimestamp(fakeLongTaskEvent.eventTime.timestamp) - durationMs
                    )
                    hasDuration(durationNs)
                    isFrozenFrame(true)
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasAccountInfo(fakeDatadogContext.accountInfo)
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasView(testedScope.viewId, testedScope.url)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasActionId(fakeActionId)
                    hasStartReason(fakeParentContext.sessionStartReason)
                    hasSessionType(fakeRumSessionType?.toLongTask() ?: LongTaskEvent.LongTaskEventSessionType.USER)
                    hasNoSyntheticsTest()
                    hasReplay(fakeHasReplay)
                    containsExactlyContextAttributes(expectedAttributes)
                    hasSource(fakeSourceLongTaskEvent)
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toLongTaskSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasSampleRate(fakeSampleRate)
                }
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `M do nothing W handleEvent(AddLongTask) on stopped view`(
        @LongForgery(0) durationNs: Long,
        @StringForgery target: String,
        @LongForgery(1) pending: Long
    ) {
        // Given
        testedScope.activeActionScope = mockActionScope
        testedScope.pendingLongTaskCount = pending
        fakeEvent = RumRawEvent.AddLongTask(durationNs, target)
        testedScope.stopped = true

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        verifyNoInteractions(mockWriter)
        assertThat(testedScope.pendingLongTaskCount).isEqualTo(pending)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `M wait for pending Long Task W handleEvent(AddLongTask) on active view {not frozen}`(
        @LongForgery(0L, 700_000_000L) durationNs: Long,
        @StringForgery target: String
    ) {
        // Given
        testedScope.pendingLongTaskCount = 0
        fakeEvent = RumRawEvent.AddLongTask(durationNs, target)

        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        assertThat(testedScope.pendingLongTaskCount).isEqualTo(1)
        assertThat(testedScope.pendingFrozenFrameCount).isEqualTo(0)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `M wait for pending LT and FF W handleEvent(AddLongTask) on active view {frozen}`(
        @LongForgery(700_000_000L, 10_000_000_000L) durationNs: Long,
        @StringForgery target: String
    ) {
        // Given
        testedScope.pendingLongTaskCount = 0
        fakeEvent = RumRawEvent.AddLongTask(durationNs, target)

        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        assertThat(testedScope.pendingLongTaskCount).isEqualTo(1)
        assertThat(testedScope.pendingFrozenFrameCount).isEqualTo(1)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `M decrease pending Long Task W handleEvent(LongTaskDropped) on active view {not frozen}`(
        @LongForgery(1) pendingLongTask: Long,
        @LongForgery(1) pendingFrozenFrame: Long
    ) {
        // Given
        testedScope.pendingLongTaskCount = pendingLongTask
        testedScope.pendingFrozenFrameCount = pendingFrozenFrame
        fakeEvent = RumRawEvent.LongTaskDropped(testedScope.viewId, false)

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        assertThat(testedScope.pendingLongTaskCount).isEqualTo(pendingLongTask - 1)
        assertThat(testedScope.pendingFrozenFrameCount).isEqualTo(pendingFrozenFrame)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `M decrease pending LT and FF W handleEvent(LongTaskDropped) on active view {frozen}`(
        @LongForgery(1) pendingLongTask: Long,
        @LongForgery(1) pendingFrozenFrame: Long
    ) {
        // Given
        testedScope.pendingLongTaskCount = pendingLongTask
        testedScope.pendingFrozenFrameCount = pendingFrozenFrame
        fakeEvent = RumRawEvent.LongTaskDropped(testedScope.viewId, true)

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        assertThat(testedScope.pendingLongTaskCount).isEqualTo(pendingLongTask - 1)
        assertThat(testedScope.pendingFrozenFrameCount).isEqualTo(pendingFrozenFrame - 1)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `M decrease pending LT W handleEvent(LongTaskDropped) on stopped view {not frozen}`() {
        // Given
        testedScope.pendingLongTaskCount = 1
        testedScope.pendingFrozenFrameCount = 0
        fakeEvent = RumRawEvent.LongTaskDropped(testedScope.viewId, false)
        testedScope.stopped = true

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        assertThat(testedScope.pendingLongTaskCount).isEqualTo(0)
        assertThat(testedScope.pendingFrozenFrameCount).isEqualTo(0)
        assertThat(result).isNull()
    }

    @Test
    fun `M decrease pending LT and FF W handleEvent(LongTaskDropped) on stopped view {frozen}`() {
        // Given
        testedScope.pendingLongTaskCount = 1
        testedScope.pendingFrozenFrameCount = 1
        fakeEvent = RumRawEvent.LongTaskDropped(testedScope.viewId, true)
        testedScope.stopped = true

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        assertThat(testedScope.pendingLongTaskCount).isEqualTo(0)
        assertThat(testedScope.pendingLongTaskCount).isEqualTo(0)
        assertThat(result).isNull()
    }

    @Test
    fun `M do nothing W handleEvent(LongTaskDropped) on active view {unknown viewId}`(
        @LongForgery(1) pending: Long,
        @BoolForgery isFrozenFrame: Boolean,
        @Forgery viewUuid: UUID
    ) {
        // Given
        val viewId = viewUuid.toString()
        assumeTrue(viewId != testedScope.viewId)
        testedScope.pendingLongTaskCount = pending
        fakeEvent = RumRawEvent.LongTaskDropped(viewId, isFrozenFrame)

        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        assertThat(testedScope.pendingLongTaskCount).isEqualTo(pending)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `M do nothing W handleEvent(LongTaskDropped) on stopped view {unknown viewId}`(
        @LongForgery(1) pending: Long,
        @BoolForgery isFrozenFrame: Boolean,
        @Forgery viewUuid: UUID
    ) {
        // Given
        val viewId = viewUuid.toString()
        assumeTrue(viewId != testedScope.viewId)
        testedScope.pendingLongTaskCount = pending
        fakeEvent = RumRawEvent.LongTaskDropped(viewId, isFrozenFrame)
        testedScope.stopped = true

        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        assertThat(testedScope.pendingLongTaskCount).isEqualTo(pending)
        assertThat(result).isSameAs(testedScope)
    }

    // endregion

    // region Loading Time

    @Test
    fun `M send event with custom timing W handleEvent(AddCustomTiming) on active view`(
        forge: Forge
    ) {
        // Given
        val fakeTimingKey = forge.anAlphabeticalString()

        // When
        testedScope.handleEvent(
            RumRawEvent.AddCustomTiming(fakeTimingKey),
            mockWriter
        )
        val customTimingEstimatedDuration = System.nanoTime() - fakeEventTime.nanoTime

        // Then
        argumentCaptor<ViewEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))
            assertThat(lastValue)
                .apply {
                    hasTimestamp(resolveExpectedTimestamp(fakeEventTime.timestamp))
                    hasName(fakeKey.name)
                    hasUrl(fakeUrl)
                    hasDurationGreaterThan(1)
                    hasLoadingTime(null)
                    hasNetworkSettledTime(fakeNetworkSettledMetricValue)
                    hasInteractionToNextViewTime(fakeInteractionToNextViewMetricValue)
                    hasLoadingType(null)
                    hasVersion(2)
                    hasErrorCount(0)
                    hasResourceCount(0)
                    hasActionCount(0)
                    hasFrustrationCount(0)
                    hasLongTaskCount(0)
                    hasFrozenFrameCount(0)
                    hasCpuMetric(null)
                    hasMemoryMetric(null, null)
                    hasRefreshRateMetric(null, null)
                    isActive(true)
                    isSlowRendered(false)
                    hasCustomTimings(mapOf(fakeTimingKey to customTimingEstimatedDuration))
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasAccountInfo(fakeDatadogContext.accountInfo)
                    hasViewId(testedScope.viewId)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasSessionType(fakeRumSessionType?.toView() ?: ViewEvent.ViewEventSessionType.USER)
                    hasNoSyntheticsTest()
                    hasStartReason(fakeParentContext.sessionStartReason)
                    hasReplay(fakeHasReplay)
                    hasReplayStats(fakeReplayStats)
                    hasSource(fakeSourceViewEvent)
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toViewSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasSlownessInfo(fakeSlowRecords)
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasSessionActive(fakeParentContext.isSessionActive)
                    hasSampleRate(fakeSampleRate)
                }
        }
        verifyNoMoreInteractions(mockWriter)
    }

    @Test
    fun `M send event with custom timings W handleEvent(AddCustomTiming) called multiple times`(
        forge: Forge
    ) {
        // Given
        val fakeTimingKey1 = forge.anAlphabeticalString()
        val fakeTimingKey2 = forge.anAlphabeticalString()

        // When
        testedScope.handleEvent(
            RumRawEvent.AddCustomTiming(fakeTimingKey1),
            mockWriter
        )
        val customTiming1EstimatedDuration = System.nanoTime() - fakeEventTime.nanoTime
        testedScope.handleEvent(
            RumRawEvent.AddCustomTiming(fakeTimingKey2),
            mockWriter
        )
        val customTiming2EstimatedDuration = System.nanoTime() - fakeEventTime.nanoTime

        // Then
        argumentCaptor<ViewEvent> {
            verify(mockWriter, times(2)).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))
            assertThat(firstValue)
                .apply {
                    hasTimestamp(resolveExpectedTimestamp(fakeEventTime.timestamp))
                    hasName(fakeKey.name)
                    hasUrl(fakeUrl)
                    hasDurationGreaterThan(1)
                    hasLoadingTime(null)
                    hasNetworkSettledTime(fakeNetworkSettledMetricValue)
                    hasInteractionToNextViewTime(fakeInteractionToNextViewMetricValue)
                    hasLoadingType(null)
                    hasVersion(2)
                    hasErrorCount(0)
                    hasResourceCount(0)
                    hasActionCount(0)
                    hasFrustrationCount(0)
                    hasLongTaskCount(0)
                    hasFrozenFrameCount(0)
                    hasCpuMetric(null)
                    hasMemoryMetric(null, null)
                    hasRefreshRateMetric(null, null)
                    isActive(true)
                    isSlowRendered(false)
                    hasCustomTimings(mapOf(fakeTimingKey1 to customTiming1EstimatedDuration))
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasAccountInfo(fakeDatadogContext.accountInfo)
                    hasViewId(testedScope.viewId)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasSessionType(fakeRumSessionType?.toView() ?: ViewEvent.ViewEventSessionType.USER)
                    hasNoSyntheticsTest()
                    hasStartReason(fakeParentContext.sessionStartReason)
                    hasReplay(fakeHasReplay)
                    hasReplayStats(fakeReplayStats)
                    containsExactlyContextAttributes(fakeAttributes)
                    hasSource(fakeSourceViewEvent)
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toViewSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasSlownessInfo(fakeSlowRecords)
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasSampleRate(fakeSampleRate)
                }
            assertThat(lastValue)
                .apply {
                    hasTimestamp(resolveExpectedTimestamp(fakeEventTime.timestamp))
                    hasName(fakeKey.name)
                    hasUrl(fakeUrl)
                    hasDurationGreaterThan(1)
                    hasLoadingTime(null)
                    hasNetworkSettledTime(fakeNetworkSettledMetricValue)
                    hasInteractionToNextViewTime(fakeInteractionToNextViewMetricValue)
                    hasLoadingType(null)
                    hasVersion(3)
                    hasErrorCount(0)
                    hasResourceCount(0)
                    hasActionCount(0)
                    hasFrustrationCount(0)
                    hasLongTaskCount(0)
                    hasFrozenFrameCount(0)
                    hasCpuMetric(null)
                    hasMemoryMetric(null, null)
                    hasRefreshRateMetric(null, null)
                    isActive(true)
                    isSlowRendered(false)
                    hasCustomTimings(
                        mapOf(
                            fakeTimingKey1 to customTiming1EstimatedDuration,
                            fakeTimingKey2 to customTiming2EstimatedDuration
                        )
                    )
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasAccountInfo(fakeDatadogContext.accountInfo)
                    hasViewId(testedScope.viewId)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasSessionType(fakeRumSessionType?.toView() ?: ViewEvent.ViewEventSessionType.USER)
                    hasNoSyntheticsTest()
                    hasStartReason(fakeParentContext.sessionStartReason)
                    hasReplay(fakeHasReplay)
                    hasReplayStats(fakeReplayStats)
                    containsExactlyContextAttributes(fakeAttributes)
                    hasSource(fakeSourceViewEvent)
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toViewSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasSlownessInfo(fakeSlowRecords)
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasSessionActive(fakeParentContext.isSessionActive)
                    hasSampleRate(fakeSampleRate)
                }
        }
        verifyNoMoreInteractions(mockWriter)
    }

    @Test
    fun `M not add custom timing W handleEvent(AddCustomTiming) on stopped view`(
        forge: Forge
    ) {
        // Given
        testedScope.stopped = true
        val fakeTimingKey = forge.anAlphabeticalString()

        // When
        testedScope.handleEvent(
            RumRawEvent.AddCustomTiming(fakeTimingKey),
            mockWriter
        )

        // Then
        assertThat(testedScope.customTimings).isEmpty()
        verifyNoInteractions(mockWriter)
    }

    // endregion

    // region View Loading Time

    @Test
    fun `M send event with view loading time W handleEvent(AddViewLoadingTime) on active view`(
        @BoolForgery fakeOverwrite: Boolean
    ) {
        // Given
        val viewLoadingTimeEvent = RumRawEvent.AddViewLoadingTime(overwrite = fakeOverwrite)
        val expectedViewLoadingTime = viewLoadingTimeEvent.eventTime.nanoTime - fakeEventTime.nanoTime

        // When
        testedScope.handleEvent(
            viewLoadingTimeEvent,
            mockWriter
        )

        // Then
        argumentCaptor<ViewEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))
            assertThat(lastValue)
                .apply {
                    hasTimestamp(resolveExpectedTimestamp(fakeEventTime.timestamp))
                    hasName(fakeKey.name)
                    hasUrl(fakeUrl)
                    hasDurationGreaterThan(1)
                    hasLoadingType(null)
                    hasVersion(2)
                    hasErrorCount(0)
                    hasResourceCount(0)
                    hasActionCount(0)
                    hasFrustrationCount(0)
                    hasLongTaskCount(0)
                    hasFrozenFrameCount(0)
                    hasCpuMetric(null)
                    hasMemoryMetric(null, null)
                    hasRefreshRateMetric(null, null)
                    isActive(true)
                    isSlowRendered(false)
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasAccountInfo(fakeDatadogContext.accountInfo)
                    hasViewId(testedScope.viewId)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasSessionType(fakeRumSessionType?.toView() ?: ViewEvent.ViewEventSessionType.USER)
                    hasNoSyntheticsTest()
                    hasStartReason(fakeParentContext.sessionStartReason)
                    hasReplay(fakeHasReplay)
                    hasReplayStats(fakeReplayStats)
                    hasSource(fakeSourceViewEvent)
                    hasLoadingTime(expectedViewLoadingTime)
                    hasNetworkSettledTime(fakeNetworkSettledMetricValue)
                    hasInteractionToNextViewTime(fakeInteractionToNextViewMetricValue)
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toViewSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasSlownessInfo(fakeSlowRecords)
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasSessionActive(fakeParentContext.isSessionActive)
                    hasSampleRate(fakeSampleRate)
                }
        }
        mockInternalLogger.verifyLog(
            InternalLogger.Level.DEBUG,
            InternalLogger.Target.USER,
            RumViewScope.ADDING_VIEW_LOADING_TIME_DEBUG_MESSAGE_FORMAT.format(
                expectedViewLoadingTime,
                testedScope.key.name
            )
        )
        mockInternalLogger.verifyApiUsage(
            InternalTelemetryEvent.ApiUsage.AddViewLoadingTime(
                overwrite = false,
                noView = false,
                noActiveView = false
            ),
            15f
        )
        verifyNoMoreInteractions(mockWriter)
    }

    @Test
    fun `M overwrite view loading W handleEvent(AddViewLoadingTime, overwrite=true)`(
        forge: Forge
    ) {
        // Given
        val previousLoadingTime = forge.aPositiveLong()
        testedScope.viewLoadingTime = previousLoadingTime
        val newViewLoadingTime = RumRawEvent.AddViewLoadingTime(overwrite = true)
        val expectedViewLoadingTime = newViewLoadingTime.eventTime.nanoTime - fakeEventTime.nanoTime

        // When
        testedScope.handleEvent(
            newViewLoadingTime,
            mockWriter
        )

        // Then
        argumentCaptor<ViewEvent> {
            verify(mockWriter)
                .write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))
            assertThat(firstValue)
                .apply {
                    hasTimestamp(resolveExpectedTimestamp(fakeEventTime.timestamp))
                    hasName(fakeKey.name)
                    hasUrl(fakeUrl)
                    hasDurationGreaterThan(1)
                    hasLoadingType(null)
                    hasVersion(2)
                    hasErrorCount(0)
                    hasResourceCount(0)
                    hasActionCount(0)
                    hasFrustrationCount(0)
                    hasLongTaskCount(0)
                    hasFrozenFrameCount(0)
                    hasCpuMetric(null)
                    hasMemoryMetric(null, null)
                    hasRefreshRateMetric(null, null)
                    isActive(true)
                    isSlowRendered(false)
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasAccountInfo(fakeDatadogContext.accountInfo)
                    hasViewId(testedScope.viewId)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasSessionType(fakeRumSessionType?.toView() ?: ViewEvent.ViewEventSessionType.USER)
                    hasNoSyntheticsTest()
                    hasStartReason(fakeParentContext.sessionStartReason)
                    hasReplay(fakeHasReplay)
                    hasReplayStats(fakeReplayStats)
                    hasSource(fakeSourceViewEvent)
                    hasLoadingTime(expectedViewLoadingTime)
                    hasNetworkSettledTime(fakeNetworkSettledMetricValue)
                    hasInteractionToNextViewTime(fakeInteractionToNextViewMetricValue)
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toViewSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasSlownessInfo(fakeSlowRecords)
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasSessionActive(fakeParentContext.isSessionActive)
                    hasSampleRate(fakeSampleRate)
                }
        }
        mockInternalLogger.verifyLog(
            InternalLogger.Level.WARN,
            InternalLogger.Target.USER,
            RumViewScope.OVERWRITING_VIEW_LOADING_TIME_WARNING_MESSAGE_FORMAT.format(
                Locale.US,
                testedScope.key.name,
                previousLoadingTime,
                expectedViewLoadingTime
            )
        )
        mockInternalLogger.verifyApiUsage(
            InternalTelemetryEvent.ApiUsage.AddViewLoadingTime(
                noActiveView = false,
                noView = false,
                overwrite = true
            ),
            15f
        )
        verifyNoMoreInteractions(mockWriter)
    }

    @Test
    fun `M update view loading time each time W handleEvent(AddViewLoadingTime, overwrite=true) multi calls`(
        forge: Forge
    ) {
        // Given
        val viewLoadingTimeEvents = forge.aList { RumRawEvent.AddViewLoadingTime(overwrite = true) }
        val expectedViewLoadingTime = viewLoadingTimeEvents.last().eventTime.nanoTime - fakeEventTime.nanoTime

        // When
        viewLoadingTimeEvents.forEach {
            testedScope.handleEvent(
                it,
                mockWriter
            )
        }

        // Then
        argumentCaptor<ViewEvent> {
            verify(mockWriter, times(viewLoadingTimeEvents.size))
                .write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))
            assertThat(lastValue)
                .apply {
                    hasTimestamp(resolveExpectedTimestamp(fakeEventTime.timestamp))
                    hasName(fakeKey.name)
                    hasUrl(fakeUrl)
                    hasDurationGreaterThan(1)
                    hasLoadingType(null)
                    hasVersion((viewLoadingTimeEvents.size + 1).toLong())
                    hasErrorCount(0)
                    hasResourceCount(0)
                    hasActionCount(0)
                    hasFrustrationCount(0)
                    hasLongTaskCount(0)
                    hasFrozenFrameCount(0)
                    hasCpuMetric(null)
                    hasMemoryMetric(null, null)
                    hasRefreshRateMetric(null, null)
                    isActive(true)
                    isSlowRendered(false)
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasAccountInfo(fakeDatadogContext.accountInfo)
                    hasViewId(testedScope.viewId)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasSessionType(fakeRumSessionType?.toView() ?: ViewEvent.ViewEventSessionType.USER)
                    hasNoSyntheticsTest()
                    hasStartReason(fakeParentContext.sessionStartReason)
                    hasReplay(fakeHasReplay)
                    hasReplayStats(fakeReplayStats)
                    hasSource(fakeSourceViewEvent)
                    hasLoadingTime(expectedViewLoadingTime)
                    hasNetworkSettledTime(fakeNetworkSettledMetricValue)
                    hasInteractionToNextViewTime(fakeInteractionToNextViewMetricValue)
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toViewSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasSlownessInfo(fakeSlowRecords)
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasSessionActive(fakeParentContext.isSessionActive)
                    hasSampleRate(fakeSampleRate)
                }
        }
        verifyNoMoreInteractions(mockWriter)
    }

    @Test
    fun `M update view loading time only first time W handleEvent(AddViewLoadingTime, overwrite=false) multi calls`(
        forge: Forge
    ) {
        // Given
        val viewLoadingTimeEvents = forge.aList { RumRawEvent.AddViewLoadingTime(overwrite = false) }
        val expectedViewLoadingTime = viewLoadingTimeEvents.first().eventTime.nanoTime - fakeEventTime.nanoTime

        // When
        viewLoadingTimeEvents.forEach {
            testedScope.handleEvent(
                it,
                mockWriter
            )
        }

        // Then
        argumentCaptor<ViewEvent> {
            verify(mockWriter)
                .write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))
            assertThat(lastValue)
                .apply {
                    hasTimestamp(resolveExpectedTimestamp(fakeEventTime.timestamp))
                    hasName(fakeKey.name)
                    hasUrl(fakeUrl)
                    hasDurationGreaterThan(1)
                    hasLoadingType(null)
                    hasVersion(2)
                    hasErrorCount(0)
                    hasResourceCount(0)
                    hasActionCount(0)
                    hasFrustrationCount(0)
                    hasLongTaskCount(0)
                    hasFrozenFrameCount(0)
                    hasCpuMetric(null)
                    hasMemoryMetric(null, null)
                    hasRefreshRateMetric(null, null)
                    isActive(true)
                    isSlowRendered(false)
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasAccountInfo(fakeDatadogContext.accountInfo)
                    hasViewId(testedScope.viewId)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasSessionType(fakeRumSessionType?.toView() ?: ViewEvent.ViewEventSessionType.USER)
                    hasNoSyntheticsTest()
                    hasStartReason(fakeParentContext.sessionStartReason)
                    hasReplay(fakeHasReplay)
                    hasReplayStats(fakeReplayStats)
                    hasSource(fakeSourceViewEvent)
                    hasLoadingTime(expectedViewLoadingTime)
                    hasNetworkSettledTime(fakeNetworkSettledMetricValue)
                    hasInteractionToNextViewTime(fakeInteractionToNextViewMetricValue)
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toViewSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasSlownessInfo(fakeSlowRecords)
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasSessionActive(fakeParentContext.isSessionActive)
                    hasSampleRate(fakeSampleRate)
                }
        }
        verifyNoMoreInteractions(mockWriter)
    }

    @Test
    fun `M not update the view loading time W handleEvent(AddViewLoadingTime) on stopped view`(
        @BoolForgery fakeOverwrite: Boolean
    ) {
        // Given
        testedScope.stopped = true

        // When
        testedScope.handleEvent(
            RumRawEvent.AddViewLoadingTime(overwrite = fakeOverwrite),
            mockWriter
        )

        // Then
        assertThat(testedScope.viewLoadingTime).isNull()
        verifyNoInteractions(mockWriter)
    }

    // endregion

    // region NetworkSettledTime

    @Test
    fun `M mark the resource as stopped W handleEvent(ErrorSent) { resource information present }`(
        @StringForgery resourceId: String,
        @LongForgery(0) resourceStopTimestampInNanos: Long
    ) {
        // Given
        fakeEvent = RumRawEvent.ErrorSent(testedScope.viewId, resourceId, resourceStopTimestampInNanos)

        // When
        testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        verify(mockNetworkSettledMetricResolver).resourceWasStopped(
            InternalResourceContext(
                resourceId,
                resourceStopTimestampInNanos
            )
        )
    }

    @Test
    fun `M mark the resource as dropped W handleEvent(ErrorDropped) { resource information present }`(
        @StringForgery resourceId: String
    ) {
        // Given
        fakeEvent = RumRawEvent.ErrorDropped(testedScope.viewId, resourceId)

        // When
        testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        verify(mockNetworkSettledMetricResolver).resourceWasDropped(resourceId)
    }

    @Test
    fun `M not interact with networkSettledMetricResolver W handleEvent(ErrorSent) { resource info not present }`() {
        // Given
        fakeEvent = RumRawEvent.ErrorSent(testedScope.viewId)

        // When
        testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        verify(mockNetworkSettledMetricResolver, never()).resourceWasStopped(any())
    }

    @Test
    fun `M not interact with networkSettledMetricResolver W handleEvent(ErrorDropped) { resource info not present }`() {
        // Given
        fakeEvent = RumRawEvent.ErrorSent(testedScope.viewId)

        // When
        testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        verify(mockNetworkSettledMetricResolver, never()).resourceWasDropped(any())
    }

    @Test
    fun `M notify the networkSettledMetricResolver W view was stopped`() {
        // When
        testedScope.handleEvent(
            RumRawEvent.StopView(fakeKey, emptyMap()),
            mockWriter
        )

        // Then
        verify(mockNetworkSettledMetricResolver).viewWasStopped()
    }

    @Test
    fun `M not notify networkSettledMetricResolver W view was stopped { different key }`(
        @Forgery fakeOtherKey: RumScopeKey
    ) {
        // Given
        assumeFalse(fakeOtherKey == fakeKey)

        // When
        testedScope.handleEvent(RumRawEvent.StopView(fakeOtherKey, emptyMap()), mockWriter)

        // Then
        verify(mockNetworkSettledMetricResolver, never()).viewWasStopped()
    }

    @Test
    fun `M not notify networkSettledMetricResolver W view was stopped { already stopped }`() {
        // Given
        testedScope.stopped = true

        // When
        testedScope.handleEvent(
            RumRawEvent.StopView(fakeKey, emptyMap()),
            mockWriter
        )

        // Then
        verify(mockNetworkSettledMetricResolver, never()).viewWasStopped()
    }

    @Test
    fun `M notify the networkSettledMetricMetricResolver W view was created`() {
        // Then
        verify(mockNetworkSettledMetricResolver).viewWasCreated(fakeEventTime.nanoTime)
    }

// endregion

    // region InteractionToNextViewTime

    @Test
    fun `M notify the interactionToNextViewMetricResolver W view was created`() {
        // Then
        verify(mockInteractionToNextViewMetricResolver).onViewCreated(
            testedScope.viewId,
            fakeEventTime.nanoTime
        )
    }

    @Test
    fun `M notify the interactionToNextViewMetricResolver W action was sent { valid view id }`(
        @Forgery actionSent: RumRawEvent.ActionSent
    ) {
        // Given
        val validActionSent = actionSent.copy(viewId = testedScope.viewId)

        // When
        testedScope.handleEvent(
            validActionSent,
            mockWriter
        )

        // Then
        verify(mockInteractionToNextViewMetricResolver).onActionSent(
            InternalInteractionContext(
                validActionSent.viewId,
                validActionSent.type,
                validActionSent.eventEndTimestampInNanos
            )
        )
    }

    @Test
    fun `M not notify the interactionToNextViewMetricResolver W action was sent { invalid view id }`(
        @Forgery invalidActionSent: RumRawEvent.ActionSent
    ) {
        // When
        testedScope.handleEvent(
            invalidActionSent,
            mockWriter
        )

        // Then
        verify(mockInteractionToNextViewMetricResolver, never()).onActionSent(any())
    }

    // endregion

    // region Vitals

    @Test
    fun `M send View update W onVitalUpdate()+handleEvent(KeepAlive) {CPU}`(
        forge: Forge
    ) {
        // Given
        // cpu ticks should be received in ascending order
        val cpuTicks = forge.aList { aLong(1L, 65536L).toDouble() }.sorted()
        val listenerCaptor = argumentCaptor<VitalListener> {
            verify(mockCpuVitalMonitor).register(capture())
        }
        val listener = listenerCaptor.firstValue

        // When
        cpuTicks.forEachIndexed { index, value ->
            listener.onVitalUpdate(VitalInfo(index + 1, 0.0, value, value / 2.0))
        }
        val result = testedScope.handleEvent(
            RumRawEvent.KeepAlive(),
            mockWriter
        )

        // Then
        val expectedTotal = if (cpuTicks.size > 1) {
            cpuTicks.last() - cpuTicks.first()
        } else {
            // we need to have at least 2 ticks to submit "ticks on the view" metric
            null
        }
        argumentCaptor<ViewEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))
            assertThat(lastValue)
                .apply {
                    hasTimestamp(resolveExpectedTimestamp(fakeEventTime.timestamp))
                    hasName(fakeKey.name)
                    hasUrl(fakeUrl)
                    hasDurationGreaterThan(1)
                    hasVersion(2)
                    hasErrorCount(0)
                    hasCrashCount(0)
                    hasResourceCount(0)
                    hasActionCount(0)
                    hasFrustrationCount(0)
                    hasLongTaskCount(0)
                    hasFrozenFrameCount(0)
                    hasCpuMetric(expectedTotal)
                    hasMemoryMetric(null, null)
                    hasRefreshRateMetric(null, null)
                    isActive(true)
                    isSlowRendered(false)
                    hasNoCustomTimings()
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasAccountInfo(fakeDatadogContext.accountInfo)
                    hasViewId(testedScope.viewId)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasSessionType(fakeRumSessionType?.toView() ?: ViewEvent.ViewEventSessionType.USER)
                    hasNoSyntheticsTest()
                    hasStartReason(fakeParentContext.sessionStartReason)
                    hasReplay(fakeHasReplay)
                    hasReplayStats(fakeReplayStats)
                    containsExactlyContextAttributes(fakeAttributes)
                    hasSource(fakeSourceViewEvent)
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toViewSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasSlownessInfo(fakeSlowRecords)
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasSessionActive(fakeParentContext.isSessionActive)
                    hasSampleRate(fakeSampleRate)
                }
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `M send View update W onVitalUpdate()+handleEvent(KeepAlive) {CPU short timespan}`(
        @DoubleForgery(1024.0, 65536.0) cpuTicks: Double
    ) {
        // Given
        // cpu ticks should be received in ascending order
        val listenerCaptor = argumentCaptor<VitalListener> {
            verify(mockCpuVitalMonitor).register(capture())
        }
        val listener = listenerCaptor.firstValue

        // When
        listener.onVitalUpdate(VitalInfo(1, 0.0, 0.0, 0.0))
        listener.onVitalUpdate(VitalInfo(1, 0.0, cpuTicks, cpuTicks / 2.0))
        val result = testedScope.handleEvent(
            RumRawEvent.KeepAlive(fakeEventTime),
            mockWriter
        )

        // Then
        argumentCaptor<ViewEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))
            assertThat(lastValue)
                .apply {
                    hasTimestamp(resolveExpectedTimestamp(fakeEventTime.timestamp))
                    hasName(fakeKey.name)
                    hasUrl(fakeUrl)
                    hasDurationGreaterThan(1)
                    hasVersion(2)
                    hasErrorCount(0)
                    hasCrashCount(0)
                    hasResourceCount(0)
                    hasActionCount(0)
                    hasFrustrationCount(0)
                    hasLongTaskCount(0)
                    hasFrozenFrameCount(0)
                    hasCpuMetric(cpuTicks)
                    hasMemoryMetric(null, null)
                    hasRefreshRateMetric(null, null)
                    isActive(true)
                    isSlowRendered(false)
                    hasNoCustomTimings()
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasAccountInfo(fakeDatadogContext.accountInfo)
                    hasViewId(testedScope.viewId)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasSessionType(fakeRumSessionType?.toView() ?: ViewEvent.ViewEventSessionType.USER)
                    hasNoSyntheticsTest()
                    hasStartReason(fakeParentContext.sessionStartReason)
                    hasReplay(fakeHasReplay)
                    hasReplayStats(fakeReplayStats)
                    containsExactlyContextAttributes(fakeAttributes)
                    hasSource(fakeSourceViewEvent)
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toViewSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasSlownessInfo(fakeSlowRecords)
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasSessionActive(fakeParentContext.isSessionActive)
                    hasSampleRate(fakeSampleRate)
                }
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `M send View update W onVitalUpdate()+handleEvent(KeepAlive) {Memory}`(
        forge: Forge
    ) {
        // Given
        val vitals = forge.aList { getForgery<VitalInfo>() }
        val listenerCaptor = argumentCaptor<VitalListener> {
            verify(mockMemoryVitalMonitor).register(capture())
        }
        val listener = listenerCaptor.firstValue

        // When
        vitals.forEach { listener.onVitalUpdate(it) }
        val result = testedScope.handleEvent(
            RumRawEvent.KeepAlive(),
            mockWriter
        )

        // Then
        argumentCaptor<ViewEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))
            assertThat(lastValue)
                .apply {
                    hasTimestamp(resolveExpectedTimestamp(fakeEventTime.timestamp))
                    hasName(fakeKey.name)
                    hasUrl(fakeUrl)
                    hasDurationGreaterThan(1)
                    hasVersion(2)
                    hasErrorCount(0)
                    hasCrashCount(0)
                    hasResourceCount(0)
                    hasActionCount(0)
                    hasFrustrationCount(0)
                    hasLongTaskCount(0)
                    hasFrozenFrameCount(0)
                    hasCpuMetric(null)
                    hasMemoryMetric(vitals.last().meanValue, vitals.last().maxValue)
                    hasRefreshRateMetric(null, null)
                    isActive(true)
                    isSlowRendered(false)
                    hasNoCustomTimings()
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasAccountInfo(fakeDatadogContext.accountInfo)
                    hasViewId(testedScope.viewId)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasSessionType(fakeRumSessionType?.toView() ?: ViewEvent.ViewEventSessionType.USER)
                    hasNoSyntheticsTest()
                    hasStartReason(fakeParentContext.sessionStartReason)
                    hasReplay(fakeHasReplay)
                    hasReplayStats(fakeReplayStats)
                    containsExactlyContextAttributes(fakeAttributes)
                    hasSource(fakeSourceViewEvent)
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toViewSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasSlownessInfo(fakeSlowRecords)
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasSessionActive(fakeParentContext.isSessionActive)
                    hasSampleRate(fakeSampleRate)
                }
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `M send View update W onVitalUpdate()+handleEvent(KeepAlive) {high frameRate}`(
        forge: Forge
    ) {
        // Given
        val frameRates = forge.aList { aDouble(55.0, 60.0) }.sorted()
        val listenerCaptor = argumentCaptor<VitalListener> {
            verify(mockFrameRateVitalMonitor).register(capture())
        }
        val listener = listenerCaptor.firstValue

        // When
        var sum = 0.0
        var min = 60.0
        var max = 0.0
        var count = 0
        frameRates.forEach { value ->
            count++
            sum += value
            min = min(min, value)
            max = max(max, value)
            listener.onVitalUpdate(VitalInfo(count, min, max, sum / count))
        }
        val result = testedScope.handleEvent(
            RumRawEvent.KeepAlive(),
            mockWriter
        )

        // Then
        argumentCaptor<ViewEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))
            assertThat(lastValue)
                .apply {
                    hasTimestamp(resolveExpectedTimestamp(fakeEventTime.timestamp))
                    hasName(fakeKey.name)
                    hasUrl(fakeUrl)
                    hasDurationGreaterThan(1)
                    hasVersion(2)
                    hasErrorCount(0)
                    hasCrashCount(0)
                    hasResourceCount(0)
                    hasActionCount(0)
                    hasFrustrationCount(0)
                    hasLongTaskCount(0)
                    hasFrozenFrameCount(0)
                    hasCpuMetric(null)
                    hasMemoryMetric(null, null)
                    hasRefreshRateMetric(sum / frameRates.size, min)
                    isActive(true)
                    isSlowRendered(false)
                    hasNoCustomTimings()
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasAccountInfo(fakeDatadogContext.accountInfo)
                    hasViewId(testedScope.viewId)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasSessionType(fakeRumSessionType?.toView() ?: ViewEvent.ViewEventSessionType.USER)
                    hasNoSyntheticsTest()
                    hasStartReason(fakeParentContext.sessionStartReason)
                    hasReplay(fakeHasReplay)
                    hasReplayStats(fakeReplayStats)
                    containsExactlyContextAttributes(fakeAttributes)
                    hasSource(fakeSourceViewEvent)
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toViewSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasSlownessInfo(fakeSlowRecords)
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasSessionActive(fakeParentContext.isSessionActive)
                    hasSampleRate(fakeSampleRate)
                }
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `M send View update W onVitalUpdate()+handleEvent(KeepAlive) {low frameRate}`(
        forge: Forge
    ) {
        // Given
        val frameRates = forge.aList { aDouble(10.0, 55.0) }.sorted()
        val listenerCaptor = argumentCaptor<VitalListener> {
            verify(mockFrameRateVitalMonitor).register(capture())
        }
        val listener = listenerCaptor.firstValue

        // When
        var sum = 0.0
        var min = 60.0
        var max = 0.0
        var count = 0
        frameRates.forEach { value ->
            count++
            sum += value
            min = min(min, value)
            max = max(max, value)
            listener.onVitalUpdate(VitalInfo(count, min, max, sum / count))
        }
        val result = testedScope.handleEvent(
            RumRawEvent.KeepAlive(),
            mockWriter
        )

        // Then
        argumentCaptor<ViewEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))
            assertThat(lastValue)
                .apply {
                    hasTimestamp(resolveExpectedTimestamp(fakeEventTime.timestamp))
                    hasName(fakeKey.name)
                    hasUrl(fakeUrl)
                    hasDurationGreaterThan(1)
                    hasVersion(2)
                    hasErrorCount(0)
                    hasCrashCount(0)
                    hasResourceCount(0)
                    hasActionCount(0)
                    hasFrustrationCount(0)
                    hasLongTaskCount(0)
                    hasFrozenFrameCount(0)
                    hasCpuMetric(null)
                    hasMemoryMetric(null, null)
                    hasRefreshRateMetric(sum / frameRates.size, min)
                    isActive(true)
                    isSlowRendered(true)
                    hasNoCustomTimings()
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasAccountInfo(fakeDatadogContext.accountInfo)
                    hasViewId(testedScope.viewId)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasSessionType(fakeRumSessionType?.toView() ?: ViewEvent.ViewEventSessionType.USER)
                    hasNoSyntheticsTest()
                    hasStartReason(fakeParentContext.sessionStartReason)
                    hasReplay(fakeHasReplay)
                    hasReplayStats(fakeReplayStats)
                    containsExactlyContextAttributes(fakeAttributes)
                    hasSource(fakeSourceViewEvent)
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toViewSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasSlownessInfo(fakeSlowRecords)
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasSessionActive(fakeParentContext.isSessionActive)
                    hasSampleRate(fakeSampleRate)
                }
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `M unregister vital monitors W handleEvent(StopView)`() {
        // Given

        // When
        testedScope.handleEvent(RumRawEvent.StopView(fakeKey, emptyMap()), mockWriter)

        // Then
        verify(mockCpuVitalMonitor).unregister(testedScope.cpuVitalListener)
        verify(mockMemoryVitalMonitor).unregister(testedScope.memoryVitalListener)
        verify(mockFrameRateVitalMonitor).unregister(testedScope.frameRateVitalListener)
    }

    @Test
    fun `M not unregister vital monitors W handleEvent(StopView) {different key}`(
        @Forgery fakeOtherKey: RumScopeKey
    ) {
        // Given
        assumeFalse(fakeOtherKey == fakeKey)

        // When
        testedScope.handleEvent(RumRawEvent.StopView(fakeOtherKey, emptyMap()), mockWriter)

        // Then
        verify(mockCpuVitalMonitor, never()).unregister(testedScope.cpuVitalListener)
        verify(mockMemoryVitalMonitor, never()).unregister(testedScope.memoryVitalListener)
        verify(mockFrameRateVitalMonitor, never()).unregister(testedScope.frameRateVitalListener)
    }

    @Test
    fun `M unregister vital monitors W handleEvent(StartView)`(
        @Forgery fakeOtherKey: RumScopeKey
    ) {
        // Given

        // When
        testedScope.handleEvent(RumRawEvent.StartView(fakeOtherKey, emptyMap()), mockWriter)

        // Then
        verify(mockCpuVitalMonitor).unregister(testedScope.cpuVitalListener)
        verify(mockMemoryVitalMonitor).unregister(testedScope.memoryVitalListener)
        verify(mockFrameRateVitalMonitor).unregister(testedScope.frameRateVitalListener)
    }

    @Test
    fun `M unregister vital monitors W handleEvent(StopSession)`() {
        // Given

        // When
        testedScope.handleEvent(RumRawEvent.StopSession(), mockWriter)

        // Then
        verify(mockCpuVitalMonitor).unregister(testedScope.cpuVitalListener)
        verify(mockMemoryVitalMonitor).unregister(testedScope.memoryVitalListener)
        verify(mockFrameRateVitalMonitor).unregister(testedScope.frameRateVitalListener)
    }

    @Test
    fun `M unregister vital monitors only once W handleEvent(StopView + StopView) {different key}`() {
        // Given

        // When
        testedScope.handleEvent(RumRawEvent.StopView(fakeKey, emptyMap()), mockWriter)
        testedScope.handleEvent(RumRawEvent.StopView(fakeKey, emptyMap()), mockWriter)

        // Then
        verify(mockCpuVitalMonitor).unregister(testedScope.cpuVitalListener)
        verify(mockMemoryVitalMonitor).unregister(testedScope.memoryVitalListener)
        verify(mockFrameRateVitalMonitor).unregister(testedScope.frameRateVitalListener)
    }

    @Test
    fun `M unregister vital monitors only once W handleEvent(StopView + StartView) {different key}`(
        @Forgery fakeOtherKey: RumScopeKey
    ) {
        // Given
        assumeFalse(fakeOtherKey == fakeKey)

        // When
        testedScope.handleEvent(RumRawEvent.StopView(fakeKey, emptyMap()), mockWriter)
        testedScope.handleEvent(RumRawEvent.StartView(fakeOtherKey, emptyMap()), mockWriter)

        // Then
        verify(mockCpuVitalMonitor).unregister(testedScope.cpuVitalListener)
        verify(mockMemoryVitalMonitor).unregister(testedScope.memoryVitalListener)
        verify(mockFrameRateVitalMonitor).unregister(testedScope.frameRateVitalListener)
    }

    @Test
    fun `M unregister vital monitors only once W handleEvent(StopView + StopSession) {different key}`() {
        // Given

        // When
        testedScope.handleEvent(RumRawEvent.StopView(fakeKey, emptyMap()), mockWriter)
        testedScope.handleEvent(RumRawEvent.StopSession(), mockWriter)

        // Then
        verify(mockCpuVitalMonitor).unregister(testedScope.cpuVitalListener)
        verify(mockMemoryVitalMonitor).unregister(testedScope.memoryVitalListener)
        verify(mockFrameRateVitalMonitor).unregister(testedScope.frameRateVitalListener)
    }

    @Test
    fun `M unregister vital monitors only once W handleEvent(StartView + StopView) {different key}`(
        @Forgery fakeOtherKey: RumScopeKey
    ) {
        // Given
        assumeFalse(fakeOtherKey == fakeKey)

        // When
        testedScope.handleEvent(RumRawEvent.StartView(fakeOtherKey, emptyMap()), mockWriter)
        testedScope.handleEvent(RumRawEvent.StopView(fakeKey, emptyMap()), mockWriter)

        // Then
        verify(mockCpuVitalMonitor).unregister(testedScope.cpuVitalListener)
        verify(mockMemoryVitalMonitor).unregister(testedScope.memoryVitalListener)
        verify(mockFrameRateVitalMonitor).unregister(testedScope.frameRateVitalListener)
    }

    @Test
    fun `M unregister vital monitors only once W handleEvent(StartView + StartView) {different key}`(
        @Forgery fakeOtherKey1: RumScopeKey,
        @Forgery fakeOtherKey2: RumScopeKey
    ) {
        // Given
        assumeFalse(fakeOtherKey1 == fakeKey)
        assumeFalse(fakeOtherKey2 == fakeKey)
        assumeFalse(fakeOtherKey1 == fakeOtherKey2)

        // When
        testedScope.handleEvent(RumRawEvent.StartView(fakeOtherKey1, emptyMap()), mockWriter)
        testedScope.handleEvent(RumRawEvent.StartView(fakeOtherKey2, emptyMap()), mockWriter)

        // Then
        verify(mockCpuVitalMonitor).unregister(testedScope.cpuVitalListener)
        verify(mockMemoryVitalMonitor).unregister(testedScope.memoryVitalListener)
        verify(mockFrameRateVitalMonitor).unregister(testedScope.frameRateVitalListener)
    }

    @Test
    fun `M unregister vital monitors only once W handleEvent(StartView + StopSession) {different key}`(
        @Forgery fakeOtherKey: RumScopeKey
    ) {
        // Given
        assumeFalse(fakeOtherKey == fakeKey)

        // When
        testedScope.handleEvent(RumRawEvent.StartView(fakeOtherKey, emptyMap()), mockWriter)
        testedScope.handleEvent(RumRawEvent.StopSession(), mockWriter)

        // Then
        verify(mockCpuVitalMonitor).unregister(testedScope.cpuVitalListener)
        verify(mockMemoryVitalMonitor).unregister(testedScope.memoryVitalListener)
        verify(mockFrameRateVitalMonitor).unregister(testedScope.frameRateVitalListener)
    }

    @Test
    fun `M unregister vital monitors only once W handleEvent(StopSession + StopView) {different key}`(
        @Forgery fakeOtherKey: RumScopeKey
    ) {
        // Given
        assumeFalse(fakeOtherKey == fakeKey)

        // When
        testedScope.handleEvent(RumRawEvent.StopSession(), mockWriter)
        testedScope.handleEvent(RumRawEvent.StopView(fakeKey, emptyMap()), mockWriter)

        // Then
        verify(mockCpuVitalMonitor).unregister(testedScope.cpuVitalListener)
        verify(mockMemoryVitalMonitor).unregister(testedScope.memoryVitalListener)
        verify(mockFrameRateVitalMonitor).unregister(testedScope.frameRateVitalListener)
    }

    @Test
    fun `M unregister vital monitors only once W handleEvent(StopSession + StartView) {different key}`(
        @Forgery fakeOtherKey: RumScopeKey
    ) {
        // Given
        assumeFalse(fakeOtherKey == fakeKey)

        // When
        testedScope.handleEvent(RumRawEvent.StopSession(), mockWriter)
        testedScope.handleEvent(RumRawEvent.StartView(fakeOtherKey, emptyMap()), mockWriter)

        // Then
        verify(mockCpuVitalMonitor).unregister(testedScope.cpuVitalListener)
        verify(mockMemoryVitalMonitor).unregister(testedScope.memoryVitalListener)
        verify(mockFrameRateVitalMonitor).unregister(testedScope.frameRateVitalListener)
    }

    @Test
    fun `M unregister vital monitors only once W handleEvent(StopSession + StopSession) {different key}`(
        @Forgery fakeOtherKey: RumScopeKey
    ) {
        // Given
        assumeFalse(fakeOtherKey == fakeKey)

        // When
        testedScope.handleEvent(RumRawEvent.StopSession(), mockWriter)
        testedScope.handleEvent(RumRawEvent.StopSession(), mockWriter)

        // Then
        verify(mockCpuVitalMonitor).unregister(testedScope.cpuVitalListener)
        verify(mockMemoryVitalMonitor).unregister(testedScope.memoryVitalListener)
        verify(mockFrameRateVitalMonitor).unregister(testedScope.frameRateVitalListener)
    }

    // endregion

    // region Cross-platform performance metrics

    @Test
    fun `M send update W handleEvent(UpdatePerformanceMetric+KeepAlive) { FlutterBuildTime }`(
        forge: Forge
    ) {
        // GIVEN
        val value = forge.aDouble()

        // WHEN
        testedScope.handleEvent(
            RumRawEvent.UpdatePerformanceMetric(
                metric = RumPerformanceMetric.FLUTTER_BUILD_TIME,
                value = value
            ),
            mockWriter
        )
        val result = testedScope.handleEvent(
            RumRawEvent.KeepAlive(),
            mockWriter
        )

        // THEN
        argumentCaptor<ViewEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))
            assertThat(lastValue)
                .apply {
                    hasFlutterBuildTime(ViewEvent.FlutterBuildTime(value, value, value, null))
                    hasFlutterRasterTime(null)
                    hasJsRefreshRate(null)
                }
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `M send update W handleEvent(UpdatePerformanceMetric+KeepAlive) { FlutterRasterTime }`(
        forge: Forge
    ) {
        // GIVEN
        val value = forge.aDouble()

        // WHEN
        testedScope.handleEvent(
            RumRawEvent.UpdatePerformanceMetric(
                metric = RumPerformanceMetric.FLUTTER_RASTER_TIME,
                value = value
            ),
            mockWriter
        )
        val result = testedScope.handleEvent(
            RumRawEvent.KeepAlive(),
            mockWriter
        )

        // THEN
        argumentCaptor<ViewEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))
            assertThat(lastValue)
                .apply {
                    hasFlutterBuildTime(null)
                    hasFlutterRasterTime(ViewEvent.FlutterBuildTime(value, value, value, null))
                    hasJsRefreshRate(null)
                }
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `M send View update W handleEvent(UpdatePerformanceMetric+KeepAlive) { JsRefreshRate }`(
        forge: Forge
    ) {
        // GIVEN
        val value = forge.aPositiveDouble(true)
        val frameRate = TimeUnit.SECONDS.toNanos(1) / value

        // WHEN
        testedScope.handleEvent(
            RumRawEvent.UpdatePerformanceMetric(
                metric = RumPerformanceMetric.JS_FRAME_TIME,
                value = value
            ),
            mockWriter
        )
        val result = testedScope.handleEvent(
            RumRawEvent.KeepAlive(),
            mockWriter
        )

        // THEN
        argumentCaptor<ViewEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))
            assertThat(lastValue)
                .apply {
                    hasFlutterBuildTime(null)
                    hasFlutterRasterTime(null)
                    hasJsRefreshRate(
                        ViewEvent.FlutterBuildTime(
                            frameRate,
                            frameRate,
                            frameRate,
                            null
                        )
                    )
                }
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `M send View update with all values W handleEvent(UpdatePerformanceMetric+KeepAlive)`(
        forge: Forge
    ) {
        // GIVEN
        val flutterBuildTimes = DoubleArray(5) { forge.aDouble() }
        val flutterRasterTimes = DoubleArray(5) { forge.aDouble() }
        val jsFrameTimes = DoubleArray(5) { forge.aPositiveDouble(true) }

        // WHEN
        for (i in 0..4) {
            testedScope.handleEvent(
                RumRawEvent.UpdatePerformanceMetric(
                    metric = RumPerformanceMetric.FLUTTER_BUILD_TIME,
                    value = flutterBuildTimes[i]
                ),
                mockWriter
            )
            testedScope.handleEvent(
                RumRawEvent.UpdatePerformanceMetric(
                    metric = RumPerformanceMetric.FLUTTER_RASTER_TIME,
                    value = flutterRasterTimes[i]
                ),
                mockWriter
            )
            testedScope.handleEvent(
                RumRawEvent.UpdatePerformanceMetric(
                    metric = RumPerformanceMetric.JS_FRAME_TIME,
                    value = jsFrameTimes[i]
                ),
                mockWriter
            )
        }
        val result = testedScope.handleEvent(
            RumRawEvent.KeepAlive(),
            mockWriter
        )

        // THEN
        val flutterBuildTimeStats = Arrays.stream(flutterBuildTimes).summaryStatistics()
        val flutterRasterTimeStats = Arrays.stream(flutterRasterTimes).summaryStatistics()
        val jsFrameTimeStats = Arrays.stream(jsFrameTimes).summaryStatistics()
        argumentCaptor<ViewEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))
            assertThat(lastValue)
                .apply {
                    hasFlutterBuildTime(
                        ViewEvent.FlutterBuildTime(
                            min = flutterBuildTimeStats.min,
                            max = flutterBuildTimeStats.max,
                            average = flutterBuildTimeStats.average
                        )
                    )
                    hasFlutterRasterTime(
                        ViewEvent.FlutterBuildTime(
                            min = flutterRasterTimeStats.min,
                            max = flutterRasterTimeStats.max,
                            average = flutterRasterTimeStats.average
                        )
                    )
                    hasJsRefreshRate(
                        ViewEvent.FlutterBuildTime(
                            min = TimeUnit.SECONDS.toNanos(1) / jsFrameTimeStats.max,
                            max = TimeUnit.SECONDS.toNanos(1) / jsFrameTimeStats.min,
                            average = TimeUnit.SECONDS.toNanos(1) / jsFrameTimeStats.average
                        )
                    )
                }
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
    }

    // endregion

    // region External Refresh Rate

    @Test
    fun `M send update W handleEvent(UpdateExternalRefreshRate+KeepAlive) { single value }`(
        forge: Forge
    ) {
        // GIVEN
        val frameTimeSeconds = forge.aDouble(min = 0.001, max = 0.05) // 1ms to 50ms
        val expectedRefreshRate = 1.0 / frameTimeSeconds
        var expectedRefreshRateMin = expectedRefreshRate

        // WHEN
        testedScope.handleEvent(
            RumRawEvent.UpdateExternalRefreshRate(frameTimeSeconds),
            mockWriter
        )
        val result = testedScope.handleEvent(
            RumRawEvent.KeepAlive(),
            mockWriter
        )

        // THEN
        argumentCaptor<ViewEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))
            assertThat(lastValue)
                .hasRefreshRateMetric(expectedRefreshRate, expectedRefreshRateMin)
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `M send update W handleEvent(UpdateExternalRefreshRate+KeepAlive) { multiple values }`(
        forge: Forge
    ) {
        // GIVEN
        val frameTimesSeconds = forge.aList(size = 5) {
            aDouble(min = 0.008, max = 0.02) // ~50-125 FPS range
        }

        var sum = 0.0
        var min = Double.MAX_VALUE
        var max = -Double.MAX_VALUE
        val refreshRates = mutableListOf<Double>()

        // WHEN
        frameTimesSeconds.forEach { frameTime ->
            val refreshRate = 1.0 / frameTime
            refreshRates.add(refreshRate)
            sum += refreshRate
            min = min(min, refreshRate)
            max = max(max, refreshRate)

            testedScope.handleEvent(
                RumRawEvent.UpdateExternalRefreshRate(frameTime),
                mockWriter
            )
        }

        val result = testedScope.handleEvent(
            RumRawEvent.KeepAlive(),
            mockWriter
        )

        // THEN
        val expectedAverage = sum / refreshRates.size
        argumentCaptor<ViewEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))
            assertThat(lastValue)
                .hasRefreshRateMetric(expectedAverage, min)
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `M ignore invalid frame time W handleEvent(UpdateExternalRefreshRate+KeepAlive) { zero frame time }`() {
        // WHEN
        testedScope.handleEvent(
            RumRawEvent.UpdateExternalRefreshRate(0.0),
            mockWriter
        )
        val result = testedScope.handleEvent(
            RumRawEvent.KeepAlive(),
            mockWriter
        )

        // THEN
        argumentCaptor<ViewEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))
            assertThat(lastValue)
                .hasRefreshRateMetric(null, null)
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `M ignore invalid frame time W handleEvent(UpdateExternalRefreshRate+KeepAlive) { negative frame time }`(
        forge: Forge
    ) {
        // GIVEN
        val negativeFrameTime = -forge.aDouble(min = 0.001, max = 1.0)

        // WHEN
        testedScope.handleEvent(
            RumRawEvent.UpdateExternalRefreshRate(negativeFrameTime),
            mockWriter
        )
        val result = testedScope.handleEvent(
            RumRawEvent.KeepAlive(),
            mockWriter
        )

        // THEN
        argumentCaptor<ViewEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))
            assertThat(lastValue)
                .hasRefreshRateMetric(null, null)
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `M prioritize external data W handleEvent(UpdateExternalRefreshRate+VitalUpdate+KeepAlive)`(
        forge: Forge
    ) {
        // GIVEN
        val externalFrameTime = forge.aDouble(min = 0.0004, max = 100.0)
        val expectedExternalRefreshRate = 1.0 / externalFrameTime

        val internalRefreshRate = forge.aDouble(min = 0.1, max = 240.0)
        val listenerCaptor = argumentCaptor<VitalListener> {
            verify(mockFrameRateVitalMonitor).register(capture())
        }
        val vitalListener = listenerCaptor.firstValue

        // WHEN
        testedScope.handleEvent(
            RumRawEvent.UpdateExternalRefreshRate(externalFrameTime),
            mockWriter
        )

        // AND
        vitalListener.onVitalUpdate(VitalInfo(1, internalRefreshRate, internalRefreshRate, internalRefreshRate))

        val result = testedScope.handleEvent(
            RumRawEvent.KeepAlive(),
            mockWriter
        )

        // THEN
        argumentCaptor<ViewEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))
            assertThat(lastValue)
                .hasRefreshRateMetric(expectedExternalRefreshRate, expectedExternalRefreshRate)
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `M fallback to internal data W no external data provided`(
        forge: Forge
    ) {
        // GIVEN
        val internalRefreshRate = forge.aDouble(min = 1.0, max = 240.0)
        val listenerCaptor = argumentCaptor<VitalListener> {
            verify(mockFrameRateVitalMonitor).register(capture())
        }
        val vitalListener = listenerCaptor.firstValue

        // WHEN
        vitalListener.onVitalUpdate(VitalInfo(1, internalRefreshRate, internalRefreshRate, internalRefreshRate))

        val result = testedScope.handleEvent(
            RumRawEvent.KeepAlive(),
            mockWriter
        )

        // THEN
        argumentCaptor<ViewEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))
            assertThat(lastValue)
                .hasRefreshRateMetric(internalRefreshRate, internalRefreshRate)
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `M not update external refresh rate W view is stopped`(
        forge: Forge
    ) {
        // GIVEN
        testedScope.handleEvent(RumRawEvent.StopView(fakeKey, emptyMap()), mockWriter)
        val frameTimeSeconds = forge.aDouble(min = 0.08, max = 0.8)

        // WHEN
        val result = testedScope.handleEvent(
            RumRawEvent.UpdateExternalRefreshRate(frameTimeSeconds),
            mockWriter
        )

        // THEN
        // Should not process external refresh rate updates after view is stopped
        assertThat(result).isNull() // View scope should be completed
    }

    @Test
    fun `M accumulate external refresh rate samples correctly W multiple updates`() {
        // GIVEN
        val frameTime1 = 1.0 / 60.0 // 60 FPS
        val frameTime2 = 1.0 / 30.0 // 30 FPS
        val frameTime3 = 1.0 / 90.0 // 90 FPS

        val refreshRate1 = 1.0 / frameTime1
        val refreshRate2 = 1.0 / frameTime2
        val refreshRate3 = 1.0 / frameTime3

        val expectedAverage = (refreshRate1 + refreshRate2 + refreshRate3) / 3.0
        val expectedMin = min(refreshRate2, min(refreshRate1, refreshRate3))

        // WHEN
        testedScope.handleEvent(RumRawEvent.UpdateExternalRefreshRate(frameTime1), mockWriter)
        testedScope.handleEvent(RumRawEvent.UpdateExternalRefreshRate(frameTime2), mockWriter)
        testedScope.handleEvent(RumRawEvent.UpdateExternalRefreshRate(frameTime3), mockWriter)

        val result = testedScope.handleEvent(RumRawEvent.KeepAlive(), mockWriter)

        // THEN
        argumentCaptor<ViewEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))
            assertThat(lastValue)
                .hasRefreshRateMetric(expectedAverage, expectedMin)
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
    }

    // endregion

    // region Internal attributes

    @Test
    fun `M send View update with fbc metric W handleEvent(SetInternalViewAttribute+KeepAlive)`(
        forge: Forge
    ) {
        // GIVEN
        val fbc = forge.aPositiveLong()

        // WHEN
        testedScope.handleEvent(
            RumRawEvent.SetInternalViewAttribute(
                key = RumAttributes.FLUTTER_FIRST_BUILD_COMPLETE,
                value = fbc
            ),
            mockWriter
        )
        val result = testedScope.handleEvent(
            RumRawEvent.KeepAlive(),
            mockWriter
        )

        // THEN
        argumentCaptor<ViewEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))
            assertThat(lastValue)
                .hasFBCTime(fbc)
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `M send View update with custom inv metric W handleEvent(inv disabled+SetInternalViewAttribute+KeepAlive)`(
        forge: Forge
    ) {
        // GIVEN
        whenever(mockInteractionToNextViewMetricResolver.resolveMetric(any())) doReturn null
        whenever(mockInteractionToNextViewMetricResolver.getState(any())) doReturn ViewInitializationMetricsState(
            null,
            ViewInitializationMetricsConfig.DISABLED,
            NoValueReason.InteractionToNextView.DISABLED
        )
        val customInv = forge.aPositiveLong()

        // WHEN
        testedScope.handleEvent(
            RumRawEvent.SetInternalViewAttribute(
                key = RumAttributes.CUSTOM_INV_VALUE,
                value = customInv
            ),
            mockWriter
        )
        val result = testedScope.handleEvent(
            RumRawEvent.KeepAlive(),
            mockWriter
        )

        // THEN
        argumentCaptor<ViewEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))
            assertThat(lastValue)
                .hasInteractionToNextViewTime(customInv)
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
    }

    // endregion

    // region Feature Flags

    @Test
    fun `M send event W handleEvent(AddFeatureFlagEvaluation) on active view`(
        @StringForgery flagName: String,
        @StringForgery flagValue: String
    ) {
        // WHEN
        testedScope.handleEvent(
            RumRawEvent.AddFeatureFlagEvaluation(
                name = flagName,
                value = flagValue
            ),
            mockWriter
        )

        // THEN
        argumentCaptor<ViewEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))
            assertThat(lastValue).hasFeatureFlag(flagName, flagValue)
        }
    }

    @Test
    fun `M send event only once W handleEvent(AddFeatureFlagEvaluation) on active view {same value}`(
        @StringForgery flagName: String,
        @StringForgery flagValue: String
    ) {
        // WHEN
        testedScope.handleEvent(
            RumRawEvent.AddFeatureFlagEvaluation(
                name = flagName,
                value = flagValue
            ),
            mockWriter
        )
        testedScope.handleEvent(
            RumRawEvent.AddFeatureFlagEvaluation(
                name = flagName,
                value = flagValue
            ),
            mockWriter
        )

        // THEN
        argumentCaptor<ViewEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))
            assertThat(lastValue).hasFeatureFlag(flagName, flagValue)
        }
    }

    @Test
    fun `M not add feature flag W handleEvent(AddFeatureFlagEvaluation) on stopped view`(
        @StringForgery flagName: String,
        @StringForgery flagValue: String
    ) {
        // GIVEN
        testedScope.stopped = true

        // WHEN
        testedScope.handleEvent(
            RumRawEvent.AddFeatureFlagEvaluation(
                name = flagName,
                value = flagValue
            ),
            mockWriter
        )

        // THEN
        assertThat(testedScope.featureFlags).isEmpty()
        verifyNoInteractions(mockWriter)
    }

    @Test
    fun `M modify flag W handleEvent(AddFeatureFlagEvaluation) on active view { existing feature flag }`(
        @StringForgery flagName: String,
        @BoolForgery oldFlagValue: Boolean,
        @StringForgery flagValue: String
    ) {
        // GIVEN
        testedScope.handleEvent(
            RumRawEvent.AddFeatureFlagEvaluation(
                name = flagName,
                value = oldFlagValue
            ),
            mockWriter
        )

        // WHEN
        testedScope.handleEvent(
            RumRawEvent.AddFeatureFlagEvaluation(
                name = flagName,
                value = flagValue
            ),
            mockWriter
        )

        // THEN
        argumentCaptor<ViewEvent> {
            verify(mockWriter, times(2)).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))
            assertThat(lastValue).hasFeatureFlag(flagName, flagValue)
        }
    }

    @Test
    fun `M send flags on ErrorEvent W handleEvent(AddError) on active view { existing feature flags }`(
        forge: Forge,
        @StringForgery flagName: String,
        @StringForgery flagValue: String
    ) {
        // GIVEN
        testedScope.handleEvent(
            RumRawEvent.AddFeatureFlagEvaluation(
                name = flagName,
                value = flagValue
            ),
            mockWriter
        )

        // WHEN
        testedScope.handleEvent(
            RumRawEvent.AddError(
                forge.anAlphabeticalString(),
                forge.aValueFrom(RumErrorSource::class.java),
                throwable = null,
                stacktrace = null,
                isFatal = false,
                threads = emptyList(),
                attributes = emptyMap()
            ),
            mockWriter
        )

        // THEN
        argumentCaptor<Any> {
            verify(mockWriter, times(2)).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))
            assertThat(lastValue as ErrorEvent).hasFeatureFlag(flagName, flagValue)
        }
    }

    // endregion

    // region Feature Flags Batch

    @Test
    fun `M send event W handleEvent(AddFeatureFlagEvaluations) on active view`(
        @StringForgery flagName1: String,
        @StringForgery flagName2: String,
        @StringForgery flagName3: String,
        @StringForgery flagValue1: String,
        @StringForgery flagValue2: String,
        @StringForgery flagValue3: String
    ) {
        // WHEN
        testedScope.handleEvent(
            RumRawEvent.AddFeatureFlagEvaluations(
                mapOf(flagName1 to flagValue1, flagName2 to flagValue2, flagName3 to flagValue3)
            ),
            mockWriter
        )

        // THEN
        argumentCaptor<ViewEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))
            assertThat(lastValue).hasFeatureFlag(flagName1, flagValue1)
            assertThat(lastValue).hasFeatureFlag(flagName2, flagValue2)
            assertThat(lastValue).hasFeatureFlag(flagName3, flagValue3)
        }
    }

    @Test
    fun `M send event only once W handleEvent(AddFeatureFlagEvaluations) on active view {same values}`(
        @StringForgery flagName1: String,
        @StringForgery flagName2: String,
        @StringForgery flagName3: String,
        @StringForgery flagValue1: String,
        @StringForgery flagValue2: String,
        @StringForgery flagValue3: String
    ) {
        // WHEN
        testedScope.handleEvent(
            RumRawEvent.AddFeatureFlagEvaluations(
                mapOf(flagName1 to flagValue1, flagName2 to flagValue2, flagName3 to flagValue3)
            ),
            mockWriter
        )
        testedScope.handleEvent(
            RumRawEvent.AddFeatureFlagEvaluations(
                mapOf(flagName1 to flagValue1, flagName2 to flagValue2, flagName3 to flagValue3)
            ),
            mockWriter
        )

        // THEN
        argumentCaptor<ViewEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))
            assertThat(lastValue).hasFeatureFlag(flagName1, flagValue1)
            assertThat(lastValue).hasFeatureFlag(flagName2, flagValue2)
            assertThat(lastValue).hasFeatureFlag(flagName3, flagValue3)
        }
    }

    @Test
    fun `M not add feature flag W handleEvent(AddFeatureFlagEvaluations) on stopped view`(
        @StringForgery flagName: String,
        @StringForgery flagValue: String
    ) {
        // GIVEN
        testedScope.stopped = true

        // WHEN
        testedScope.handleEvent(
            RumRawEvent.AddFeatureFlagEvaluations(mapOf(flagName to flagValue)),
            mockWriter
        )

        // THEN
        assertThat(testedScope.featureFlags).isEmpty()
        verifyNoInteractions(mockWriter)
    }

    @Test
    fun `M modify flag W handleEvent(AddFeatureFlagEvaluations) on active view { existing feature flag }`(
        @StringForgery flagName1: String,
        @StringForgery flagName2: String,
        @StringForgery flagName3: String,
        @StringForgery oldFlagValue1: String,
        @StringForgery oldFlagValue2: String,
        @StringForgery oldFlagValue3: String,
        @StringForgery flagValue1: String,
        @StringForgery flagValue2: String,
        @StringForgery flagValue3: String
    ) {
        // GIVEN
        testedScope.handleEvent(
            RumRawEvent.AddFeatureFlagEvaluations(
                mapOf(flagName1 to oldFlagValue1, flagName2 to oldFlagValue2, flagName3 to oldFlagValue3)
            ),
            mockWriter
        )

        // WHEN
        testedScope.handleEvent(
            RumRawEvent.AddFeatureFlagEvaluations(
                mapOf(flagName1 to flagValue1, flagName2 to flagValue2, flagName3 to flagValue3)
            ),
            mockWriter
        )

        // THEN
        argumentCaptor<ViewEvent> {
            verify(mockWriter, times(2)).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))
            assertThat(lastValue).hasFeatureFlag(flagName1, flagValue1)
            assertThat(lastValue).hasFeatureFlag(flagName2, flagValue2)
            assertThat(lastValue).hasFeatureFlag(flagName3, flagValue3)
        }
    }

    @Test
    fun `M send flags on ErrorEvent W handleEvent(AddError) on active view { existing feature flags set }`(
        forge: Forge,
        @StringForgery flagName1: String,
        @StringForgery flagName2: String,
        @StringForgery flagName3: String,
        @StringForgery flagValue1: String,
        @StringForgery flagValue2: String,
        @StringForgery flagValue3: String
    ) {
        // GIVEN
        testedScope.handleEvent(
            RumRawEvent.AddFeatureFlagEvaluations(
                mapOf(flagName1 to flagValue1, flagName2 to flagValue2, flagName3 to flagValue3)
            ),
            mockWriter
        )

        // WHEN
        testedScope.handleEvent(
            RumRawEvent.AddError(
                forge.anAlphabeticalString(),
                forge.aValueFrom(RumErrorSource::class.java),
                throwable = null,
                stacktrace = null,
                isFatal = false,
                threads = emptyList(),
                attributes = emptyMap()
            ),
            mockWriter
        )

        // THEN
        argumentCaptor<Any> {
            verify(mockWriter, times(2)).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))
            assertThat(lastValue as ErrorEvent).hasFeatureFlag(flagName1, flagValue1)
            assertThat(lastValue as ErrorEvent).hasFeatureFlag(flagName2, flagValue2)
            assertThat(lastValue as ErrorEvent).hasFeatureFlag(flagName3, flagValue3)
        }
    }

    // endregion

    // region Stopping Sessions

    @Test
    fun `M set view to inactive and send update W handleEvent { StopSession }`() {
        // Given
        whenever(mockParentScope.getRumContext())
            .doReturn(fakeParentContext.copy(isSessionActive = false))

        // When
        testedScope.handleEvent(
            RumRawEvent.StopSession(),
            mockWriter
        )

        // Then
        assertThat(testedScope.isActive()).isFalse()

        argumentCaptor<ViewEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))
            assertThat(lastValue)
                .apply {
                    hasSessionActive(false)
                }
        }
    }

    // endregion

    // region write notification

    @Test
    fun `M notify about success W handleEvent(AddError+non-fatal) { write succeeded }`(
        @StringForgery message: String,
        @Forgery source: RumErrorSource,
        @Forgery throwable: Throwable,
        @StringForgery stacktrace: String,
        forge: Forge
    ) {
        // Given
        testedScope.activeActionScope = mockActionScope
        val attributes = forge.exhaustiveAttributes(excludedKeys = fakeAttributes.keys)
        fakeEvent = RumRawEvent.AddError(
            message,
            source,
            throwable,
            stacktrace,
            isFatal = false,
            threads = emptyList(),
            attributes = attributes
        )

        // When
        testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        verify(rumMonitor.mockInstance as AdvancedRumMonitor)
            .eventSent(testedScope.viewId, StorageEvent.Error())
    }

    @Test
    fun `M notify about error W handleEvent(AddError+non-fatal) { write failed }`(
        @StringForgery message: String,
        @Forgery source: RumErrorSource,
        @Forgery throwable: Throwable,
        @StringForgery stacktrace: String,
        forge: Forge
    ) {
        // Given
        testedScope.activeActionScope = mockActionScope
        val attributes = forge.exhaustiveAttributes(excludedKeys = fakeAttributes.keys)
        fakeEvent = RumRawEvent.AddError(
            message,
            source,
            throwable,
            stacktrace,
            isFatal = false,
            threads = emptyList(),
            attributes = attributes
        )
        whenever(mockWriter.write(eq(mockEventBatchWriter), isA<ErrorEvent>(), eq(EventType.DEFAULT))) doReturn false

        // When
        testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        verify(rumMonitor.mockInstance as AdvancedRumMonitor)
            .eventDropped(testedScope.viewId, StorageEvent.Error())
    }

    @Test
    fun `M notify about error W handleEvent(AddError+non-fatal) { write throws }`(
        @StringForgery message: String,
        @Forgery source: RumErrorSource,
        @Forgery throwable: Throwable,
        @StringForgery stacktrace: String,
        forge: Forge
    ) {
        // Given
        testedScope.activeActionScope = mockActionScope
        val attributes = forge.exhaustiveAttributes(excludedKeys = fakeAttributes.keys)
        fakeEvent = RumRawEvent.AddError(
            message,
            source,
            throwable,
            stacktrace,
            isFatal = false,
            threads = emptyList(),
            attributes = attributes
        )
        whenever(
            mockWriter.write(eq(mockEventBatchWriter), isA<ErrorEvent>(), eq(EventType.DEFAULT))
        ) doThrow forge.anException()

        // When
        testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        verify(rumMonitor.mockInstance as AdvancedRumMonitor)
            .eventDropped(testedScope.viewId, StorageEvent.Error())
    }

    @Test
    fun `M not notify about success W handleEvent(AddError+fatal) { write succeeded }`(
        @StringForgery message: String,
        @Forgery source: RumErrorSource,
        @Forgery throwable: Throwable,
        @StringForgery stacktrace: String,
        forge: Forge
    ) {
        // Given
        testedScope.activeActionScope = mockActionScope
        val attributes = forge.exhaustiveAttributes(excludedKeys = fakeAttributes.keys)
        fakeEvent = RumRawEvent.AddError(
            message,
            source,
            throwable,
            stacktrace,
            isFatal = true,
            threads = emptyList(),
            attributes = attributes
        )

        // When
        testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        verify(rumMonitor.mockInstance as AdvancedRumMonitor, never())
            .eventSent(testedScope.viewId, StorageEvent.Error())
    }

    @Test
    fun `M not notify about error W handleEvent(AddError+fatal) { write failed }`(
        @StringForgery message: String,
        @Forgery source: RumErrorSource,
        @Forgery throwable: Throwable,
        @StringForgery stacktrace: String,
        forge: Forge
    ) {
        // Given
        testedScope.activeActionScope = mockActionScope
        val attributes = forge.exhaustiveAttributes(excludedKeys = fakeAttributes.keys)
        fakeEvent = RumRawEvent.AddError(
            message,
            source,
            throwable,
            stacktrace,
            isFatal = true,
            threads = emptyList(),
            attributes = attributes
        )
        whenever(mockWriter.write(eq(mockEventBatchWriter), isA<ErrorEvent>(), eq(EventType.CRASH))) doReturn false

        // When
        testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        verify(rumMonitor.mockInstance as AdvancedRumMonitor, never())
            .eventDropped(testedScope.viewId, StorageEvent.Error())
    }

    @Test
    fun `M not notify about error W handleEvent(AddError+fatal) { write throws }`(
        @StringForgery message: String,
        @Forgery source: RumErrorSource,
        @Forgery throwable: Throwable,
        @StringForgery stacktrace: String,
        forge: Forge
    ) {
        // Given
        testedScope.activeActionScope = mockActionScope
        val attributes = forge.exhaustiveAttributes(excludedKeys = fakeAttributes.keys)
        fakeEvent = RumRawEvent.AddError(
            message,
            source,
            throwable,
            stacktrace,
            isFatal = true,
            threads = emptyList(),
            attributes = attributes
        )
        whenever(
            mockWriter.write(eq(mockEventBatchWriter), isA<ErrorEvent>(), eq(EventType.CRASH))
        ) doThrow forge.anException()

        // When
        testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        verify(rumMonitor.mockInstance as AdvancedRumMonitor, never())
            .eventDropped(testedScope.viewId, StorageEvent.Error())
    }

    @Test
    fun `M notify about success W handleEvent(ApplicationStarted) { write succeeded }`(
        forge: Forge
    ) {
        // Given
        testedScope.activeActionScope = mockActionScope
        val applicationStartupNanos = forge.aPositiveLong()
        fakeEvent = RumRawEvent.ApplicationStarted(
            eventTime = Time(),
            applicationStartupNanos = applicationStartupNanos
        )

        // When
        testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        verify(rumMonitor.mockInstance as AdvancedRumMonitor)
            .eventSent(
                testedScope.viewId,
                StorageEvent.Action(0, ActionEvent.ActionEventActionType.APPLICATION_START, applicationStartupNanos)
            )
    }

    @Test
    fun `M notify about error W handleEvent(ApplicationStarted) { write failed }`(
        forge: Forge
    ) {
        // Given
        testedScope.activeActionScope = mockActionScope
        val applicationStartupNanos = forge.aPositiveLong()
        fakeEvent = RumRawEvent.ApplicationStarted(
            eventTime = Time(),
            applicationStartupNanos = applicationStartupNanos
        )

        whenever(mockWriter.write(eq(mockEventBatchWriter), isA<ActionEvent>(), eq(EventType.DEFAULT))) doReturn false

        // When
        testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        verify(rumMonitor.mockInstance as AdvancedRumMonitor)
            .eventDropped(
                testedScope.viewId,
                StorageEvent.Action(0, ActionEvent.ActionEventActionType.APPLICATION_START, applicationStartupNanos)
            )
    }

    @Test
    fun `M notify about error W handleEvent(ApplicationStarted) { write throws }`(
        forge: Forge
    ) {
        // Given
        testedScope.activeActionScope = mockActionScope
        val applicationStartupNanos = forge.aPositiveLong()
        fakeEvent = RumRawEvent.ApplicationStarted(
            eventTime = Time(),
            applicationStartupNanos = applicationStartupNanos
        )
        whenever(
            mockWriter.write(eq(mockEventBatchWriter), isA<ActionEvent>(), eq(EventType.DEFAULT))
        ) doThrow forge.anException()

        // When
        testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        verify(rumMonitor.mockInstance as AdvancedRumMonitor)
            .eventDropped(
                testedScope.viewId,
                StorageEvent.Action(0, ActionEvent.ActionEventActionType.APPLICATION_START, applicationStartupNanos)
            )
    }

    @Test
    fun `M notify about success W handleEvent(AddLongTask) { write succeeded }`(
        @LongForgery(250_000_000L, 700_000_000L) durationNs: Long,
        @StringForgery target: String
    ) {
        // Given
        testedScope.activeActionScope = mockActionScope
        fakeEvent = RumRawEvent.AddLongTask(durationNs, target)

        // When
        testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        verify(rumMonitor.mockInstance as AdvancedRumMonitor)
            .eventSent(testedScope.viewId, StorageEvent.LongTask)
    }

    @Test
    fun `M notify about error W handleEvent(AddLongTask) { write failed }`(
        @LongForgery(250_000_000L, 700_000_000L) durationNs: Long,
        @StringForgery target: String
    ) {
        // Given
        testedScope.activeActionScope = mockActionScope
        fakeEvent = RumRawEvent.AddLongTask(durationNs, target)
        whenever(mockWriter.write(eq(mockEventBatchWriter), isA<LongTaskEvent>(), eq(EventType.DEFAULT))) doReturn false

        // When
        testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        verify(rumMonitor.mockInstance as AdvancedRumMonitor)
            .eventDropped(testedScope.viewId, StorageEvent.LongTask)
    }

    @Test
    fun `M notify about error W handleEvent(AddLongTask) { write throws }`(
        @LongForgery(250_000_000L, 700_000_000L) durationNs: Long,
        @StringForgery target: String,
        forge: Forge
    ) {
        // Given
        testedScope.activeActionScope = mockActionScope
        fakeEvent = RumRawEvent.AddLongTask(durationNs, target)
        whenever(
            mockWriter.write(eq(mockEventBatchWriter), isA<LongTaskEvent>(), eq(EventType.DEFAULT))
        ) doThrow forge.anException()

        // When
        testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        verify(rumMonitor.mockInstance as AdvancedRumMonitor)
            .eventDropped(testedScope.viewId, StorageEvent.LongTask)
    }

    @Test
    fun `M notify about success W handleEvent(AddLongTask, is frozen frame) { write succeeded }`(
        @LongForgery(700_000_000L, 10_000_000_000L) durationNs: Long,
        @StringForgery target: String
    ) {
        // Given
        testedScope.activeActionScope = mockActionScope
        fakeEvent = RumRawEvent.AddLongTask(durationNs, target)

        // When
        testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        verify(rumMonitor.mockInstance as AdvancedRumMonitor)
            .eventSent(testedScope.viewId, StorageEvent.FrozenFrame)
    }

    @Test
    fun `M notify about error W handleEvent(AddLongTask, is frozen frame) { write failed }`(
        @LongForgery(700_000_000L, 10_000_000_000L) durationNs: Long,
        @StringForgery target: String
    ) {
        // Given
        testedScope.activeActionScope = mockActionScope
        fakeEvent = RumRawEvent.AddLongTask(durationNs, target)
        whenever(mockWriter.write(eq(mockEventBatchWriter), isA<LongTaskEvent>(), eq(EventType.DEFAULT))) doReturn false

        // When
        testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        verify(rumMonitor.mockInstance as AdvancedRumMonitor)
            .eventDropped(testedScope.viewId, StorageEvent.FrozenFrame)
    }

    @Test
    fun `M notify about error W handleEvent(AddLongTask, is frozen frame) { write throws }`(
        @LongForgery(700_000_000L, 10_000_000_000L) durationNs: Long,
        @StringForgery target: String,
        forge: Forge
    ) {
        // Given
        testedScope.activeActionScope = mockActionScope
        fakeEvent = RumRawEvent.AddLongTask(durationNs, target)
        whenever(
            mockWriter.write(eq(mockEventBatchWriter), isA<LongTaskEvent>(), eq(EventType.DEFAULT))
        ) doThrow forge.anException()

        // When
        testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        verify(rumMonitor.mockInstance as AdvancedRumMonitor)
            .eventDropped(testedScope.viewId, StorageEvent.FrozenFrame)
    }

    // endregion

    // region Misc

    @ParameterizedTest
    @MethodSource("brokenTimeRawEventData")
    fun `M update the duration to 1ns W handleEvent { computed duration equal to 0 }`(
        rawEventData: RumRawEventData
    ) {
        // Given
        testedScope = newRumViewScope(
            key = rawEventData.viewKey,
            eventTime = rawEventData.event.eventTime
        )

        // When
        testedScope.handleEvent(rawEventData.event, mockWriter)

        // Then
        argumentCaptor<ViewEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))
            assertThat(firstValue)
                .apply {
                    hasDuration(1)
                }
        }
        mockInternalLogger.verifyLog(
            InternalLogger.Level.WARN,
            listOf(InternalLogger.Target.USER, InternalLogger.Target.TELEMETRY),
            RumViewScope.ZERO_DURATION_WARNING_MESSAGE.format(Locale.US, testedScope.key.name),
            additionalProperties = mapOf(
                "view.name" to rawEventData.viewKey.name
            )
        )
    }

    @ParameterizedTest
    @MethodSource("brokenTimeRawEventData")
    fun `M update the duration to 1ns W handleEvent { computed duration less than 0 }`(
        rawEventData: RumRawEventData
    ) {
        // Given
        testedScope = newRumViewScope(key = rawEventData.viewKey)

        // When
        testedScope.handleEvent(rawEventData.event, mockWriter)

        // Then
        argumentCaptor<ViewEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))
            assertThat(firstValue)
                .apply {
                    hasDuration(1)
                }
        }
        mockInternalLogger.verifyLog(
            InternalLogger.Level.WARN,
            listOf(InternalLogger.Target.USER, InternalLogger.Target.TELEMETRY),
            RumViewScope.NEGATIVE_DURATION_WARNING_MESSAGE.format(Locale.US, testedScope.key.name),
            additionalProperties = mapOf(
                "view.start_ns" to fakeEventTime.nanoTime,
                "view.end_ns" to rawEventData.event.eventTime.nanoTime,
                "view.name" to rawEventData.viewKey.name
            )
        )
    }

    @Test
    fun `M not update the duration W handleEvent { after view stop }`(
        @LongForgery(10L, 10_000_000_000L) durationNs: Long,
        @LongForgery(10L, 10_000_000_000L) additionalDurationNs: Long,
        forge: Forge
    ) {
        // Given
        testedScope = newRumViewScope()
        testedScope.pendingErrorCount = 1
        testedScope.pendingActionCount = 1
        testedScope.pendingResourceCount = 1
        testedScope.pendingLongTaskCount = 1
        val stopEvent = RumRawEvent.StopView(fakeKey, emptyMap(), fakeEventTime + durationNs)
        val otherEvent = forge.eventSent(testedScope.viewId, fakeEventTime + (durationNs + additionalDurationNs))

        // When
        testedScope.handleEvent(stopEvent, mockWriter)
        testedScope.handleEvent(otherEvent, mockWriter)

        // Then
        argumentCaptor<ViewEvent> {
            verify(mockWriter, times(2)).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))
            assertThat(lastValue)
                .apply {
                    hasDuration(durationNs)
                }
        }
    }

    // endregion

    // region Global Attributes

    @Test
    fun `M update the global attributes W handleEvent(StopView)`(
        forge: Forge
    ) {
        // Given
        val fakeStopEventAttributes = forge.aFilteredMap(excludedKeys = fakeAttributes.keys) {
            anHexadecimalString() to anAsciiString()
        }
        val excludedKeys = fakeAttributes.keys + fakeStopEventAttributes.keys
        val fakeGlobalAttributes = forge.aFilteredMap(excludedKeys = excludedKeys) {
            anHexadecimalString() to anAsciiString()
        }
        val fakeNewGlobalAttributes = forge.aFilteredMap(excludedKeys = excludedKeys) {
            anHexadecimalString() to anAsciiString()
        }
        val expectedAttributes = mutableMapOf<String, Any?>()
        expectedAttributes.putAll(fakeAttributes)
        expectedAttributes.putAll(fakeNewGlobalAttributes)
        expectedAttributes.putAll(fakeStopEventAttributes)
        whenever(rumMonitor.mockInstance.getAttributes()) doReturnConsecutively listOf(
            // one for initialization
            fakeGlobalAttributes,
            // second for event handling
            fakeNewGlobalAttributes
        )

        testedScope = newRumViewScope()

        // When
        val result = testedScope.handleEvent(
            RumRawEvent.StopView(fakeKey, fakeStopEventAttributes),
            mockWriter
        )

        // Then
        argumentCaptor<ViewEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))
            assertThat(lastValue).containsExactlyContextAttributes(expectedAttributes)
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isNull()
    }

    @Test
    fun `M not update the global attributes W handleEvent(StartView)`(
        forge: Forge
    ) {
        // Given
        val fakeGlobalAttributes = forge.aFilteredMap(excludedKeys = fakeAttributes.keys) {
            anHexadecimalString() to anAsciiString()
        }
        val fakeNewGlobalAttributes = forge.aFilteredMap(excludedKeys = fakeAttributes.keys) {
            anHexadecimalString() to anAsciiString()
        }
        val expectedAttributes = mutableMapOf<String, Any?>()
        expectedAttributes.putAll(fakeAttributes)
        expectedAttributes.putAll(fakeGlobalAttributes)
        whenever(rumMonitor.mockInstance.getAttributes()) doReturnConsecutively listOf(
            // one for initialization
            fakeGlobalAttributes,
            // second for event handling
            fakeNewGlobalAttributes
        )

        testedScope = newRumViewScope()

        // When
        val result = testedScope.handleEvent(
            RumRawEvent.StartView(forge.getForgery(), emptyMap()),
            mockWriter
        )

        argumentCaptor<ViewEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))
            assertThat(lastValue).containsExactlyContextAttributes(expectedAttributes)
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isNull()
    }

    @Test
    fun `M not update the global attributes W handleEvent(Resource Sent) on new started view`(
        @StringForgery key: String,
        @Forgery method: RumResourceMethod,
        @StringForgery url: String,
        forge: Forge
    ) {
        // Given
        val fakeGlobalAttributes = forge.aFilteredMap(excludedKeys = fakeAttributes.keys) {
            anHexadecimalString() to anAsciiString()
        }
        val fakeNewGlobalAttributes = forge.aFilteredMap(excludedKeys = fakeAttributes.keys) {
            anHexadecimalString() to anAsciiString()
        }
        val expectedAttributes = mutableMapOf<String, Any?>()
        expectedAttributes.putAll(fakeAttributes)
        expectedAttributes.putAll(fakeGlobalAttributes)
        val fakeResourceId = forge.getForgery<UUID>().toString()
        val fakeResourceEndTimestamp = forge.aPositiveLong()
        whenever(rumMonitor.mockInstance.getAttributes()) doReturnConsecutively listOf(
            // one for initialization
            fakeGlobalAttributes,
            // second one for when the resource is started
            fakeGlobalAttributes,
            // third one for when the resource scope init
            fakeGlobalAttributes,
            // third one when the new view was started
            fakeNewGlobalAttributes
        )

        testedScope = newRumViewScope()
        testedScope.handleEvent(
            RumRawEvent.StartResource(key, url, method, emptyMap()),
            mockWriter
        )
        testedScope.handleEvent(
            RumRawEvent.StartView(forge.getForgery(), emptyMap()),
            mockWriter
        )
        // When
        testedScope.handleEvent(
            RumRawEvent.ResourceSent(testedScope.viewId, fakeResourceId, fakeResourceEndTimestamp),
            mockWriter
        )

        // Then
        argumentCaptor<ViewEvent> {
            verify(mockWriter, times(2)).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))
            assertThat(lastValue).containsExactlyContextAttributes(expectedAttributes)
        }
        verifyNoMoreInteractions(mockWriter)
    }

    @Test
    fun `M not update the global attributes W handleEvent(Action Sent) on new started view`(
        @Forgery type: RumActionType,
        @StringForgery name: String,
        @LongForgery(0) actionEventTimestamp: Long,
        forge: Forge
    ) {
        // Given
        val fakeGlobalAttributes = forge.aFilteredMap(excludedKeys = fakeAttributes.keys) {
            anHexadecimalString() to anAsciiString()
        }
        val fakeNewGlobalAttributes = forge.aFilteredMap(excludedKeys = fakeAttributes.keys) {
            anHexadecimalString() to anAsciiString()
        }
        val expectedAttributes = mutableMapOf<String, Any?>()
        expectedAttributes.putAll(fakeAttributes)
        expectedAttributes.putAll(fakeGlobalAttributes)
        whenever(rumMonitor.mockInstance.getAttributes()) doReturnConsecutively listOf(
            // one for initialization
            fakeGlobalAttributes,
            // second one for when the action is started
            fakeGlobalAttributes,
            // third one for when the action scope init
            fakeGlobalAttributes,
            // third one new when the new view was started
            fakeNewGlobalAttributes
        )

        testedScope = newRumViewScope()

        testedScope.handleEvent(
            RumRawEvent.StartAction(type, name, forge.aBool(), emptyMap()),
            mockWriter
        )
        testedScope.handleEvent(
            RumRawEvent.StartView(forge.getForgery(), emptyMap()),
            mockWriter
        )
        // When
        testedScope.handleEvent(
            RumRawEvent.ActionSent(testedScope.viewId, forge.anInt(), type.toSchemaType(), actionEventTimestamp),
            mockWriter
        )

        // Then
        argumentCaptor<ViewEvent> {
            verify(mockWriter, times(2)).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))
            assertThat(lastValue).containsExactlyContextAttributes(expectedAttributes)
        }
    }

    @Test
    fun `M not update the global attributes W handleEvent(Resource Sent) on stopped view`(
        @StringForgery key: String,
        @Forgery method: RumResourceMethod,
        @StringForgery url: String,
        forge: Forge
    ) {
        // Given
        val fakeStopEventAttributes = forge.aFilteredMap(excludedKeys = fakeAttributes.keys) {
            anHexadecimalString() to anAsciiString()
        }
        val excludedKeys = fakeAttributes.keys + fakeStopEventAttributes.keys
        val fakeGlobalAttributes = forge.aFilteredMap(excludedKeys = excludedKeys) {
            anHexadecimalString() to anAsciiString()
        }
        val fakeViewStoppedGlobalAttributes = forge.aFilteredMap(excludedKeys = excludedKeys) {
            anHexadecimalString() to anAsciiString()
        }
        val fakeResourceStoppedGlobalProperties = forge.aFilteredMap(excludedKeys = excludedKeys) {
            anHexadecimalString() to anAsciiString()
        }
        val expectedAttributes = mutableMapOf<String, Any?>()
        val fakeResourceId = forge.getForgery<UUID>().toString()
        val fakeResourceEndTimestamp = forge.aPositiveLong()
        expectedAttributes.putAll(fakeAttributes)
        expectedAttributes.putAll(fakeViewStoppedGlobalAttributes)
        expectedAttributes.putAll(fakeStopEventAttributes)
        whenever(rumMonitor.mockInstance.getAttributes()) doReturnConsecutively listOf(
            // one for initialization
            fakeGlobalAttributes,
            // second one for when the resource is started
            fakeGlobalAttributes,
            // third one for when the resource scope init
            fakeGlobalAttributes,
            // fourth one for when the view is stopped
            fakeViewStoppedGlobalAttributes,
            // last one when the resource is stopped
            fakeResourceStoppedGlobalProperties
        )

        testedScope = newRumViewScope()
        testedScope.handleEvent(
            RumRawEvent.StartResource(key, url, method, emptyMap()),
            mockWriter
        )
        testedScope.handleEvent(
            RumRawEvent.StopView(fakeKey, fakeStopEventAttributes),
            mockWriter
        )

        // When
        testedScope.handleEvent(
            RumRawEvent.ResourceSent(testedScope.viewId, fakeResourceId, fakeResourceEndTimestamp),
            mockWriter
        )

        // Then
        argumentCaptor<ViewEvent> {
            verify(mockWriter, times(2)).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))
            assertThat(lastValue).containsExactlyContextAttributes(expectedAttributes)
        }
        verifyNoMoreInteractions(mockWriter)
    }

    @Test
    fun `M not update the global attributes W handleEvent(Action Sent) on stopped view`(
        @Forgery type: RumActionType,
        @StringForgery name: String,
        @LongForgery(0) actionEventTimestamp: Long,
        forge: Forge
    ) {
        // Given
        val fakeStopEventAttributes = forge.aFilteredMap(excludedKeys = fakeAttributes.keys) {
            anHexadecimalString() to anAsciiString()
        }
        val excludedKeys = fakeAttributes.keys + fakeStopEventAttributes.keys
        val fakeGlobalAttributes = forge.aFilteredMap(excludedKeys = excludedKeys) {
            anHexadecimalString() to anAsciiString()
        }
        val fakeViewStoppedGlobalAttributes = forge.aFilteredMap(excludedKeys = excludedKeys) {
            anHexadecimalString() to anAsciiString()
        }
        val fakeActionSentGlobalProperties = forge.aFilteredMap(excludedKeys = excludedKeys) {
            anHexadecimalString() to anAsciiString()
        }
        val expectedAttributes = mutableMapOf<String, Any?>()
        expectedAttributes.putAll(fakeViewStoppedGlobalAttributes)
        expectedAttributes.putAll(fakeAttributes)
        expectedAttributes.putAll(fakeStopEventAttributes)
        whenever(rumMonitor.mockInstance.getAttributes()) doReturnConsecutively listOf(
            // one for initialization
            fakeGlobalAttributes,
            // second one for when the action is started
            fakeGlobalAttributes,
            // third one for the action scope init
            fakeGlobalAttributes,
            // fourth one for when the view is stopped
            fakeViewStoppedGlobalAttributes,
            // last one when the action was sent
            fakeActionSentGlobalProperties
        )
        testedScope = newRumViewScope()

        testedScope.handleEvent(
            RumRawEvent.StartAction(type, name, forge.aBool(), emptyMap()),
            mockWriter
        )
        testedScope.handleEvent(
            RumRawEvent.StopView(fakeKey, fakeStopEventAttributes),
            mockWriter
        )

        // When
        testedScope.handleEvent(
            RumRawEvent.ActionSent(testedScope.viewId, forge.anInt(), type.toSchemaType(), actionEventTimestamp),
            mockWriter
        )

        // Then
        argumentCaptor<ViewEvent> {
            verify(mockWriter, times(2)).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))
            assertThat(lastValue).containsExactlyContextAttributes(expectedAttributes)
        }
    }

    @Test
    fun `M use a copy of the global attributes W handleEvent(StopView)`(
        forge: Forge
    ) {
        // Given
        val fakeStopEventAttributes = forge.aFilteredMap(excludedKeys = fakeAttributes.keys) {
            anHexadecimalString() to anAsciiString()
        }
        val excludedKeys = fakeAttributes.keys + fakeStopEventAttributes.keys
        val fakeGlobalAttributes = forge.aFilteredMap(excludedKeys = excludedKeys) {
            anHexadecimalString() to anAsciiString()
        }.toMutableMap()
        val expectedAttributes = mutableMapOf<String, Any?>()
        expectedAttributes.putAll(fakeAttributes)
        expectedAttributes.putAll(fakeGlobalAttributes)
        expectedAttributes.putAll(fakeStopEventAttributes)
        whenever(rumMonitor.mockInstance.getAttributes()) doReturn fakeGlobalAttributes

        testedScope = newRumViewScope()

        // When
        val result = testedScope.handleEvent(
            RumRawEvent.StopView(fakeKey, fakeStopEventAttributes),
            mockWriter
        )
        // updating the global attributes here
        fakeGlobalAttributes[forge.anAlphabeticalString()] = forge.anAlphabeticalString()

        // Then
        argumentCaptor<ViewEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))
            assertThat(lastValue).containsExactlyContextAttributes(expectedAttributes)
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isNull()
    }

    // endregion

    // region Slow frames

    @Test
    fun `M call onViewCreated on slowFramesListener W init`() {
        verify(mockSlowFramesListener).onViewCreated(testedScope.viewId, fakeEventTime.nanoTime)
    }

    @Test
    fun `M call onAddLongTask of slowFramesListener W handleEvent(AddLongTask)`(
        @LongForgery(0L, 700_000_000L) durationNs: Long,
        @StringForgery target: String
    ) {
        // Given
        fakeEvent = RumRawEvent.AddLongTask(durationNs, target)

        // When
        testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        verify(mockSlowFramesListener).onAddLongTask(durationNs)
    }

    @Test
    fun `M call resolveReport(viewId, true) of slowFramesListener W handleEvent(StopView)`(
        forge: Forge
    ) {
        // Given
        fakeEvent = RumRawEvent.StopView(
            key = testedScope.key,
            attributes = forge.exhaustiveAttributes(),
            eventTime = Time(nanoTime = fakeEventTime.nanoTime + fakeViewDurationNs)
        )

        // When
        testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        verify(mockSlowFramesListener).resolveReport(testedScope.viewId, true, fakeViewDurationNs)
    }

    @Test
    fun `M call resolveReport(viewId, true) of slowFramesListener W handleEvent(StartView)`(
        forge: Forge
    ) {
        // When
        testedScope.handleEvent(
            forge.startViewEvent(eventTime = Time(nanoTime = fakeEventTime.nanoTime + fakeViewDurationNs)),
            mockWriter
        )

        // Then
        verify(mockSlowFramesListener).resolveReport(testedScope.viewId, true, fakeViewDurationNs)
    }

    @Test
    fun `M call resolveReport(viewId, true, Long) of slowFramesListener W handleEvent(StopSession)`() {
        // When
        testedScope.handleEvent(
            RumRawEvent.StopSession(eventTime = Time(nanoTime = fakeEventTime.nanoTime + fakeViewDurationNs)),
            mockWriter
        )

        // Then
        verify(mockSlowFramesListener).resolveReport(testedScope.viewId, true, fakeViewDurationNs)
    }

    @Test
    fun `M call resolveReport(viewId, false) of slowFramesListener W handleEvent()`(forge: Forge) {
        // Given
        val nonTerminalViewUpdateEvents = listOf(
            forge.getForgery<RumRawEvent.KeepAlive>(),
            forge.getForgery<RumRawEvent.AddCustomTiming>(),
            forge.getForgery<RumRawEvent.AddFeatureFlagEvaluation>(),
            forge.getForgery<RumRawEvent.AddFeatureFlagEvaluations>(),
            forge.getForgery<RumRawEvent.AddError>().copy(isFatal = true),
            forge.getForgery<RumRawEvent.ActionSent>().copy(viewId = testedScope.viewId),
            forge.getForgery<RumRawEvent.ErrorSent>().copy(viewId = testedScope.viewId),
            forge.getForgery<RumRawEvent.ResourceSent>().copy(viewId = testedScope.viewId),
            forge.getForgery<RumRawEvent.LongTaskSent>().copy(viewId = testedScope.viewId)
        )
        val event = forge.anElementFrom(nonTerminalViewUpdateEvents)

        // When
        testedScope.handleEvent(event, mockWriter)

        // Then
        verify(mockSlowFramesListener).resolveReport(eq(testedScope.viewId), eq(false), any())
    }

    // endregion

    @Test
    fun `M produce event safe for serialization W handleEvent()`(
        forge: Forge
    ) {
        // Given
        val writeWorker = Executors.newCachedThreadPool()
        val tasks = mutableListOf<Future<*>>()
        whenever(mockRumFeatureScope.withWriteContext(any(), any())) doAnswer {
            val callback = it.getArgument<(DatadogContext, EventBatchWriter) -> Unit>(1)
            tasks += writeWorker.submit {
                callback.invoke(fakeDatadogContext, mockEventBatchWriter)
            }
        }
        whenever(mockWriter.write(eq(mockEventBatchWriter), any(), eq(EventType.DEFAULT))) doAnswer {
            when (val event = it.getArgument<Any>(1)) {
                is ViewEvent -> assertDoesNotThrow { event.toJson() }
                is ErrorEvent -> assertDoesNotThrow { event.toJson() }
                is ActionEvent -> assertDoesNotThrow { event.toJson() }
                is LongTaskEvent -> assertDoesNotThrow { event.toJson() }
                // error is on purpose here, because under the hood all the Exceptions are caught
                else -> throw Error("unsupported event type ${event::class}")
            }
            true
        }
        whenever(rumMonitor.mockInstance.getAttributes()) doReturn forge.exhaustiveAttributes()

        testedScope = newRumViewScope()

        // When
        repeat(1000) {
            testedScope.handleEvent(forge.applicationStartedEvent(), mockWriter)
            testedScope.handleEvent(
                forge.anyRumEvent(
                    excluding = listOf(
                        RumRawEvent.StartView::class,
                        RumRawEvent.StopView::class,
                        RumRawEvent.StartAction::class,
                        RumRawEvent.StopAction::class,
                        RumRawEvent.StartResource::class,
                        RumRawEvent.StopResource::class,
                        RumRawEvent.StopResourceWithError::class,
                        RumRawEvent.StopResourceWithStackTrace::class
                    )
                ),
                mockWriter
            )
        }
        testedScope.handleEvent(forge.stopViewEvent(), mockWriter)

        writeWorker.shutdown()
        writeWorker.awaitTermination(5, TimeUnit.SECONDS)

        // Then
        tasks.forEach {
            // if there is any assertion error, it will be re-thrown
            it.get()
        }
    }

    // region Global Attributes

    @Test
    fun `M sendViewEnded W handleEvent(StopView)`(forge: Forge) {
        // Given
        testedScope = newRumViewScope()

        // When
        testedScope.handleEvent(forge.stopViewEvent(), mockWriter)

        // Then
        mockViewEndedMetricDispatcher.sendViewEnded(fakeInvState, fakeTnsState)
    }

    @Test
    fun `M sendViewEnded W handleEvent(StartView)`(forge: Forge) {
        // Given
        testedScope = newRumViewScope()

        // When
        testedScope.handleEvent(forge.startViewEvent(), mockWriter)

        // Then
        mockViewEndedMetricDispatcher.sendViewEnded(fakeInvState, fakeTnsState)
    }

    @Test
    fun `M sendViewEnded W handleEvent(StopSession)`() {
        // Given
        testedScope = newRumViewScope()

        // When
        testedScope.handleEvent(RumRawEvent.StopSession(), mockWriter)

        // Then
        mockViewEndedMetricDispatcher.sendViewEnded(fakeInvState, fakeTnsState)
    }

    @Test
    fun `M onDurationResolved W closing scope(StopSession)`(@LongForgery(min = 1) expectedDuration: Long) {
        // Given
        val stopEvent = RumRawEvent.StopSession(eventTime = Time(nanoTime = fakeEventTime.nanoTime + expectedDuration))
        testedScope = newRumViewScope()

        // When
        testedScope.handleEvent(stopEvent, mockWriter)

        // Then
        mockViewEndedMetricDispatcher.onDurationResolved(expectedDuration)
    }

    @Test
    fun `M onViewLoadingTimeResolved W handleEvent(AddViewLoadingTime)`(@LongForgery(min = 1) expectedDuration: Long) {
        // Given
        val stopEvent = RumRawEvent.AddViewLoadingTime(
            overwrite = false,
            eventTime = Time(nanoTime = fakeEventTime.nanoTime + expectedDuration)
        )
        testedScope = newRumViewScope()

        // When
        testedScope.handleEvent(stopEvent, mockWriter)

        // Then
        mockViewEndedMetricDispatcher.onViewLoadingTimeResolved(expectedDuration)
    }

    @Test
    fun `M return a new RumViewScope W renew the current one`() {
        // Given
        val expectedTime = Time(nanoTime = fakeEventTime.nanoTime)

        // When
        val newScope = testedScope.renew(expectedTime)

        assertThat(newScope.key).isEqualTo(testedScope.key)
        assertThat(newScope.firstPartyHostHeaderTypeResolver).isEqualTo(testedScope.firstPartyHostHeaderTypeResolver)
        assertThat(newScope.cpuVitalMonitor).isEqualTo(testedScope.cpuVitalMonitor)
        assertThat(newScope.memoryVitalMonitor).isEqualTo(testedScope.memoryVitalMonitor)
        assertThat(newScope.frameRateVitalMonitor).isEqualTo(testedScope.frameRateVitalMonitor)
        assertThat(newScope.type).isEqualTo(testedScope.type)
        assertThat(newScope.sampleRate).isEqualTo(testedScope.sampleRate)
        assertThat(newScope.url).isEqualTo(testedScope.url)
        assertThat(newScope.eventAttributes).isEqualTo(testedScope.eventAttributes)
        assertThat(newScope.stoppedNanos).isEqualTo(expectedTime.nanoTime)
        assertThat(newScope.viewLoadingTime).isNull()
        assertThat(newScope.activeActionScope).isNull()
        assertThat(newScope.activeResourceScopes).isEmpty()
        assertThat(newScope.pendingResourceCount).isEqualTo(0)
        assertThat(newScope.pendingActionCount).isEqualTo(0)
        assertThat(newScope.pendingErrorCount).isEqualTo(0)
        assertThat(newScope.pendingLongTaskCount).isEqualTo(0)
        assertThat(newScope.pendingFrozenFrameCount).isEqualTo(0)
        assertThat(newScope.version).isEqualTo(1)
        assertThat(newScope.customTimings).isEmpty()
        assertThat(newScope.featureFlags).isEmpty()
        assertThat(newScope.stopped).isEqualTo(false)
    }

    // endregion

    // region Internal

    private fun mockEvent(): RumRawEvent {
        val event: RumRawEvent = mock()
        whenever(event.eventTime) doReturn Time()
        return event
    }

    private fun forgeGlobalAttributes(
        forge: Forge,
        existingAttributes: Map<String, Any?>
    ): Map<String, Any?> {
        val existingKeys = existingAttributes.keys
        return forge.aFilteredMap(excludedKeys = existingKeys) {
            anHexadecimalString() to anAsciiString()
        }
    }

    private fun resolveExpectedTimestamp(timestamp: Long): Long {
        return timestamp + fakeTimeInfoAtScopeStart.serverTimeOffsetMs
    }

    private fun mockSessionReplayContext(testedScope: RumViewScope) {
        whenever(
            mockFeaturesContextResolver.resolveViewHasReplay(
                fakeDatadogContext,
                testedScope.viewId
            )
        ).thenReturn(fakeHasReplay)
        whenever(
            mockFeaturesContextResolver.resolveViewRecordsCount(
                fakeDatadogContext,
                testedScope.viewId
            )
        ).thenReturn(fakeReplayRecordsCount)
    }

    // endregion

    private fun newRumViewScope(
        parentScope: RumScope = mockParentScope,
        sdkCore: InternalSdkCore = rumMonitor.mockSdkCore,
        sessionEndedMetricDispatcher: SessionMetricDispatcher = mockSessionEndedMetricDispatcher,
        key: RumScopeKey = fakeKey,
        eventTime: Time = fakeEventTime,
        initialAttributes: Map<String, Any?> = fakeAttributes,
        viewChangedListener: RumViewChangedListener? = mockViewChangedListener,
        firstPartyHostHeaderTypeResolver: FirstPartyHostHeaderTypeResolver = mockResolver,
        cpuVitalMonitor: VitalMonitor = mockCpuVitalMonitor,
        memoryVitalMonitor: VitalMonitor = mockMemoryVitalMonitor,
        frameRateVitalMonitor: VitalMonitor = mockFrameRateVitalMonitor,
        featuresContextResolver: FeaturesContextResolver = mockFeaturesContextResolver,
        type: RumViewType = RumViewType.FOREGROUND,
        trackFrustrations: Boolean = fakeTrackFrustrations,
        sampleRate: Float = fakeSampleRate,
        interactionNextViewMetricResolver: InteractionToNextViewMetricResolver =
            mockInteractionToNextViewMetricResolver,
        networkSettledMetricResolver: NetworkSettledMetricResolver = mockNetworkSettledMetricResolver,
        viewEndedMetricDispatcher: ViewMetricDispatcher = mockViewEndedMetricDispatcher,
        slowFramesMetricListener: SlowFramesListener = mockSlowFramesListener
    ) = RumViewScope(
        parentScope = parentScope,
        sdkCore = sdkCore,
        sessionEndedMetricDispatcher = sessionEndedMetricDispatcher,
        key = key,
        eventTime = eventTime,
        initialAttributes = initialAttributes,
        viewChangedListener = viewChangedListener,
        firstPartyHostHeaderTypeResolver = firstPartyHostHeaderTypeResolver,
        cpuVitalMonitor = cpuVitalMonitor,
        memoryVitalMonitor = memoryVitalMonitor,
        frameRateVitalMonitor = frameRateVitalMonitor,
        featuresContextResolver = featuresContextResolver,
        type = type,
        trackFrustrations = trackFrustrations,
        sampleRate = sampleRate,
        interactionToNextViewMetricResolver = interactionNextViewMetricResolver,
        networkSettledMetricResolver = networkSettledMetricResolver,
        slowFramesListener = slowFramesMetricListener,
        viewEndedMetricDispatcher = viewEndedMetricDispatcher,
        rumSessionTypeOverride = fakeRumSessionType,
        accessibilityReader = mockAccessibilityReader
    )

    data class RumRawEventData(val event: RumRawEvent, val viewKey: RumScopeKey)

    companion object {
        private const val MAX_DURATION_VALUE_NS = 10_000_000_000L
        val rumMonitor = GlobalRumMonitorTestConfiguration()

        @TestConfigurationsProvider
        @JvmStatic
        fun getTestConfigurations(): List<TestConfiguration> {
            return listOf(rumMonitor)
        }

        @Suppress("unused")
        @JvmStatic
        fun brokenTimeRawEventData(): List<RumRawEventData> {
            val forge = Forge()
            Configurator().apply { configure(forge) }
            val fakeKey = forge.getForgery<RumScopeKey>()
            val fakeName = forge.anAlphabeticalString()
            val eventTime = Time(0, 0)
            return listOf(
                RumRawEventData(
                    RumRawEvent.KeepAlive(
                        eventTime = eventTime
                    ),
                    fakeKey
                ),
                RumRawEventData(
                    RumRawEvent.AddCustomTiming(
                        name = fakeName,
                        eventTime = eventTime
                    ),
                    fakeKey
                ),
                RumRawEventData(
                    RumRawEvent.StopView(
                        key = fakeKey,
                        attributes = emptyMap(),
                        eventTime = eventTime
                    ),
                    fakeKey
                ),
                RumRawEventData(
                    RumRawEvent.StartView(
                        key = fakeKey,
                        attributes = emptyMap(),
                        eventTime = eventTime
                    ),
                    fakeKey
                )
            )
        }
    }
}

private operator fun Time.plus(durationNs: Long): Time {
    return Time(
        timestamp = timestamp + TimeUnit.NANOSECONDS.toMillis(durationNs),
        nanoTime = nanoTime + durationNs
    )
}
