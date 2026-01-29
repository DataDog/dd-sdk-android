/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.trace.internal

import com.datadog.android.api.instrumentation.network.ExtendedRequestInfo
import com.datadog.android.api.instrumentation.network.HttpRequestInfo
import com.datadog.android.api.instrumentation.network.HttpRequestInfoModifier
import com.datadog.android.trace.TraceContextInjection
import com.datadog.android.trace.TracingHeaderType
import com.datadog.android.trace.api.DatadogTracingConstants.PrioritySampling
import com.datadog.android.trace.api.propagation.DatadogPropagation
import com.datadog.android.trace.api.span.DatadogSpan
import com.datadog.android.trace.api.span.DatadogSpanContext
import com.datadog.android.trace.api.trace.DatadogTraceId
import com.datadog.android.trace.api.tracer.DatadogTracer
import com.datadog.android.trace.internal.net.TraceContext
import com.datadog.android.utils.forge.Configurator
import com.datadog.trace.core.propagation.B3HttpCodec
import com.datadog.trace.core.propagation.DatadogHttpCodec
import com.datadog.trace.core.propagation.ExtractedContext
import com.datadog.trace.core.propagation.W3CHttpCodec
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.stream.Stream

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class DatadogPropagationHelperTest {

    private lateinit var testedHelper: DatadogPropagationHelper

    @Mock
    lateinit var mockTracer: DatadogTracer

    @Mock
    lateinit var mockSpan: DatadogSpan

    @Mock
    lateinit var mockSpanContext: DatadogSpanContext

    @Mock
    lateinit var mockTraceId: DatadogTraceId

    @Mock
    lateinit var mockRequestModifier: HttpRequestInfoModifier

    @Mock
    lateinit var mockPropagation: DatadogPropagation

    @BeforeEach
    fun `set up`() {
        testedHelper = DatadogPropagationHelper()

        whenever(mockSpan.context()) doReturn mockSpanContext
        whenever(mockSpanContext.traceId) doReturn mockTraceId
        whenever(mockTracer.propagate()) doReturn mockPropagation
        whenever(mockRequestModifier.result()) doReturn mock()
    }

    @Test
    fun `M return true W isExtractedContext() {extracted context}`(forge: Forge) {
        // Given
        val extractedContext = ExtractedContext(
            forge.getForgery(),
            forge.aLong(),
            forge.anInt(),
            null,
            null,
            null
        )
        val context = DatadogSpanContextAdapter(extractedContext)

        // When
        val result = testedHelper.isExtractedContext(context)

        // Then
        assertThat(result).isTrue()
    }

    @Test
    fun `M return false W isExtractedContext() {non-extracted DatadogSpanContextAdapter}`() {
        // Given
        val mockAgentSpanContext: com.datadog.trace.bootstrap.instrumentation.api.AgentSpan.Context = mock()
        val context = DatadogSpanContextAdapter(mockAgentSpanContext)

        // When
        val result = testedHelper.isExtractedContext(context)

        // Then
        assertThat(result).isFalse()
    }

    @Test
    fun `M return false W isExtractedContext() {non-DatadogSpanContextAdapter}`(
        @Forgery fakeContext: DatadogSpanContext
    ) {
        // When
        val result = testedHelper.isExtractedContext(fakeContext)

        // Then
        assertThat(result).isFalse()
    }

    @Test
    fun `M add TraceContext tag W setTraceContext()`(
        @StringForgery fakeTraceId: String,
        @StringForgery fakeSpanId: String,
        @IntForgery fakeSamplingPriority: Int
    ) {
        // When
        testedHelper.setTraceContext(mockRequestModifier, fakeTraceId, fakeSpanId, fakeSamplingPriority)

        // Then
        verify(mockRequestModifier).addTag(
            eq(TraceContext::class.java),
            eq(TraceContext(fakeTraceId, fakeSpanId, fakeSamplingPriority))
        )
    }

    @Test
    fun `M create extracted context W createExtractedContext()`(
        @StringForgery(regex = "[a-f0-9]{32}") fakeTraceId: String,
        @StringForgery(regex = "[a-f0-9]{16}") fakeSpanId: String,
        @IntForgery fakeSamplingPriority: Int
    ) {
        // When
        val result = testedHelper.createExtractedContext(fakeTraceId, fakeSpanId, fakeSamplingPriority)

        // Then
        assertThat(result).isInstanceOf(DatadogSpanContextAdapter::class.java)
        assertThat(testedHelper.isExtractedContext(result)).isTrue()
    }

    @Test
    fun `M return context with correct traceId W createExtractedContext()`(
        @StringForgery(regex = "[a-f0-9]{32}") fakeTraceId: String,
        @StringForgery(regex = "[a-f0-9]{16}") fakeSpanId: String,
        @IntForgery fakeSamplingPriority: Int
    ) {
        // When
        val result = testedHelper.createExtractedContext(fakeTraceId, fakeSpanId, fakeSamplingPriority)

        // Then
        assertThat(result.traceId.toHexString()).isEqualTo(fakeTraceId)
    }

    @Test
    fun `M return context with correct samplingPriority W createExtractedContext()`(
        @StringForgery(regex = "[a-f0-9]{32}") fakeTraceId: String,
        @StringForgery(regex = "[a-f0-9]{16}") fakeSpanId: String,
        @IntForgery fakeSamplingPriority: Int
    ) {
        // When
        val result = testedHelper.createExtractedContext(fakeTraceId, fakeSpanId, fakeSamplingPriority)

        // Then
        assertThat(result.samplingPriority).isEqualTo(fakeSamplingPriority)
    }

    @Test
    fun `M return span context from tag W extractParentContext() {DatadogSpan tag}`() {
        // Given
        val mockRequest = createMockRequestWithTags(datadogSpan = mockSpan)
        whenever(
            mockPropagation.extract(
                eq(mockRequest),
                any<
                    (
                        HttpRequestInfo,
                        (String, String) -> Boolean
                    ) -> Unit
                    >()
            )
        )
            .doReturn(null)

        // When
        val result = testedHelper.extractParentContext(mockTracer, mockRequest)

        // Then
        assertThat(result).isSameAs(mockSpanContext)
    }

    @Test
    fun `M return extracted header context W extractParentContext() {extracted context from headers}`(
        forge: Forge
    ) {
        // Given
        val mockRequest = createMockRequestWithTags()
        val extractedContext = ExtractedContext(
            forge.getForgery(),
            forge.aLong(),
            forge.anInt(),
            null,
            null,
            null
        )
        val extractedSpanContext = DatadogSpanContextAdapter(extractedContext)

        whenever(
            mockPropagation.extract(eq(mockRequest), any<(HttpRequestInfo, (String, String) -> Boolean) -> Unit>())
        )
            .doReturn(extractedSpanContext)

        // When
        val result = testedHelper.extractParentContext(mockTracer, mockRequest)

        // Then
        assertThat(result).isSameAs(extractedSpanContext)
    }

    @Test
    fun `M return tag context W extractParentContext() {non-extracted header context}`() {
        // Given
        val mockRequest = createMockRequestWithTags(datadogSpan = mockSpan)
        val nonExtractedContext: DatadogSpanContext = mock()

        whenever(
            mockPropagation.extract(eq(mockRequest), any<(HttpRequestInfo, (String, String) -> Boolean) -> Unit>())
        )
            .doReturn(nonExtractedContext)

        // When
        val result = testedHelper.extractParentContext(mockTracer, mockRequest)

        // Then
        assertThat(result).isSameAs(mockSpanContext)
    }

    @Test
    fun `M return TraceContext W extractParentContext() {TraceContext tag, no span}`(
        @StringForgery(regex = "[a-f0-9]{32}") fakeTraceId: String,
        @StringForgery(regex = "[a-f0-9]{16}") fakeSpanId: String,
        @IntForgery fakeSamplingPriority: Int
    ) {
        // Given
        val traceContext = TraceContext(fakeTraceId, fakeSpanId, fakeSamplingPriority)
        val mockRequest = createMockRequestWithTags(traceContext = traceContext)

        whenever(
            mockPropagation.extract(eq(mockRequest), any<(HttpRequestInfo, (String, String) -> Boolean) -> Unit>())
        )
            .doReturn(null)

        // When
        val result = testedHelper.extractParentContext(mockTracer, mockRequest)

        // Then
        assertThat(result).isNotNull
        assertThat(result!!.traceId.toHexString()).isEqualTo(fakeTraceId)
        assertThat(result.samplingPriority).isEqualTo(fakeSamplingPriority)
    }

    @Test
    fun `M return null W extractParentContext() {no tags, no headers}`() {
        // Given
        val mockRequest = createMockRequestWithTags()
        whenever(
            mockPropagation.extract(
                eq(mockRequest),
                any<
                    (
                        HttpRequestInfo,
                        (String, String) -> Boolean
                    ) -> Unit
                    >()
            )
        )
            .doReturn(null)

        // When
        val result = testedHelper.extractParentContext(mockTracer, mockRequest)

        // Then
        assertThat(result).isNull()
    }

    @Test
    fun `M return true W extractSamplingDecision() {Datadog header sampler keep}`() {
        // Given
        val headers = mapOf(
            DatadogHttpCodec.SAMPLING_PRIORITY_KEY to listOf(PrioritySampling.SAMPLER_KEEP.toString())
        )
        val mockRequest = createMockRequestWithTags(headers = headers)

        // When
        val result = testedHelper.extractSamplingDecision(mockRequest)

        // Then
        assertThat(result).isTrue()
    }

    @Test
    fun `M return true W extractSamplingDecision() {Datadog header user keep}`() {
        // Given
        val headers = mapOf(
            DatadogHttpCodec.SAMPLING_PRIORITY_KEY to listOf(PrioritySampling.USER_KEEP.toString())
        )
        val mockRequest = createMockRequestWithTags(headers = headers)

        // When
        val result = testedHelper.extractSamplingDecision(mockRequest)

        // Then
        assertThat(result).isTrue()
    }

    @Test
    fun `M return false W extractSamplingDecision() {Datadog header sampler drop}`() {
        // Given
        val headers = mapOf(
            DatadogHttpCodec.SAMPLING_PRIORITY_KEY to listOf(PrioritySampling.SAMPLER_DROP.toString())
        )
        val mockRequest = createMockRequestWithTags(headers = headers)

        // When
        val result = testedHelper.extractSamplingDecision(mockRequest)

        // Then
        assertThat(result).isFalse()
    }

    @Test
    fun `M return null W extractSamplingDecision() {Datadog header unset}`() {
        // Given
        val headers = mapOf(
            DatadogHttpCodec.SAMPLING_PRIORITY_KEY to listOf(PrioritySampling.UNSET.toString())
        )
        val mockRequest = createMockRequestWithTags(headers = headers)

        // When
        val result = testedHelper.extractSamplingDecision(mockRequest)

        // Then
        assertThat(result).isNull()
    }

    @Test
    fun `M return true W extractSamplingDecision() {B3Multi header sampled}`() {
        // Given
        val headers = mapOf(
            B3HttpCodec.SAMPLING_PRIORITY_KEY to listOf("1")
        )
        val mockRequest = createMockRequestWithTags(headers = headers)

        // When
        val result = testedHelper.extractSamplingDecision(mockRequest)

        // Then
        assertThat(result).isTrue()
    }

    @Test
    fun `M return false W extractSamplingDecision() {B3Multi header not sampled}`() {
        // Given
        val headers = mapOf(
            B3HttpCodec.SAMPLING_PRIORITY_KEY to listOf("0")
        )
        val mockRequest = createMockRequestWithTags(headers = headers)

        // When
        val result = testedHelper.extractSamplingDecision(mockRequest)

        // Then
        assertThat(result).isFalse()
    }

    @Test
    fun `M return null W extractSamplingDecision() {B3Multi header invalid value}`() {
        // Given
        val headers = mapOf(
            B3HttpCodec.SAMPLING_PRIORITY_KEY to listOf("invalid")
        )
        val mockRequest = createMockRequestWithTags(headers = headers)

        // When
        val result = testedHelper.extractSamplingDecision(mockRequest)

        // Then
        assertThat(result).isNull()
    }

    @Test
    fun `M return true W extractSamplingDecision() {B3 single header sampled}`() {
        // Given
        val headers = mapOf(
            B3HttpCodec.B3_KEY to listOf("80f198ee56343ba864fe8b2a57d3eff7-e457b5a2e4d86bd1-1")
        )
        val mockRequest = createMockRequestWithTags(headers = headers)

        // When
        val result = testedHelper.extractSamplingDecision(mockRequest)

        // Then
        assertThat(result).isTrue()
    }

    @Test
    fun `M return true W extractSamplingDecision() {B3 single header debug}`() {
        // Given
        val headers = mapOf(
            B3HttpCodec.B3_KEY to listOf("80f198ee56343ba864fe8b2a57d3eff7-e457b5a2e4d86bd1-d")
        )
        val mockRequest = createMockRequestWithTags(headers = headers)

        // When
        val result = testedHelper.extractSamplingDecision(mockRequest)

        // Then
        assertThat(result).isTrue()
    }

    @Test
    fun `M return false W extractSamplingDecision() {B3 single header not sampled}`() {
        // Given
        val headers = mapOf(
            B3HttpCodec.B3_KEY to listOf("80f198ee56343ba864fe8b2a57d3eff7-e457b5a2e4d86bd1-0")
        )
        val mockRequest = createMockRequestWithTags(headers = headers)

        // When
        val result = testedHelper.extractSamplingDecision(mockRequest)

        // Then
        assertThat(result).isFalse()
    }

    @Test
    fun `M return false W extractSamplingDecision() {B3 single header deny all}`() {
        // Given
        val headers = mapOf(
            B3HttpCodec.B3_KEY to listOf("0")
        )
        val mockRequest = createMockRequestWithTags(headers = headers)

        // When
        val result = testedHelper.extractSamplingDecision(mockRequest)

        // Then
        assertThat(result).isFalse()
    }

    @Test
    fun `M return true W extractSamplingDecision() {W3C traceparent sampled}`() {
        // Given
        val headers = mapOf(
            W3CHttpCodec.TRACE_PARENT_KEY to listOf("00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01")
        )
        val mockRequest = createMockRequestWithTags(headers = headers)

        // When
        val result = testedHelper.extractSamplingDecision(mockRequest)

        // Then
        assertThat(result).isTrue()
    }

    @Test
    fun `M return false W extractSamplingDecision() {W3C traceparent not sampled}`() {
        // Given
        val headers = mapOf(
            W3CHttpCodec.TRACE_PARENT_KEY to listOf("00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-00")
        )
        val mockRequest = createMockRequestWithTags(headers = headers)

        // When
        val result = testedHelper.extractSamplingDecision(mockRequest)

        // Then
        assertThat(result).isFalse()
    }

    @Test
    fun `M return null W extractSamplingDecision() {W3C traceparent invalid flags}`() {
        // Given
        val headers = mapOf(
            W3CHttpCodec.TRACE_PARENT_KEY to listOf("00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-xx")
        )
        val mockRequest = createMockRequestWithTags(headers = headers)

        // When
        val result = testedHelper.extractSamplingDecision(mockRequest)

        // Then
        assertThat(result).isNull()
    }

    @Test
    fun `M return sampled from span W extractSamplingDecision() {DatadogSpan tag with positive priority}`() {
        // Given
        whenever(mockSpanContext.samplingPriority) doReturn PrioritySampling.SAMPLER_KEEP
        val mockRequest = createMockRequestWithTags(datadogSpan = mockSpan)

        // When
        val result = testedHelper.extractSamplingDecision(mockRequest)

        // Then
        assertThat(result).isTrue()
    }

    @Test
    fun `M return not sampled from span W extractSamplingDecision() {DatadogSpan tag with zero priority}`() {
        // Given
        whenever(mockSpanContext.samplingPriority) doReturn PrioritySampling.SAMPLER_DROP
        val mockRequest = createMockRequestWithTags(datadogSpan = mockSpan)

        // When
        val result = testedHelper.extractSamplingDecision(mockRequest)

        // Then
        assertThat(result).isFalse()
    }

    @Test
    fun `M return sampled from TraceContext W extractSamplingDecision() {TraceContext tag positive priority}`() {
        // Given
        val traceContext = TraceContext("traceId", "spanId", PrioritySampling.USER_KEEP)
        val mockRequest = createMockRequestWithTags(traceContext = traceContext)

        // When
        val result = testedHelper.extractSamplingDecision(mockRequest)

        // Then
        assertThat(result).isTrue()
    }

    @Test
    fun `M return not sampled W extractSamplingDecision() {TraceContext tag zero priority}`() {
        // Given
        val traceContext = TraceContext("traceId", "spanId", PrioritySampling.SAMPLER_DROP)
        val mockRequest = createMockRequestWithTags(traceContext = traceContext)

        // When
        val result = testedHelper.extractSamplingDecision(mockRequest)

        // Then
        assertThat(result).isFalse()
    }

    @Test
    fun `M return null W extractSamplingDecision() {TraceContext tag unset priority}`() {
        // Given
        val traceContext = TraceContext("traceId", "spanId", PrioritySampling.UNSET)
        val mockRequest = createMockRequestWithTags(traceContext = traceContext)

        // When
        val result = testedHelper.extractSamplingDecision(mockRequest)

        // Then
        assertThat(result).isNull()
    }

    @Test
    fun `M return null W extractSamplingDecision() {no headers, no tags}`() {
        // Given
        val mockRequest = createMockRequestWithTags()

        // When
        val result = testedHelper.extractSamplingDecision(mockRequest)

        // Then
        assertThat(result).isNull()
    }

    @Test
    fun `M prioritize header over tag W extractSamplingDecision() {both present, different decisions}`() {
        // Given
        val headers = mapOf(
            DatadogHttpCodec.SAMPLING_PRIORITY_KEY to listOf(PrioritySampling.SAMPLER_DROP.toString())
        )
        whenever(mockSpanContext.samplingPriority) doReturn PrioritySampling.SAMPLER_KEEP
        val mockRequest = createMockRequestWithTags(headers = headers, datadogSpan = mockSpan)

        // When
        val result = testedHelper.extractSamplingDecision(mockRequest)

        // Then
        assertThat(result).isFalse()
    }

    @Test
    fun `M call tracer inject W propagateSampledHeaders()`() {
        // Given
        val tracingHeaderTypes = setOf(TracingHeaderType.DATADOG)

        // When
        testedHelper.propagateSampledHeaders(mockRequestModifier, mockTracer, mockSpan, tracingHeaderTypes)

        // Then
        verify(mockPropagation).inject(eq(mockSpanContext), eq(mockRequestModifier), any())
    }

    @ParameterizedTest
    @MethodSource("datadogHeaderKeys")
    fun `M replace Datadog headers W propagateSampledHeaders() {DATADOG header type}`(
        headerKey: String
    ) {
        // Given
        val tracingHeaderTypes = setOf(TracingHeaderType.DATADOG)
        val fakeValue = "test-value"
        setupInjectorCallback(headerKey, fakeValue)

        // When
        testedHelper.propagateSampledHeaders(mockRequestModifier, mockTracer, mockSpan, tracingHeaderTypes)

        // Then
        verify(mockRequestModifier).replaceHeader(headerKey, fakeValue)
    }

    @ParameterizedTest
    @MethodSource("datadogHeaderKeys")
    fun `M remove Datadog headers W propagateSampledHeaders() {no DATADOG header type}`(
        headerKey: String
    ) {
        // Given
        val tracingHeaderTypes = setOf(TracingHeaderType.B3)
        val fakeValue = "test-value"
        setupInjectorCallback(headerKey, fakeValue)

        // When
        testedHelper.propagateSampledHeaders(mockRequestModifier, mockTracer, mockSpan, tracingHeaderTypes)

        // Then
        verify(mockRequestModifier).removeHeader(headerKey)
        verify(mockRequestModifier, never()).replaceHeader(eq(headerKey), any())
    }

    @Test
    fun `M replace B3 header W propagateSampledHeaders() {B3 header type}`() {
        // Given
        val tracingHeaderTypes = setOf(TracingHeaderType.B3)
        val fakeValue = "test-b3-value"
        setupInjectorCallback(B3HttpCodec.B3_KEY, fakeValue)

        // When
        testedHelper.propagateSampledHeaders(mockRequestModifier, mockTracer, mockSpan, tracingHeaderTypes)

        // Then
        verify(mockRequestModifier).replaceHeader(B3HttpCodec.B3_KEY, fakeValue)
    }

    @Test
    fun `M remove B3 header W propagateSampledHeaders() {no B3 header type}`() {
        // Given
        val tracingHeaderTypes = setOf(TracingHeaderType.DATADOG)
        val fakeValue = "test-b3-value"
        setupInjectorCallback(B3HttpCodec.B3_KEY, fakeValue)

        // When
        testedHelper.propagateSampledHeaders(mockRequestModifier, mockTracer, mockSpan, tracingHeaderTypes)

        // Then
        verify(mockRequestModifier).removeHeader(B3HttpCodec.B3_KEY)
    }

    @ParameterizedTest
    @MethodSource("b3MultiHeaderKeys")
    fun `M replace B3Multi headers W propagateSampledHeaders() {B3MULTI header type}`(
        headerKey: String
    ) {
        // Given
        val tracingHeaderTypes = setOf(TracingHeaderType.B3MULTI)
        val fakeValue = "test-value"
        setupInjectorCallback(headerKey, fakeValue)

        // When
        testedHelper.propagateSampledHeaders(mockRequestModifier, mockTracer, mockSpan, tracingHeaderTypes)

        // Then
        verify(mockRequestModifier).replaceHeader(headerKey, fakeValue)
    }

    @ParameterizedTest
    @MethodSource("w3cHeaderKeys")
    fun `M replace W3C headers W propagateSampledHeaders() {TRACECONTEXT header type}`(
        headerKey: String
    ) {
        // Given
        val tracingHeaderTypes = setOf(TracingHeaderType.TRACECONTEXT)
        val fakeValue = "test-value"
        setupInjectorCallback(headerKey, fakeValue)

        // When
        testedHelper.propagateSampledHeaders(mockRequestModifier, mockTracer, mockSpan, tracingHeaderTypes)

        // Then
        verify(mockRequestModifier).replaceHeader(headerKey, fakeValue)
    }

    @ParameterizedTest
    @MethodSource("w3cHeaderKeys")
    fun `M remove W3C headers W propagateSampledHeaders() {no TRACECONTEXT header type}`(
        headerKey: String
    ) {
        // Given
        val tracingHeaderTypes = setOf(TracingHeaderType.DATADOG)
        val fakeValue = "test-value"
        setupInjectorCallback(headerKey, fakeValue)

        // When
        testedHelper.propagateSampledHeaders(mockRequestModifier, mockTracer, mockSpan, tracingHeaderTypes)

        // Then
        verify(mockRequestModifier).removeHeader(headerKey)
    }

    @Test
    fun `M remove Datadog headers W propagateNotSampledHeaders() {SAMPLED injection type}`() {
        // Given
        val tracingHeaderTypes = setOf(TracingHeaderType.DATADOG)

        // When
        testedHelper.propagateNotSampledHeaders(
            mockRequestModifier,
            mockTracer,
            mockSpan,
            tracingHeaderTypes,
            TraceContextInjection.SAMPLED,
            null
        )

        // Then
        DatadogPropagationHelper.DATADOG_CODEC_HEADERS.forEach { headerKey ->
            verify(mockRequestModifier).removeHeader(headerKey)
        }
    }

    @Test
    fun `M remove B3 header W propagateNotSampledHeaders() {SAMPLED injection type}`() {
        // Given
        val tracingHeaderTypes = setOf(TracingHeaderType.B3)

        // When
        testedHelper.propagateNotSampledHeaders(
            mockRequestModifier,
            mockTracer,
            mockSpan,
            tracingHeaderTypes,
            TraceContextInjection.SAMPLED,
            null
        )

        // Then
        verify(mockRequestModifier).removeHeader(B3HttpCodec.B3_KEY)
    }

    @Test
    fun `M remove B3Multi headers W propagateNotSampledHeaders() {SAMPLED injection type}`() {
        // Given
        val tracingHeaderTypes = setOf(TracingHeaderType.B3MULTI)

        // When
        testedHelper.propagateNotSampledHeaders(
            mockRequestModifier,
            mockTracer,
            mockSpan,
            tracingHeaderTypes,
            TraceContextInjection.SAMPLED,
            null
        )

        // Then
        DatadogPropagationHelper.B3M_CODEC_HEADERS.forEach { headerKey ->
            verify(mockRequestModifier).removeHeader(headerKey)
        }
    }

    @Test
    fun `M remove W3C headers W propagateNotSampledHeaders() {SAMPLED injection type}`() {
        // Given
        val tracingHeaderTypes = setOf(TracingHeaderType.TRACECONTEXT)

        // When
        testedHelper.propagateNotSampledHeaders(
            mockRequestModifier,
            mockTracer,
            mockSpan,
            tracingHeaderTypes,
            TraceContextInjection.SAMPLED,
            null
        )

        // Then
        DatadogPropagationHelper.W3C_CODEC_HEADERS.forEach { headerKey ->
            verify(mockRequestModifier).removeHeader(headerKey)
        }
    }

    @Test
    fun `M add drop sampling B3 header W propagateNotSampledHeaders() {ALL injection type, B3}`() {
        // Given
        val tracingHeaderTypes = setOf(TracingHeaderType.B3)

        // When
        testedHelper.propagateNotSampledHeaders(
            mockRequestModifier,
            mockTracer,
            mockSpan,
            tracingHeaderTypes,
            TraceContextInjection.ALL,
            null
        )

        // Then
        verify(mockRequestModifier).addHeader(B3HttpCodec.B3_KEY, DatadogPropagationHelper.B3_DROP_SAMPLING_DECISION)
    }

    @Test
    fun `M add drop sampling B3Multi header W propagateNotSampledHeaders() {ALL injection type, B3MULTI}`() {
        // Given
        val tracingHeaderTypes = setOf(TracingHeaderType.B3MULTI)

        // When
        testedHelper.propagateNotSampledHeaders(
            mockRequestModifier,
            mockTracer,
            mockSpan,
            tracingHeaderTypes,
            TraceContextInjection.ALL,
            null
        )

        // Then
        verify(mockRequestModifier).addHeader(
            B3HttpCodec.SAMPLING_PRIORITY_KEY,
            DatadogPropagationHelper.B3M_DROP_SAMPLING_DECISION
        )
    }

    private fun createMockRequestWithTags(
        headers: Map<String, List<String>> = emptyMap(),
        datadogSpan: DatadogSpan? = null,
        traceContext: TraceContext? = null
    ): HttpRequestInfo {
        return mock<HttpRequestInfoWithTags> {
            on { this.headers } doReturn headers
            on { tag(DatadogSpan::class.java) } doReturn datadogSpan
            on { tag(TraceContext::class.java) } doReturn traceContext
        }
    }

    private fun setupInjectorCallback(expectedKey: String, expectedValue: String) {
        whenever(
            mockPropagation.inject(
                eq(mockSpanContext),
                eq(mockRequestModifier),
                any<(HttpRequestInfoModifier, String, String) -> Unit>()
            )
        ).doAnswer { invocation ->
            @Suppress("UNCHECKED_CAST")
            val injector = invocation.arguments[2] as (HttpRequestInfoModifier, String, String) -> Unit
            injector(mockRequestModifier, expectedKey, expectedValue)
        }
    }

    private interface HttpRequestInfoWithTags : HttpRequestInfo, ExtendedRequestInfo

    companion object {
        @JvmStatic
        fun datadogHeaderKeys(): Stream<Arguments> = Stream.of(
            Arguments.of(DatadogHttpCodec.ORIGIN_KEY),
            Arguments.of(DatadogHttpCodec.SPAN_ID_KEY),
            Arguments.of(DatadogHttpCodec.TRACE_ID_KEY),
            Arguments.of(DatadogHttpCodec.DATADOG_TAGS_KEY),
            Arguments.of(DatadogHttpCodec.SAMPLING_PRIORITY_KEY)
        )

        @JvmStatic
        fun b3MultiHeaderKeys(): Stream<Arguments> = Stream.of(
            Arguments.of(B3HttpCodec.SPAN_ID_KEY),
            Arguments.of(B3HttpCodec.TRACE_ID_KEY),
            Arguments.of(B3HttpCodec.SAMPLING_PRIORITY_KEY)
        )

        @JvmStatic
        fun w3cHeaderKeys(): Stream<Arguments> = Stream.of(
            Arguments.of(W3CHttpCodec.TRACE_PARENT_KEY),
            Arguments.of(W3CHttpCodec.TRACE_STATE_KEY)
        )
    }
}
