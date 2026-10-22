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
import com.datadog.android.api.feature.EventWriteScope
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureScope
import com.datadog.android.api.storage.DataWriter
import com.datadog.android.api.storage.EventBatchWriter
import com.datadog.android.api.storage.EventType
import com.datadog.android.core.InternalSdkCore
import com.datadog.android.core.internal.net.FirstPartyHostHeaderTypeResolver
import com.datadog.android.core.sampling.Sampler
import com.datadog.android.internal.tests.stub.StubTimeProvider
import com.datadog.android.rum.RumResourceKind
import com.datadog.android.rum.RumResourceMethod
import com.datadog.android.rum.configuration.ViewEventWriteConfig
import com.datadog.android.rum.event.ViewEventMapper
import com.datadog.android.rum.internal.domain.InfoProvider
import com.datadog.android.rum.internal.domain.Time
import com.datadog.android.rum.internal.domain.accessibility.AccessibilityInfo
import com.datadog.android.rum.internal.domain.battery.BatteryInfo
import com.datadog.android.rum.internal.domain.display.DisplayInfo
import com.datadog.android.rum.internal.domain.state.ViewUIPerformanceReport
import com.datadog.android.rum.internal.instrumentation.insights.InsightsCollector
import com.datadog.android.rum.internal.metric.SessionMetricDispatcher
import com.datadog.android.rum.internal.metric.slowframes.SlowFramesListener
import com.datadog.android.rum.internal.timeseries.Buffer
import com.datadog.android.rum.internal.timeseries.CollectedTimeseriesSessionRegistry
import com.datadog.android.rum.internal.timeseries.DataPoint
import com.datadog.android.rum.internal.timeseries.Pipeline
import com.datadog.android.rum.internal.timeseries.PipelineFactory
import com.datadog.android.rum.internal.timeseries.collector.DefaultTimeseriesCollector
import com.datadog.android.rum.internal.timeseries.factory.EventFactory
import com.datadog.android.rum.internal.timeseries.provider.DataPointsReader
import com.datadog.android.rum.internal.vitals.VitalMonitor
import com.datadog.android.rum.model.ResourceEvent
import com.datadog.android.rum.model.ViewEvent
import com.datadog.android.rum.timeseries.TimeseriesConfiguration
import com.datadog.android.rum.utils.forge.Configurator
import com.google.gson.JsonObject
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.annotation.LongForgery
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
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * Runs a real RUM scope tree with a real [DefaultTimeseriesCollector] across a session transition.
 * The context and storage executors are modeled as FIFO queues, drained after each RUM event,
 * so the asynchronous timeseries writes land in the same order as on a device.
 */
