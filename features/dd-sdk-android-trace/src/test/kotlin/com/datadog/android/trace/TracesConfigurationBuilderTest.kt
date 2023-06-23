/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.trace

import com.datadog.android.trace.event.NoOpSpanEventMapper
import com.datadog.android.trace.event.SpanEventMapper
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.v2.api.FeatureSdkCore
import com.datadog.android.v2.api.InternalLogger
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
internal class TracesConfigurationBuilderTest {

    private val testedBuilder: TracesConfiguration.Builder = TracesConfiguration.Builder()

    @Mock
    lateinit var mockSdkCore: FeatureSdkCore

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @BeforeEach
    fun `set up`() {
        whenever(mockSdkCore.internalLogger) doReturn mockInternalLogger
    }

    @Test
    fun `𝕄 use sensible defaults 𝕎 build()`() {
        // When
        val tracesConfiguration = testedBuilder.build()

        // Then
        assertThat(tracesConfiguration.customEndpointUrl).isNull()
        assertThat(tracesConfiguration.eventMapper)
            .isInstanceOf(NoOpSpanEventMapper::class.java)
    }

    @Test
    fun `𝕄 build configuration with custom site 𝕎 useCustomEndpoint() and build()`(
        @StringForgery(regex = "https://[a-z]+\\.com") tracesEndpointUrl: String
    ) {
        // When
        val tracesConfiguration = testedBuilder.useCustomEndpoint(tracesEndpointUrl).build()

        // Then
        assertThat(tracesConfiguration.customEndpointUrl).isEqualTo(tracesEndpointUrl)
        assertThat(tracesConfiguration.eventMapper)
            .isInstanceOf(NoOpSpanEventMapper::class.java)
    }

    @Test
    fun `𝕄 build configuration with Span eventMapper 𝕎 setSpanEventMapper() and build()`() {
        // Given
        val mockEventMapper = mock<SpanEventMapper>()

        // When
        val tracesConfiguration = testedBuilder
            .setSpanEventMapper(mockEventMapper)
            .build()

        // Then
        assertThat(tracesConfiguration.customEndpointUrl).isNull()
        assertThat(tracesConfiguration.eventMapper).isEqualTo(mockEventMapper)
    }
}
