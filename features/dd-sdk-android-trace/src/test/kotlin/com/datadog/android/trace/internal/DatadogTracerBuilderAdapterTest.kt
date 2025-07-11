/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.trace.internal

import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.trace.TracingHeaderType
import com.datadog.android.trace.api.DatadogTracingConstants.TracerConfig
import com.datadog.android.trace.internal.DatadogTracerBuilderAdapter.Companion.DEFAULT_URL_AS_RESOURCE_NAME
import com.datadog.android.utils.forge.Configurator
import com.datadog.trace.api.DD128bTraceId
import com.datadog.trace.api.DD64bTraceId
import com.datadog.trace.api.IdGenerationStrategy
import com.datadog.trace.core.CoreTracer
import fr.xgouchet.elmyr.Forge
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
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.Properties

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
class DatadogTracerBuilderAdapterTest {
    private lateinit var testedBuilder: DatadogTracerBuilderAdapter

    @Mock
    lateinit var mockSdkCore: FeatureSdkCore

    @Mock
    lateinit var mockBuilder: CoreTracer.CoreTracerBuilder

    @Mock
    lateinit var mockTracerDelegate: CoreTracer

    @StringForgery
    lateinit var fakeServiceName: String

    @BoolForgery
    private var fakeBoolean: Boolean = false

    @BeforeEach
    fun `set up`() {
        whenever(mockBuilder.build()).thenReturn(mockTracerDelegate)
        whenever(mockBuilder.withProperties(any())).thenReturn(mockBuilder)
        testedBuilder = DatadogTracerBuilderAdapter(mockSdkCore, fakeServiceName, mockBuilder)
    }

    @Test
    fun `M return default properties W properties() {no method called}`() {
        // Given
        val expected = Properties()
        expected.setProperty(TracerConfig.PROPAGATION_STYLE_EXTRACT, EXPECTED_DEFAULT_PROPAGATION)
        expected.setProperty(TracerConfig.PROPAGATION_STYLE_INJECT, EXPECTED_DEFAULT_PROPAGATION)
        expected.setProperty(TracerConfig.SERVICE_NAME, fakeServiceName)
        expected.setProperty(TracerConfig.TRACE_RATE_LIMIT, Int.MAX_VALUE.toString())
        expected.setProperty(
            TracerConfig.PARTIAL_FLUSH_MIN_SPANS,
            DatadogTracerBuilderAdapter.DEFAULT_PARTIAL_MIN_FLUSH.toString()
        )
        expected.setProperty(TracerConfig.URL_AS_RESOURCE_NAME, DEFAULT_URL_AS_RESOURCE_NAME.toString())
        expected.setProperty(TracerConfig.TAGS, "")

        // When
        val actual = testedBuilder.properties()

        // Then
        assertThat(actual).isEqualTo(expected)
    }

    @Test
    fun `M return valid properties W {setters called}`(forge: Forge) {
        // Given
        val fakeServiceName = forge.aString()
        val fakeTagKey = forge.aString()
        val fakeTagValue = forge.aString()
        val fakeSampleRate = forge.aDouble(min = 0.0, max = 100.0)
        val fakeTraceLimit = forge.anInt()
        val fakePartialFlushMinSpans = forge.anInt()
        val fakeHeaderType = forge.anElementFrom(
            TracingHeaderType.B3,
            TracingHeaderType.DATADOG,
            TracingHeaderType.TRACECONTEXT,
            TracingHeaderType.B3MULTI
        )

        val expected = Properties().apply {
            setProperty(TracerConfig.PROPAGATION_STYLE_EXTRACT, fakeHeaderType.toString())
            setProperty(TracerConfig.PROPAGATION_STYLE_INJECT, fakeHeaderType.toString())
            setProperty(TracerConfig.SERVICE_NAME, fakeServiceName)
            setProperty(TracerConfig.TRACE_RATE_LIMIT, fakeTraceLimit.toString())
            setProperty(TracerConfig.TRACE_SAMPLE_RATE, (fakeSampleRate / 100.0).toString())
            setProperty(TracerConfig.PARTIAL_FLUSH_MIN_SPANS, fakePartialFlushMinSpans.toString())
            setProperty(TracerConfig.URL_AS_RESOURCE_NAME, DEFAULT_URL_AS_RESOURCE_NAME.toString())
            setProperty(TracerConfig.TAGS, "$fakeTagKey:$fakeTagValue")
        }

        // When
        val actual = testedBuilder
            .withTag(fakeTagKey, fakeTagValue)
            .withSampleRate(fakeSampleRate)
            .withTraceLimit(fakeTraceLimit)
            .withServiceName(fakeServiceName)
            .withTracingHeadersTypes(setOf(fakeHeaderType))
            .withPartialFlushMinSpans(fakePartialFlushMinSpans)
            .properties()

        // Then
        assertThat(actual).isEqualTo(expected)
    }

    @Test
    fun `M delegate CoreTracerBuilder#idGenerationStrategy W idGenerationStrategy`() {
        // Given
        val expectedTraceIdClass = if (fakeBoolean) {
            DD128bTraceId::class.java
        } else {
            DD64bTraceId::class.java
        }

        // When
        testedBuilder.setTraceId128BitGenerationEnabled(fakeBoolean)

        // Then
        argumentCaptor<IdGenerationStrategy> {
            verify(mockBuilder).idGenerationStrategy(capture())
            assertThat(firstValue::class.simpleName).isEqualTo("SRandom")
            assertThat(firstValue.generateTraceId()).isInstanceOf(expectedTraceIdClass)
        }
    }

    @Test
    fun `M return tracer with expected parameters W build`() {
        // When
        val tracer = testedBuilder
            .setBundleWithRumEnabled(fakeBoolean)
            .build() as DatadogTracerAdapter

        // Then
        assertThat(tracer.delegate).isEqualTo(mockTracerDelegate)
        assertThat(tracer.sdkCore).isEqualTo(mockSdkCore)
        assertThat(tracer.bundleWithRumEnabled).isEqualTo(fakeBoolean)
        argumentCaptor<DatadogScopeListenerAdapter> {
            verify(mockTracerDelegate).addScopeListener(capture())
            assertThat(firstValue.delegate).isInstanceOf(TracePropagationDataScopeListener::class.java)
        }
    }

    companion object {
        private const val EXPECTED_DEFAULT_PROPAGATION = "DATADOG,TRACECONTEXT"
    }
}
