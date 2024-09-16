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
import com.datadog.android.core.sampling.Sampler
import com.datadog.android.internal.telemetry.InternalTelemetryEvent
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
    fun `M create debug event W handleEvent(Log Debug)`(@Forgery fakeLogDebugEvent: InternalTelemetryEvent.Log.Debug) {
        // Given
        val fakeWrappedEvent = RumRawEvent.TelemetryEventWrapper(fakeLogDebugEvent)

        // When
        testedTelemetryHandler.handleEvent(fakeWrappedEvent, mockWriter)

        // Then
        argumentCaptor<TelemetryDebugEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.TELEMETRY))
            assertDebugEventMatchesInternalEvent(
                lastValue,
                fakeLogDebugEvent,
                fakeRumContext,
                fakeWrappedEvent.eventTime.timestamp
            )
        }
    }

    @Test
    fun `M create debug event W handleEvent(Log Debug, no RUM)`(
        @Forgery fakeLogDebugEvent: InternalTelemetryEvent.Log.Debug
    ) {
        // Given
        val fakeWrappedEvent = RumRawEvent.TelemetryEventWrapper(fakeLogDebugEvent)
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
        testedTelemetryHandler.handleEvent(fakeWrappedEvent, mockWriter)

        // Then
        argumentCaptor<TelemetryDebugEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.TELEMETRY))
            assertDebugEventMatchesInternalEvent(
                lastValue,
                fakeLogDebugEvent,
                noRumContext,
                fakeWrappedEvent.eventTime.timestamp
            )
        }
    }

    // endregion

    // region Error Event

    @Test
    fun `M create error event W handleEvent(Log Error)`(@Forgery fakeLogErrorEvent: InternalTelemetryEvent.Log.Error) {
        // Given
        val fakeWrappedEvent = RumRawEvent.TelemetryEventWrapper(fakeLogErrorEvent)
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
        testedTelemetryHandler.handleEvent(fakeWrappedEvent, mockWriter)

        // Then
        argumentCaptor<TelemetryErrorEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.TELEMETRY))
            assertErrorEventMatchesInternalEvent(
                lastValue,
                fakeLogErrorEvent,
                noRumContext,
                fakeWrappedEvent.eventTime.timestamp
            )
        }
    }

    // endregion

    // region Configuration Event

    @Test
    fun `M create config event W handleEvent(Configuration)`(
        @Forgery fakeConfigurationEvent: InternalTelemetryEvent.Configuration
    ) {
        // Given
        val fakeWrappedEvent = RumRawEvent.TelemetryEventWrapper(fakeConfigurationEvent)

        // When
        testedTelemetryHandler.handleEvent(fakeWrappedEvent, mockWriter)

        // Then
        argumentCaptor<TelemetryConfigurationEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.TELEMETRY))
            assertConfigEventMatchesInternalEvent(
                firstValue,
                fakeConfigurationEvent,
                fakeRumContext,
                fakeWrappedEvent.eventTime.timestamp
            )
        }
    }

    @Test
    fun `M create config event W handleEvent() { Configuration, no RUM)`(
        @Forgery fakeConfigurationEvent: InternalTelemetryEvent.Configuration
    ) {
        // Given
        val fakeWrappedEvent = RumRawEvent.TelemetryEventWrapper(fakeConfigurationEvent)
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
        testedTelemetryHandler.handleEvent(fakeWrappedEvent, mockWriter)

        // Then
        argumentCaptor<TelemetryConfigurationEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.TELEMETRY))
            assertConfigEventMatchesInternalEvent(
                firstValue,
                fakeConfigurationEvent,
                noRumContext,
                fakeWrappedEvent.eventTime.timestamp
            )
        }
    }

    @Test
    fun `M create config event W handleEvent() {Configuration, with RUM config }`(
        @Forgery fakeRumConfiguration: RumFeature.Configuration,
        @Forgery fakeConfigurationEvent: InternalTelemetryEvent.Configuration
    ) {
        // Given
        val mockRumFeature = mock<RumFeature>()
        whenever(mockRumFeature.configuration) doReturn fakeRumConfiguration
        whenever(mockRumFeatureScope.unwrap<RumFeature>()) doReturn mockRumFeature

        val configRawEvent = RumRawEvent.TelemetryEventWrapper(fakeConfigurationEvent)

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
            assertConfigEventMatchesInternalEvent(
                firstValue,
                fakeConfigurationEvent,
                fakeRumContext,
                configRawEvent.eventTime.timestamp
            )
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

    @ParameterizedTest
    @MethodSource("tracingConfigurationParameters")
    fun `M create config event W handleEvent() { tracing configuration with tracing settings }`(
        useTracer: Boolean,
        tracerApi: TelemetryEventHandler.TracerApi?,
        tracerApiVersion: String?,
        @Forgery fakeConfiguration: InternalTelemetryEvent.Configuration
    ) {
        // Given
        val configRawEvent = RumRawEvent.TelemetryEventWrapper(fakeConfiguration)
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
            assertConfigEventMatchesInternalEvent(
                firstValue,
                fakeConfiguration,
                fakeRumContext,
                configRawEvent.eventTime.timestamp
            )
            assertThat(firstValue)
                .hasUseTracing(useTracer)
                .hasTracerApi(tracerApi?.name)
                .hasTracerApiVersion(tracerApiVersion)
        }
    }

    @Test
    fun `M create config event W handleEvent() { configuration with interceptor }`(
        @Forgery fakeConfiguration: InternalTelemetryEvent.Configuration,
        forge: Forge
    ) {
        // Given
        val trackNetworkRequests = forge.aBool()
        val configRawEvent = RumRawEvent.TelemetryEventWrapper(fakeConfiguration)

        // When
        if (trackNetworkRequests) {
            testedTelemetryHandler.handleEvent(
                RumRawEvent.TelemetryEventWrapper(InternalTelemetryEvent.InterceptorInstantiated),
                mockWriter
            )
        }
        testedTelemetryHandler.handleEvent(configRawEvent, mockWriter)

        // Then
        argumentCaptor<TelemetryConfigurationEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.TELEMETRY))
            assertConfigEventMatchesInternalEvent(
                firstValue,
                fakeConfiguration,
                fakeRumContext,
                configRawEvent.eventTime.timestamp
            )
            assertThat(firstValue)
                .hasTrackNetworkRequests(trackNetworkRequests)
        }
    }

    @Test
    fun `M create config event W handleEvent() { configuration, no SessionReplay }`(
        @Forgery fakeConfiguration: InternalTelemetryEvent.Configuration
    ) {
        // Given
        val configRawEvent = RumRawEvent.TelemetryEventWrapper(fakeConfiguration)

        // When
        testedTelemetryHandler.handleEvent(configRawEvent, mockWriter)

        // Then
        argumentCaptor<TelemetryConfigurationEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.TELEMETRY))
            assertConfigEventMatchesInternalEvent(firstValue, fakeConfiguration, configRawEvent.eventTime.timestamp)
            assertThat(firstValue).hasSessionReplaySampleRate(null)
            assertThat(firstValue).hasStartRecordingImmediately(null)
            assertThat(firstValue).hasSessionReplayImagePrivacy(null)
            assertThat(firstValue).hasSessionReplayTouchPrivacy(null)
            assertThat(firstValue).hasSessionReplayTextAndInputPrivacy(null)
        }
    }

    @Test
    fun `M create config event W handleEvent() { configuration, with SessionReplay }`(
        @Forgery fakeConfiguration: InternalTelemetryEvent.Configuration,
        forge: Forge
    ) {
        // Given
        val fakeSampleRate = forge.aPositiveLong()
        val fakeSessionReplayImagePrivacy = forge.aString()
        val fakeSessionReplayTouchPrivacy = forge.aString()
        val fakeSessionReplayTextAndInputPrivacy = forge.aString()
        val fakeSessionReplayIsStartImmediately = forge.aBool()
        val fakeSessionReplayContext = mutableMapOf<String, Any?>(
            TelemetryEventHandler.SESSION_REPLAY_START_IMMEDIATE_RECORDING_KEY to
                fakeSessionReplayIsStartImmediately,
            TelemetryEventHandler.SESSION_REPLAY_SAMPLE_RATE_KEY to fakeSampleRate,
            TelemetryEventHandler.SESSION_REPLAY_IMAGE_PRIVACY_KEY to fakeSessionReplayImagePrivacy,
            TelemetryEventHandler.SESSION_REPLAY_TOUCH_PRIVACY_KEY to fakeSessionReplayTouchPrivacy,
            TelemetryEventHandler.SESSION_REPLAY_TEXT_AND_INPUT_PRIVACY_KEY to fakeSessionReplayTextAndInputPrivacy
        )
        whenever(mockSdkCore.getFeatureContext(Feature.SESSION_REPLAY_FEATURE_NAME)) doReturn
            fakeSessionReplayContext
        val configRawEvent = RumRawEvent.TelemetryEventWrapper(fakeConfiguration)

        // When
        testedTelemetryHandler.handleEvent(configRawEvent, mockWriter)

        // Then
        argumentCaptor<TelemetryConfigurationEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.TELEMETRY))
            assertConfigEventMatchesInternalEvent(firstValue, fakeConfiguration, configRawEvent.eventTime.timestamp)
            assertThat(firstValue).hasSessionReplaySampleRate(fakeSampleRate)
            assertThat(firstValue).hasStartRecordingImmediately(fakeSessionReplayIsStartImmediately)
            assertThat(firstValue).hasSessionReplayImagePrivacy(fakeSessionReplayImagePrivacy)
            assertThat(firstValue).hasSessionReplayTouchPrivacy(fakeSessionReplayTouchPrivacy)
            assertThat(firstValue).hasSessionReplayTextAndInputPrivacy(fakeSessionReplayTextAndInputPrivacy)
        }
    }

    @Test
    fun `M create config event W handleEvent(SendTelemetry) { with SessionReplay, bad format }`(
        @Forgery fakeConfiguration: InternalTelemetryEvent.Configuration,
        forge: Forge
    ) {
        // Given
        val fakeSampleRate = forge.aNullable { aString() }
        val fakeSessionReplayIsStartedImmediatley = forge.aNullable { aString() }
        val fakeSessionReplayContext = mutableMapOf<String, Any?>(
            TelemetryEventHandler.SESSION_REPLAY_START_IMMEDIATE_RECORDING_KEY to
                fakeSessionReplayIsStartedImmediatley,
            TelemetryEventHandler.SESSION_REPLAY_SAMPLE_RATE_KEY to fakeSampleRate
        )
        whenever(mockSdkCore.getFeatureContext(Feature.SESSION_REPLAY_FEATURE_NAME)) doReturn
            fakeSessionReplayContext
        val configRawEvent = RumRawEvent.TelemetryEventWrapper(fakeConfiguration)

        // When
        testedTelemetryHandler.handleEvent(configRawEvent, mockWriter)

        // Then
        argumentCaptor<TelemetryConfigurationEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.TELEMETRY))
            assertConfigEventMatchesInternalEvent(firstValue, fakeConfiguration, configRawEvent.eventTime.timestamp)
            assertThat(firstValue).hasSessionReplaySampleRate(null)
            assertThat(firstValue).hasStartRecordingImmediately(null)
            assertThat(firstValue).hasSessionReplayImagePrivacy(null)
            assertThat(firstValue).hasSessionReplayTouchPrivacy(null)
            assertThat(firstValue).hasSessionReplayTextAndInputPrivacy(null)
        }
    }

    // endregion

    // region Sampling

    @Test
    fun `M not write event W handleEvent() { event is not sampled }`(
        @Forgery fakeInternalTelemetryEvent: InternalTelemetryEvent
    ) {
        // Given
        val rawEvent = RumRawEvent.TelemetryEventWrapper(fakeInternalTelemetryEvent)
        whenever(mockSampler.sample()) doReturn false

        // When
        testedTelemetryHandler.handleEvent(rawEvent, mockWriter)

        // Then
        verifyNoInteractions(mockWriter)
    }

    @Test
    fun `M write debug&error event W handleEvent() { log events, configuration sampler returns false }`(
        forge: Forge
    ) {
        // Given
        val logeEvent = forge.anElementFrom(
            forge.getForgery<InternalTelemetryEvent.Log.Error>(),
            forge.getForgery<InternalTelemetryEvent.Log.Debug>()
        )
        val rawEvent = RumRawEvent.TelemetryEventWrapper(logeEvent)
        whenever(mockSampler.sample()) doReturn true
        whenever(mockConfigurationSampler.sample()) doReturn false

        // When
        testedTelemetryHandler.handleEvent(rawEvent, mockWriter)

        // Then
        argumentCaptor<Any> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.TELEMETRY))
            if (logeEvent is InternalTelemetryEvent.Log.Debug) {
                assertThat(lastValue).isInstanceOf(TelemetryDebugEvent::class.java)
            } else {
                assertThat(lastValue).isInstanceOf(TelemetryErrorEvent::class.java)
            }
        }
    }

    @Test
    fun `M not write configuration event W handleEvent() { event is not sampled }`(
        @Forgery fakeConfiguration: InternalTelemetryEvent.Configuration
    ) {
        // Given
        val rawEvent = RumRawEvent.TelemetryEventWrapper(fakeConfiguration)
        whenever(mockSampler.sample()) doReturn true
        whenever(mockConfigurationSampler.sample()) doReturn false

        // When
        testedTelemetryHandler.handleEvent(rawEvent, mockWriter)

        // Then
        verifyNoInteractions(mockWriter)
    }

    @Test
    fun `M not write event W handleEvent(){ seen in the session, not metric }`(
        forge: Forge
    ) {
        // Given
        val internalTelemetryEvent = forge.anElementFrom(
            forge.getForgery<InternalTelemetryEvent.Log.Error>(),
            forge.getForgery<InternalTelemetryEvent.Log.Debug>(),
            forge.getForgery<InternalTelemetryEvent.Configuration>()
        )
        val rawEvent = RumRawEvent.TelemetryEventWrapper(internalTelemetryEvent)
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
                internalTelemetryEvent.identity
            )
        )

        argumentCaptor<Any> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.TELEMETRY))
            when (val capturedValue = lastValue) {
                is TelemetryDebugEvent -> {
                    assertDebugEventMatchesInternalEvent(
                        capturedValue,
                        internalTelemetryEvent as InternalTelemetryEvent.Log.Debug,
                        fakeRumContext,
                        rawEvent.eventTime.timestamp
                    )
                }

                is TelemetryErrorEvent -> {
                    assertErrorEventMatchesInternalEvent(
                        capturedValue,
                        internalTelemetryEvent as InternalTelemetryEvent.Log.Error,
                        fakeRumContext,
                        rawEvent.eventTime.timestamp
                    )
                }

                is TelemetryConfigurationEvent -> {
                    assertConfigEventMatchesInternalEvent(
                        capturedValue,
                        internalTelemetryEvent as InternalTelemetryEvent.Configuration,
                        fakeRumContext,
                        rawEvent.eventTime.timestamp
                    )
                }

                is InternalTelemetryEvent.InterceptorInstantiated -> {
                    assertThat(capturedValue).isEqualTo(InternalTelemetryEvent.InterceptorInstantiated)
                }

                else -> throw IllegalArgumentException(
                    "Unexpected type=${lastValue::class.jvmName} of the captured value."
                )
            }
            verifyNoMoreInteractions(mockWriter)
        }
    }

    @Test
    fun `M write event W handleEvent(){ seen in the session, is metric }`(
        @Forgery fakeMetricEvent: InternalTelemetryEvent.Metric
    ) {
        // Given
        val rawEvent = RumRawEvent.TelemetryEventWrapper(fakeMetricEvent)
        val events = listOf(rawEvent, RumRawEvent.TelemetryEventWrapper(fakeMetricEvent))

        // When
        testedTelemetryHandler.handleEvent(events[0], mockWriter)
        testedTelemetryHandler.handleEvent(events[1], mockWriter)

        // Then
        argumentCaptor<Any> {
            verify(mockWriter, times(2)).write(eq(mockEventBatchWriter), capture(), eq(EventType.TELEMETRY))
            assertDebugEventMatchesMetricInternalEvent(
                firstValue as TelemetryDebugEvent,
                fakeMetricEvent,
                fakeRumContext,
                rawEvent.eventTime.timestamp
            )
            assertDebugEventMatchesMetricInternalEvent(
                secondValue as TelemetryDebugEvent,
                fakeMetricEvent,
                fakeRumContext,
                rawEvent.eventTime.timestamp
            )
        }
    }

    @Test
    fun `M not write events over the limit W handleEvent() { metric event }`(
        forge: Forge
    ) {
        val events = (0..MAX_EVENTS_PER_SESSION_TEST).map { forge.getForgery<InternalTelemetryEvent.Metric>() }

        // When
        events.forEach {
            testedTelemetryHandler.handleEvent(RumRawEvent.TelemetryEventWrapper(it), mockWriter)
        }

        // Then
        mockInternalLogger.verifyLog(
            level = InternalLogger.Level.INFO,
            target = InternalLogger.Target.MAINTAINER,
            message = TelemetryEventHandler.MAX_EVENT_NUMBER_REACHED_MESSAGE
        )
    }

    @Test
    fun `M continue writing events after new session W handleEvent() { metric event }`(forge: Forge) {
        // Given
        // important because non-metric events can only be seen once
        val eventsInOldSession = (0..MAX_EVENTS_PER_SESSION_TEST / 2).map {
            forge.getForgery<InternalTelemetryEvent.Metric>()
        }.map { RumRawEvent.TelemetryEventWrapper(it) }
        val eventsInNewSession = (0..MAX_EVENTS_PER_SESSION_TEST / 2).map {
            forge.getForgery<InternalTelemetryEvent.Metric>()
        }.map { RumRawEvent.TelemetryEventWrapper(it) }

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
    fun `M count the limit only after the sampling W handleEvent()`(forge: Forge) {
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
        ) { forge.getForgery<InternalTelemetryEvent>() }
            // remove unwanted identity collisions
            .groupBy { it.identity }
            .map { RumRawEvent.TelemetryEventWrapper(it.value.first()) }
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

    private fun assertDebugEventMatchesInternalEvent(
        actual: TelemetryDebugEvent,
        internalDebugEvent: InternalTelemetryEvent.Log.Debug,
        rumContext: RumContext,
        time: Long
    ) {
        assertThat(actual)
            .hasDate(time + fakeServerOffset)
            .hasSource(TelemetryDebugEvent.Source.ANDROID)
            .hasMessage(internalDebugEvent.message)
            .hasService(TelemetryEventHandler.TELEMETRY_SERVICE_NAME)
            .hasVersion(fakeDatadogContext.sdkVersion)
            .hasApplicationId(rumContext.applicationId)
            .hasSessionId(rumContext.sessionId)
            .hasViewId(rumContext.viewId)
            .hasActionId(rumContext.actionId)
            .hasAdditionalProperties(internalDebugEvent.additionalProperties ?: emptyMap())
            .hasDeviceArchitecture(fakeDeviceArchitecture)
            .hasDeviceBrand(fakeDeviceBrand)
            .hasDeviceModel(fakeDeviceModel)
            .hasOsBuild(fakeOsBuildId)
            .hasOsName(fakeOsName)
            .hasOsVersion(fakeOsVersion)
    }

    private fun assertDebugEventMatchesMetricInternalEvent(
        actual: TelemetryDebugEvent,
        internalMetricEvent: InternalTelemetryEvent.Metric,
        rumContext: RumContext,
        time: Long
    ) {
        assertThat(actual)
            .hasDate(time + fakeServerOffset)
            .hasSource(TelemetryDebugEvent.Source.ANDROID)
            .hasMessage(internalMetricEvent.message)
            .hasService(TelemetryEventHandler.TELEMETRY_SERVICE_NAME)
            .hasVersion(fakeDatadogContext.sdkVersion)
            .hasApplicationId(rumContext.applicationId)
            .hasSessionId(rumContext.sessionId)
            .hasViewId(rumContext.viewId)
            .hasActionId(rumContext.actionId)
            .hasAdditionalProperties(internalMetricEvent.additionalProperties ?: emptyMap())
            .hasDeviceArchitecture(fakeDeviceArchitecture)
            .hasDeviceBrand(fakeDeviceBrand)
            .hasDeviceModel(fakeDeviceModel)
            .hasOsBuild(fakeOsBuildId)
            .hasOsName(fakeOsName)
            .hasOsVersion(fakeOsVersion)
    }

    private fun assertErrorEventMatchesInternalEvent(
        actual: TelemetryErrorEvent,
        internalErrorEvent: InternalTelemetryEvent.Log.Error,
        rumContext: RumContext,
        time: Long
    ) {
        assertThat(actual)
            .hasDate(time + fakeServerOffset)
            .hasSource(TelemetryErrorEvent.Source.ANDROID)
            .hasMessage(internalErrorEvent.message)
            .hasService(TelemetryEventHandler.TELEMETRY_SERVICE_NAME)
            .hasVersion(fakeDatadogContext.sdkVersion)
            .hasApplicationId(rumContext.applicationId)
            .hasSessionId(rumContext.sessionId)
            .hasViewId(rumContext.viewId)
            .hasActionId(rumContext.actionId)
            .hasErrorStack(internalErrorEvent.stacktrace)
            .hasErrorKind(internalErrorEvent.kind)
            .hasDeviceArchitecture(fakeDeviceArchitecture)
            .hasDeviceBrand(fakeDeviceBrand)
            .hasDeviceModel(fakeDeviceModel)
            .hasOsBuild(fakeOsBuildId)
            .hasOsName(fakeOsName)
            .hasOsVersion(fakeOsVersion)
            .hasAdditionalProperties(internalErrorEvent.additionalProperties ?: emptyMap())
    }

    private fun assertConfigEventMatchesInternalEvent(
        actual: TelemetryConfigurationEvent,
        internalConfigurationEvent: InternalTelemetryEvent.Configuration,
        rumContext: RumContext,
        time: Long
    ) {
        assertThat(actual)
            .hasDate(time + fakeServerOffset)
            .hasSource(TelemetryConfigurationEvent.Source.ANDROID)
            .hasService(TelemetryEventHandler.TELEMETRY_SERVICE_NAME)
            .hasVersion(fakeDatadogContext.sdkVersion)
            .hasApplicationId(rumContext.applicationId)
            .hasSessionId(rumContext.sessionId)
            .hasViewId(rumContext.viewId)
            .hasActionId(rumContext.actionId)
            .hasBatchSize(internalConfigurationEvent.batchSize)
            .hasBatchUploadFrequency(internalConfigurationEvent.batchUploadFrequency)
            .hasBatchProcessingLevel(internalConfigurationEvent.batchProcessingLevel)
            .hasTrackErrors(internalConfigurationEvent.trackErrors)
            .hasUseProxy(internalConfigurationEvent.useProxy)
            .hasUseLocalEncryption(internalConfigurationEvent.useLocalEncryption)
    }

    private fun assertConfigEventMatchesInternalEvent(
        actual: TelemetryConfigurationEvent,
        internalConfigurationEvent: InternalTelemetryEvent.Configuration,
        time: Long
    ) {
        assertThat(actual)
            .hasDate(time + fakeServerOffset)
            .hasSource(TelemetryConfigurationEvent.Source.ANDROID)
            .hasService(TelemetryEventHandler.TELEMETRY_SERVICE_NAME)
            .hasVersion(fakeDatadogContext.sdkVersion)
            .hasBatchSize(internalConfigurationEvent.batchSize)
            .hasBatchUploadFrequency(internalConfigurationEvent.batchUploadFrequency)
            .hasBatchProcessingLevel(internalConfigurationEvent.batchProcessingLevel)
            .hasTrackErrors(internalConfigurationEvent.trackErrors)
            .hasUseProxy(internalConfigurationEvent.useProxy)
            .hasUseLocalEncryption(internalConfigurationEvent.useLocalEncryption)
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
