/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.trace

import com.datadog.android.trace.internal.data.TraceWriter
import com.datadog.android.trace.internal.domain.event.SpanEventMapper
import com.datadog.android.trace.internal.domain.event.SpanEventMapperWrapper
import com.datadog.android.trace.internal.net.TracesRequestFactory
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.v2.api.Feature
import com.datadog.android.v2.api.FeatureStorageConfiguration
import com.datadog.android.v2.api.InternalLogger
import com.datadog.android.v2.api.SdkCore
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
    lateinit var mockSdkCore: SdkCore

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @Mock
    lateinit var mockSpanEventMapper: SpanEventMapper

    @StringForgery(regex = "https://[a-z]+\\.com")
    lateinit var fakeEndpointUrl: String

    @BeforeEach
    fun `set up`() {
        whenever(mockSdkCore._internalLogger) doReturn mockInternalLogger

        testedFeature = TracingFeature(fakeEndpointUrl, mockSpanEventMapper)
    }

    @Test
    fun `𝕄 initialize writer 𝕎 initialize()`() {
        // When
        testedFeature.onInitialize(mockSdkCore, mock())

        // Then
        assertThat(testedFeature.dataWriter)
            .isInstanceOf(TraceWriter::class.java)
    }

    @Test
    fun `𝕄 use the eventMapper 𝕎 initialize()`() {
        // When
        testedFeature.onInitialize(mockSdkCore, mock())

        // Then
        val dataWriter = testedFeature.dataWriter as? TraceWriter
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
        testedFeature.onInitialize(mockSdkCore, mock())

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
