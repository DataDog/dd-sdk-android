/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.opentelemetry.trace

import com.datadog.android.api.InternalLogger
import com.datadog.android.utils.forge.Configurator
import com.datadog.trace.bootstrap.instrumentation.api.AgentTracer
import com.datadog.trace.bootstrap.instrumentation.api.AgentTracer.TracerAPI
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import io.opentelemetry.api.trace.SpanBuilder
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class OtelTracerTest {

    private lateinit var testedTracer: OtelTracer

    @Mock
    lateinit var mockDelegateTracer: TracerAPI

    @StringForgery
    lateinit var fakeIntstrumentationName: String

    @StringForgery
    lateinit var fakeSpanName: String

    @Mock
    lateinit var mockLogger: InternalLogger

    @Mock
    lateinit var mockDelegateSpanBuilder: AgentTracer.SpanBuilder

    // region Unit Tests

    @BeforeEach
    fun `set up`() {
        whenever(mockDelegateSpanBuilder.withResourceName(any())).thenReturn(mockDelegateSpanBuilder)
        whenever(mockDelegateTracer.buildSpan(any(), any())).thenReturn(mockDelegateSpanBuilder)
        testedTracer = OtelTracer(fakeIntstrumentationName, mockDelegateTracer, mockLogger)
    }

    @Test
    fun `M build a SpanBuilder W spanBuilder() {`() {
        // When
        val builder = testedTracer.spanBuilder(fakeSpanName)

        // Then
        assertThat(builder).isInstanceOf(OtelSpanBuilder::class.java)
    }

    @Test
    fun `M decorate the SpanBuilder W spanBuilder() { decorator provided }{`() {
        // Given
        val mockDecoratedSpanBuilder: SpanBuilder = mock()
        val decorator: (SpanBuilder) -> SpanBuilder = { mockDecoratedSpanBuilder }
        testedTracer = OtelTracer(fakeIntstrumentationName, mockDelegateTracer, mockLogger, decorator)

        // When
        val builder = testedTracer.spanBuilder(fakeSpanName)

        // Then
        assertThat(builder).isSameAs(mockDecoratedSpanBuilder)
    }

    // endregion
}
