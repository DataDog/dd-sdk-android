/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.trace.internal

import com.datadog.android.trace.api.span.DatadogSpan
import com.datadog.android.trace.api.span.DatadogSpanLink
import com.datadog.android.utils.forge.Configurator
import com.datadog.trace.bootstrap.instrumentation.api.AgentSpan
import com.datadog.trace.bootstrap.instrumentation.api.AgentSpanLink
import com.datadog.trace.bootstrap.instrumentation.api.AgentTracer
import fr.xgouchet.elmyr.annotation.DoubleForgery
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.LongForgery
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
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
class DatadogSpanBuilderAdapterTest {

    @StringForgery
    lateinit var fakeString: String

    @LongForgery
    private var fakeLong: Long = 0

    @DoubleForgery
    private var fakeDouble: Double = 0.0

    @Forgery
    lateinit var fakeDatadogSpanLink: DatadogSpanLink

    @Mock
    private lateinit var mockAgentSpanBuilderAdapter: AgentTracer.SpanBuilder

    @Mock
    private lateinit var mockAgentSpanContext: AgentSpan.Context

    @Mock
    private lateinit var mockSpanLogger: DatadogSpanLogger

    private lateinit var testedBuilderAdapter: DatadogSpanBuilderAdapter

    @BeforeEach
    fun `set up`() {
        testedBuilderAdapter = DatadogSpanBuilderAdapter(mockAgentSpanBuilderAdapter, mockSpanLogger)
    }

    @Test
    fun `M delegate ignoreActiveSpan W ignoreActiveSpan is called`() {
        // When
        testedBuilderAdapter.ignoreActiveSpan()

        // Then
        verify(mockAgentSpanBuilderAdapter).ignoreActiveSpan()
    }

    @Test
    fun `M return DatadogSpan W start() is called`() {
        // Given
        whenever(mockAgentSpanBuilderAdapter.start()).thenReturn(mock<AgentSpan>())

        // When
        val actual = testedBuilderAdapter.start()

        // Then
        assertThat(actual).isInstanceOf(DatadogSpan::class.java)
    }

    @Test
    fun `M delegate withOrigin W withOrigin is called`() {
        // When
        val actual = testedBuilderAdapter.withOrigin(fakeString)

        // Then
        assertThat(actual).isEqualTo(testedBuilderAdapter)
        verify(mockAgentSpanBuilderAdapter).withOrigin(fakeString)
    }

    @Test
    fun `M delegate withStartTimestamp W withStartTimestamp is called`() {
        // When
        val actual = testedBuilderAdapter.withStartTimestamp(fakeLong)

        // Then
        assertThat(actual).isEqualTo(testedBuilderAdapter)
        verify(mockAgentSpanBuilderAdapter).withStartTimestamp(fakeLong)
    }

    @Test
    fun `M delegate withTag W withTag(String, Long) is called`() {
        // When
        testedBuilderAdapter.withTag(fakeString, fakeLong)

        // Then
        verify(mockAgentSpanBuilderAdapter).withTag(fakeString, fakeLong)
    }

    @Test
    fun `M delegate withTag W withTag(String, Any) is called`() {
        // Given
        val value = Any()

        // When
        testedBuilderAdapter.withTag(fakeString, value)

        // Then
        verify(mockAgentSpanBuilderAdapter).withTag(fakeString, value)
    }

    @Test
    fun `M delegate withTag W withTag(String, Double) is called`() {
        // When
        testedBuilderAdapter.withTag(fakeString, fakeDouble)

        // Then
        verify(mockAgentSpanBuilderAdapter).withTag(fakeString, fakeDouble)
    }

    @Test
    fun `M delegate withTag W withLink(DatadogSpanLink) is called`() {
        // When
        testedBuilderAdapter.withLink(fakeDatadogSpanLink)

        // Then
        argumentCaptor<AgentSpanLink> {
            verify(mockAgentSpanBuilderAdapter).withLink(capture())
            assertThat(firstValue.spanId()).isEqualTo(fakeDatadogSpanLink.spanId)
            assertThat(firstValue.traceId().toString()).isEqualTo(fakeDatadogSpanLink.traceId.toString())
            assertThat(firstValue.attributes().asMap()).isEqualTo(fakeDatadogSpanLink.attributes)
        }
    }

    @Test
    fun `M delegate withResourceName W withResourceName(String) is called`() {
        // When
        testedBuilderAdapter.withResourceName(fakeString)

        // Then
        verify(mockAgentSpanBuilderAdapter).withResourceName(fakeString)
    }

    @Test
    fun `M delegate asChildOf W withParentContext(DatadogSpanContextAdapter) is called`() {
        // When
        testedBuilderAdapter.withParentContext(DatadogSpanContextAdapter(mockAgentSpanContext))

        // Then
        verify(mockAgentSpanBuilderAdapter).asChildOf(mockAgentSpanContext)
    }

    @Test
    fun `M delegate asChildOf W withParentSpan(DatadogSpanContextAdapter) is called`() {
        // Given
        val mockSpan = mock<DatadogSpan> {
            on { context() } doReturn DatadogSpanContextAdapter(mockAgentSpanContext)
        }

        // When
        testedBuilderAdapter.withParentSpan(mockSpan)

        // Then
        verify(mockAgentSpanBuilderAdapter).asChildOf(mockAgentSpanContext)
    }
}
