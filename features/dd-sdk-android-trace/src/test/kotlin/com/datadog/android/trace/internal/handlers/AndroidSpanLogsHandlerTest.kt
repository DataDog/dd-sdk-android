/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.trace.internal.handlers

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureScope
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.core.internal.utils.loggableStackTrace
import com.datadog.android.log.LogAttributes
import com.datadog.android.trace.utils.verifyLog
import com.datadog.android.utils.forge.Configurator
import com.datadog.legacy.trace.api.DDTags
import com.datadog.opentracing.DDSpan
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import io.opentracing.log.Fields
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.math.BigInteger
import java.util.concurrent.TimeUnit

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class AndroidSpanLogsHandlerTest {

    lateinit var testedLogHandler: AndroidSpanLogsHandler

    @Mock
    lateinit var mockSdkCore: FeatureSdkCore

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @Mock
    lateinit var mockLogsFeatureScope: FeatureScope

    @Mock
    lateinit var mockSpan: DDSpan

    @LongForgery
    var fakeTraceId: Long = 0L

    @LongForgery
    var fakeSpanId: Long = 0L

    @BeforeEach
    fun `set up`() {
        whenever(mockSpan.traceId) doReturn BigInteger.valueOf(fakeTraceId)
        whenever(mockSpan.spanId) doReturn BigInteger.valueOf(fakeSpanId)

        whenever(
            mockSdkCore.getFeature(Feature.LOGS_FEATURE_NAME)
        ) doReturn mockLogsFeatureScope

        whenever(mockSdkCore.internalLogger) doReturn mockInternalLogger

        testedLogHandler = AndroidSpanLogsHandler(mockSdkCore)
    }

    @Test
    fun `log event`(
        @StringForgery event: String
    ) {
        // When
        testedLogHandler.log(event, mockSpan)

        // Then
        argumentCaptor<Map<*, *>> {
            verify(mockLogsFeatureScope)
                .sendEvent(capture())

            val spanLogEvent = firstValue.toMutableMap()
            val timestamp = spanLogEvent.remove("timestamp")
            assertThat(spanLogEvent).isEqualTo(
                mapOf(
                    "type" to "span_log",
                    "loggerName" to AndroidSpanLogsHandler.TRACE_LOGGER_NAME,
                    "message" to AndroidSpanLogsHandler.DEFAULT_EVENT_MESSAGE,
                    "attributes" to mapOf(
                        Fields.EVENT to event,
                        LogAttributes.DD_TRACE_ID to fakeTraceId.toString(),
                        LogAttributes.DD_SPAN_ID to fakeSpanId.toString()
                    )
                )
            )

            val timestampAge = System.currentTimeMillis() - timestamp as Long
            assertThat(timestampAge).isBetween(0, 150)
        }
    }

    @Test
    fun `log event with timestamp`(
        @StringForgery event: String,
        @LongForgery timestampMicros: Long
    ) {
        // When
        testedLogHandler.log(timestampMicros, event, mockSpan)

        // Then
        verify(mockLogsFeatureScope)
            .sendEvent(
                mapOf(
                    "type" to "span_log",
                    "loggerName" to AndroidSpanLogsHandler.TRACE_LOGGER_NAME,
                    "message" to AndroidSpanLogsHandler.DEFAULT_EVENT_MESSAGE,
                    "attributes" to mapOf(
                        Fields.EVENT to event,
                        LogAttributes.DD_TRACE_ID to fakeTraceId.toString(),
                        LogAttributes.DD_SPAN_ID to fakeSpanId.toString()
                    ),
                    "timestamp" to TimeUnit.MICROSECONDS.toMillis(timestampMicros)
                )
            )
    }

    @Test
    fun `log map`(
        forge: Forge
    ) {
        // Given
        val fields = forge.aMap { anAlphabeticalString() to anAsciiString() }
        val logAttributes = fields.toMutableMap()
            .apply {
                put(LogAttributes.DD_TRACE_ID, fakeTraceId.toString())
                put(LogAttributes.DD_SPAN_ID, fakeSpanId.toString())
            }

        // When
        testedLogHandler.log(fields, mockSpan)

        // Then
        argumentCaptor<Map<*, *>> {
            verify(mockLogsFeatureScope)
                .sendEvent(capture())

            val spanLogEvent = firstValue.toMutableMap()
            val timestamp = spanLogEvent.remove("timestamp")
            assertThat(spanLogEvent).isEqualTo(
                mapOf(
                    "type" to "span_log",
                    "loggerName" to AndroidSpanLogsHandler.TRACE_LOGGER_NAME,
                    "message" to AndroidSpanLogsHandler.DEFAULT_EVENT_MESSAGE,
                    "attributes" to logAttributes
                )
            )

            val timestampAge = System.currentTimeMillis() - timestamp as Long
            assertThat(timestampAge).isBetween(0, 150)
        }
    }

    @Test
    fun `log map with timestamp`(
        forge: Forge,
        @LongForgery timestampMicros: Long
    ) {
        // Given
        val fields = forge.aMap { anAlphabeticalString() to anAsciiString() }
        val logAttributes = fields.toMutableMap()
            .apply {
                put(LogAttributes.DD_TRACE_ID, fakeTraceId.toString())
                put(LogAttributes.DD_SPAN_ID, fakeSpanId.toString())
            }

        // When
        testedLogHandler.log(timestampMicros, fields, mockSpan)

        // Then
        verify(mockLogsFeatureScope)
            .sendEvent(
                mapOf(
                    "type" to "span_log",
                    "loggerName" to AndroidSpanLogsHandler.TRACE_LOGGER_NAME,
                    "message" to AndroidSpanLogsHandler.DEFAULT_EVENT_MESSAGE,
                    "attributes" to logAttributes,
                    "timestamp" to TimeUnit.MICROSECONDS.toMillis(timestampMicros)
                )
            )
    }

    @Test
    fun `log map with throwable`(
        forge: Forge,
        @Forgery throwable: Throwable
    ) {
        // Given
        val fields = forge.aMap<String, Any?> { aNumericalString() to anAsciiString() }
        val fieldsWithError = fields.toMutableMap()
            .apply { put(Fields.ERROR_OBJECT, throwable) }

        val logAttributes = fields.toMutableMap()
            .apply {
                put(LogAttributes.DD_TRACE_ID, fakeTraceId.toString())
                put(LogAttributes.DD_SPAN_ID, fakeSpanId.toString())
            }

        // When
        testedLogHandler.log(fieldsWithError, mockSpan)

        // Then
        verify(mockSpan).setError(true)
        verify(mockSpan).setTag(DDTags.ERROR_MSG, throwable.message)
        verify(mockSpan).setTag(DDTags.ERROR_TYPE, throwable.javaClass.name)
        verify(mockSpan).setTag(DDTags.ERROR_STACK, throwable.loggableStackTrace())

        argumentCaptor<Map<*, *>> {
            verify(mockLogsFeatureScope)
                .sendEvent(capture())

            val spanLogEvent = firstValue.toMutableMap()
            val timestamp = spanLogEvent.remove("timestamp")
            assertThat(spanLogEvent).isEqualTo(
                mapOf(
                    "type" to "span_log",
                    "loggerName" to AndroidSpanLogsHandler.TRACE_LOGGER_NAME,
                    "message" to AndroidSpanLogsHandler.DEFAULT_EVENT_MESSAGE,
                    "attributes" to logAttributes
                )
            )

            val timestampAge = System.currentTimeMillis() - timestamp as Long
            assertThat(timestampAge).isBetween(0, 150)
        }
    }

    @Test
    fun `log map with throwable and timestamp`(
        forge: Forge,
        @Forgery throwable: Throwable,
        @LongForgery timestampMicros: Long
    ) {
        // Given
        val fields = forge.aMap<String, Any?> { aNumericalString() to anAsciiString() }
        val fieldsWithError = fields.toMutableMap()
            .apply { put(Fields.ERROR_OBJECT, throwable) }

        val logAttributes = fields.toMutableMap()
            .apply {
                put(LogAttributes.DD_TRACE_ID, fakeTraceId.toString())
                put(LogAttributes.DD_SPAN_ID, fakeSpanId.toString())
            }

        // When
        testedLogHandler.log(timestampMicros, fieldsWithError, mockSpan)

        // Then
        verify(mockSpan).setError(true)
        verify(mockSpan).setTag(DDTags.ERROR_MSG, throwable.message)
        verify(mockSpan).setTag(DDTags.ERROR_TYPE, throwable.javaClass.name)
        verify(mockSpan).setTag(DDTags.ERROR_STACK, throwable.loggableStackTrace())
        verify(mockLogsFeatureScope)
            .sendEvent(
                mapOf(
                    "type" to "span_log",
                    "loggerName" to AndroidSpanLogsHandler.TRACE_LOGGER_NAME,
                    "message" to AndroidSpanLogsHandler.DEFAULT_EVENT_MESSAGE,
                    "attributes" to logAttributes,
                    "timestamp" to TimeUnit.MICROSECONDS.toMillis(timestampMicros)
                )
            )
    }

    @Test
    fun `log map with throwable and overridden error fields`(
        forge: Forge,
        @Forgery throwable: Throwable,
        @StringForgery message: String,
        @StringForgery kind: String
    ) {
        // Given
        val fields = forge.aMap<String, Any?> { aNumericalString() to anAsciiString() }
        val fieldsWithError = fields.toMutableMap()
            .apply {
                put(Fields.ERROR_OBJECT, throwable)
                put(Fields.ERROR_KIND, kind)
                put(Fields.MESSAGE, message)
            }

        val logAttributes = fields.toMutableMap()
            .apply {
                put(LogAttributes.DD_TRACE_ID, fakeTraceId.toString())
                put(LogAttributes.DD_SPAN_ID, fakeSpanId.toString())
            }

        // When
        testedLogHandler.log(fieldsWithError, mockSpan)

        // Then
        verify(mockSpan).setError(true)
        verify(mockSpan).setTag(DDTags.ERROR_MSG, message)
        verify(mockSpan).setTag(DDTags.ERROR_TYPE, kind)
        verify(mockSpan).setTag(DDTags.ERROR_STACK, throwable.loggableStackTrace())

        argumentCaptor<Map<*, *>> {
            verify(mockLogsFeatureScope)
                .sendEvent(capture())

            val spanLogEvent = firstValue.toMutableMap()
            val timestamp = spanLogEvent.remove("timestamp")
            assertThat(spanLogEvent).isEqualTo(
                mapOf(
                    "type" to "span_log",
                    "loggerName" to AndroidSpanLogsHandler.TRACE_LOGGER_NAME,
                    "message" to message,
                    "attributes" to logAttributes
                )
            )

            val timestampAge = System.currentTimeMillis() - timestamp as Long
            assertThat(timestampAge).isBetween(0, 150)
        }
    }

    @Test
    fun `log map with throwable and stack trace`(
        forge: Forge,
        @Forgery throwable: Throwable,
        @StringForgery stack: String
    ) {
        // Given
        val fields = forge.aMap<String, Any?> { aNumericalString() to anAsciiString() }
        val fieldsWithError = fields.toMutableMap()
            .apply {
                put(Fields.ERROR_OBJECT, throwable)
                put(Fields.STACK, stack)
            }

        val logAttributes = fields.toMutableMap()
            .apply {
                put(LogAttributes.DD_TRACE_ID, fakeTraceId.toString())
                put(LogAttributes.DD_SPAN_ID, fakeSpanId.toString())
            }

        // When
        testedLogHandler.log(fieldsWithError, mockSpan)

        // Then
        verify(mockSpan).setError(true)
        verify(mockSpan).setTag(DDTags.ERROR_MSG, throwable.message)
        verify(mockSpan).setTag(DDTags.ERROR_TYPE, throwable.javaClass.name)
        verify(mockSpan).setTag(DDTags.ERROR_STACK, stack)

        argumentCaptor<Map<*, *>> {
            verify(mockLogsFeatureScope)
                .sendEvent(capture())

            val spanLogEvent = firstValue.toMutableMap()
            val timestamp = spanLogEvent.remove("timestamp")
            assertThat(spanLogEvent).isEqualTo(
                mapOf(
                    "type" to "span_log",
                    "loggerName" to AndroidSpanLogsHandler.TRACE_LOGGER_NAME,
                    "message" to AndroidSpanLogsHandler.DEFAULT_EVENT_MESSAGE,
                    "attributes" to logAttributes
                )
            )

            val timestampAge = System.currentTimeMillis() - timestamp as Long
            assertThat(timestampAge).isBetween(0, 150)
        }
    }

    @Test
    fun `log map with error fields`(
        forge: Forge,
        @StringForgery stack: String,
        @StringForgery message: String,
        @StringForgery kind: String
    ) {
        // Given
        val fields = forge.aMap<String, Any?> { aNumericalString() to anAsciiString() }
        val fieldsWithError = fields.toMutableMap()
            .apply {
                put(Fields.STACK, stack)
                put(Fields.ERROR_KIND, kind)
                put(Fields.MESSAGE, message)
            }

        val logAttributes = fields.toMutableMap()
            .apply {
                put(LogAttributes.DD_TRACE_ID, fakeTraceId.toString())
                put(LogAttributes.DD_SPAN_ID, fakeSpanId.toString())
            }

        // When
        testedLogHandler.log(fieldsWithError, mockSpan)

        // Then
        verify(mockSpan).setError(true)
        verify(mockSpan).setTag(DDTags.ERROR_MSG, message)
        verify(mockSpan).setTag(DDTags.ERROR_TYPE, kind)
        verify(mockSpan).setTag(DDTags.ERROR_STACK, stack)

        argumentCaptor<Map<*, *>> {
            verify(mockLogsFeatureScope)
                .sendEvent(capture())

            val spanLogEvent = firstValue.toMutableMap()
            val timestamp = spanLogEvent.remove("timestamp")
            assertThat(spanLogEvent).isEqualTo(
                mapOf(
                    "type" to "span_log",
                    "loggerName" to AndroidSpanLogsHandler.TRACE_LOGGER_NAME,
                    "message" to message,
                    "attributes" to logAttributes
                )
            )

            val timestampAge = System.currentTimeMillis() - timestamp as Long
            assertThat(timestampAge).isBetween(0, 150)
        }
    }

    @Test
    fun `M log info W log() { Logs feature is not registered }`(
        @StringForgery event: String
    ) {
        // When
        whenever(mockSdkCore.getFeature(Feature.LOGS_FEATURE_NAME)) doReturn null
        testedLogHandler.log(event, mockSpan)

        // Then
        mockInternalLogger.verifyLog(
            InternalLogger.Level.WARN,
            InternalLogger.Target.USER,
            AndroidSpanLogsHandler.MISSING_LOG_FEATURE_INFO
        )
    }
}
