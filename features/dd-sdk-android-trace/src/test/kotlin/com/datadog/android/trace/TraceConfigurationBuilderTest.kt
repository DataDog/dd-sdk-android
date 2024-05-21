/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.trace

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.trace.event.NoOpSpanEventMapper
import com.datadog.android.trace.event.SpanEventMapper
import com.datadog.android.utils.forge.Configurator
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
internal class TraceConfigurationBuilderTest {

    private val testedBuilder: TraceConfiguration.Builder = TraceConfiguration.Builder()

    @Mock
    lateinit var mockSdkCore: FeatureSdkCore

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @BeforeEach
    fun `set up`() {
        whenever(mockSdkCore.internalLogger) doReturn mockInternalLogger
    }

    @Test
    fun `M use sensible defaults W build()`() {
        // When
        val traceConfiguration = testedBuilder.build()

        // Then
        assertThat(traceConfiguration.customEndpointUrl).isNull()
        assertThat(traceConfiguration.eventMapper)
            .isInstanceOf(NoOpSpanEventMapper::class.java)
    }

    @Test
    fun `M build configuration with custom site W useCustomEndpoint() and build()`(
        @StringForgery(regex = "https://[a-z]+\\.com") tracesEndpointUrl: String
    ) {
        // When
        val traceConfiguration = testedBuilder.useCustomEndpoint(tracesEndpointUrl).build()

        // Then
        assertThat(traceConfiguration.customEndpointUrl).isEqualTo(tracesEndpointUrl)
        assertThat(traceConfiguration.eventMapper)
            .isInstanceOf(NoOpSpanEventMapper::class.java)
    }

    @Test
    fun `M build configuration with Span eventMapper W setEventMapper() and build()`() {
        // Given
        val mockEventMapper = mock<SpanEventMapper>()

        // When
        val traceConfiguration = testedBuilder
            .setEventMapper(mockEventMapper)
            .build()

        // Then
        assertThat(traceConfiguration.customEndpointUrl).isNull()
        assertThat(traceConfiguration.eventMapper).isEqualTo(mockEventMapper)
    }
}
