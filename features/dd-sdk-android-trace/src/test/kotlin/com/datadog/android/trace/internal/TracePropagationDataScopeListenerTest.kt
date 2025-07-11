/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.trace.internal

import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.trace.api.span.DatadogSpan
import com.datadog.android.trace.api.span.DatadogSpanContext
import com.datadog.android.trace.api.tracer.DatadogTracer
import com.datadog.android.utils.forge.Configurator
import fr.xgouchet.elmyr.annotation.Forgery
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
import org.mockito.kotlin.eq
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
class TracePropagationDataScopeListenerTest {

    @Mock
    lateinit var mockSdkCore: FeatureSdkCore

    @Mock
    lateinit var mockTracer: DatadogTracer

    @Mock
    lateinit var mockSpan: DatadogSpan

    @Forgery
    lateinit var fakeSpanContext: DatadogSpanContext

    private val contextName = "context@${Thread.currentThread().name}"

    private lateinit var testedListener: TracePropagationDataScopeListener

    @BeforeEach
    fun `set up`() {
        whenever(mockSpan.context()).thenReturn(fakeSpanContext)
        testedListener = TracePropagationDataScopeListener(mockSdkCore, mockTracer)
    }

    @Test
    fun `M add context tag W afterScopeActivated {context present}`() {
        // Given
        whenever(mockTracer.activeSpan()).thenReturn(mockSpan)

        // When
        testedListener.afterScopeActivated()

        // Then
        val mapWithContext = assertCallbackCalled(mutableMapOf())
        assertThat(mapWithContext[contextName]).isEqualTo(
            mapOf(
                "span_id" to fakeSpanContext.spanId.toString(),
                "trace_id" to DatadogTracingToolkit.traceIdConverter.toHexString(fakeSpanContext.traceId)
            )
        )
    }

    @Test
    fun `M context tag not added W afterScopeActivated {context absent}`() {
        // Given
        whenever(mockTracer.activeSpan()).thenReturn(null)

        // When
        testedListener.afterScopeActivated()

        // Then
        verifyNoInteractions(mockSdkCore)
    }

    @Test
    fun `M remove context tag W afterScopeClosed`() {
        // Given
        testedListener.afterScopeClosed()

        // When
        val mapWithContext = assertCallbackCalled(mutableMapOf(contextName to Any()))

        // Then
        assertThat(mapWithContext).isEmpty()
    }

    private fun assertCallbackCalled(mapWithContext: MutableMap<String, Any?>): MutableMap<String, Any?> {
        val callbackCaptor = argumentCaptor<(MutableMap<String, Any?>) -> Unit>()
        verify(mockSdkCore).updateFeatureContext(
            eq(Feature.TRACING_FEATURE_NAME),
            eq(true),
            callbackCaptor.capture()
        )

        callbackCaptor.firstValue.invoke(mapWithContext)
        return mapWithContext
    }
}
