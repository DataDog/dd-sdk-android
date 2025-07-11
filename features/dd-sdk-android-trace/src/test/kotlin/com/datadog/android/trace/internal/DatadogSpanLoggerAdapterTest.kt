/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.trace.internal

import android.util.Log
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureScope
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.internal.utils.loggableStackTrace
import com.datadog.android.log.LogAttributes
import com.datadog.android.trace.api.DatadogTracingConstants
import com.datadog.android.trace.api.span.DatadogSpan
import com.datadog.android.trace.internal.DatadogSpanLoggerAdapter.Companion.DEFAULT_EVENT_MESSAGE
import com.datadog.android.trace.internal.DatadogSpanLoggerAdapter.Companion.TRACE_LOGGER_NAME
import com.datadog.android.utils.forge.Configurator
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
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
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
class DatadogSpanLoggerAdapterTest {

    private lateinit var mockSdkCore: FeatureSdkCore

    @StringForgery
    lateinit var fakeString: String

    @Forgery
    lateinit var fakeThrowable: Throwable

    @Mock
    lateinit var mockSpan: DatadogSpan

    @Forgery
    lateinit var fakeSpan: DatadogSpan

    @Mock
    lateinit var mockLogFeatureScope: FeatureScope

    private lateinit var testedLogger: DatadogSpanLoggerAdapter

    @BeforeEach
    fun `set up`() {
        mockSdkCore = mock<FeatureSdkCore> {
            on { getFeature(Feature.LOGS_FEATURE_NAME) } doReturn mockLogFeatureScope
        }

        testedLogger = DatadogSpanLoggerAdapter(mockSdkCore)
    }

    @Test
    fun `M send expected event W log(String)`() {
        // When
        testedLogger.log(fakeString, fakeSpan)

        // Then
        argumentCaptor<Map<Any, Any>> {
            verify(mockLogFeatureScope).sendEvent(capture())

            assertThat(firstValue["type"]).isEqualTo("span_log")
            assertThat(firstValue["loggerName"]).isEqualTo(TRACE_LOGGER_NAME)
            assertThat(firstValue["message"]).isEqualTo(DEFAULT_EVENT_MESSAGE)
            assertThat(firstValue["logStatus"]).isEqualTo(Log.VERBOSE)

            val attributes = firstValue["attributes"] as Map<*, *>
            assertThat(attributes[DatadogTracingConstants.LogAttributes.EVENT]).isEqualTo(fakeString)
            assertThat(attributes[LogAttributes.DD_SPAN_ID]).isEqualTo(fakeSpan.context().spanId.toString())
            assertThat(attributes[LogAttributes.DD_TRACE_ID]).isEqualTo(fakeSpan.context().traceId.toHexString())
        }
    }

    @Test
    fun `M send expected event W logErrorMessage(String)`() {
        // When
        testedLogger.logErrorMessage(fakeString, fakeSpan)

        // Then
        argumentCaptor<Map<Any, Any>> {
            verify(mockLogFeatureScope).sendEvent(capture())

            assertThat(firstValue["type"]).isEqualTo("span_log")
            assertThat(firstValue["loggerName"]).isEqualTo(TRACE_LOGGER_NAME)
            assertThat(firstValue["message"]).isEqualTo(fakeString)
            assertThat(firstValue["logStatus"]).isEqualTo(Log.ERROR)

            val attributes = firstValue["attributes"] as Map<*, *>
            assertThat(attributes[LogAttributes.DD_SPAN_ID]).isEqualTo(fakeSpan.context().spanId.toString())
            assertThat(attributes[LogAttributes.DD_TRACE_ID]).isEqualTo(fakeSpan.context().traceId.toHexString())
        }
    }

    @Test
    fun `M set expected tags W log(fakeThrowable)`() {
        // When
        testedLogger.log(fakeThrowable, mockSpan)

        // Then
        verify(mockSpan).isError = true
        verify(mockSpan).setTag(DatadogTracingConstants.Tags.KEY_ERROR_TYPE, fakeThrowable.javaClass.name)
        verify(mockSpan).setTag(DatadogTracingConstants.Tags.KEY_ERROR_MSG, fakeThrowable.message)
        verify(mockSpan).setTag(DatadogTracingConstants.Tags.KEY_ERROR_STACK, fakeThrowable.loggableStackTrace())
    }

    @Test
    fun `M send expected event W logErrorMessage(Map)`(forge: Forge) {
        // Given
        val fakeAttributes = forge.aMap { aString() to aString() }

        // When
        testedLogger.log(fakeAttributes, fakeSpan)

        // Then
        argumentCaptor<Map<Any, Any>> {
            verify(mockLogFeatureScope).sendEvent(capture())

            assertThat(firstValue["type"]).isEqualTo("span_log")
            assertThat(firstValue["loggerName"]).isEqualTo(TRACE_LOGGER_NAME)
            assertThat(firstValue["message"]).isEqualTo(DEFAULT_EVENT_MESSAGE)
            assertThat(firstValue["logStatus"]).isEqualTo(Log.VERBOSE)

            val attributes = (firstValue["attributes"] as Map<*, *>).toMutableMap()
            assertThat(attributes[LogAttributes.DD_SPAN_ID]).isEqualTo(fakeSpan.context().spanId.toString())
            assertThat(attributes[LogAttributes.DD_TRACE_ID]).isEqualTo(fakeSpan.context().traceId.toHexString())
            assertThat(attributes).containsAllEntriesOf(fakeAttributes)
        }
    }
}
