/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.telemetry.internal

import android.util.Log
import com.datadog.android.core.internal.sampling.Sampler
import com.datadog.android.core.internal.time.TimeProvider
import com.datadog.android.core.internal.utils.loggableStackTrace
import com.datadog.android.rum.GlobalRum
import com.datadog.android.rum.internal.RumFeature
import com.datadog.android.rum.internal.domain.event.RumEventSourceProvider
import com.datadog.android.rum.internal.domain.scope.RumRawEvent
import com.datadog.android.telemetry.assertj.TelemetryDebugEventAssert
import com.datadog.android.telemetry.assertj.TelemetryErrorEventAssert
import com.datadog.android.telemetry.model.TelemetryDebugEvent
import com.datadog.android.telemetry.model.TelemetryErrorEvent
import com.datadog.android.utils.config.GlobalRumMonitorTestConfiguration
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
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.atLeastOnce
import com.nhaarman.mockitokotlin2.doAnswer
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyNoMoreInteractions
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.data.Percentage
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
    lateinit var mockTimeProvider: TimeProvider

    @Mock
    lateinit var mockWriter: DataWriter<Any>

    @Mock
    lateinit var mockSourceProvider: RumEventSourceProvider

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

    private var fakeServerOffset: Long = 0L

    @BeforeEach
    fun `set up`(forge: Forge) {
        fakeServerOffset = forge.aLong(-50000, 50000)

        whenever(mockTimeProvider.getServerOffsetMillis()) doReturn fakeServerOffset

        whenever(mockSourceProvider.telemetryDebugEventSource) doReturn
            TelemetryDebugEvent.Source.ANDROID
        whenever(mockSourceProvider.telemetryErrorEventSource) doReturn
            TelemetryErrorEvent.Source.ANDROID

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
                mockSourceProvider,
                mockTimeProvider,
                mockSampler
            )
    }

    @Test
    fun `𝕄 create debug event 𝕎 handleEvent(SendTelemetry) { debug event status }`(forge: Forge) {
        // Given
        val debugRawEvent = forge.createRumRawTelemetryDebugEvent()

        val rumContext = GlobalRum.getRumContext()

        // When
        testedTelemetryHandler.handleEvent(debugRawEvent, mockWriter)

        // Then
        argumentCaptor<TelemetryDebugEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture())
            TelemetryDebugEventAssert.assertThat(lastValue).apply {
                hasDate(debugRawEvent.eventTime.timestamp + fakeServerOffset)
                hasSource(TelemetryDebugEvent.Source.ANDROID)
                hasMessage(debugRawEvent.message)
                hasService(TelemetryEventHandler.TELEMETRY_SERVICE_NAME)
                hasVersion(fakeDatadogContext.sdkVersion)
                hasApplicationId(rumContext.applicationId)
                hasSessionId(rumContext.sessionId)
                hasViewId(rumContext.viewId)
                hasActionId(rumContext.actionId)
            }
        }
    }

    @Test
    fun `𝕄 create error event 𝕎 handleEvent(SendTelemetry) { error event status }`(forge: Forge) {
        // Given
        val errorRawEvent = forge.createRumRawTelemetryErrorEvent()

        val rumContext = GlobalRum.getRumContext()

        // When
        testedTelemetryHandler.handleEvent(errorRawEvent, mockWriter)

        // Then
        argumentCaptor<TelemetryErrorEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture())
            TelemetryErrorEventAssert.assertThat(lastValue).apply {
                hasDate(errorRawEvent.eventTime.timestamp + fakeServerOffset)
                hasSource(TelemetryErrorEvent.Source.ANDROID)
                hasMessage(errorRawEvent.message)
                hasService(TelemetryEventHandler.TELEMETRY_SERVICE_NAME)
                hasVersion(fakeDatadogContext.sdkVersion)
                hasApplicationId(rumContext.applicationId)
                hasSessionId(rumContext.sessionId)
                hasViewId(rumContext.viewId)
                hasActionId(rumContext.actionId)
                hasErrorStack(errorRawEvent.stack)
                hasErrorKind(errorRawEvent.kind)
            }
        }
    }

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

        val rumContext = GlobalRum.getRumContext()

        // When
        testedTelemetryHandler.handleEvent(rawEvent, mockWriter)
        testedTelemetryHandler.handleEvent(anotherEvent, mockWriter)

        // Then
        verify(logger.mockSdkLogHandler)
            .handleLog(
                Log.INFO,
                TelemetryEventHandler.ALREADY_SEEN_EVENT_MESSAGE.format(
                    Locale.US,
                    TelemetryEventHandler.EventIdentity(
                        rawEvent.message,
                        rawEvent.kind
                    )
                )
            )

        argumentCaptor<Any> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture())
            when (val capturedValue = lastValue) {
                is TelemetryDebugEvent -> {
                    TelemetryDebugEventAssert.assertThat(capturedValue).apply {
                        hasDate(rawEvent.eventTime.timestamp + fakeServerOffset)
                        hasSource(TelemetryDebugEvent.Source.ANDROID)
                        hasMessage(rawEvent.message)
                        hasService(TelemetryEventHandler.TELEMETRY_SERVICE_NAME)
                        hasVersion(fakeDatadogContext.sdkVersion)
                        hasApplicationId(rumContext.applicationId)
                        hasSessionId(rumContext.sessionId)
                        hasViewId(rumContext.viewId)
                        hasActionId(rumContext.actionId)
                    }
                }
                is TelemetryErrorEvent -> {
                    TelemetryErrorEventAssert.assertThat(capturedValue).apply {
                        hasDate(rawEvent.eventTime.timestamp + fakeServerOffset)
                        hasSource(TelemetryErrorEvent.Source.ANDROID)
                        hasMessage(rawEvent.message)
                        hasService(TelemetryEventHandler.TELEMETRY_SERVICE_NAME)
                        hasVersion(fakeDatadogContext.sdkVersion)
                        hasApplicationId(rumContext.applicationId)
                        hasSessionId(rumContext.sessionId)
                        hasViewId(rumContext.viewId)
                        hasActionId(rumContext.actionId)
                        hasErrorStack(rawEvent.stack)
                        hasErrorKind(rawEvent.kind)
                    }
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
        val extraNumber = forge.aSmallInt()
        val events = forge.aList(
            size = TelemetryEventHandler.MAX_EVENTS_PER_SESSION + extraNumber
        ) {
            createRumRawTelemetryEvent()
        }

        val expectedInvocations = TelemetryEventHandler.MAX_EVENTS_PER_SESSION

        val rumContext = GlobalRum.getRumContext()

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
                        TelemetryDebugEventAssert.assertThat(capturedValue).apply {
                            hasDate(events[it.index].eventTime.timestamp + fakeServerOffset)
                            hasSource(TelemetryDebugEvent.Source.ANDROID)
                            hasMessage(events[it.index].message)
                            hasService(TelemetryEventHandler.TELEMETRY_SERVICE_NAME)
                            hasVersion(fakeDatadogContext.sdkVersion)
                            hasApplicationId(rumContext.applicationId)
                            hasSessionId(rumContext.sessionId)
                            hasViewId(rumContext.viewId)
                            hasActionId(rumContext.actionId)
                        }
                    }
                    is TelemetryErrorEvent -> {
                        TelemetryErrorEventAssert.assertThat(capturedValue).apply {
                            hasDate(events[it.index].eventTime.timestamp + fakeServerOffset)
                            hasSource(TelemetryErrorEvent.Source.ANDROID)
                            hasMessage(events[it.index].message)
                            hasService(TelemetryEventHandler.TELEMETRY_SERVICE_NAME)
                            hasVersion(fakeDatadogContext.sdkVersion)
                            hasApplicationId(rumContext.applicationId)
                            hasSessionId(rumContext.sessionId)
                            hasViewId(rumContext.viewId)
                            hasActionId(rumContext.actionId)
                            hasErrorStack(events[it.index].stack)
                            hasErrorKind(events[it.index].kind)
                        }
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
        val extraNumber = forge.aSmallInt()
        val eventsInOldSession = forge.aList(
            size = TelemetryEventHandler.MAX_EVENTS_PER_SESSION + extraNumber
        ) {
            createRumRawTelemetryEvent()
        }

        val eventsInNewSessionNumber = forge.aTinyInt()
        val eventsInNewSession = forge.aList(
            size = eventsInNewSessionNumber
        ) {
            createRumRawTelemetryEvent()
        }

        val expectedInvocations = TelemetryEventHandler.MAX_EVENTS_PER_SESSION +
            eventsInNewSessionNumber
        val expectedEvents = eventsInOldSession
            .take(TelemetryEventHandler.MAX_EVENTS_PER_SESSION) + eventsInNewSession

        val rumContext = GlobalRum.getRumContext()

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
                        TelemetryDebugEventAssert.assertThat(capturedValue).apply {
                            hasDate(expectedEvents[it.index].eventTime.timestamp + fakeServerOffset)
                            hasSource(TelemetryDebugEvent.Source.ANDROID)
                            hasMessage(expectedEvents[it.index].message)
                            hasService(TelemetryEventHandler.TELEMETRY_SERVICE_NAME)
                            hasVersion(fakeDatadogContext.sdkVersion)
                            hasApplicationId(rumContext.applicationId)
                            hasSessionId(rumContext.sessionId)
                            hasViewId(rumContext.viewId)
                            hasActionId(rumContext.actionId)
                        }
                    }
                    is TelemetryErrorEvent -> {
                        TelemetryErrorEventAssert.assertThat(capturedValue).apply {
                            hasDate(expectedEvents[it.index].eventTime.timestamp + fakeServerOffset)
                            hasSource(TelemetryErrorEvent.Source.ANDROID)
                            hasMessage(expectedEvents[it.index].message)
                            hasService(TelemetryEventHandler.TELEMETRY_SERVICE_NAME)
                            hasVersion(fakeDatadogContext.sdkVersion)
                            hasApplicationId(rumContext.applicationId)
                            hasSessionId(rumContext.sessionId)
                            hasViewId(rumContext.viewId)
                            hasActionId(rumContext.actionId)
                            hasErrorStack(expectedEvents[it.index].stack)
                            hasErrorKind(
                                expectedEvents[it.index].kind
                            )
                        }
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

        val extraNumber = forge.aTinyInt()
        val events = forge.aList(
            size = TelemetryEventHandler.MAX_EVENTS_PER_SESSION + extraNumber
        ) {
            createRumRawTelemetryEvent()
        }

        val expectedWrites = events.size / 2

        // When
        events.forEach {
            testedTelemetryHandler.handleEvent(it, mockWriter)
        }

        // Then
        verifyZeroInteractions(logger.mockSdkLogHandler)

        argumentCaptor<Any> {
            verify(mockWriter, atLeastOnce())
                .write(eq(mockEventBatchWriter), capture())
            assertThat(allValues.size).isCloseTo(expectedWrites, Percentage.withPercentage(25.0))
        }
    }

    // region private

    private fun Forge.createRumRawTelemetryEvent(): RumRawEvent.SendTelemetry {
        return anElementFrom(
            createRumRawTelemetryDebugEvent(),
            createRumRawTelemetryErrorEvent()
        )
    }

    private fun Forge.createRumRawTelemetryDebugEvent(): RumRawEvent.SendTelemetry {
        return RumRawEvent.SendTelemetry(
            TelemetryType.DEBUG,
            aString(),
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
            throwable?.javaClass?.canonicalName
        )
    }

    // endregion

    companion object {
        val rumMonitor = GlobalRumMonitorTestConfiguration()
        val logger = LoggerTestConfiguration()

        @TestConfigurationsProvider
        @JvmStatic
        fun getTestConfigurations(): List<TestConfiguration> {
            return listOf(rumMonitor, logger)
        }
    }
}
