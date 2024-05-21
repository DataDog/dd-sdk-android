/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.trace.opentelemetry

import com.datadog.android.utils.forge.Configurator
import com.datadog.opentelemetry.trace.OtelSpan
import com.datadog.trace.bootstrap.instrumentation.api.AgentScope
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import io.opentelemetry.api.trace.Span
import io.opentelemetry.context.Context
import io.opentelemetry.context.ContextKey
import io.opentelemetry.context.Scope
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class OtelContextTest {

    @Mock
    private lateinit var mockContext: Context

    @Mock
    private lateinit var mockCurrentSpan: Span

    @Mock
    private lateinit var mockRootSpan: Span

    private lateinit var testedContext: OtelContext

    @BeforeEach
    fun setUp() {
        testedContext = OtelContext(mockContext, mockCurrentSpan, mockRootSpan)
    }

    // region get by key

    @Test
    fun `M return current span W get { key is OTEL_CONTEXT_SPAN_KEY }`() {
        // Given
        val key = ContextKey.named<Span>(OtelContext.OTEL_CONTEXT_SPAN_KEY)

        // When
        val result = testedContext.get(key)

        // Then
        assertThat(result).isEqualTo(mockCurrentSpan)
    }

    @Test
    fun `M return root span W get { key is OTEL_CONTEXT_ROOT_SPAN_KEY }`() {
        // Given
        val key = ContextKey.named<Span>(OtelContext.OTEL_CONTEXT_ROOT_SPAN_KEY)

        // When
        val result = testedContext.get(key)

        // Then
        assertThat(result).isEqualTo(mockRootSpan)
    }

    @Test
    fun `M delegate to wrapped Context W get { key is not recognized }`(forge: Forge) {
        // Given
        val fakeKeyName = forge.anAlphabeticalString()
        val key = ContextKey.named<String>(fakeKeyName)
        val fakeKeyValue = forge.anAlphabeticalString()
        whenever(mockContext.get(key)).thenReturn(fakeKeyValue)

        // When
        val result = testedContext.get(key)

        // Then
        assertThat(result).isEqualTo(fakeKeyValue)
    }

    // endregion

    // region with key and value

    @Test
    fun `M return OtelContext W with { key is OTEL_CONTEXT_SPAN_KEY }`() {
        // Given
        val key = ContextKey.named<Span>(OtelContext.OTEL_CONTEXT_SPAN_KEY)
        val value = mock<Span>()

        // When
        val result = testedContext.with(key, value)

        // Then
        assertThat(result).isInstanceOf(OtelContext::class.java)
        assertThat((result as OtelContext).currentSpan).isSameAs(value)
    }

    @Test
    fun `M return OtelContext W with { key is OTEL_CONTEXT_ROOT_SPAN_KEY }`() {
        // Given
        val key = ContextKey.named<Span>(OtelContext.OTEL_CONTEXT_ROOT_SPAN_KEY)
        val value = mock<Span>()

        // When
        val result = testedContext.with(key, value)

        // Then
        assertThat(result).isInstanceOf(OtelContext::class.java)
        assertThat((result as OtelContext).rootSpan).isSameAs(value)
    }

    @Test
    fun `M return OtelContext W with { key is not recognized }`(forge: Forge) {
        // Given
        val fakeKeyName = forge.anAlphabeticalString()
        val key = ContextKey.named<String>(fakeKeyName)
        val fakeKeyValue = forge.anAlphabeticalString()
        val wrappedContext: Context = mock()
        whenever(mockContext.with(key, fakeKeyValue)).thenReturn(wrappedContext)

        // When
        val result = testedContext.with(key, fakeKeyValue)

        // Then
        assertThat(result).isInstanceOf(OtelContext::class.java)
        assertThat((result as OtelContext).currentSpan).isEqualTo(mockCurrentSpan)
    }

    @Test
    fun `M return OtelContext W with consecutively`(forge: Forge) {
        // Given
        val keysToValues = forge.aList {
            ContextKey.named<String>(forge.anAlphabeticalString()) to forge.anAlphabeticalString()
        }

        // When
        val context = keysToValues.fold(OtelContext(Context.root())) { acc, (key, value) ->
            acc.with(key, value) as OtelContext
        }

        // Then
        assertThat(context).isInstanceOf(OtelContext::class.java)
        val wrapped = context.wrapped
        assertThat(wrapped).isInstanceOf(Context::class.java)
        assertThat(wrapped).isNotExactlyInstanceOf(OtelContext::class.java)
        keysToValues.forEach {
            val (key, value) = it
            assertThat(wrapped.get(key)).isEqualTo(value)
        }
    }

    // endregion

    // region makeCurrent

    @Test
    fun `M return OtelScope W makeCurrent { currentSpan is OtelSpan }`() {
        // Given
        val mockAgentScope: AgentScope = mock()
        val mockOtelSpan: OtelSpan = mock {
            on { activate() }.thenReturn(mockAgentScope)
        }
        testedContext = OtelContext(mockContext, mockOtelSpan, mockRootSpan)

        // When
        val result = testedContext.makeCurrent()

        // Then
        assertThat(result).isInstanceOf(OtelScope::class.java)
        val otelScope = result as OtelScope
        assertThat(otelScope.delegate).isSameAs(mockAgentScope)
        assertThat(otelScope.scope).isInstanceOf(Scope::class.java)
    }

    @Test
    fun `M return OtelScope W makeCurrent { currentSpan is not OtelSpan }`() {
        // Given
        testedContext = OtelContext(mockContext)

        // When
        val result = testedContext.makeCurrent()

        // Then
        assertThat(result).isInstanceOf(Scope::class.java)
        assertThat(result).isNotExactlyInstanceOf(OtelScope::class.java)
    }

    // endregion
}
