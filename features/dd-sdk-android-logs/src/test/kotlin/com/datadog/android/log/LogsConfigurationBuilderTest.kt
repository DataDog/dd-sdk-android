/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.log

import com.datadog.android.event.EventMapper
import com.datadog.android.event.NoOpEventMapper
import com.datadog.android.log.model.LogEvent
import com.datadog.android.utils.forge.Configurator
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.mock
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(ForgeExtension::class),
    ExtendWith(MockitoExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class LogsConfigurationBuilderTest {

    private val testedBuilder: LogsConfiguration.Builder = LogsConfiguration.Builder()

    @Test
    fun `M use sensible defaults W build()`() {
        // When
        val logsConfiguration = testedBuilder.build()

        // Then
        assertThat(logsConfiguration.customEndpointUrl).isNull()
        assertThat(logsConfiguration.eventMapper).isInstanceOf(NoOpEventMapper::class.java)
    }

    @Test
    fun `M build configuration with custom site W useCustomEndpoint() and build()`(
        @StringForgery(regex = "https://[a-z]+\\.com(/[a-z]+)+") logsEndpointUrl: String
    ) {
        // When
        val logsConfiguration = testedBuilder.useCustomEndpoint(logsEndpointUrl).build()

        // Then
        assertThat(logsConfiguration.customEndpointUrl).isEqualTo(logsEndpointUrl)
        assertThat(logsConfiguration.eventMapper).isInstanceOf(NoOpEventMapper::class.java)
    }

    @Test
    fun `M build configuration with Log eventMapper W setEventMapper() and build()`() {
        // Given
        val mockEventMapper: EventMapper<LogEvent> = mock()

        // When
        val logsConfiguration = testedBuilder
            .setEventMapper(mockEventMapper)
            .build()

        // Then
        assertThat(logsConfiguration.customEndpointUrl).isNull()
        assertThat(logsConfiguration.eventMapper).isEqualTo(mockEventMapper)
    }
}
