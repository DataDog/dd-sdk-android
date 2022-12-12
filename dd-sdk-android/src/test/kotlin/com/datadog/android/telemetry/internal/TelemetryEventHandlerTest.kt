/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.telemetry.internal

import android.util.Log
import com.datadog.android.core.configuration.BatchSize
import com.datadog.android.core.configuration.Configuration
import com.datadog.android.core.configuration.UploadFrequency
import com.datadog.android.core.configuration.VitalsUpdateFrequency
import com.datadog.android.core.internal.sampling.Sampler
import com.datadog.android.core.internal.utils.loggableStackTrace
import com.datadog.android.rum.internal.RumFeature
import com.datadog.android.rum.internal.domain.RumContext
import com.datadog.android.rum.internal.domain.scope.RumRawEvent
import com.datadog.android.rum.tracking.ActivityViewTrackingStrategy
import com.datadog.android.rum.tracking.FragmentViewTrackingStrategy
import com.datadog.android.rum.tracking.MixedViewTrackingStrategy
import com.datadog.android.rum.tracking.NavigationViewTrackingStrategy
import com.datadog.android.rum.tracking.ViewTrackingStrategy
import com.datadog.android.telemetry.assertj.TelemetryConfigurationEventAssert.Companion.assertThat
import com.datadog.android.telemetry.assertj.TelemetryDebugEventAssert.Companion.assertThat
import com.datadog.android.telemetry.assertj.TelemetryErrorEventAssert.Companion.assertThat
import com.datadog.android.telemetry.model.TelemetryConfigurationEvent
import com.datadog.android.telemetry.model.TelemetryDebugEvent
import com.datadog.android.telemetry.model.TelemetryErrorEvent
import com.datadog.android.utils.config.LoggerTestConfiguration
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.v2.api.EventBatchWriter
import com.datadog.android.v2.api.FeatureScope
import com.datadog.android.v2.api.SdkCore
import com.datadog.android.v2.api.context.DatadogContext
import com.datadog.android.v2.core.internal.storage.DataWriter
import com.datadog.tools.unit.annotations.TestConfigurationsProvider
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.datadog.tools.unit.extensions.config.TestConfiguration
import com.datadog.tools.unit.forge.aThrowable
import com.datadog.tools.unit.setStaticValue
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.atLeastOnce
import com.nhaarman.mockitokotlin2.doAnswer
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyNoMoreInteractions
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.BoolForgery
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import io.opentracing.Tracer
import io.opentracing.util.GlobalTracer
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.data.Percentage
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness
import java.util.Locale
import kotlin.reflect.jvm.jvmName
import com.datadog.android.telemetry.model.TelemetryConfigurationEvent.ViewTrackingStrategy as VTS

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(TestConfigurationExtension::class)
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
    lateinit var mockSdkCore: SdkCore

    @Mock
    lateinit var mockRumFeatureScope: FeatureScope

    @Mock
    lateinit var mockEventBatchWriter: EventBatchWriter

    @Forgery
    lateinit var fakeDatadogContext: DatadogContext

    @Forgery
    lateinit var fakeRumContext: RumContext

    private var fakeServerOffset: Long = 0L

    @BeforeEach
    fun `set up`(forge: Forge) {
        fakeServerOffset = forge.aLong(-50000, 50000)

        fakeDatadogContext = fakeDatadogContext.copy(
            source = "android",
            featuresContext = fakeDatadogContext.featuresContext.toMutableMap().apply {
                put(
                    RumFeature.RUM_FEATURE_NAME,
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
            )
        )

        whenever(mockSampler.sample()) doReturn true

        whenever(
            mockSdkCore.getFeature(RumFeature.RUM_FEATURE_NAME)
        ) doReturn mockRumFeatureScope
        whenever(mockRumFeatureScope.withWriteContext(any())) doAnswer {
            val callback = it.getArgument<(DatadogContext, EventBatchWriter) -> Unit>(0)
            callback.invoke(fakeDatadogContext, mockEventBatchWriter)
        }

        testedTelemetryHandler =
            TelemetryEventHandler(
                mockSdkCore,
                mockSampler,
                MAX_EVENTS_PER_SESSION_TEST
            )
    }

    @AfterEach
    fun `tear down`() {
        GlobalTracer::class.java.setStaticValue("isRegistered", false)
    }

    // region Debug Event

    @Test
    fun `𝕄 create debug event 𝕎 handleEvent(SendTelemetry) { debug event status }`(forge: Forge) {
        // Given
        val debugRawEvent = forge.createRumRawTelemetryDebugEvent()

        // When
        testedTelemetryHandler.handleEvent(debugRawEvent, mockWriter)

        // Then
        argumentCaptor<TelemetryDebugEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture())
            assertDebugEventMatchesRawEvent(lastValue, debugRawEvent, fakeRumContext)
        }
    }

    @Test
    fun `𝕄 create debug event 𝕎 handleEvent(SendTelemetry) { debug event status, no RUM }`(forge: Forge) {
        // Given
        val debugRawEvent = forge.createRumRawTelemetryDebugEvent()
        fakeDatadogContext = fakeDatadogContext.copy(
            featuresContext = fakeDatadogContext.featuresContext.toMutableMap().apply {
                remove(RumFeature.RUM_FEATURE_NAME)
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
            verify(mockWriter).write(eq(mockEventBatchWriter), capture())
            assertDebugEventMatchesRawEvent(lastValue, debugRawEvent, noRumContext)
        }
    }

    // endregion

    // region Error Event

    @Test
    fun `𝕄 create error event 𝕎 handleEvent(SendTelemetry) { error event status }`(forge: Forge) {
        // Given
        val errorRawEvent = forge.createRumRawTelemetryErrorEvent()

        // When
        testedTelemetryHandler.handleEvent(errorRawEvent, mockWriter)

        // Then
        argumentCaptor<TelemetryErrorEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture())
            assertErrorEventMatchesRawEvent(lastValue, errorRawEvent, fakeRumContext)
        }
    }

    @Test
    fun `𝕄 create error event 𝕎 handleEvent(SendTelemetry) { error event status, no RUM }`(forge: Forge) {
        // Given
        val errorRawEvent = forge.createRumRawTelemetryErrorEvent()
        fakeDatadogContext = fakeDatadogContext.copy(
            featuresContext = fakeDatadogContext.featuresContext.toMutableMap().apply {
                remove(RumFeature.RUM_FEATURE_NAME)
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
            verify(mockWriter).write(eq(mockEventBatchWriter), capture())
            assertErrorEventMatchesRawEvent(lastValue, errorRawEvent, noRumContext)
        }
    }

    // endregion

    // region Configuration Event

    @Test
    fun `𝕄 create config event 𝕎 handleEvent(SendTelemetry) { configuration }`(forge: Forge) {
        // Given
        val configRawEvent = forge.createRumRawTelemetryConfigurationEvent()

        // When
        testedTelemetryHandler.handleEvent(configRawEvent, mockWriter)

        // Then
        argumentCaptor<TelemetryConfigurationEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture())
            assertConfigEventMatchesRawEvent(firstValue, configRawEvent, fakeRumContext)
        }
    }

    @Test
    fun `𝕄 create config event 𝕎 handleEvent(SendTelemetry) { configuration, no RUM }`(forge: Forge) {
        // Given
        val configRawEvent = forge.createRumRawTelemetryConfigurationEvent()
        fakeDatadogContext = fakeDatadogContext.copy(
            featuresContext = fakeDatadogContext.featuresContext.toMutableMap().apply {
                remove(RumFeature.RUM_FEATURE_NAME)
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
            verify(mockWriter).write(eq(mockEventBatchWriter), capture())
            assertConfigEventMatchesRawEvent(firstValue, configRawEvent, noRumContext)
        }
    }

    @Test
    fun `𝕄 create config event 𝕎 handleEvent(SendTelemetry) { configuration with sampling rates}`(
        forge: Forge
    ) {
        // Given
        val sessionSampleRate = forge.aLong(0L, 100L)
        val telemetrySamplingRate = forge.aLong(0L, 100L)
        val configuration = Configuration.Builder(
            logsEnabled = true,
            tracesEnabled = true,
            crashReportsEnabled = true,
            rumEnabled = true
        )
            .sampleRumSessions(sessionSampleRate.toFloat())
            .sampleTelemetry(telemetrySamplingRate.toFloat())
            .build()
        val configRawEvent = forge.createRumRawTelemetryConfigurationEvent(configuration)

        // When
        testedTelemetryHandler.handleEvent(configRawEvent, mockWriter)

        // Then
        argumentCaptor<TelemetryConfigurationEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture())
            assertConfigEventMatchesRawEvent(firstValue, configRawEvent, fakeRumContext)
            assertThat(firstValue)
                .hasSessionSampleRate(sessionSampleRate)
                .hasTelemetrySampleRate(telemetrySamplingRate)
        }
    }

    @Test
    fun `𝕄 create config event 𝕎 handleEvent(SendTelemetry) { configuration with proxy}`(
        forge: Forge
    ) {
        // Given
        val useProxy = forge.aBool()
        val configuration = Configuration.Builder(
            logsEnabled = true,
            tracesEnabled = true,
            crashReportsEnabled = true,
            rumEnabled = true
        ).apply {
            if (useProxy) {
                setProxy(mock(), forge.aNullable { mock() })
            }
        }.build()
        val configRawEvent = forge.createRumRawTelemetryConfigurationEvent(configuration)

        // When
        testedTelemetryHandler.handleEvent(configRawEvent, mockWriter)

        // Then
        argumentCaptor<TelemetryConfigurationEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture())
            assertConfigEventMatchesRawEvent(firstValue, configRawEvent, fakeRumContext)
            assertThat(firstValue)
                .hasUseProxy(useProxy)
        }
    }

    @Test
    fun `𝕄 create config event 𝕎 handleEvent(SendTelemetry) { configuration with local encryption}`(
        forge: Forge
    ) {
        // Given
        val useLocalEncryption = forge.aBool()
        val configuration = Configuration.Builder(
            logsEnabled = true,
            tracesEnabled = true,
            crashReportsEnabled = true,
            rumEnabled = true
        ).apply {
            if (useLocalEncryption) {
                setEncryption(mock())
            }
        }.build()
        val configRawEvent = forge.createRumRawTelemetryConfigurationEvent(configuration)

        // When
        testedTelemetryHandler.handleEvent(configRawEvent, mockWriter)

        // Then
        argumentCaptor<TelemetryConfigurationEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture())
            assertConfigEventMatchesRawEvent(firstValue, configRawEvent, fakeRumContext)
            assertThat(firstValue)
                .hasUseLocalEncryption(useLocalEncryption)
        }
    }

    @Test
    fun `𝕄 create config event 𝕎 handleEvent(SendTelemetry) { configuration with auto-instrumentation }`(
        forge: Forge
    ) {
        // Given
        val vts = forge.aValueFrom(VTS::class.java)
        val trackErrors = forge.aBool()
        val trackFrustrations = forge.aBool()
        val trackBackgroundEvents = forge.aBool()
        val trackLongTasks = forge.aBool()
        val configuration = Configuration.Builder(
            logsEnabled = true,
            tracesEnabled = true,
            crashReportsEnabled = trackErrors,
            rumEnabled = true
        )
            .trackFrustrations(trackFrustrations)
            .useViewTrackingStrategy(forge.aViewTrackingStrategy(vts))
            .trackBackgroundRumEvents(trackBackgroundEvents)
            .trackLongTasks(if (trackLongTasks) forge.aPositiveLong() else forge.aNegativeLong())
            .build()
        val configRawEvent = forge.createRumRawTelemetryConfigurationEvent(configuration)

        // When
        testedTelemetryHandler.handleEvent(configRawEvent, mockWriter)

        // Then
        argumentCaptor<TelemetryConfigurationEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture())
            assertConfigEventMatchesRawEvent(firstValue, configRawEvent, fakeRumContext)
            assertThat(firstValue)
                .hasTrackErrors(trackErrors)
                .hasTrackLongTasks(trackLongTasks)
                .hasTrackFrustrations(trackFrustrations)
                .hasViewTrackingStrategy(vts)
                .hasTrackBackgroundEvents(trackBackgroundEvents)
        }
    }

    @Test
    fun `𝕄 create config event 𝕎 handleEvent(SendTelemetry) { configuration with rum settings }`(
        forge: Forge
    ) {
        // Given
        val vitalsUpdateFrequency = forge.aValueFrom(VitalsUpdateFrequency::class.java)
        val batchSize = forge.aValueFrom(BatchSize::class.java)
        val uploadFrequency = forge.aValueFrom(UploadFrequency::class.java)
        val configuration = Configuration.Builder(
            logsEnabled = true,
            tracesEnabled = true,
            crashReportsEnabled = true,
            rumEnabled = true
        )
            .setBatchSize(batchSize)
            .setUploadFrequency(uploadFrequency)
            .setVitalsUpdateFrequency(vitalsUpdateFrequency)
            .build()
        val configRawEvent = forge.createRumRawTelemetryConfigurationEvent(configuration)

        // When
        testedTelemetryHandler.handleEvent(configRawEvent, mockWriter)

        // Then
        argumentCaptor<TelemetryConfigurationEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture())
            assertConfigEventMatchesRawEvent(firstValue, configRawEvent, fakeRumContext)
            assertThat(firstValue)
                .hasBatchSize(batchSize.windowDurationMs)
                .hasBatchUploadFrequency(uploadFrequency.baseStepMs)
                .hasMobileVitalsUpdatePeriod(vitalsUpdateFrequency.periodInMs)
        }
    }

    @Test
    fun `𝕄 create config event 𝕎 handleEvent(SendTelemetry) { configuration with tracing settings }`(
        @BoolForgery useTracing: Boolean,
        forge: Forge
    ) {
        // Given
        val configuration = Configuration.Builder(
            logsEnabled = true,
            tracesEnabled = useTracing || forge.aBool(),
            crashReportsEnabled = true,
            rumEnabled = true
        ).build()
        val configRawEvent = forge.createRumRawTelemetryConfigurationEvent(configuration)
        if (useTracing) {
            GlobalTracer.registerIfAbsent(mock<Tracer>())
        }

        // When
        testedTelemetryHandler.handleEvent(configRawEvent, mockWriter)

        // Then
        argumentCaptor<TelemetryConfigurationEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture())
            assertConfigEventMatchesRawEvent(firstValue, configRawEvent, fakeRumContext)
            assertThat(firstValue)
                .hasUseTracing(useTracing)
        }
    }

    @Test
    fun `𝕄 create config event 𝕎 handleEvent(SendTelemetry) { configuration with interceptor }`(
        forge: Forge
    ) {
        // Given
        val trackNetworkRequests = forge.aBool()
        val configuration = Configuration.Builder(
            logsEnabled = true,
            tracesEnabled = true,
            crashReportsEnabled = true,
            rumEnabled = true
        ).build()
        val configRawEvent = forge.createRumRawTelemetryConfigurationEvent(configuration)

        // When
        if (trackNetworkRequests) {
            testedTelemetryHandler.handleEvent(
                RumRawEvent.SendTelemetry(TelemetryType.INTERCEPTOR_SETUP, "", null, null, null),
                mockWriter
            )
        }
        testedTelemetryHandler.handleEvent(configRawEvent, mockWriter)

        // Then
        argumentCaptor<TelemetryConfigurationEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture())
            assertConfigEventMatchesRawEvent(firstValue, configRawEvent, fakeRumContext)
            assertThat(firstValue)
                .hasTrackNetworkRequests(trackNetworkRequests)
        }
    }

    // endregion

    // region Sampling

    @Test
    fun `𝕄 not write event 𝕎 handleEvent(SendTelemetry) { event is not sampled }`(forge: Forge) {
        // Given
        val rawEvent = forge.createRumRawTelemetryEvent()
        whenever(mockSampler.sample()) doReturn false

        // When
        testedTelemetryHandler.handleEvent(rawEvent, mockWriter)

        // Then
        verifyZeroInteractions(mockWriter)
    }

    @Test
    fun `𝕄 not write event 𝕎 handleEvent(SendTelemetry) { seen in the session }`(forge: Forge) {
        // Given
        val rawEvent = forge.createRumRawTelemetryEvent()
        val anotherEvent = rawEvent.copy()

        // When
        testedTelemetryHandler.handleEvent(rawEvent, mockWriter)
        testedTelemetryHandler.handleEvent(anotherEvent, mockWriter)

        // Then
        verify(logger.mockSdkLogHandler)
            .handleLog(
                Log.INFO,
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
            verify(mockWriter).write(eq(mockEventBatchWriter), capture())
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
    fun `𝕄 not write events over the limit 𝕎 handleEvent(SendTelemetry)`(forge: Forge) {
        // Given
        val events = forge.aList(
            size = MAX_EVENTS_PER_SESSION_TEST * 5
        ) { createRumRawTelemetryEvent() }
            // remove unwanted identity collisions
            .groupBy { it.identity }.map { it.value.first() }
        val extraNumber = events.size - MAX_EVENTS_PER_SESSION_TEST

        val expectedInvocations = MAX_EVENTS_PER_SESSION_TEST

        // When
        events.forEach {
            testedTelemetryHandler.handleEvent(it, mockWriter)
        }

        // Then
        verify(logger.mockSdkLogHandler, times(extraNumber))
            .handleLog(
                Log.INFO,
                TelemetryEventHandler.MAX_EVENT_NUMBER_REACHED_MESSAGE
            )

        argumentCaptor<Any> {
            verify(mockWriter, times(expectedInvocations))
                .write(eq(mockEventBatchWriter), capture())
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
    fun `𝕄 continue writing events after new session 𝕎 handleEvent(SendTelemetry)`(forge: Forge) {
        // Given
        val eventsInOldSession = forge.aList(
            size = forge.anInt(MAX_EVENTS_PER_SESSION_TEST * 2, MAX_EVENTS_PER_SESSION_TEST * 4)
        ) { createRumRawTelemetryEvent() }
            // remove unwanted identity collisions
            .groupBy { it.identity }.map { it.value.first() }
        val extraNumber = eventsInOldSession.size - MAX_EVENTS_PER_SESSION_TEST

        val eventsInNewSession = forge.aList(
            size = forge.anInt(1, MAX_EVENTS_PER_SESSION_TEST)
        ) { createRumRawTelemetryEvent() }
            // remove unwanted identity collisions
            .groupBy { it.identity }.map { it.value.first() }

        val expectedEvents = eventsInOldSession
            .take(MAX_EVENTS_PER_SESSION_TEST) + eventsInNewSession
        val expectedInvocations = expectedEvents.size

        // When
        eventsInOldSession.forEach {
            testedTelemetryHandler.handleEvent(it, mockWriter)
        }
        testedTelemetryHandler.onSessionStarted(forge.aString(), forge.aBool())
        eventsInNewSession.forEach {
            testedTelemetryHandler.handleEvent(it, mockWriter)
        }

        // Then
        verify(logger.mockSdkLogHandler, times(extraNumber))
            .handleLog(
                Log.INFO,
                TelemetryEventHandler.MAX_EVENT_NUMBER_REACHED_MESSAGE
            )

        argumentCaptor<Any> {
            verify(mockWriter, times(expectedInvocations))
                .write(eq(mockEventBatchWriter), capture())
            allValues.withIndex().forEach {
                when (val capturedValue = it.value) {
                    is TelemetryDebugEvent -> {
                        assertDebugEventMatchesRawEvent(
                            capturedValue,
                            expectedEvents[it.index],
                            fakeRumContext
                        )
                    }
                    is TelemetryErrorEvent -> {
                        assertErrorEventMatchesRawEvent(
                            capturedValue,
                            expectedEvents[it.index],
                            fakeRumContext
                        )
                    }
                    is TelemetryConfigurationEvent -> {
                        assertConfigEventMatchesRawEvent(
                            capturedValue,
                            expectedEvents[it.index],
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
    fun `𝕄 count the limit only after the sampling 𝕎 handleEvent(SendTelemetry)`(forge: Forge) {
        // Given
        whenever(mockSampler.sample()) doAnswer { forge.aBool() }
        val events = forge.aList(
            size = MAX_EVENTS_PER_SESSION_TEST * 5
        ) { createRumRawTelemetryEvent() }
            // remove unwanted identity collisions
            .groupBy { it.identity }.map { it.value.first() }
            .take(MAX_EVENTS_PER_SESSION_TEST)
        val repeats = 10
        val expectedWrites = MAX_EVENTS_PER_SESSION_TEST * repeats / 2

        // When
        repeat(repeats) {
            testedTelemetryHandler.onSessionStarted(forge.aString(), false)
            events.forEach {
                testedTelemetryHandler.handleEvent(it, mockWriter)
            }
        }

        // Then
        argumentCaptor<Any> {
            verify(mockWriter, atLeastOnce())
                .write(eq(mockEventBatchWriter), capture())
            assertThat(allValues.size).isCloseTo(expectedWrites, Percentage.withPercentage(25.0))
        }
        verifyZeroInteractions(logger.mockSdkLogHandler)
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

    // endregion

    // region Forgeries

    private fun Forge.aViewTrackingStrategy(vts: VTS): ViewTrackingStrategy {
        return when (vts) {
            VTS.ACTIVITYVIEWTRACKINGSTRATEGY -> ActivityViewTrackingStrategy(
                trackExtras = aBool(),
                componentPredicate = mock()
            )
            VTS.FRAGMENTVIEWTRACKINGSTRATEGY -> FragmentViewTrackingStrategy(
                trackArguments = aBool(),
                supportFragmentComponentPredicate = mock(),
                defaultFragmentComponentPredicate = mock()
            )
            VTS.MIXEDVIEWTRACKINGSTRATEGY -> MixedViewTrackingStrategy(
                trackExtras = aBool(),
                componentPredicate = mock(),
                supportFragmentComponentPredicate = mock(),
                defaultFragmentComponentPredicate = mock()
            )
            VTS.NAVIGATIONVIEWTRACKINGSTRATEGY -> NavigationViewTrackingStrategy(
                navigationViewId = anInt(),
                trackArguments = aBool(),
                componentPredicate = mock()
            )
        }
    }

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
            null
        )
    }

    private fun Forge.createRumRawTelemetryErrorEvent(): RumRawEvent.SendTelemetry {
        val throwable = aNullable { aThrowable() }
        return RumRawEvent.SendTelemetry(
            TelemetryType.ERROR,
            aString(),
            throwable?.loggableStackTrace(),
            throwable?.javaClass?.canonicalName,
            null
        )
    }

    private fun Forge.createRumRawTelemetryConfigurationEvent(
        configuration: Configuration? = null
    ): RumRawEvent.SendTelemetry {
        return RumRawEvent.SendTelemetry(
            TelemetryType.CONFIGURATION,
            "",
            null,
            null,
            configuration ?: getForgery()
        )
    }

    // endregion

    companion object {

        private const val MAX_EVENTS_PER_SESSION_TEST = 10

        val logger = LoggerTestConfiguration()

        @TestConfigurationsProvider
        @JvmStatic
        fun getTestConfigurations(): List<TestConfiguration> {
            return listOf(logger)
        }
    }
}
