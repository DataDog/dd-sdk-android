/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.log

import com.datadog.android.DatadogEndpoint
import com.datadog.android.DatadogSite
import com.datadog.android.event.EventMapper
import com.datadog.android.event.NoOpEventMapper
import com.datadog.android.log.internal.net.LogsRequestFactory
import com.datadog.android.log.model.LogEvent
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
internal class LogsFeatureBuilderTest {

    private val testedBuilder: LogsFeature.Builder = LogsFeature.Builder()

    @Test
    fun `𝕄 use sensible defaults 𝕎 build()`() {
        // When
        val config = testedBuilder.build()

        // Then
        val requestFactory = config.requestFactory
        assertThat(requestFactory).isInstanceOf(LogsRequestFactory::class.java)
        assertThat((requestFactory as LogsRequestFactory).endpointUrl)
            .isEqualTo(DatadogEndpoint.LOGS_US1)

        assertThat(config.eventMapper).isInstanceOf(NoOpEventMapper::class.java)
    }

    @Test
    fun `𝕄 build feature with custom site 𝕎 useSite() and build()`(
        @Forgery site: DatadogSite
    ) {
        // When
        val config = testedBuilder.useSite(site).build()

        // Then
        val requestFactory = config.requestFactory
        assertThat(requestFactory).isInstanceOf(LogsRequestFactory::class.java)
        assertThat((requestFactory as LogsRequestFactory).endpointUrl)
            .isEqualTo(site.logsEndpoint())
    }

    @Test
    fun `𝕄 build feature with custom site 𝕎 useCustomEndpoint() and build()`(
        @StringForgery(regex = "https://[a-z]+\\.com") logsEndpointUrl: String
    ) {
        // When
        val config = testedBuilder.useCustomEndpoint(logsEndpointUrl).build()

        // Then
        val requestFactory = config.requestFactory
        assertThat(requestFactory).isInstanceOf(LogsRequestFactory::class.java)
        assertThat((requestFactory as LogsRequestFactory).endpointUrl)
            .isEqualTo(logsEndpointUrl)
    }

    @Test
    fun `𝕄 build feature with Log eventMapper 𝕎 setLogEventMapper() and build()`() {
        // Given
        val mockEventMapper: EventMapper<LogEvent> = mock()

        // When
        val config = testedBuilder
            .setLogEventMapper(mockEventMapper)
            .build()

        // Then
        assertThat(config.eventMapper).isEqualTo(mockEventMapper)
    }
}
