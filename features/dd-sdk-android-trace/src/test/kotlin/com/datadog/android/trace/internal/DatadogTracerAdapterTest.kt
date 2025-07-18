/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.trace.internal

import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.trace.api.scope.DatadogScopeListener
import com.datadog.android.trace.api.span.DatadogSpanBuilder
import com.datadog.android.utils.forge.Configurator
import com.datadog.trace.bootstrap.instrumentation.api.AgentScope
import com.datadog.trace.bootstrap.instrumentation.api.AgentSpan
import com.datadog.trace.bootstrap.instrumentation.api.AgentTracer
import com.datadog.trace.bootstrap.instrumentation.api.ScopeSource
import fr.xgouchet.elmyr.annotation.BoolForgery
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
import org.mockito.kotlin.any
import org.mockito.kotlin.isA
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
internal class DatadogTracerAdapterTest {

    private lateinit var testedTracer: DatadogTracerAdapter

    @Mock
    lateinit var mockSdk: FeatureSdkCore

    @Mock
    lateinit var mockTracer: AgentTracer.TracerAPI

    @Mock
    lateinit var mockSpan: AgentSpan

    @Mock
    lateinit var mockSpanLogger: DatadogSpanLogger

    @Mock
    lateinit var mockDatadogSpan: DatadogSpanAdapter

    @Mock
    lateinit var mockScope: AgentScope

    @Mock
    lateinit var mockSpanBuilder: AgentTracer.SpanBuilder

    @StringForgery
    lateinit var fakeString: String

    @BoolForgery
    private var fakeBool: Boolean = false

    @BeforeEach
    fun `set up`() {
        whenever(mockSdk.internalLogger).thenReturn(mock())
        testedTracer = DatadogTracerAdapter(mockSdk, mockTracer, true, mockSpanLogger)
        whenever(mockDatadogSpan.delegate).thenReturn(mockSpan)
        whenever(mockTracer.propagate()).thenReturn(mock())
        @Suppress("DEPRECATION")
        whenever(mockTracer.buildSpan(any())).thenReturn(mockSpanBuilder)
        whenever(mockTracer.buildSpan(any(), any())).thenReturn(mockSpanBuilder)
    }

    @Test
    fun `M return span W buildSpan(String)`() {
        // When
        val builder = testedTracer.buildSpan(fakeString)

        // Then
        assertThat(builder).isInstanceOf(DatadogSpanBuilder::class.java)
        @Suppress("DEPRECATION")
        verify(mockTracer).buildSpan(fakeString)
    }

    @Test
    fun `M return span W buildSpan(String, String) `() {
        // When
        val builder = testedTracer.buildSpan(fakeString, fakeString)

        // Then
        assertThat(builder).isInstanceOf(DatadogSpanBuilder::class.java)
        verify(mockTracer).buildSpan(fakeString, fakeString)
    }

    @Test
    fun `M delegate SpanBuilder#addScopeListener W addScopeListener`() {
        // Given
        val mockListener = mock<DatadogScopeListener>()

        // When
        testedTracer.addScopeListener(mockListener)

        // Then
        verify(mockTracer).addScopeListener(isA<DatadogScopeListenerAdapter>())
    }

    @Test
    fun `M return wrapped span W activeSpan`() {
        // Given
        whenever(mockTracer.activeSpan()).thenReturn(mockSpan)

        // When
        val span = testedTracer.activeSpan() as DatadogSpanAdapter

        // Then
        assertThat(span.delegate).isEqualTo(mockSpan)
    }

    @Test
    fun `M return wrapped scope W activateSpan(DatadogSpan)`() {
        // Given
        whenever(mockTracer.activateSpan(mockSpan, ScopeSource.INSTRUMENTATION)).thenReturn(mockScope)

        // When
        val scope = testedTracer.activateSpan(mockDatadogSpan) as DatadogScopeAdapter

        // Then
        assertThat(scope.delegate).isEqualTo(mockScope)
    }

    @Test
    fun `M return wrapped scope W activateSpan(DatadogSpan, Boolean)`() {
        // Given
        whenever(mockTracer.activateSpan(mockSpan, ScopeSource.INSTRUMENTATION, fakeBool)).thenReturn(mockScope)

        // When
        val scope = testedTracer.activateSpan(mockDatadogSpan, fakeBool) as DatadogScopeAdapter

        // Then
        assertThat(scope.delegate).isEqualTo(mockScope)
    }

    @Test
    fun `M return propagate W propagate()`() {
        // When
        val actual = testedTracer.propagate()

        // Then
        assertThat(actual).isInstanceOf(DatadogPropagationAdapter::class.java)
    }
}
