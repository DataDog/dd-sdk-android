/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.trace.internal

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.api.storage.FeatureStorageConfiguration
import com.datadog.android.trace.event.SpanEventMapper
import com.datadog.android.trace.internal.data.OtelTraceWriter
import com.datadog.android.trace.internal.data.TraceWriter
import com.datadog.android.trace.internal.domain.event.CoreTracerSpanToSpanEventMapper
import com.datadog.android.trace.internal.domain.event.DdSpanToSpanEventMapper
import com.datadog.android.trace.internal.domain.event.SpanEventMapperWrapper
import com.datadog.android.trace.internal.net.TracesRequestFactory
import com.datadog.android.utils.forge.Configurator
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
internal class TracingFeatureTest {

    private lateinit var testedFeature: TracingFeature

    @Mock
    lateinit var mockSdkCore: FeatureSdkCore

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @Mock
    lateinit var mockSpanEventMapper: SpanEventMapper

    @StringForgery(regex = "https://[a-z]+\\.com")
    lateinit var fakeEndpointUrl: String

    @BoolForgery
    var fakeNetworkInfoEnabled: Boolean = false

    @BeforeEach
    fun `set up`() {
        whenever(mockSdkCore.internalLogger) doReturn mockInternalLogger

        testedFeature = TracingFeature(
            mockSdkCore,
            fakeEndpointUrl,
            mockSpanEventMapper,
            fakeNetworkInfoEnabled
        )
    }

    @Test
    fun `𝕄 initialize opentracing writer 𝕎 initialize()`() {
        // When
        testedFeature.onInitialize(mock())

        // Then
        assertThat(testedFeature.legacyTracerWriter)
            .isInstanceOf(TraceWriter::class.java)
        val traceWriter = testedFeature.legacyTracerWriter as TraceWriter
        val ddSpanToSpanEventMapper = traceWriter.ddSpanToSpanEventMapper
        assertThat(ddSpanToSpanEventMapper).isInstanceOf(DdSpanToSpanEventMapper::class.java)
        assertThat((ddSpanToSpanEventMapper as DdSpanToSpanEventMapper).networkInfoEnabled)
            .isEqualTo(fakeNetworkInfoEnabled)
    }

    @Test
    fun `𝕄 initialize otel writer 𝕎 initialize()`() {
        // When
        testedFeature.onInitialize(mock())

        // Then
        assertThat(testedFeature.legacyTracerWriter)
            .isInstanceOf(TraceWriter::class.java)
        val traceWriter = testedFeature.coreTracerDataWriter as OtelTraceWriter
        val ddSpanToSpanEventMapper = traceWriter.ddSpanToSpanEventMapper
        assertThat(ddSpanToSpanEventMapper).isInstanceOf(CoreTracerSpanToSpanEventMapper::class.java)
        assertThat((ddSpanToSpanEventMapper as CoreTracerSpanToSpanEventMapper).networkInfoEnabled)
            .isEqualTo(fakeNetworkInfoEnabled)
    }

    @Test
    fun `𝕄 use the eventMapper for opentracing writer 𝕎 initialize()`() {
        // When
        testedFeature.onInitialize(mock())

        // Then
        val dataWriter = testedFeature.legacyTracerWriter as? TraceWriter
        val spanEventMapperWrapper = dataWriter?.eventMapper as? SpanEventMapperWrapper
        val spanEventMapper = spanEventMapperWrapper?.wrappedEventMapper
        assertThat(spanEventMapper).isSameAs(mockSpanEventMapper)
    }

    @Test
    fun `𝕄 use the eventMapper for otel writer 𝕎 initialize()`() {
        // When
        testedFeature.onInitialize(mock())

        // Then
        val dataWriter = testedFeature.coreTracerDataWriter as? OtelTraceWriter
        val spanEventMapperWrapper = dataWriter?.eventMapper as? SpanEventMapperWrapper
        val spanEventMapper = spanEventMapperWrapper?.wrappedEventMapper
        assertThat(spanEventMapper).isSameAs(mockSpanEventMapper)
    }

    @Test
    fun `𝕄 provide tracing feature name 𝕎 name()`() {
        // When+Then
        assertThat(testedFeature.name)
            .isEqualTo(Feature.TRACING_FEATURE_NAME)
    }

    @Test
    fun `𝕄 provide tracing request factory 𝕎 requestFactory()`() {
        // Given
        testedFeature.onInitialize(mock())

        // When+Then
        assertThat(testedFeature.requestFactory)
            .isInstanceOf(TracesRequestFactory::class.java)
    }

    @Test
    fun `𝕄 provide default storage configuration 𝕎 storageConfiguration()`() {
        // When+Then
        assertThat(testedFeature.storageConfiguration)
            .isEqualTo(FeatureStorageConfiguration.DEFAULT)
    }
}
