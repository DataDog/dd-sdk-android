/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.trace.internal

import com.datadog.android.api.InternalLogger
import com.datadog.android.trace.api.span.DatadogSpanContext
import com.datadog.android.trace.api.trace.DatadogTraceId
import com.datadog.android.trace.utils.verifyLog
import com.datadog.trace.bootstrap.instrumentation.api.AgentPropagation
import com.datadog.trace.bootstrap.instrumentation.api.AgentSpan
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@Extensions(
    ExtendWith(MockitoExtension::class)
)
internal class DatadogPropagationAdapterTest {

    private lateinit var testedPropagation: DatadogPropagationAdapter

    @Mock
    lateinit var mockAgentPropagation: AgentPropagation

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @Mock
    lateinit var mockAgentSpanContext: AgentSpan.Context

    @BeforeEach
    fun `set up`() {
        testedPropagation = DatadogPropagationAdapter(
            mockInternalLogger,
            mockAgentPropagation
        )
    }

    @Test
    fun `M report an error W inject { unsupported DatadogSpanContext implementation provided }`() {
        // Given
        val carrier = Any()

        // When
        testedPropagation.inject(UnsupportedDatadogSpanContextImplementation(), carrier) { _, _, _ -> }

        // Then
        mockInternalLogger.verifyLog(
            InternalLogger.Level.ERROR,
            listOf(
                InternalLogger.Target.MAINTAINER,
                InternalLogger.Target.USER
            ),
            "DatadogPropagationAdapter supports only DatadogSpanContextAdapter instancies for injection " +
                "but UnsupportedDatadogSpanContextImplementation is given"

        )
    }

    @Test
    fun `M delegate to AgentPropagation W inject `() {
        // Given
        val carrier = Any()
        val mockContext: DatadogSpanContextAdapter = mock {
            on { mock.delegate } doReturn mockAgentSpanContext
        }

        // When
        val setter: (carrier: Any, key: String, value: String) -> Unit = { _, _, _ -> }
        testedPropagation.inject(mockContext, carrier, setter)

        // Then
        verify(mockAgentPropagation).inject(eq(mockAgentSpanContext), any<Any>(), any())
    }

    @Test
    fun `M delegate to AgentPropagation W extract `() {
        // Given
        val carrier = Any()
        val getter = { _: Any, _: (String, String) -> Boolean -> }
        // When
        testedPropagation.extract(carrier, getter)

        // Then
        verify(mockAgentPropagation).extract(eq(carrier), any())
    }

    @Test
    fun `M return DatadogSpanContextAdapter W extract `() {
        // Given
        val carrier = Any()
        val expectedContext = mock<AgentSpan.Context.Extracted>()
        val getter = { _: Any, _: (String, String) -> Boolean -> }
        whenever(mockAgentPropagation.extract(eq(carrier), any())).doReturn(expectedContext)

        // When
        val actual = testedPropagation.extract(carrier, getter) as DatadogSpanContextAdapter

        // Then
        assertThat(actual.delegate).isEqualTo(expectedContext)
    }

    private class UnsupportedDatadogSpanContextImplementation : DatadogSpanContext {
        override val traceId: DatadogTraceId
            get() = TODO("Not yet implemented")
        override val spanId: Long
            get() = TODO("Not yet implemented")
        override val samplingPriority: Int
            get() = TODO("Not yet implemented")
        override val tags: Map<String?, Any?>
            get() = TODO("Not yet implemented")

        override fun setSamplingPriority(samplingPriority: Int): Boolean = TODO("Not yet implemented")
        override fun setMetric(key: CharSequence?, value: Double): Unit = TODO("Not yet implemented")
    }
}
