/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.trace.internal.net

import com.datadog.android.api.instrumentation.network.HttpRequestInfo
import com.datadog.android.api.instrumentation.network.HttpRequestInfoBuilder
import com.datadog.android.trace.api.span.DatadogSpan
import com.datadog.android.trace.api.span.DatadogSpanContext
import com.datadog.android.trace.api.trace.DatadogTraceId
import com.datadog.android.utils.forge.Configurator
import fr.xgouchet.elmyr.annotation.FloatForgery
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class RequestTracingStateTest {

    @Mock
    lateinit var mockRequestBuilder: HttpRequestInfoBuilder

    @Mock
    lateinit var mockRequestInfo: HttpRequestInfo

    @Mock
    lateinit var mockSpan: DatadogSpan

    @Mock
    lateinit var mockSpanContext: DatadogSpanContext

    @StringForgery
    lateinit var fakeTraceIdKey: String

    @StringForgery
    lateinit var fakeSpanIdKey: String

    @StringForgery
    lateinit var fakeRulePsrKey: String

    @Test
    fun `M return request info W createModifiedRequestInfo()`() {
        // Given
        whenever(mockRequestBuilder.build()) doReturn mockRequestInfo
        val state = RequestTracingState(tracedRequestInfoBuilder = mockRequestBuilder)

        // When / Then
        assertThat(state.createModifiedRequestInfo()).isSameAs(mockRequestInfo)
    }

    @Test
    fun `M return empty map W toAttributesMap() { null state }`() {
        // Given
        val state: RequestTracingState? = null

        // When
        val result = state.toAttributesMap(fakeTraceIdKey, fakeSpanIdKey, fakeRulePsrKey)

        // Then
        assertThat(result).isEmpty()
    }

    @Test
    fun `M return empty map W toAttributesMap() { span is null }`() {
        // Given
        val state = RequestTracingState(
            tracedRequestInfoBuilder = mockRequestBuilder,
            isSampled = true,
            span = null
        )

        // When
        val result = state.toAttributesMap(fakeTraceIdKey, fakeSpanIdKey, fakeRulePsrKey)

        // Then
        assertThat(result).isEmpty()
    }

    @Test
    fun `M return empty map W toAttributesMap() { not sampled }`() {
        // Given
        val state = RequestTracingState(
            tracedRequestInfoBuilder = mockRequestBuilder,
            isSampled = false,
            span = mockSpan
        )

        // When
        val result = state.toAttributesMap(fakeTraceIdKey, fakeSpanIdKey, fakeRulePsrKey)

        // Then
        assertThat(result).isEmpty()
    }

    @Test
    fun `M return attributes map W toAttributesMap() { sampled with span }`(
        @LongForgery fakeSpanId: Long,
        @FloatForgery(min = 0f, max = 100f) fakeSampleRate: Float,
        @StringForgery fakeTraceIdHex: String
    ) {
        // Given
        val mockTraceId: DatadogTraceId = mock()
        whenever(mockTraceId.toHexString()) doReturn fakeTraceIdHex
        whenever(mockSpan.context()) doReturn mockSpanContext
        whenever(mockSpanContext.traceId) doReturn mockTraceId
        whenever(mockSpanContext.spanId) doReturn fakeSpanId

        val state = RequestTracingState(
            tracedRequestInfoBuilder = mockRequestBuilder,
            isSampled = true,
            span = mockSpan,
            sampleRate = fakeSampleRate
        )

        // When
        val result = state.toAttributesMap(fakeTraceIdKey, fakeSpanIdKey, fakeRulePsrKey)

        // Then
        assertThat(result).containsEntry(fakeTraceIdKey, fakeTraceIdHex)
        assertThat(result).containsEntry(fakeSpanIdKey, fakeSpanId.toString())
        assertThat(result).containsEntry(fakeRulePsrKey, fakeSampleRate / 100f)
    }

    @Test
    fun `M use zero sample rate W toAttributesMap() { sampleRate is null }`(
        @LongForgery fakeSpanId: Long,
        @StringForgery fakeTraceIdHex: String
    ) {
        // Given
        val mockTraceId: DatadogTraceId = mock()
        whenever(mockTraceId.toHexString()) doReturn fakeTraceIdHex
        whenever(mockSpan.context()) doReturn mockSpanContext
        whenever(mockSpanContext.traceId) doReturn mockTraceId
        whenever(mockSpanContext.spanId) doReturn fakeSpanId

        val state = RequestTracingState(
            tracedRequestInfoBuilder = mockRequestBuilder,
            isSampled = true,
            span = mockSpan,
            sampleRate = null
        )

        // When
        val result = state.toAttributesMap(fakeTraceIdKey, fakeSpanIdKey, fakeRulePsrKey)

        // Then
        assertThat(result).containsEntry(fakeRulePsrKey, 0f)
    }
}
