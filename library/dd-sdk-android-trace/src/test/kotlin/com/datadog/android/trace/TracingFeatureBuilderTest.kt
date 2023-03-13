/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.trace

import com.datadog.android.DatadogSite
import com.datadog.android.trace.internal.domain.event.NoOpSpanEventMapper
import com.datadog.android.trace.internal.domain.event.SpanEventMapper
import com.datadog.android.trace.internal.net.TracesRequestFactory
import com.datadog.android.utils.forge.Configurator
import com.nhaarman.mockitokotlin2.mock
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions

@Extensions(
    ExtendWith(ForgeExtension::class)
)
@ForgeConfiguration(Configurator::class)
internal class TracingFeatureBuilderTest {

    private val testedBuilder: TracingFeature.Builder = TracingFeature.Builder()

    @Test
    fun `𝕄 use sensible defaults 𝕎 build()`() {
        // When
        val config = testedBuilder.build()

        // Then
        val requestFactory = config.requestFactory
        assertThat(requestFactory).isInstanceOf(TracesRequestFactory::class.java)
        assertThat((requestFactory as TracesRequestFactory).endpointUrl)
            .isEqualTo(DatadogSite.US1.intakeEndpoint)

        assertThat(config.spanEventMapper).isInstanceOf(NoOpSpanEventMapper::class.java)
    }

    @Test
    fun `𝕄 build feature with custom site 𝕎 useSite() and build()`(
        @Forgery site: DatadogSite
    ) {
        // When
        val config = testedBuilder.useSite(site).build()

        // Then
        val requestFactory = config.requestFactory
        assertThat(requestFactory).isInstanceOf(TracesRequestFactory::class.java)
        assertThat((requestFactory as TracesRequestFactory).endpointUrl)
            .isEqualTo(site.intakeEndpoint)
    }

    @Test
    fun `𝕄 build feature with custom site 𝕎 useCustomEndpoint() and build()`(
        @StringForgery(regex = "https://[a-z]+\\.com") tracesEndpointUrl: String
    ) {
        // When
        val config = testedBuilder.useCustomEndpoint(tracesEndpointUrl).build()

        // Then
        val requestFactory = config.requestFactory
        assertThat(requestFactory).isInstanceOf(TracesRequestFactory::class.java)
        assertThat((requestFactory as TracesRequestFactory).endpointUrl)
            .isEqualTo(tracesEndpointUrl)
    }

    @Test
    fun `𝕄 build feature with Span eventMapper 𝕎 setSpanEventMapper() and build()`() {
        // Given
        val mockEventMapper = mock<SpanEventMapper>()

        // When
        val config = testedBuilder
            .setSpanEventMapper(mockEventMapper)
            .build()

        // Then
        assertThat(config.spanEventMapper).isEqualTo(mockEventMapper)
    }
}
