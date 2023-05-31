/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.trace

import com.datadog.android.trace.internal.domain.event.NoOpSpanEventMapper
import com.datadog.android.trace.internal.domain.event.SpanEventMapper
import com.datadog.android.trace.internal.net.TracesRequestFactory
import com.datadog.android.utils.forge.Configurator
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
    ExtendWith(ForgeExtension::class),
    ExtendWith(MockitoExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class TracingFeatureBuilderTest {

    private val testedBuilder: TracingFeature.Builder = TracingFeature.Builder()

    @Mock
    lateinit var mockSdkCore: SdkCore

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @BeforeEach
    fun `set up`() {
        whenever(mockSdkCore._internalLogger) doReturn mockInternalLogger
    }

    @Test
    fun `𝕄 use sensible defaults 𝕎 build()`() {
        // When
        val tracingFeature = testedBuilder.build()
        tracingFeature.onInitialize(mockSdkCore, appContext = mock())

        // Then
        val requestFactory = tracingFeature.requestFactory
        assertThat(requestFactory).isInstanceOf(TracesRequestFactory::class.java)
        assertThat((requestFactory as TracesRequestFactory).customEndpointUrl)
            .isNull()

        assertThat(tracingFeature.spanEventMapper).isInstanceOf(NoOpSpanEventMapper::class.java)
    }

    @Test
    fun `𝕄 build feature with custom site 𝕎 useCustomEndpoint() and build()`(
        @StringForgery(regex = "https://[a-z]+\\.com") tracesEndpointUrl: String
    ) {
        // When
        val tracingFeature = testedBuilder.useCustomEndpoint(tracesEndpointUrl).build()
        tracingFeature.onInitialize(mockSdkCore, appContext = mock())

        // Then
        val requestFactory = tracingFeature.requestFactory
        assertThat(requestFactory).isInstanceOf(TracesRequestFactory::class.java)
        assertThat((requestFactory as TracesRequestFactory).customEndpointUrl)
            .isEqualTo(tracesEndpointUrl)
    }

    @Test
    fun `𝕄 build feature with Span eventMapper 𝕎 setSpanEventMapper() and build()`() {
        // Given
        val mockEventMapper = mock<SpanEventMapper>()

        // When
        val tracingFeature = testedBuilder
            .setSpanEventMapper(mockEventMapper)
            .build()

        // Then
        assertThat(tracingFeature.spanEventMapper).isEqualTo(mockEventMapper)
    }
}
