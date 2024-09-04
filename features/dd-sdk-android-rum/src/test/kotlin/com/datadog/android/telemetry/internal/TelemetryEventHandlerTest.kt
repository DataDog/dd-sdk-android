/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.telemetry.internal

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.context.DatadogContext
import com.datadog.android.api.context.DeviceInfo
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureScope
import com.datadog.android.api.storage.DataWriter
import com.datadog.android.api.storage.EventBatchWriter
import com.datadog.android.api.storage.EventType
import com.datadog.android.core.InternalSdkCore
import com.datadog.android.core.internal.utils.loggableStackTrace
import com.datadog.android.core.sampling.Sampler
import com.datadog.android.rum.internal.RumFeature
import com.datadog.android.rum.internal.domain.RumContext
import com.datadog.android.rum.internal.domain.scope.RumRawEvent
import com.datadog.android.rum.internal.metric.SessionMetricDispatcher
import com.datadog.android.rum.tracking.ActivityViewTrackingStrategy
import com.datadog.android.rum.tracking.FragmentViewTrackingStrategy
import com.datadog.android.rum.tracking.MixedViewTrackingStrategy
import com.datadog.android.rum.tracking.NavigationViewTrackingStrategy
import com.datadog.android.rum.utils.forge.Configurator
import com.datadog.android.rum.utils.verifyLog
import com.datadog.android.telemetry.assertj.TelemetryConfigurationEventAssert.Companion.assertThat
import com.datadog.android.telemetry.assertj.TelemetryDebugEventAssert.Companion.assertThat
import com.datadog.android.telemetry.assertj.TelemetryErrorEventAssert.Companion.assertThat
import com.datadog.android.telemetry.model.TelemetryConfigurationEvent
import com.datadog.android.telemetry.model.TelemetryDebugEvent
import com.datadog.android.telemetry.model.TelemetryErrorEvent
import com.datadog.tools.unit.forge.aThrowable
import com.datadog.tools.unit.forge.exhaustiveAttributes
import com.datadog.tools.unit.setStaticValue
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import io.opentracing.Tracer
import io.opentracing.util.GlobalTracer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.Mock
import org.mockito.invocation.InvocationOnMock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import org.mockito.stubbing.Answer
import java.util.Locale
import kotlin.reflect.jvm.jvmName
import com.datadog.android.telemetry.model.TelemetryConfigurationEvent.ViewTrackingStrategy as VTS

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class TelemetryEventHandlerTest {

    private lateinit var testedTelemetryHandler: TelemetryEventHandler

    @Mock
    lateinit var mockWriter: DataWriter<Any>

    @Mock
    lateinit var mockSampler: Sampler

    @Mock
    lateinit var mockConfigurationSampler: Sampler

    @Mock
    lateinit var mockSdkCore: InternalSdkCore

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @Mock
    lateinit var mockRumFeatureScope: FeatureScope

    @Mock
    lateinit var mockEventBatchWriter: EventBatchWriter

    @Mock
    lateinit var mockDeviceInfo: DeviceInfo

    @Mock
    lateinit var sessionEndedMetricDispatcher: SessionMetricDispatcher

    @Forgery
    lateinit var fakeDatadogContext: DatadogContext

    @Forgery
    lateinit var fakeRumContext: RumContext

    @StringForgery
    lateinit var fakeDeviceArchitecture: String

    @StringForgery
    lateinit var fakeDeviceBrand: String

    @StringForgery
    lateinit var fakeDeviceModel: String

    @StringForgery
    lateinit var fakeOsBuildId: String

    @StringForgery
    lateinit var fakeOsVersion: String

    @StringForgery
    lateinit var fakeOsName: String

    private var fakeServerOffset: Long = 0L

    @BeforeEach
    fun `set up`(forge: Forge) {
        whenever(mockDeviceInfo.architecture).thenReturn(fakeDeviceArchitecture)
        whenever(mockDeviceInfo.deviceBrand).thenReturn(fakeDeviceBrand)
        whenever(mockDeviceInfo.deviceModel).thenReturn(fakeDeviceModel)
        whenever(mockDeviceInfo.deviceBuildId).thenReturn(fakeOsBuildId)
        whenever(mockDeviceInfo.osVersion).thenReturn(fakeOsVersion)
        whenever(mockDeviceInfo.osName).thenReturn(fakeOsName)
        whenever(mockDeviceInfo.architecture).thenReturn(fakeDeviceArchitecture)

        fakeServerOffset = forge.aLong(-50000, 50000)

        fakeDatadogContext = fakeDatadogContext.copy(
            source = "android",
            featuresContext = fakeDatadogContext.featuresContext.toMutableMap().apply {
                put(
                    Feature.RUM_FEATURE_NAME,
                    mapOf(
                        "application_id" to fakeRumContext.applicationId,
                        "session_id" to fakeRumContext.sessionId,
                        "view_id" to fakeRumContext.viewId,
                        "action_id" to fakeRumContext.actionId
                    )
                )
            },
            time = fakeDatadogContext.time.copy(
                serverTimeOffsetMs = fakeServerOffset
            ),
            deviceInfo = mockDeviceInfo
        )

        whenever(mockSampler.sample()) doReturn true
        whenever(mockConfigurationSampler.sample()) doReturn true

        whenever(
            mockSdkCore.getFeature(Feature.RUM_FEATURE_NAME)
        ) doReturn mockRumFeatureScope
        whenever(mockRumFeatureScope.withWriteContext(any(), any())) doAnswer {
            val callback = it.getArgument<(DatadogContext, EventBatchWriter) -> Unit>(1)
            callback.invoke(fakeDatadogContext, mockEventBatchWriter)
        }
        whenever(mockSdkCore.internalLogger) doReturn mockInternalLogger

        testedTelemetryHandler = TelemetryEventHandler(
            mockSdkCore,
            mockSampler,
            mockConfigurationSampler,
            sessionEndedMetricDispatcher,
            MAX_EVENTS_PER_SESSION_TEST
        )
    }

    @AfterEach
    fun `tear down`() {
        GlobalTracer::class.java.setStaticValue("isRegistered", false)
    }

    // region Debug Event

    @Test
    fun `M create debug event W handleEvent(SendTelemetry) { debug event status }`(forge: Forge) {
        // Given
        val debugRawEvent = forge.createRumRawTelemetryDebugEvent()

        // When
        testedTelemetryHandler.handleEvent(debugRawEvent, mockWriter)

        // Then
        argumentCaptor<TelemetryDebugEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.TELEMETRY))
            assertDebugEventMatchesRawEvent(lastValue, debugRawEvent, fakeRumContext)
        }
    }

    @Test
    fun `M create debug event W handleEvent(SendTelemetry) { debug event status, no RUM }`(forge: Forge) {
        // Given
        val debugRawEvent = forge.createRumRawTelemetryDebugEvent()
        fakeDatadogContext = fakeDatadogContext.copy(
            featuresContext = fakeDatadogContext.featuresContext.toMutableMap().apply {
                remove(Feature.RUM_FEATURE_NAME)
            }
        )
        val noRumContext = RumContext(
            applicationId = RumContext.NULL_UUID,
            sessionId = RumContext.NULL_UUID,
            viewId = null,
            actionId = null
        )

        // When
        testedTelemetryHandler.handleEvent(debugRawEvent, mockWriter)

        // Then
        argumentCaptor<TelemetryDebugEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.TELEMETRY))
            assertDebugEventMatchesRawEvent(lastValue, debugRawEvent, noRumContext)
        }
    }

    // endregion

    // region Error Event

    @Test
    fun `M create error event W handleEvent(SendTelemetry) { error event status }`(forge: Forge) {
        // Given
        val errorRawEvent = forge.createRumRawTelemetryErrorEvent()

        // When
        testedTelemetryHandler.handleEvent(errorRawEvent, mockWriter)

        // Then
        argumentCaptor<TelemetryErrorEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.TELEMETRY))
            assertErrorEventMatchesRawEvent(lastValue, errorRawEvent, fakeRumContext)
        }
    }

    @Test
    fun `M create error event W handleEvent(SendTelemetry) { error event status, no RUM }`(forge: Forge) {
        // Given
        val errorRawEvent = forge.createRumRawTelemetryErrorEvent()
        fakeDatadogContext = fakeDatadogContext.copy(
            featuresContext = fakeDatadogContext.featuresContext.toMutableMap().apply {
                remove(Feature.RUM_FEATURE_NAME)
            }
        )
        val noRumContext = RumContext(
            applicationId = RumContext.NULL_UUID,
            sessionId = RumContext.NULL_UUID,
            viewId = null,
            actionId = null
        )

        // When
        testedTelemetryHandler.handleEvent(errorRawEvent, mockWriter)

        // Then
        argumentCaptor<TelemetryErrorEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.TELEMETRY))
            assertErrorEventMatchesRawEvent(lastValue, errorRawEvent, noRumContext)
        }
    }

    // endregion

    // region Configuration Event

    @Test
    fun `M create config event W handleEvent(SendTelemetry) { configuration }`(forge: Forge) {
        // Given
        val configRawEvent = forge.createRumRawTelemetryConfigurationEvent()

        // When
        testedTelemetryHandler.handleEvent(configRawEvent, mockWriter)

        // Then
        argumentCaptor<TelemetryConfigurationEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.TELEMETRY))
            assertConfigEventMatchesRawEvent(firstValue, configRawEvent, fakeRumContext)
        }
    }

    @Test
    fun `M create config event W handleEvent(SendTelemetry) { configuration, no RUM }`(forge: Forge) {
        // Given
        val configRawEvent = forge.createRumRawTelemetryConfigurationEvent()
        fakeDatadogContext = fakeDatadogContext.copy(
            featuresContext = fakeDatadogContext.featuresContext.toMutableMap().apply {
                remove(Feature.RUM_FEATURE_NAME)
            }
        )
        val noRumContext = RumContext(
            applicationId = RumContext.NULL_UUID,
            sessionId = RumContext.NULL_UUID,
            viewId = null,
            actionId = null
        )

        // When
        testedTelemetryHandler.handleEvent(configRawEvent, mockWriter)

        // Then
        argumentCaptor<TelemetryConfigurationEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.TELEMETRY))
            assertConfigEventMatchesRawEvent(firstValue, configRawEvent, noRumContext)
        }
    }

    @Test
    fun `M create config event W handleEvent(SendTelemetry) { with RUM config }`(
        @Forgery fakeRumConfiguration: RumFeature.Configuration,
        forge: Forge
    ) {
        // Given
        val mockRumFeature = mock<RumFeature>()
        whenever(mockRumFeature.configuration) doReturn fakeRumConfiguration
        whenever(mockRumFeatureScope.unwrap<RumFeature>()) doReturn mockRumFeature

        val configRawEvent = forge.createRumRawTelemetryConfigurationEvent()

        val expectedViewTrackingStrategy = when (fakeRumConfiguration.viewTrackingStrategy) {
            is ActivityViewTrackingStrategy -> VTS.ACTIVITYVIEWTRACKINGSTRATEGY
            is FragmentViewTrackingStrategy -> VTS.FRAGMENTVIEWTRACKINGSTRATEGY
            is MixedViewTrackingStrategy -> VTS.MIXEDVIEWTRACKINGSTRATEGY
            is NavigationViewTrackingStrategy -> VTS.NAVIGATIONVIEWTRACKINGSTRATEGY
            else -> null
        }

        // When
        testedTelemetryHandler.handleEvent(configRawEvent, mockWriter)

        // Then
        argumentCaptor<TelemetryConfigurationEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.TELEMETRY))
            assertConfigEventMatchesRawEvent(firstValue, configRawEvent, fakeRumContext)
            assertThat(firstValue)
                .hasSessionSampleRate(fakeRumConfiguration.sampleRate.toLong())
                .hasTelemetrySampleRate(fakeRumConfiguration.telemetrySampleRate.toLong())
                .hasTrackLongTasks(fakeRumConfiguration.longTaskTrackingStrategy != null)
                .hasTrackFrustrations(fakeRumConfiguration.trackFrustrations)
                .hasViewTrackingStrategy(expectedViewTrackingStrategy)
                .hasTrackBackgroundEvents(fakeRumConfiguration.backgroundEventTracking)
                .hasMobileVitalsUpdatePeriod(
                    fakeRumConfiguration.vitalsMonitorUpdateFrequency.periodInMs
                )
        }
    }

    @Test
    fun `M create config event W handleEvent(SendTelemetry) { with Core config }`(
        @Forgery fakeCoreConfiguration: TelemetryCoreConfiguration,
        forge: Forge
    ) {
        // Given
        val configRawEvent = forge.createRumRawTelemetryConfigurationEvent(fakeCoreConfiguration)

        // When
        testedTelemetryHandler.handleEvent(configRawEvent, mockWriter)

        // Then
        argumentCaptor<TelemetryConfigurationEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.TELEMETRY))
            assertConfigEventMatchesRawEvent(firstValue, configRawEvent, fakeRumContext)
            assertThat(firstValue)
                .hasUseProxy(fakeCoreConfiguration.useProxy)
                .hasUseLocalEncryption(fakeCoreConfiguration.useLocalEncryption)
                .hasTrackErrors(fakeCoreConfiguration.trackErrors)
                .hasBatchSize(fakeCoreConfiguration.batchSize)
                .hasBatchUploadFrequency(fakeCoreConfiguration.batchUploadFrequency)
                .hasBatchProcessingLevel(fakeCoreConfiguration.batchProcessingLevel)
        }
    }

    @ParameterizedTest
    @MethodSource("tracingConfigurationParameters")
    fun `M create config event W handleEvent(SendTelemetry) { tracing configuration with tracing settings }`(
        useTracer: Boolean,
        tracerApi: TelemetryEventHandler.TracerApi?,
        tracerApiVersion: String?,
        @Forgery fakeConfiguration: TelemetryCoreConfiguration
    ) {
        // Given
        val configRawEvent = forge.createRumRawTelemetryConfigurationEvent(fakeConfiguration)
        if (useTracer) {
            whenever(mockSdkCore.getFeature(Feature.TRACING_FEATURE_NAME)) doReturn mock()
            if (tracerApi == TelemetryEventHandler.TracerApi.OpenTracing) {
                GlobalTracer.registerIfAbsent(mock<Tracer>())
            } else if (tracerApi == TelemetryEventHandler.TracerApi.OpenTelemetry) {
                whenever(mockSdkCore.getFeatureContext(Feature.TRACING_FEATURE_NAME)) doReturn
                    mapOf(
                        TelemetryEventHandler.IS_OPENTELEMETRY_ENABLED_CONTEXT_KEY to true,
                        TelemetryEventHandler.OPENTELEMETRY_API_VERSION_CONTEXT_KEY to tracerApiVersion
                    )
            }
        }

        // When
        testedTelemetryHandler.handleEvent(configRawEvent, mockWriter)

        // Then
        argumentCaptor<TelemetryConfigurationEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.TELEMETRY))
            assertConfigEventMatchesRawEvent(firstValue, configRawEvent, fakeRumContext)
            assertThat(firstValue)
                .hasUseTracing(useTracer)
                .hasTracerApi(tracerApi?.name)
                .hasTracerApiVersion(tracerApiVersion)
        }
    }

    @Test
    fun `M create config event W handleEvent(SendTelemetry) { configuration with interceptor }`(
        @Forgery fakeConfiguration: TelemetryCoreConfiguration,
        forge: Forge
    ) {
        // Given
        val trackNetworkRequests = forge.aBool()
        val configRawEvent = forge.createRumRawTelemetryConfigurationEvent(fakeConfiguration)

        // When
        if (trackNetworkRequests) {
            testedTelemetryHandler.handleEvent(
                RumRawEvent.SendTelemetry(
                    TelemetryType.INTERCEPTOR_SETUP,
                    "",
                    null,
                    null,
                    coreConfiguration = null,
                    additionalProperties = null
                ),
                mockWriter
            )
        }
        testedTelemetryHandler.handleEvent(configRawEvent, mockWriter)

        // Then
        argumentCaptor<TelemetryConfigurationEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.TELEMETRY))
            assertConfigEventMatchesRawEvent(firstValue, configRawEvent, fakeRumContext)
            assertThat(firstValue)
                .hasTrackNetworkRequests(trackNetworkRequests)
        }
    }

    @Test
    fun `M create config event W handleEvent(SendTelemetry) { configuration, no SessionReplay }`(
        forge: Forge
    ) {
        // Given
        val configRawEvent = forge.createRumRawTelemetryConfigurationEvent()

        // When
        testedTelemetryHandler.handleEvent(configRawEvent, mockWriter)

        // Then
        argumentCaptor<TelemetryConfigurationEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.TELEMETRY))
            assertConfigEventMatchesRawEvent(firstValue, configRawEvent)
            assertThat(firstValue).hasSessionReplaySampleRate(null)
            assertThat(firstValue).hasSessionReplayStartManually(null)
            assertThat(firstValue).hasSessionReplayPrivacy(null)
        }
    }

    @Test
    fun `M create config event W handleEvent(SendTelemetry) { configuration, with SessionReplay }`(
        forge: Forge
    ) {
        // Given
        val fakeSampleRate = forge.aPositiveLong()
        val fakeSessionReplayPrivacy = forge.aString()
        val fakeSessionReplayIsStartManually = forge.aBool()
        val fakeSessionReplayContext = mutableMapOf<String, Any?>(
            TelemetryEventHandler.SESSION_REPLAY_PRIVACY_KEY to fakeSessionReplayPrivacy,
            TelemetryEventHandler.SESSION_REPLAY_MANUAL_RECORDING_KEY to
                fakeSessionReplayIsStartManually,
            TelemetryEventHandler.SESSION_REPLAY_SAMPLE_RATE_KEY to fakeSampleRate
        )
        whenever(mockSdkCore.getFeatureContext(Feature.SESSION_REPLAY_FEATURE_NAME)) doReturn
            fakeSessionReplayContext
        val configRawEvent = forge.createRumRawTelemetryConfigurationEvent()

        // When
        testedTelemetryHandler.handleEvent(configRawEvent, mockWriter)

        // Then
        argumentCaptor<TelemetryConfigurationEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.TELEMETRY))
            assertConfigEventMatchesRawEvent(firstValue, configRawEvent)
            assertThat(firstValue).hasSessionReplaySampleRate(fakeSampleRate)
            assertThat(firstValue).hasSessionReplayStartManually(fakeSessionReplayIsStartManually)
            assertThat(firstValue).hasSessionReplayPrivacy(fakeSessionReplayPrivacy)
        }
    }

    @Test
    fun `M create config event W handleEvent(SendTelemetry) { with SessionReplay, bad format }`(
        forge: Forge
    ) {
        // Given
        val fakeSampleRate = forge.aNullable { aString() }
        val fakeSessionReplayPrivacy = forge.aNullable { aLong() }
        val fakeSessionReplayIsStartManually = forge.aNullable { aString() }
        val fakeSessionReplayContext = mutableMapOf<String, Any?>(
            TelemetryEventHandler.SESSION_REPLAY_PRIVACY_KEY to fakeSessionReplayPrivacy,
            TelemetryEventHandler.SESSION_REPLAY_MANUAL_RECORDING_KEY to
                fakeSessionReplayIsStartManually,
            TelemetryEventHandler.SESSION_REPLAY_SAMPLE_RATE_KEY to fakeSampleRate
        )
        whenever(mockSdkCore.getFeatureContext(Feature.SESSION_REPLAY_FEATURE_NAME)) doReturn
            fakeSessionReplayContext
        val configRawEvent = forge.createRumRawTelemetryConfigurationEvent()

        // When
        testedTelemetryHandler.handleEvent(configRawEvent, mockWriter)

        // Then
        argumentCaptor<TelemetryConfigurationEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.TELEMETRY))
            assertConfigEventMatchesRawEvent(firstValue, configRawEvent)
            assertThat(firstValue).hasSessionReplaySampleRate(null)
            assertThat(firstValue).hasSessionReplayStartManually(null)
            assertThat(firstValue).hasSessionReplayPrivacy(null)
        }
    }

    // endregion

    // region Sampling

    @Test
    fun `M not write event W handleEvent(SendTelemetry) { event is not sampled }`(forge: Forge) {
        // Given
        val rawEvent = forge.createRumRawTelemetryEvent()
        whenever(mockSampler.sample()) doReturn false

        // When
        testedTelemetryHandler.handleEvent(rawEvent, mockWriter)

        // Then
        verifyNoInteractions(mockWriter)
    }

    @Test
    fun `M write debug&error event W handleEvent(SendTelemetry) { configuration sampler returns false }`(
        forge: Forge
    ) {
        // Given
        val rawEvent = forge.anElementFrom(
            forge.createRumRawTelemetryDebugEvent(),
            forge.createRumRawTelemetryErrorEvent()
        )
        whenever(mockSampler.sample()) doReturn true
        whenever(mockConfigurationSampler.sample()) doReturn false

        // When
        testedTelemetryHandler.handleEvent(rawEvent, mockWriter)

        // Then
        argumentCaptor<Any> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.TELEMETRY))
            if (rawEvent.type == TelemetryType.DEBUG) {
                assertThat(lastValue).isInstanceOf(TelemetryDebugEvent::class.java)
            } else {
                assertThat(lastValue).isInstanceOf(TelemetryErrorEvent::class.java)
            }
        }
    }

    @Test
    fun `M not write configuration event W handleEvent(SendTelemetry) { event is not sampled }`(
        forge: Forge
    ) {
        // Given
        val rawEvent = forge.createRumRawTelemetryConfigurationEvent()
        whenever(mockSampler.sample()) doReturn true
        whenever(mockConfigurationSampler.sample()) doReturn false

        // When
        testedTelemetryHandler.handleEvent(rawEvent, mockWriter)

        // Then
        verifyNoInteractions(mockWriter)
    }

    @Test
    fun `M not write event W handleEvent(SendTelemetry){seen in the session, not metric}`(
        forge: Forge
    ) {
        // Given
        val rawEvent = forge.createRumRawTelemetryEvent().copy(isMetric = false)
        val anotherEvent = rawEvent.copy()

        // When
        testedTelemetryHandler.handleEvent(rawEvent, mockWriter)
        testedTelemetryHandler.handleEvent(anotherEvent, mockWriter)

        // Then
        mockInternalLogger.verifyLog(
            InternalLogger.Level.INFO,
            InternalLogger.Target.MAINTAINER,
            TelemetryEventHandler.ALREADY_SEEN_EVENT_MESSAGE.format(
                Locale.US,
                TelemetryEventId(
                    rawEvent.type,
                    rawEvent.message,
                    rawEvent.kind
                )
            )
        )

        argumentCaptor<Any> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.TELEMETRY))
            when (val capturedValue = lastValue) {
                is TelemetryDebugEvent -> {
                    assertDebugEventMatchesRawEvent(capturedValue, rawEvent, fakeRumContext)
                }

                is TelemetryErrorEvent -> {
                    assertErrorEventMatchesRawEvent(capturedValue, rawEvent, fakeRumContext)
                }

                is TelemetryConfigurationEvent -> {
                    assertConfigEventMatchesRawEvent(capturedValue, rawEvent, fakeRumContext)
                }

                else -> throw IllegalArgumentException(
                    "Unexpected type=${lastValue::class.jvmName} of the captured value."
                )
            }
            verifyNoMoreInteractions(mockWriter)
        }
    }

    @Test
    fun `M write event W handleEvent(SendTelemetry){seen in the session, is metric}`(
        forge: Forge
    ) {
        // Given
        val rawEvent = forge.createRumRawTelemetryEvent().copy(isMetric = true)
        val events = listOf(rawEvent, rawEvent.copy())

        // When
        testedTelemetryHandler.handleEvent(events[0], mockWriter)
        testedTelemetryHandler.handleEvent(events[1], mockWriter)

        // Then
        argumentCaptor<Any> {
            verify(mockWriter, times(2)).write(eq(mockEventBatchWriter), capture(), eq(EventType.TELEMETRY))
            allValues.withIndex().forEach {
                when (val capturedValue = it.value) {
                    is TelemetryDebugEvent -> {
                        assertDebugEventMatchesRawEvent(
                            capturedValue,
                            events[it.index],
                            fakeRumContext
                        )
                    }

                    is TelemetryErrorEvent -> {
                        assertErrorEventMatchesRawEvent(
                            capturedValue,
                            events[it.index],
                            fakeRumContext
                        )
                    }

                    is TelemetryConfigurationEvent -> {
                        assertConfigEventMatchesRawEvent(
                            capturedValue,
                            events[it.index],
                            fakeRumContext
                        )
                    }

                    else -> throw IllegalArgumentException(
                        "Unexpected type=${lastValue::class.jvmName} of the captured value."
                    )
                }
            }
        }
    }

    @Test
    fun `M not write events over the limit W handleEvent(SendTelemetry)`() {
        // Given
        val event = RumRawEvent.SendTelemetry(
            TelemetryType.DEBUG,
            "Metric event",
            null,
            null,
            coreConfiguration = null,
            additionalProperties = null,
            isMetric = true
        )
        val events = (0..MAX_EVENTS_PER_SESSION_TEST).map { event }

        // When
        events.forEach {
            testedTelemetryHandler.handleEvent(it, mockWriter)
        }

        // Then
        mockInternalLogger.verifyLog(
            level = InternalLogger.Level.INFO,
            target = InternalLogger.Target.MAINTAINER,
            message = TelemetryEventHandler.MAX_EVENT_NUMBER_REACHED_MESSAGE
        )
    }

    @Test
    fun `M continue writing events after new session W handleEvent(SendTelemetry)`(forge: Forge) {
        // Given
        val event = RumRawEvent.SendTelemetry(
            TelemetryType.DEBUG,
            "Metric event",
            null,
            null,
            coreConfiguration = null,
            additionalProperties = null,
            isMetric = true // important because non-metric events can only be seen once
        )
        val eventsInOldSession = (0..MAX_EVENTS_PER_SESSION_TEST / 2).map { event }
        val eventsInNewSession = (0..MAX_EVENTS_PER_SESSION_TEST / 2).map { event }

        eventsInOldSession.forEach {
            testedTelemetryHandler.handleEvent(it, mockWriter)
        }

        // When
        testedTelemetryHandler.onSessionStarted(forge.aString(), forge.aBool())

        eventsInNewSession.forEach {
            testedTelemetryHandler.handleEvent(it, mockWriter)
        }

        // Then
        verifyNoMoreInteractions(mockInternalLogger)
    }

    @Test
    fun `M count the limit only after the sampling W handleEvent(SendTelemetry)`(forge: Forge) {
        // Given
        // sample out 50%
        whenever(mockSampler.sample()) doAnswer object : Answer<Boolean> {
            var invocationCount = 0
            override fun answer(invocation: InvocationOnMock): Boolean {
                invocationCount++
                return invocationCount % 2 == 0
            }
        }

        val events = forge.aList(
            size = MAX_EVENTS_PER_SESSION_TEST * 10
        ) { createRumRawTelemetryEvent() }
            // remove unwanted identity collisions
            .groupBy { it.identity }
            .map { it.value.first() }
            .take(MAX_EVENTS_PER_SESSION_TEST * 2)

        assumeTrue(events.size == MAX_EVENTS_PER_SESSION_TEST * 2)

        // When
        events.forEach {
            testedTelemetryHandler.handleEvent(it, mockWriter)
        }

        // Then
        // if limit would be counted before the sampler, it will be twice less writes
        verify(mockWriter, times(MAX_EVENTS_PER_SESSION_TEST))
            .write(eq(mockEventBatchWriter), any(), eq(EventType.TELEMETRY))
        verifyNoInteractions(mockInternalLogger)
    }

    // endregion

    // region Assertions

    private fun assertDebugEventMatchesRawEvent(
        actual: TelemetryDebugEvent,
        rawEvent: RumRawEvent.SendTelemetry,
        rumContext: RumContext
    ) {
        assertThat(actual)
            .hasDate(rawEvent.eventTime.timestamp + fakeServerOffset)
            .hasSource(TelemetryDebugEvent.Source.ANDROID)
            .hasMessage(rawEvent.message)
            .hasService(TelemetryEventHandler.TELEMETRY_SERVICE_NAME)
            .hasVersion(fakeDatadogContext.sdkVersion)
            .hasApplicationId(rumContext.applicationId)
            .hasSessionId(rumContext.sessionId)
            .hasViewId(rumContext.viewId)
            .hasActionId(rumContext.actionId)
            .hasAdditionalProperties(rawEvent.additionalProperties ?: emptyMap())
            .hasDeviceArchitecture(fakeDeviceArchitecture)
            .hasDeviceBrand(fakeDeviceBrand)
            .hasDeviceModel(fakeDeviceModel)
            .hasOsBuild(fakeOsBuildId)
            .hasOsName(fakeOsName)
            .hasOsVersion(fakeOsVersion)
    }

    private fun assertErrorEventMatchesRawEvent(
        actual: TelemetryErrorEvent,
        rawEvent: RumRawEvent.SendTelemetry,
        rumContext: RumContext
    ) {
        assertThat(actual)
            .hasDate(rawEvent.eventTime.timestamp + fakeServerOffset)
            .hasSource(TelemetryErrorEvent.Source.ANDROID)
            .hasMessage(rawEvent.message)
            .hasService(TelemetryEventHandler.TELEMETRY_SERVICE_NAME)
            .hasVersion(fakeDatadogContext.sdkVersion)
            .hasApplicationId(rumContext.applicationId)
            .hasSessionId(rumContext.sessionId)
            .hasViewId(rumContext.viewId)
            .hasActionId(rumContext.actionId)
            .hasErrorStack(rawEvent.stack)
            .hasErrorKind(rawEvent.kind)
            .hasDeviceArchitecture(fakeDeviceArchitecture)
            .hasDeviceBrand(fakeDeviceBrand)
            .hasDeviceModel(fakeDeviceModel)
            .hasOsBuild(fakeOsBuildId)
            .hasOsName(fakeOsName)
            .hasOsVersion(fakeOsVersion)
            .hasAdditionalProperties(rawEvent.additionalProperties ?: emptyMap())
    }

    private fun assertConfigEventMatchesRawEvent(
        actual: TelemetryConfigurationEvent,
        rawEvent: RumRawEvent.SendTelemetry,
        rumContext: RumContext
    ) {
        assertThat(actual)
            .hasDate(rawEvent.eventTime.timestamp + fakeServerOffset)
            .hasSource(TelemetryConfigurationEvent.Source.ANDROID)
            .hasService(TelemetryEventHandler.TELEMETRY_SERVICE_NAME)
            .hasVersion(fakeDatadogContext.sdkVersion)
            .hasApplicationId(rumContext.applicationId)
            .hasSessionId(rumContext.sessionId)
            .hasViewId(rumContext.viewId)
            .hasActionId(rumContext.actionId)
    }

    private fun assertConfigEventMatchesRawEvent(
        actual: TelemetryConfigurationEvent,
        rawEvent: RumRawEvent.SendTelemetry
    ) {
        assertThat(actual)
            .hasDate(rawEvent.eventTime.timestamp + fakeServerOffset)
            .hasSource(TelemetryConfigurationEvent.Source.ANDROID)
            .hasService(TelemetryEventHandler.TELEMETRY_SERVICE_NAME)
            .hasVersion(fakeDatadogContext.sdkVersion)
    }

    // endregion

    // region Forgeries

    private fun Forge.createRumRawTelemetryEvent(): RumRawEvent.SendTelemetry {
        return anElementFrom(
            createRumRawTelemetryDebugEvent(),
            createRumRawTelemetryErrorEvent(),
            createRumRawTelemetryConfigurationEvent()
        )
    }

    private fun Forge.createRumRawTelemetryDebugEvent(): RumRawEvent.SendTelemetry {
        return RumRawEvent.SendTelemetry(
            TelemetryType.DEBUG,
            aString(),
            null,
            null,
            coreConfiguration = null,
            additionalProperties = aNullable { exhaustiveAttributes() },
            isMetric = aBool()
        )
    }

    private fun Forge.createRumRawTelemetryErrorEvent(): RumRawEvent.SendTelemetry {
        val throwable = aNullable { aThrowable() }
        return RumRawEvent.SendTelemetry(
            TelemetryType.ERROR,
            aString(),
            throwable?.loggableStackTrace(),
            throwable?.javaClass?.canonicalName,
            coreConfiguration = null,
            additionalProperties = aNullable { exhaustiveAttributes() },
            isMetric = aBool()
        )
    }

    private fun Forge.createRumRawTelemetryConfigurationEvent(
        configuration: TelemetryCoreConfiguration? = null
    ): RumRawEvent.SendTelemetry {
        return RumRawEvent.SendTelemetry(
            TelemetryType.CONFIGURATION,
            "",
            null,
            null,
            coreConfiguration = (configuration ?: getForgery()),
            additionalProperties = null,
            isMetric = aBool()
        )
    }

    // endregion

    companion object {

        private val forge = Forge().apply {
            Configurator().configure(this)
        }

        @JvmStatic
        fun tracingConfigurationParameters() = listOf(
            // hasTracer, tracerApiName, tracerApiVersion
            Arguments.of(true, TelemetryEventHandler.TracerApi.OpenTracing, null),
            Arguments.of(
                true,
                TelemetryEventHandler.TracerApi.OpenTelemetry,
                forge.aStringMatching("[0-9]+\\.[0-9]+\\.[0-9]+")
            ),
            Arguments.of(false, null, null)
        )

        private const val MAX_EVENTS_PER_SESSION_TEST = 10
    }
}
