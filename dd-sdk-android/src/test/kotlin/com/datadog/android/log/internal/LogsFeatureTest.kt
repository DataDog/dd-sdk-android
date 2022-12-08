/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.log.internal

import android.util.Log
import com.datadog.android.core.configuration.Configuration
import com.datadog.android.event.MapperSerializer
import com.datadog.android.log.LogAttributes
import com.datadog.android.log.internal.domain.event.LogEventMapperWrapper
import com.datadog.android.log.model.LogEvent
import com.datadog.android.rum.internal.RumFeature
import com.datadog.android.rum.internal.domain.RumContext
import com.datadog.android.utils.config.LoggerTestConfiguration
import com.datadog.android.utils.extension.toIsoFormattedTimestamp
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.utils.forge.exhaustiveAttributes
import com.datadog.android.v2.api.EventBatchWriter
import com.datadog.android.v2.api.FeatureScope
import com.datadog.android.v2.api.SdkCore
import com.datadog.android.v2.api.context.DatadogContext
import com.datadog.android.v2.api.context.NetworkInfo
import com.datadog.android.v2.api.context.UserInfo
import com.datadog.android.v2.core.internal.storage.DataWriter
import com.datadog.android.v2.log.internal.storage.LogsDataWriter
import com.datadog.opentracing.DDSpanContext
import com.datadog.tools.unit.annotations.TestConfigurationsProvider
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.datadog.tools.unit.extensions.config.TestConfiguration
import com.datadog.tools.unit.forge.aThrowable
import com.datadog.tools.unit.setStaticValue
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.doAnswer
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.annotation.StringForgeryType
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import io.opentracing.Span
import io.opentracing.Tracer
import io.opentracing.util.GlobalTracer
import org.assertj.core.api.Assertions.assertThat
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
import java.util.concurrent.Executors
import com.datadog.android.log.assertj.LogEventAssert.Companion.assertThat as assertThatLog

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(TestConfigurationExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class LogsFeatureTest {

    private lateinit var testedFeature: LogsFeature

    @Forgery
    lateinit var fakeConfigurationFeature: Configuration.Feature.Logs

    @Mock
    lateinit var mockSdkCore: SdkCore

    @Mock
    lateinit var mockLogsFeatureScope: FeatureScope

    @Mock
    lateinit var mockEventBatchWriter: EventBatchWriter

    @Mock
    lateinit var mockDataWriter: DataWriter<LogEvent>

    @Mock
    lateinit var mockTracer: Tracer

    @Mock
    lateinit var mockSpanContext: DDSpanContext

    @Mock
    lateinit var mockSpan: Span

    @Forgery
    lateinit var fakeDatadogContext: DatadogContext

    @Forgery
    lateinit var fakeRumContext: RumContext

    @StringForgery(StringForgeryType.HEXADECIMAL)
    lateinit var fakeSpanId: String

    @StringForgery(StringForgeryType.HEXADECIMAL)
    lateinit var fakeTraceId: String

    private var fakeServerTimeOffset: Long = 0L

    @BeforeEach
    fun `set up`(
        forge: Forge
    ) {
        val now = System.currentTimeMillis()
        fakeServerTimeOffset = forge.aLong(min = -now, max = Long.MAX_VALUE - now)

        whenever(
            mockSdkCore.getFeature(LogsFeature.LOGS_FEATURE_NAME)
        ) doReturn mockLogsFeatureScope

        whenever(mockLogsFeatureScope.withWriteContext(any(), any())) doAnswer {
            val callback = it.getArgument<(DatadogContext, EventBatchWriter) -> Unit>(1)
            callback.invoke(fakeDatadogContext, mockEventBatchWriter)
        }

        whenever(mockTracer.activeSpan()).thenReturn(mockSpan)
        whenever(mockSpan.context()) doReturn mockSpanContext
        whenever(mockSpanContext.toSpanId()) doReturn fakeSpanId
        whenever(mockSpanContext.toTraceId()) doReturn fakeTraceId

        fakeDatadogContext = fakeDatadogContext.copy(
            time = fakeDatadogContext.time.copy(
                serverTimeOffsetMs = fakeServerTimeOffset
            ),
            featuresContext = fakeDatadogContext.featuresContext.toMutableMap().apply {
                put(RumFeature.RUM_FEATURE_NAME, fakeRumContext.toMap())
            }
        )

        GlobalTracer.registerIfAbsent(mockTracer)

        testedFeature = LogsFeature(mockSdkCore)
    }

    @AfterEach
    fun `tear down`() {
        GlobalTracer::class.java.setStaticValue("isRegistered", false)
    }

    @Test
    fun `𝕄 initialize data writer 𝕎 initialize()`() {
        // When
        testedFeature.initialize(fakeConfigurationFeature)

        // Then
        assertThat(testedFeature.dataWriter)
            .isInstanceOf(LogsDataWriter::class.java)
    }

    @Test
    fun `𝕄 use the eventMapper 𝕎 initialize()`() {
        // When
        testedFeature.initialize(fakeConfigurationFeature)

        // Then
        val dataWriter = testedFeature.dataWriter as? LogsDataWriter
        val logMapperSerializer = dataWriter?.serializer as? MapperSerializer<LogEvent>
        val logEventMapperWrapper = logMapperSerializer?.eventMapper as? LogEventMapperWrapper
        val logEventMapper = logEventMapperWrapper?.wrappedEventMapper
        assertThat(
            logEventMapper
        ).isSameAs(
            fakeConfigurationFeature.logsEventMapper
        )
    }

    @Test
    fun `𝕄 log warning and do nothing 𝕎 onReceive() { unknown event type }`() {
        // Given
        testedFeature.dataWriter = mockDataWriter

        // When
        testedFeature.onReceive(Any())

        // Then
        verify(logger.mockDevLogHandler)
            .handleLog(
                Log.WARN,
                LogsFeature.UNSUPPORTED_EVENT_TYPE.format(
                    Locale.US,
                    Any()::class.java.canonicalName
                )
            )

        verifyZeroInteractions(
            logger.mockDevLogHandler,
            mockSdkCore,
            mockDataWriter
        )
    }

    @Test
    fun `𝕄 log warning and do nothing 𝕎 onReceive() { unknown type property value }`(
        forge: Forge
    ) {
        // Given
        testedFeature.dataWriter = mockDataWriter
        val event = mapOf(
            "type" to forge.anAlphabeticalString()
        )

        // When
        testedFeature.onReceive(event)

        // Then
        verify(logger.mockDevLogHandler)
            .handleLog(
                Log.WARN,
                LogsFeature.UNKNOWN_EVENT_TYPE_PROPERTY_VALUE.format(Locale.US, event["type"])
            )

        verifyZeroInteractions(
            mockSdkCore,
            mockDataWriter
        )
    }

    @Test
    fun `𝕄 log warning and do nothing 𝕎 onReceive() { missing mandatory fields }`(
        @StringForgery fakeThreadName: String,
        @LongForgery fakeTimestamp: Long,
        @StringForgery fakeMessage: String,
        @StringForgery fakeLoggerName: String,
        forge: Forge
    ) {
        // Given
        testedFeature.dataWriter = mockDataWriter
        val event = mutableMapOf(
            "type" to "crash",
            "threadName" to fakeThreadName,
            "timestamp" to fakeTimestamp,
            "message" to fakeMessage,
            "loggerName" to fakeLoggerName
        )

        event.remove(
            forge.anElementFrom(event.keys.filterNot { it == "type" })
        )

        // When
        testedFeature.onReceive(event)

        // Then
        verify(logger.mockDevLogHandler)
            .handleLog(
                Log.WARN,
                LogsFeature.EVENT_MISSING_MANDATORY_FIELDS
            )

        verifyZeroInteractions(
            mockSdkCore,
            mockDataWriter
        )
    }

    @Test
    fun `𝕄 log warning and do nothing 𝕎 onReceive() { wrong type for mandatory fields }`(
        @StringForgery fakeThreadName: String,
        @LongForgery fakeTimestamp: Long,
        @StringForgery fakeMessage: String,
        @StringForgery fakeLoggerName: String,
        forge: Forge
    ) {
        // Given
        testedFeature.dataWriter = mockDataWriter
        val event = mutableMapOf<String, Any>(
            "type" to "crash",
            "threadName" to fakeThreadName,
            "timestamp" to fakeTimestamp,
            "message" to fakeMessage,
            "loggerName" to fakeLoggerName
        )

        event[forge.anElementFrom(event.keys.filterNot { it == "type" })] = Any()

        // When
        testedFeature.onReceive(event)

        // Then
        verify(logger.mockDevLogHandler)
            .handleLog(
                Log.WARN,
                LogsFeature.EVENT_MISSING_MANDATORY_FIELDS
            )

        verifyZeroInteractions(
            mockSdkCore,
            mockDataWriter
        )
    }

    @Test
    fun `𝕄 write crash log event 𝕎 onReceive() { bare minimum }`(
        @StringForgery fakeThreadName: String,
        @LongForgery fakeTimestamp: Long,
        @StringForgery fakeMessage: String,
        @StringForgery fakeLoggerName: String
    ) {
        // Given
        testedFeature.dataWriter = mockDataWriter
        val event = mapOf(
            "type" to "crash",
            "threadName" to fakeThreadName,
            "timestamp" to fakeTimestamp,
            "message" to fakeMessage,
            "loggerName" to fakeLoggerName
        )

        // When
        testedFeature.onReceive(event)

        // Then
        argumentCaptor<LogEvent> {
            verify(mockDataWriter).write(eq(mockEventBatchWriter), capture())

            val log = lastValue

            assertThatLog(log)
                .hasStatus(LogEvent.Status.EMERGENCY)
                .hasLoggerName(fakeLoggerName)
                .hasServiceName(fakeDatadogContext.service)
                .hasMessage(fakeMessage)
                .hasThreadName(fakeThreadName)
                .hasDate((fakeTimestamp + fakeServerTimeOffset).toIsoFormattedTimestamp())
                .hasNetworkInfo(fakeDatadogContext.networkInfo)
                .hasUserInfo(fakeDatadogContext.userInfo)
                .hasExactlyAttributes(
                    mapOf(
                        LogAttributes.RUM_APPLICATION_ID to fakeRumContext.applicationId,
                        LogAttributes.RUM_SESSION_ID to fakeRumContext.sessionId,
                        LogAttributes.RUM_VIEW_ID to fakeRumContext.viewId,
                        LogAttributes.RUM_ACTION_ID to fakeRumContext.actionId,
                        LogAttributes.DD_TRACE_ID to fakeTraceId,
                        LogAttributes.DD_SPAN_ID to fakeSpanId
                    )
                )
                .hasExactlyTags(
                    setOf(
                        "${LogAttributes.ENV}:${fakeDatadogContext.env}",
                        "${LogAttributes.APPLICATION_VERSION}:${fakeDatadogContext.version}",
                        "${LogAttributes.VARIANT}:${fakeDatadogContext.variant}"
                    )
                )
        }
    }

    @Test
    fun `𝕄 write crash log event 𝕎 onReceive() { with attributes }`(
        @StringForgery fakeThreadName: String,
        @LongForgery fakeTimestamp: Long,
        @StringForgery fakeMessage: String,
        @StringForgery fakeLoggerName: String,
        forge: Forge
    ) {
        // Given
        testedFeature.dataWriter = mockDataWriter
        val fakeAttributes = forge.exhaustiveAttributes()
        val event = mapOf(
            "type" to "crash",
            "threadName" to fakeThreadName,
            "timestamp" to fakeTimestamp,
            "message" to fakeMessage,
            "loggerName" to fakeLoggerName,
            "attributes" to fakeAttributes
        )

        // When
        testedFeature.onReceive(event)

        // Then
        argumentCaptor<LogEvent> {
            verify(mockDataWriter).write(eq(mockEventBatchWriter), capture())

            val log = lastValue

            assertThatLog(log)
                .hasStatus(LogEvent.Status.EMERGENCY)
                .hasLoggerName(fakeLoggerName)
                .hasServiceName(fakeDatadogContext.service)
                .hasMessage(fakeMessage)
                .hasThreadName(fakeThreadName)
                .hasDate((fakeTimestamp + fakeServerTimeOffset).toIsoFormattedTimestamp())
                .hasNetworkInfo(fakeDatadogContext.networkInfo)
                .hasUserInfo(fakeDatadogContext.userInfo)
                .hasExactlyAttributes(
                    fakeAttributes + mapOf(
                        LogAttributes.RUM_APPLICATION_ID to fakeRumContext.applicationId,
                        LogAttributes.RUM_SESSION_ID to fakeRumContext.sessionId,
                        LogAttributes.RUM_VIEW_ID to fakeRumContext.viewId,
                        LogAttributes.RUM_ACTION_ID to fakeRumContext.actionId,
                        LogAttributes.DD_TRACE_ID to fakeTraceId,
                        LogAttributes.DD_SPAN_ID to fakeSpanId
                    )
                )
                .hasExactlyTags(
                    setOf(
                        "${LogAttributes.ENV}:${fakeDatadogContext.env}",
                        "${LogAttributes.APPLICATION_VERSION}:${fakeDatadogContext.version}",
                        "${LogAttributes.VARIANT}:${fakeDatadogContext.variant}"
                    )
                )
        }
    }

    @Test
    fun `𝕄 write crash log event 𝕎 onReceive() { with throwable }`(
        @StringForgery fakeThreadName: String,
        @LongForgery fakeTimestamp: Long,
        @StringForgery fakeMessage: String,
        @StringForgery fakeLoggerName: String,
        forge: Forge
    ) {
        // Given
        testedFeature.dataWriter = mockDataWriter
        val fakeThrowable = forge.aThrowable()
        val fakeAttributes = forge.exhaustiveAttributes()
        val event = mapOf(
            "type" to "crash",
            "threadName" to fakeThreadName,
            "timestamp" to fakeTimestamp,
            "message" to fakeMessage,
            "loggerName" to fakeLoggerName,
            "attributes" to fakeAttributes,
            "throwable" to fakeThrowable
        )

        // When
        testedFeature.onReceive(event)

        // Then
        argumentCaptor<LogEvent> {
            verify(mockDataWriter).write(eq(mockEventBatchWriter), capture())

            val log = lastValue

            assertThatLog(log)
                .hasStatus(LogEvent.Status.EMERGENCY)
                .hasLoggerName(fakeLoggerName)
                .hasServiceName(fakeDatadogContext.service)
                .hasMessage(fakeMessage)
                .hasError(
                    LogEvent.Error(
                        kind = fakeThrowable.javaClass.canonicalName,
                        stack = fakeThrowable.stackTraceToString(),
                        message = fakeThrowable.message
                    )
                )
                .hasThreadName(fakeThreadName)
                .hasDate((fakeTimestamp + fakeServerTimeOffset).toIsoFormattedTimestamp())
                .hasNetworkInfo(fakeDatadogContext.networkInfo)
                .hasUserInfo(fakeDatadogContext.userInfo)
                .hasExactlyAttributes(
                    fakeAttributes + mapOf(
                        LogAttributes.RUM_APPLICATION_ID to fakeRumContext.applicationId,
                        LogAttributes.RUM_SESSION_ID to fakeRumContext.sessionId,
                        LogAttributes.RUM_VIEW_ID to fakeRumContext.viewId,
                        LogAttributes.RUM_ACTION_ID to fakeRumContext.actionId,
                        LogAttributes.DD_TRACE_ID to fakeTraceId,
                        LogAttributes.DD_SPAN_ID to fakeSpanId
                    )
                )
                .hasExactlyTags(
                    setOf(
                        "${LogAttributes.ENV}:${fakeDatadogContext.env}",
                        "${LogAttributes.APPLICATION_VERSION}:${fakeDatadogContext.version}",
                        "${LogAttributes.VARIANT}:${fakeDatadogContext.variant}"
                    )
                )
        }
    }

    @Test
    fun `𝕄 write crash log event 𝕎 onReceive() { bundleWithTraces = false }`(
        @StringForgery fakeThreadName: String,
        @LongForgery fakeTimestamp: Long,
        @StringForgery fakeMessage: String,
        @StringForgery fakeLoggerName: String,
        forge: Forge
    ) {
        // Given
        testedFeature.dataWriter = mockDataWriter

        val fakeAttributes = forge.exhaustiveAttributes()
        val event = mapOf(
            "type" to "crash",
            "threadName" to fakeThreadName,
            "timestamp" to fakeTimestamp,
            "message" to fakeMessage,
            "loggerName" to fakeLoggerName,
            "attributes" to fakeAttributes,
            "bundleWithTraces" to false
        )

        // When
        testedFeature.onReceive(event)

        // Then
        argumentCaptor<LogEvent> {
            verify(mockDataWriter).write(eq(mockEventBatchWriter), capture())

            val log = lastValue

            assertThatLog(log)
                .hasStatus(LogEvent.Status.EMERGENCY)
                .hasLoggerName(fakeLoggerName)
                .hasServiceName(fakeDatadogContext.service)
                .hasMessage(fakeMessage)
                .hasThreadName(fakeThreadName)
                .hasDate((fakeTimestamp + fakeServerTimeOffset).toIsoFormattedTimestamp())
                .hasUserInfo(fakeDatadogContext.userInfo)
                .hasNetworkInfo(fakeDatadogContext.networkInfo)
                .hasExactlyAttributes(
                    fakeAttributes + mapOf(
                        LogAttributes.RUM_APPLICATION_ID to fakeRumContext.applicationId,
                        LogAttributes.RUM_SESSION_ID to fakeRumContext.sessionId,
                        LogAttributes.RUM_VIEW_ID to fakeRumContext.viewId,
                        LogAttributes.RUM_ACTION_ID to fakeRumContext.actionId
                    )
                )
                .hasExactlyTags(
                    setOf(
                        "${LogAttributes.ENV}:${fakeDatadogContext.env}",
                        "${LogAttributes.APPLICATION_VERSION}:${fakeDatadogContext.version}",
                        "${LogAttributes.VARIANT}:${fakeDatadogContext.variant}"
                    )
                )
        }
    }

    @Test
    fun `𝕄 write crash log event 𝕎 onReceive() { bundleWithRum = false }`(
        @StringForgery fakeThreadName: String,
        @LongForgery fakeTimestamp: Long,
        @StringForgery fakeMessage: String,
        @StringForgery fakeLoggerName: String,
        forge: Forge
    ) {
        // Given
        testedFeature.dataWriter = mockDataWriter
        val fakeAttributes = forge.exhaustiveAttributes()
        val event = mapOf(
            "type" to "crash",
            "threadName" to fakeThreadName,
            "timestamp" to fakeTimestamp,
            "message" to fakeMessage,
            "loggerName" to fakeLoggerName,
            "attributes" to fakeAttributes,
            "bundleWithRum" to false
        )

        // When
        testedFeature.onReceive(event)

        // Then
        argumentCaptor<LogEvent> {
            verify(mockDataWriter).write(eq(mockEventBatchWriter), capture())

            val log = lastValue

            assertThatLog(log)
                .hasStatus(LogEvent.Status.EMERGENCY)
                .hasLoggerName(fakeLoggerName)
                .hasServiceName(fakeDatadogContext.service)
                .hasMessage(fakeMessage)
                .hasThreadName(fakeThreadName)
                .hasDate((fakeTimestamp + fakeServerTimeOffset).toIsoFormattedTimestamp())
                .hasUserInfo(fakeDatadogContext.userInfo)
                .hasNetworkInfo(fakeDatadogContext.networkInfo)
                .hasExactlyAttributes(
                    fakeAttributes + mapOf(
                        LogAttributes.DD_TRACE_ID to fakeTraceId,
                        LogAttributes.DD_SPAN_ID to fakeSpanId
                    )
                )
                .hasExactlyTags(
                    setOf(
                        "${LogAttributes.ENV}:${fakeDatadogContext.env}",
                        "${LogAttributes.APPLICATION_VERSION}:${fakeDatadogContext.version}",
                        "${LogAttributes.VARIANT}:${fakeDatadogContext.variant}"
                    )
                )
        }
    }

    @Test
    fun `𝕄 write crash log event 𝕎 onReceive() { explicit network info }`(
        @StringForgery fakeThreadName: String,
        @LongForgery fakeTimestamp: Long,
        @StringForgery fakeMessage: String,
        @StringForgery fakeLoggerName: String,
        @Forgery fakeNetworkInfo: NetworkInfo,
        forge: Forge
    ) {
        // Given
        testedFeature.dataWriter = mockDataWriter
        val fakeAttributes = forge.exhaustiveAttributes()
        val event = mapOf(
            "type" to "crash",
            "threadName" to fakeThreadName,
            "timestamp" to fakeTimestamp,
            "message" to fakeMessage,
            "loggerName" to fakeLoggerName,
            "attributes" to fakeAttributes,
            "networkInfo" to fakeNetworkInfo
        )

        // When
        testedFeature.onReceive(event)

        // Then
        argumentCaptor<LogEvent> {
            verify(mockDataWriter).write(eq(mockEventBatchWriter), capture())

            val log = lastValue

            assertThatLog(log)
                .hasStatus(LogEvent.Status.EMERGENCY)
                .hasLoggerName(fakeLoggerName)
                .hasServiceName(fakeDatadogContext.service)
                .hasMessage(fakeMessage)
                .hasThreadName(fakeThreadName)
                .hasDate((fakeTimestamp + fakeServerTimeOffset).toIsoFormattedTimestamp())
                .hasUserInfo(fakeDatadogContext.userInfo)
                .hasNetworkInfo(fakeNetworkInfo)
                .hasExactlyAttributes(
                    fakeAttributes + mapOf(
                        LogAttributes.RUM_APPLICATION_ID to fakeRumContext.applicationId,
                        LogAttributes.RUM_SESSION_ID to fakeRumContext.sessionId,
                        LogAttributes.RUM_VIEW_ID to fakeRumContext.viewId,
                        LogAttributes.RUM_ACTION_ID to fakeRumContext.actionId,
                        LogAttributes.DD_TRACE_ID to fakeTraceId,
                        LogAttributes.DD_SPAN_ID to fakeSpanId
                    )
                )
                .hasExactlyTags(
                    setOf(
                        "${LogAttributes.ENV}:${fakeDatadogContext.env}",
                        "${LogAttributes.APPLICATION_VERSION}:${fakeDatadogContext.version}",
                        "${LogAttributes.VARIANT}:${fakeDatadogContext.variant}"
                    )
                )
        }
    }

    @Test
    fun `𝕄 write crash log event 𝕎 onReceive() { explicit user info }`(
        @StringForgery fakeThreadName: String,
        @LongForgery fakeTimestamp: Long,
        @StringForgery fakeMessage: String,
        @StringForgery fakeLoggerName: String,
        @Forgery fakeUserInfo: UserInfo,
        forge: Forge
    ) {
        // Given
        testedFeature.dataWriter = mockDataWriter
        val fakeAttributes = forge.exhaustiveAttributes()
        val event = mapOf(
            "type" to "crash",
            "threadName" to fakeThreadName,
            "timestamp" to fakeTimestamp,
            "message" to fakeMessage,
            "loggerName" to fakeLoggerName,
            "attributes" to fakeAttributes,
            "userInfo" to fakeUserInfo
        )

        // When
        testedFeature.onReceive(event)

        // Then
        argumentCaptor<LogEvent> {
            verify(mockDataWriter).write(eq(mockEventBatchWriter), capture())

            val log = lastValue

            assertThatLog(log)
                .hasStatus(LogEvent.Status.EMERGENCY)
                .hasLoggerName(fakeLoggerName)
                .hasServiceName(fakeDatadogContext.service)
                .hasMessage(fakeMessage)
                .hasThreadName(fakeThreadName)
                .hasDate((fakeTimestamp + fakeServerTimeOffset).toIsoFormattedTimestamp())
                .hasUserInfo(fakeUserInfo)
                .hasNetworkInfo(fakeDatadogContext.networkInfo)
                .hasExactlyAttributes(
                    fakeAttributes + mapOf(
                        LogAttributes.RUM_APPLICATION_ID to fakeRumContext.applicationId,
                        LogAttributes.RUM_SESSION_ID to fakeRumContext.sessionId,
                        LogAttributes.RUM_VIEW_ID to fakeRumContext.viewId,
                        LogAttributes.RUM_ACTION_ID to fakeRumContext.actionId,
                        LogAttributes.DD_TRACE_ID to fakeTraceId,
                        LogAttributes.DD_SPAN_ID to fakeSpanId
                    )
                )
                .hasExactlyTags(
                    setOf(
                        "${LogAttributes.ENV}:${fakeDatadogContext.env}",
                        "${LogAttributes.APPLICATION_VERSION}:${fakeDatadogContext.version}",
                        "${LogAttributes.VARIANT}:${fakeDatadogContext.variant}"
                    )
                )
        }
    }

    @Test
    fun `𝕄 write crash log event and wait 𝕎 onReceive() { syncWrite = true }`(
        @StringForgery fakeThreadName: String,
        @LongForgery fakeTimestamp: Long,
        @StringForgery fakeMessage: String,
        @StringForgery fakeLoggerName: String
    ) {
        // Given
        whenever(mockLogsFeatureScope.withWriteContext(any(), any())) doAnswer {
            val callback = it.getArgument<(DatadogContext, EventBatchWriter) -> Unit>(1)
            val executor = Executors.newSingleThreadExecutor()
            executor.execute {
                Thread.sleep(300)
                callback.invoke(fakeDatadogContext, mockEventBatchWriter)
            }
        }
        testedFeature.dataWriter = mockDataWriter
        val event = mapOf(
            "type" to "crash",
            "threadName" to fakeThreadName,
            "timestamp" to fakeTimestamp,
            "message" to fakeMessage,
            "loggerName" to fakeLoggerName,
            "syncWrite" to true
        )

        // When
        testedFeature.onReceive(event)

        // Then
        argumentCaptor<LogEvent> {
            verify(mockDataWriter).write(eq(mockEventBatchWriter), capture())

            val log = lastValue

            assertThatLog(log)
                .hasStatus(LogEvent.Status.EMERGENCY)
                .hasLoggerName(fakeLoggerName)
                .hasServiceName(fakeDatadogContext.service)
                .hasMessage(fakeMessage)
                .hasThreadName(fakeThreadName)
                .hasDate((fakeTimestamp + fakeServerTimeOffset).toIsoFormattedTimestamp())
                .hasNetworkInfo(fakeDatadogContext.networkInfo)
                .hasUserInfo(fakeDatadogContext.userInfo)
                .hasExactlyAttributes(
                    mapOf(
                        LogAttributes.RUM_APPLICATION_ID to fakeRumContext.applicationId,
                        LogAttributes.RUM_SESSION_ID to fakeRumContext.sessionId,
                        LogAttributes.RUM_VIEW_ID to fakeRumContext.viewId,
                        LogAttributes.RUM_ACTION_ID to fakeRumContext.actionId,
                        LogAttributes.DD_TRACE_ID to fakeTraceId,
                        LogAttributes.DD_SPAN_ID to fakeSpanId
                    )
                )
                .hasExactlyTags(
                    setOf(
                        "${LogAttributes.ENV}:${fakeDatadogContext.env}",
                        "${LogAttributes.APPLICATION_VERSION}:${fakeDatadogContext.version}",
                        "${LogAttributes.VARIANT}:${fakeDatadogContext.variant}"
                    )
                )
        }
    }

    @Test
    fun `𝕄 write crash log event and not wait 𝕎 onReceive() { syncWrite = true + timeout }`(
        @StringForgery fakeThreadName: String,
        @LongForgery fakeTimestamp: Long,
        @StringForgery fakeMessage: String,
        @StringForgery fakeLoggerName: String
    ) {
        // Given
        whenever(mockLogsFeatureScope.withWriteContext(any(), any())) doAnswer {
            val callback = it.getArgument<(DatadogContext, EventBatchWriter) -> Unit>(1)
            val executor = Executors.newSingleThreadExecutor()
            executor.execute {
                Thread.sleep(LogsFeature.MAX_WRITE_WAIT_TIMEOUT_MS + 200)
                callback.invoke(fakeDatadogContext, mockEventBatchWriter)
            }
        }
        testedFeature.dataWriter = mockDataWriter
        val event = mapOf(
            "type" to "crash",
            "threadName" to fakeThreadName,
            "timestamp" to fakeTimestamp,
            "message" to fakeMessage,
            "loggerName" to fakeLoggerName,
            "syncWrite" to true
        )

        // When
        testedFeature.onReceive(event)

        // Then
        verify(mockLogsFeatureScope).withWriteContext(any(), any())
        verifyZeroInteractions(mockDataWriter)
    }

    companion object {
        val logger = LoggerTestConfiguration()

        @TestConfigurationsProvider
        @JvmStatic
        fun getTestConfigurations(): List<TestConfiguration> {
            return listOf(logger)
        }
    }
}
