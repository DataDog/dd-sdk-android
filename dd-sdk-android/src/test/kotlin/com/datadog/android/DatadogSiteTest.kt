/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android

import com.datadog.android.utils.forge.Configurator
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import java.util.stream.Stream
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class DatadogSiteTest {

    @ParameterizedTest
    @MethodSource("provideSiteWithLogsEndpoint")
    fun `𝕄 return logs endpoint 𝕎 logsEndpoint()`(
        site: DatadogSite,
        expectedEndpoint: String
    ) {
        // When
        val logsEndpoint = site.logsEndpoint()

        // Then
        assertThat(logsEndpoint).isEqualTo(expectedEndpoint)
    }

    @ParameterizedTest
    @MethodSource("provideSiteWithTracesEndpoint")
    fun `𝕄 return traces endpoint 𝕎 tracesEndpoint()`(
        site: DatadogSite,
        expectedEndpoint: String
    ) {
        // When
        val tracesEndpoint = site.tracesEndpoint()

        // Then
        assertThat(tracesEndpoint).isEqualTo(expectedEndpoint)
    }

    @ParameterizedTest
    @MethodSource("provideSiteWithRumEndpoint")
    fun `𝕄 return rum endpoint 𝕎 rumEndpoint()`(
        site: DatadogSite,
        expectedEndpoint: String
    ) {
        // When
        val rumEndpoint = site.rumEndpoint()

        // Then
        assertThat(rumEndpoint).isEqualTo(expectedEndpoint)
    }

    companion object {
        @JvmStatic
        private fun provideSiteWithLogsEndpoint(): Stream<Arguments?>? {
            return Stream.of(
                Arguments.of(DatadogSite.US1, DatadogEndpoint.LOGS_US1),
                Arguments.of(DatadogSite.US3, DatadogEndpoint.LOGS_US3),
                Arguments.of(DatadogSite.US5, DatadogEndpoint.LOGS_US5),
                Arguments.of(DatadogSite.US1_FED, DatadogEndpoint.LOGS_US1_FED),
                Arguments.of(DatadogSite.EU1, DatadogEndpoint.LOGS_EU1)
            )
        }

        @JvmStatic
        private fun provideSiteWithTracesEndpoint(): Stream<Arguments?>? {
            return Stream.of(
                Arguments.of(DatadogSite.US1, DatadogEndpoint.TRACES_US1),
                Arguments.of(DatadogSite.US3, DatadogEndpoint.TRACES_US3),
                Arguments.of(DatadogSite.US5, DatadogEndpoint.TRACES_US5),
                Arguments.of(DatadogSite.US1_FED, DatadogEndpoint.TRACES_US1_FED),
                Arguments.of(DatadogSite.EU1, DatadogEndpoint.TRACES_EU1)
            )
        }

        @JvmStatic
        private fun provideSiteWithRumEndpoint(): Stream<Arguments?>? {
            return Stream.of(
                Arguments.of(DatadogSite.US1, DatadogEndpoint.RUM_US1),
                Arguments.of(DatadogSite.US3, DatadogEndpoint.RUM_US3),
                Arguments.of(DatadogSite.US5, DatadogEndpoint.RUM_US5),
                Arguments.of(DatadogSite.US1_FED, DatadogEndpoint.RUM_US1_FED),
                Arguments.of(DatadogSite.EU1, DatadogEndpoint.RUM_EU1)
            )
        }
    }
}