@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class RumTimeseriesSessionTransitionTest {

    private lateinit var testedScope: RumApplicationScope

    private lateinit var testedCollector: DefaultTimeseriesCollector

    @Mock
    lateinit var mockSdkCore: InternalSdkCore

    @Mock
    lateinit var mockRumFeatureScope: FeatureScope

    @Mock
    lateinit var mockEventWriteScope: EventWriteScope

    @Mock
    lateinit var mockEventBatchWriter: EventBatchWriter

    @Mock
    lateinit var mockWriter: DataWriter<Any>

    @Mock
    lateinit var mockTimeseriesWriter: DataWriter<Any>

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @Mock
    lateinit var mockSessionSampler: Sampler<String>

    @Mock
    lateinit var mockScheduledExecutor: ScheduledExecutorService

    @Mock
    lateinit var mockPipelinesFactory: PipelineFactory

    @Mock
    lateinit var mockReader: DataPointsReader<Double>

    @Mock
    lateinit var mockEventFactory: EventFactory<Double, JsonObject>

    @Mock
    lateinit var mockInsightsCollector: InsightsCollector

    @Mock
    lateinit var mockSlowFramesListener: SlowFramesListener

    @Mock
    lateinit var mockAccessibilityInfoProvider: InfoProvider<AccessibilityInfo>

    @Mock
    lateinit var mockBatteryInfoProvider: InfoProvider<BatteryInfo>

    @Mock
    lateinit var mockDisplayInfoProvider: InfoProvider<DisplayInfo>

    @Forgery
    lateinit var fakeDatadogContext: DatadogContext

    @Forgery
    lateinit var fakeTimeInfo: TimeInfo

    @Forgery
    lateinit var fakeNetworkInfo: NetworkInfo

    @Forgery
    lateinit var fakeViewUIPerformanceReport: ViewUIPerformanceReport

    @StringForgery(regex = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
    lateinit var fakeApplicationId: String

    @StringForgery
    lateinit var fakeLongViewKey: String

    @StringForgery
    lateinit var fakeNextViewKey: String

    @StringForgery
    lateinit var fakeResourceKey: String

    @StringForgery(regex = "https://[a-z]+\\.com/[a-z]+")
    lateinit var fakeResourceUrl: String

    @IntForgery(min = 2, max = 16)
    var fakeBufferSize: Int = 0

    @LongForgery(min = 1L, max = 1000L)
    var fakeSamplingIntervalMs: Long = 0L

    private val registry = CollectedTimeseriesSessionRegistry()
    private val stubTimeProvider = StubTimeProvider(
        elapsedTimeNs = RumSessionScope.DEFAULT_SESSION_INACTIVITY_NS + 1,
        elapsedRealtimeNs = RumSessionScope.DEFAULT_SESSION_INACTIVITY_NS + 1
    )
    private val contextQueue = mutableListOf<(DatadogContext, EventWriteScope) -> Unit>()
    private val storageQueue = mutableListOf<(EventBatchWriter) -> Unit>()
    private val scheduledTasks = mutableListOf<Runnable>()

    @BeforeEach
    fun `set up`() {
        whenever(mockSdkCore.getFeature(Feature.RUM_FEATURE_NAME)) doReturn mockRumFeatureScope
        whenever(mockSdkCore.time) doReturn fakeTimeInfo
        whenever(mockSdkCore.internalLogger) doReturn mockInternalLogger
        whenever(mockSdkCore.timeProvider) doReturn stubTimeProvider
        whenever(mockSdkCore.networkInfo) doReturn fakeNetworkInfo
        whenever(mockRumFeatureScope.withWriteContext(any(), any())) doAnswer {
            contextQueue += it.getArgument<(DatadogContext, EventWriteScope) -> Unit>(it.arguments.lastIndex)
        }
        whenever(mockEventWriteScope.invoke(any())) doAnswer {
            storageQueue += it.getArgument<(EventBatchWriter) -> Unit>(0)
        }
        whenever(mockWriter.write(any(), any(), any())) doReturn true
        whenever(mockTimeseriesWriter.write(any(), any(), any())) doReturn true
        whenever(mockScheduledExecutor.schedule(any<Runnable>(), any(), any())) doAnswer {
            scheduledTasks += it.getArgument<Runnable>(0)
            null
        }
        whenever(mockReader.intervalMs) doReturn fakeSamplingIntervalMs
        whenever(mockReader.read()) doAnswer { DataPoint(stubTimeProvider.elapsedTimeNs, 1.0) }
        whenever(mockEventFactory.create(any(), any(), any())) doReturn JsonObject()
        whenever(mockEventFactory.eventName) doReturn "view.cpu"
        whenever(mockPipelinesFactory.create(any())) doAnswer {
            listOf(
                Pipeline(
                    sdkCore = mockSdkCore,
                    reader = mockReader,
                    buffer = Buffer(fakeBufferSize),
                    eventFactory = mockEventFactory,
                    dataWriter = mockTimeseriesWriter,
                    collectedSessionsRegistry = registry,
                    insightsCollector = mockInsightsCollector
                )
            )
        }
        whenever(mockSessionSampler.sample(any())) doReturn true
        whenever(mockSlowFramesListener.resolveReport(any(), any(), any())) doReturn
            fakeViewUIPerformanceReport.snapshot()
        whenever(mockAccessibilityInfoProvider.getState()) doReturn mock()
        whenever(mockBatteryInfoProvider.getState()) doReturn BatteryInfo()
        whenever(mockDisplayInfoProvider.getState()) doReturn DisplayInfo()

        testedCollector = DefaultTimeseriesCollector(
            sdkCore = mockSdkCore,
            viewTransitionDelayMs = TimeseriesConfiguration.BACKGROUND_TRANSITION_DELAY_MS,
            pipelinesFactory = mockPipelinesFactory,
            scheduledExecutorService = mockScheduledExecutor,
            sessionRegistry = registry
        )
        testedScope = RumApplicationScope(
            applicationId = fakeApplicationId,
            sdkCore = mockSdkCore,
            sessionSampler = mockSessionSampler,
            backgroundTrackingEnabled = false,
            trackFrustrations = false,
            firstPartyHostHeaderTypeResolver = mock<FirstPartyHostHeaderTypeResolver>(),
            cpuVitalMonitor = mock<VitalMonitor>(),
            memoryVitalMonitor = mock<VitalMonitor>(),
            frameRateVitalMonitor = mock<VitalMonitor>(),
            sessionEndedMetricDispatcher = mock<SessionMetricDispatcher>(),
            sessionListener = null,
            initialResourceIdentifier = mock(),
            lastInteractionIdentifier = mock(),
            slowFramesListener = mockSlowFramesListener,
            rumSessionTypeOverride = null,
            accessibilityInfoProvider = mockAccessibilityInfoProvider,
            batteryInfoProvider = mockBatteryInfoProvider,
            displayInfoProvider = mockDisplayInfoProvider,
            rumSessionScopeStartupManagerFactory = mock(),
            insightsCollector = mockInsightsCollector,
            viewEventMapper = ViewEventMapper { it },
            viewEventWriteConfig = ViewEventWriteConfig.AlwaysFullView,
            heatmapIdentifierRegistry = null,
            timeseriesCollector = testedCollector
        )
        testedCollector.onUiVisible()
    }

    @Test
    fun `M send has_timeseries=true in late view of stopped session W new session started`() {
        // Given
        val previousSessionId = startLongViewWithPendingResource()
        runSamplingTicks(fakeBufferSize)
        handle(RumRawEvent.StopSession(currentTime()))
        handle(startViewEvent(fakeNextViewKey))

        // When
        completePendingResource()

        // Then
        val lateViewUpdate = viewEvents(previousSessionId).last()
        assertThat(lateViewUpdate.view.isActive).isFalse()
        assertThat(lateViewUpdate.session.hasTimeseries).isTrue()
    }

    @Test
    fun `M send has_timeseries=true in late view of stopped session W new session started { buffer not full }`() {
        // Given
        val previousSessionId = startLongViewWithPendingResource()
        runSamplingTicks(fakeBufferSize - 1)
        handle(RumRawEvent.StopSession(currentTime()))
        handle(startViewEvent(fakeNextViewKey))

        // When
        completePendingResource()

        // Then
        val lateViewUpdate = viewEvents(previousSessionId).last()
        assertThat(lateViewUpdate.view.isActive).isFalse()
        assertThat(lateViewUpdate.session.hasTimeseries).isTrue()
    }

    @Test
    fun `M send has_timeseries=true in final view W handleEvent(StopSession) { buffer not full, no pending work }`() {
        // Given
        handle(startViewEvent(fakeLongViewKey))
        val sessionId = checkNotNull(testedScope.activeSession).sessionId
        runSamplingTicks(fakeBufferSize - 1)

        // When
        handle(RumRawEvent.StopSession(currentTime()))

        // Then
        assertThat(testedScope.childScopes.map { it.sessionId }).doesNotContain(sessionId)
        val finalView = viewEvents(sessionId).last()
        assertThat(finalView.view.isActive).isFalse()
        assertThat(finalView.session.hasTimeseries).isTrue()
    }

    @Test
    fun `M send has_timeseries=false for new session W new session started { previous session has timeseries }`() {
        // Given
        val previousSessionId = startLongViewWithPendingResource()
        runSamplingTicks(fakeBufferSize)
        handle(RumRawEvent.StopSession(currentTime()))

        // When
        handle(startViewEvent(fakeNextViewKey))

        // Then
        val newSessionId = checkNotNull(testedScope.activeSession).sessionId
        assertThat(newSessionId).isNotEqualTo(previousSessionId)
        assertThat(viewEvents(newSessionId)).isNotEmpty.allSatisfy {
            assertThat(it.session.hasTimeseries).isFalse()
        }
    }

    @Test
    fun `M keep timeseries only for previous session W session renewed { inactivity }`() {
        // Given
        val fakeTimeseriesEvent = JsonObject()
        whenever(mockEventFactory.create(any(), any(), any())) doReturn fakeTimeseriesEvent
        handle(startViewEvent(fakeLongViewKey))
        val previousSessionId = checkNotNull(testedScope.activeSession).sessionId
        runSamplingTicks(fakeBufferSize - 1)
        advanceTimeByNs(RumSessionScope.DEFAULT_SESSION_INACTIVITY_NS)

        // When
        handle(startViewEvent(fakeNextViewKey))

        // Then
        val newSessionId = checkNotNull(testedScope.activeSession).sessionId
        assertThat(newSessionId).isNotEqualTo(previousSessionId)
        verify(mockTimeseriesWriter).write(mockEventBatchWriter, fakeTimeseriesEvent, EventType.DEFAULT)
        assertThat(testedCollector.isTimeseriesCollected(previousSessionId)).isTrue()
        assertThat(testedCollector.isTimeseriesCollected(newSessionId)).isFalse()
    }

    // region Internal

    private fun startLongViewWithPendingResource(): String {
        handle(startViewEvent(fakeLongViewKey))
        handle(
            RumRawEvent.StartResource(
                key = fakeResourceKey,
                url = fakeResourceUrl,
                method = RumResourceMethod.GET,
                attributes = emptyMap(),
                eventTime = currentTime()
            )
        )
        return checkNotNull(testedScope.activeSession).sessionId
    }

    // The long view of the stopped session stays alive until its pending resource is reported as sent.
    private fun completePendingResource() {
        handle(
            RumRawEvent.StopResource(
                key = fakeResourceKey,
                statusCode = 200L,
                size = null,
                kind = RumResourceKind.NATIVE,
                attributes = emptyMap(),
                eventTime = currentTime()
            )
        )
        val resourceEvent = writtenEvents().filterIsInstance<ResourceEvent>().last()
        handle(
            RumRawEvent.ResourceSent(
                viewId = resourceEvent.view.id,
                resourceId = checkNotNull(resourceEvent.resource.id),
                resourceEndTimestampInNanos = stubTimeProvider.elapsedTimeNs,
                eventTime = currentTime()
            )
        )
    }

    private fun handle(event: RumRawEvent) {
        testedScope.handleEvent(event, fakeDatadogContext, mockEventWriteScope, mockWriter)
        drainExecutors()
    }

    private fun drainExecutors() {
        while (contextQueue.isNotEmpty() || storageQueue.isNotEmpty()) {
            while (contextQueue.isNotEmpty()) {
                contextQueue.removeAt(0).invoke(fakeDatadogContext, mockEventWriteScope)
            }
            while (storageQueue.isNotEmpty()) {
                storageQueue.removeAt(0).invoke(mockEventBatchWriter)
            }
        }
    }

    private fun runSamplingTicks(count: Int) {
        repeat(count) {
            advanceTimeByNs(TimeUnit.MILLISECONDS.toNanos(fakeSamplingIntervalMs))
            val dueTasks = scheduledTasks.toList()
            scheduledTasks.clear()
            dueTasks.forEach(Runnable::run)
        }
        drainExecutors()
    }

    private fun startViewEvent(key: String) = RumRawEvent.StartView(
        key = RumScopeKey.from(key),
        attributes = emptyMap(),
        eventTime = currentTime()
    )

    private fun writtenEvents(): List<Any> {
        val captor = argumentCaptor<Any>()
        verify(mockWriter, atLeastOnce()).write(any(), captor.capture(), any())
        return captor.allValues
    }

    private fun viewEvents(sessionId: String): List<ViewEvent> = writtenEvents()
        .filterIsInstance<MappedViewEvent>()
        .map { it.viewEvent }
        .filter { it.session.id == sessionId }

    private fun currentTime() = Time(
        timestamp = stubTimeProvider.deviceTimestampMs,
        nanoTime = stubTimeProvider.elapsedTimeNs
    )

    private fun advanceTimeByNs(ns: Long) {
        stubTimeProvider.elapsedTimeNs += ns
        stubTimeProvider.elapsedRealtimeNs += ns
        stubTimeProvider.deviceTimestampMs += TimeUnit.NANOSECONDS.toMillis(ns)
    }

    // endregion
}
