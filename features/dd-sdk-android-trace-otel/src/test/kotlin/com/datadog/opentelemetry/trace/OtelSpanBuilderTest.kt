/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.opentelemetry.trace

import com.datadog.android.api.InternalLogger
import com.datadog.android.trace.api.DatadogTracingConstants
import com.datadog.android.trace.api.span.DatadogSpan
import com.datadog.android.trace.api.span.DatadogSpanBuilder
import com.datadog.android.trace.api.tracer.DatadogTracer
import com.datadog.android.trace.opentelemetry.utils.forge.Configurator
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import io.opentelemetry.api.common.AttributeKey
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class OtelSpanBuilderTest {

    private lateinit var testedBuilder: OtelSpanBuilder

    @Mock
    lateinit var mockDelegateBuilder: DatadogSpanBuilder

    @Mock
    lateinit var mockAgentTracer: DatadogTracer

    @Mock
    lateinit var mockLogger: InternalLogger

    @Mock
    lateinit var mockAgentSpan: DatadogSpan

    // region Init
    @BeforeEach
    fun `set up`() {
        whenever(mockDelegateBuilder.start()).thenReturn(mockAgentSpan)
        testedBuilder = OtelSpanBuilder(mockDelegateBuilder, mockAgentTracer, mockLogger)
    }

    // endregion

    // region setAttribute

    @Test
    fun `M add attribute W setAttribute`() {
        // When
        testedBuilder.setAttribute("string", "b")
        testedBuilder.setAttribute("empty_string", "")
        testedBuilder.setAttribute("number", 2L)
        testedBuilder.setAttribute("boolean", false)

        // Then
        verify(mockDelegateBuilder).withTag("string", "b")
        verify(mockDelegateBuilder).withTag("empty_string", "")
        verify(mockDelegateBuilder).withTag("number", 2L)
        verify(mockDelegateBuilder).withTag("boolean", false)
    }

    @Test
    fun `M add attribute W setAttribute with reserved string key`() {
        // When
        testedBuilder.setAttribute(OtelConventions.OPERATION_NAME_SPECIFIC_ATTRIBUTE, "op_name")
        testedBuilder.setAttribute(OtelConventions.ANALYTICS_EVENT_SPECIFIC_ATTRIBUTES, "true")

        // Then
        verifyNoInteractions(mockDelegateBuilder)
        testedBuilder.startSpan()

        verify(mockAgentSpan, times(1)).operationName = "op_name"
        verify(mockAgentSpan).setMetric(DatadogTracingConstants.Tags.KEY_ANALYTICS_SAMPLE_RATE, 1)
    }

    @Test
    fun `M add attribute W setAttribute with reserved attribute key`() {
        // When
        testedBuilder.setAttribute(
            AttributeKey.stringKey(OtelConventions.OPERATION_NAME_SPECIFIC_ATTRIBUTE), "op_name")
        testedBuilder.setAttribute(
            AttributeKey.stringKey(OtelConventions.ANALYTICS_EVENT_SPECIFIC_ATTRIBUTES), "false")

        // Then
        verifyNoInteractions(mockDelegateBuilder)
        testedBuilder.startSpan()

        verify(mockAgentSpan, times(1)).operationName = "op_name"
        verify(mockAgentSpan).setMetric(DatadogTracingConstants.Tags.KEY_ANALYTICS_SAMPLE_RATE, 0)
    }

    // endregion
}
