/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.tracing.internal.handlers

import android.util.Log
import com.datadog.android.core.internal.utils.loggableStackTrace
import com.datadog.android.log.LogAttributes
import com.datadog.android.log.Logger
import com.datadog.android.utils.forge.Configurator
import com.datadog.opentracing.DDSpan
import com.datadog.trace.api.DDTags
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import io.opentracing.log.Fields
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
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
    lateinit var mockLogger: Logger

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

        testedLogHandler = AndroidSpanLogsHandler(
            mockLogger
        )
    }

    @Test
    fun `log event`(
        @StringForgery event: String
    ) {
        testedLogHandler.log(event, mockSpan)

        verify(mockLogger)
            .internalLog(
                Log.VERBOSE,
                AndroidSpanLogsHandler.DEFAULT_EVENT_MESSAGE,
                null,
                mapOf(
                    Fields.EVENT to event,
                    LogAttributes.DD_TRACE_ID to fakeTraceId.toString(),
                    LogAttributes.DD_SPAN_ID to fakeSpanId.toString()
                )
            )
    }

    @Test
    fun `log event with timestamp`(
        @StringForgery event: String,
        @LongForgery timestampMicros: Long
    ) {
        testedLogHandler.log(timestampMicros, event, mockSpan)

        verify(mockLogger)
            .internalLog(
                Log.VERBOSE,
                AndroidSpanLogsHandler.DEFAULT_EVENT_MESSAGE,
                null,
                mapOf(
                    Fields.EVENT to event,
                    LogAttributes.DD_TRACE_ID to fakeTraceId.toString(),
                    LogAttributes.DD_SPAN_ID to fakeSpanId.toString()
                ),
                TimeUnit.MICROSECONDS.toMillis(timestampMicros)
            )
    }

    @Test
    fun `log map`(
        forge: Forge
    ) {
        val fields = forge.aMap { anAlphabeticalString() to anAsciiString() }
        val logAttributes = fields.toMutableMap()
            .apply {
                put(LogAttributes.DD_TRACE_ID, fakeTraceId.toString())
                put(LogAttributes.DD_SPAN_ID, fakeSpanId.toString())
            }

        testedLogHandler.log(fields, mockSpan)

        verify(mockLogger)
            .internalLog(
                Log.VERBOSE,
                AndroidSpanLogsHandler.DEFAULT_EVENT_MESSAGE,
                null,
                logAttributes
            )
    }

    @Test
    fun `log map with timestamp`(
        forge: Forge,
        @LongForgery timestampMicros: Long
    ) {
        val fields = forge.aMap { anAlphabeticalString() to anAsciiString() }
        val logAttributes = fields.toMutableMap()
            .apply {
                put(LogAttributes.DD_TRACE_ID, fakeTraceId.toString())
                put(LogAttributes.DD_SPAN_ID, fakeSpanId.toString())
            }

        testedLogHandler.log(timestampMicros, fields, mockSpan)

        verify(mockLogger)
            .internalLog(
                Log.VERBOSE,
                AndroidSpanLogsHandler.DEFAULT_EVENT_MESSAGE,
                null,
                logAttributes,
                TimeUnit.MICROSECONDS.toMillis(timestampMicros)
            )
    }

    @Test
    fun `log map with throwable`(
        forge: Forge,
        @Forgery throwable: Throwable
    ) {
        val fields = forge.aMap<String, Any?> { aNumericalString() to anAsciiString() }
        val fieldsWithError = fields.toMutableMap()
            .apply { put(Fields.ERROR_OBJECT, throwable) }

        val logAttributes = fields.toMutableMap()
            .apply {
                put(LogAttributes.DD_TRACE_ID, fakeTraceId.toString())
                put(LogAttributes.DD_SPAN_ID, fakeSpanId.toString())
            }

        testedLogHandler.log(fieldsWithError, mockSpan)

        verify(mockSpan).setError(true)
        verify(mockSpan).setTag(DDTags.ERROR_MSG, throwable.message)
        verify(mockSpan).setTag(DDTags.ERROR_TYPE, throwable.javaClass.name)
        verify(mockSpan).setTag(DDTags.ERROR_STACK, throwable.loggableStackTrace())
        verify(mockLogger)
            .internalLog(
                Log.VERBOSE,
                AndroidSpanLogsHandler.DEFAULT_EVENT_MESSAGE,
                null,
                logAttributes
            )
    }

    @Test
    fun `log map with throwable and timestamp`(
        forge: Forge,
        @Forgery throwable: Throwable,
        @LongForgery timestampMicros: Long
    ) {
        val fields = forge.aMap<String, Any?> { aNumericalString() to anAsciiString() }
        val fieldsWithError = fields.toMutableMap()
            .apply { put(Fields.ERROR_OBJECT, throwable) }

        val logAttributes = fields.toMutableMap()
            .apply {
                put(LogAttributes.DD_TRACE_ID, fakeTraceId.toString())
                put(LogAttributes.DD_SPAN_ID, fakeSpanId.toString())
            }

        testedLogHandler.log(timestampMicros, fieldsWithError, mockSpan)

        verify(mockSpan).setError(true)
        verify(mockSpan).setTag(DDTags.ERROR_MSG, throwable.message)
        verify(mockSpan).setTag(DDTags.ERROR_TYPE, throwable.javaClass.name)
        verify(mockSpan).setTag(DDTags.ERROR_STACK, throwable.loggableStackTrace())
        verify(mockLogger)
            .internalLog(
                Log.VERBOSE,
                AndroidSpanLogsHandler.DEFAULT_EVENT_MESSAGE,
                null,
                logAttributes,
                TimeUnit.MICROSECONDS.toMillis(timestampMicros)
            )
    }

    @Test
    fun `log map with throwable and overridden error fields`(
        forge: Forge,
        @Forgery throwable: Throwable,
        @StringForgery message: String,
        @StringForgery kind: String
    ) {
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

        testedLogHandler.log(fieldsWithError, mockSpan)

        verify(mockSpan).setError(true)
        verify(mockSpan).setTag(DDTags.ERROR_MSG, message)
        verify(mockSpan).setTag(DDTags.ERROR_TYPE, kind)
        verify(mockSpan).setTag(DDTags.ERROR_STACK, throwable.loggableStackTrace())
        verify(mockLogger)
            .internalLog(
                Log.VERBOSE,
                message,
                null,
                logAttributes
            )
    }

    @Test
    fun `log map with throwable and stack trace`(
        forge: Forge,
        @Forgery throwable: Throwable,
        @StringForgery stack: String
    ) {
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

        testedLogHandler.log(fieldsWithError, mockSpan)

        verify(mockSpan).setError(true)
        verify(mockSpan).setTag(DDTags.ERROR_MSG, throwable.message)
        verify(mockSpan).setTag(DDTags.ERROR_TYPE, throwable.javaClass.name)
        verify(mockSpan).setTag(DDTags.ERROR_STACK, stack)
        verify(mockLogger)
            .internalLog(
                Log.VERBOSE,
                AndroidSpanLogsHandler.DEFAULT_EVENT_MESSAGE,
                null,
                logAttributes
            )
    }

    @Test
    fun `log map with error fields`(
        forge: Forge,
        @StringForgery stack: String,
        @StringForgery message: String,
        @StringForgery kind: String
    ) {
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

        testedLogHandler.log(fieldsWithError, mockSpan)

        verify(mockSpan).setError(true)
        verify(mockSpan).setTag(DDTags.ERROR_MSG, message)
        verify(mockSpan).setTag(DDTags.ERROR_TYPE, kind)
        verify(mockSpan).setTag(DDTags.ERROR_STACK, stack)
        verify(mockLogger)
            .internalLog(
                Log.VERBOSE,
                message,
                null,
                logAttributes
            )
    }
}
