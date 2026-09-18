/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.trace

import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.trace.internal.ClientStatsFeature
import com.datadog.android.trace.internal.TracingFeature
import com.datadog.android.trace.internal.net.TracesRequestFactory
import com.datadog.android.utils.forge.Configurator
import fr.xgouchet.elmyr.annotation.Forgery
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
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class TraceTest {

    @Mock
    lateinit var mockSdkCore: FeatureSdkCore

    @BeforeEach
    fun `set up`() {
        whenever(mockSdkCore.internalLogger) doReturn mock()
    }

    @Test
    fun `M register traces features W enable() { statsComputationEnabled = false }`(
        @StringForgery fakePackageName: String,
        @Forgery fakeTraceConfiguration: TraceConfiguration
    ) {
        // Given
        val config = fakeTraceConfiguration.copy(statsComputationEnabled = false)

        // When
        Trace.enable(config, mockSdkCore)

        // Then
        argumentCaptor<TracingFeature> {
            verify(mockSdkCore).registerFeature(capture())

            lastValue.onInitialize(
                appContext = mock { whenever(it.packageName) doReturn fakePackageName }
            )
            assertThat(lastValue.spanEventMapper).isEqualTo(config.eventMapper)
            assertThat((lastValue.requestFactory as TracesRequestFactory).customEndpointUrl)
                .isEqualTo(config.customEndpointUrl)
        }
    }

    @Test
    fun `M register traces features W enable() { statsComputationEnabled = true }`(
        @StringForgery fakePackageName: String,
        @Forgery fakeTraceConfiguration: TraceConfiguration
    ) {
        // Given
        val config = fakeTraceConfiguration.copy(statsComputationEnabled = true)

        // When
        Trace.enable(config, mockSdkCore)

        // Then
        argumentCaptor<Feature> {
            verify(mockSdkCore, times(2)).registerFeature(capture())

            val tracingFeature = allValues.filterIsInstance<TracingFeature>().first()
            tracingFeature.onInitialize(
                appContext = mock { whenever(it.packageName) doReturn fakePackageName }
            )
            assertThat(tracingFeature.spanEventMapper).isEqualTo(config.eventMapper)
            assertThat((tracingFeature.requestFactory as TracesRequestFactory).customEndpointUrl)
                .isEqualTo(config.customEndpointUrl)

            val statsFeature = allValues.filterIsInstance<ClientStatsFeature>().first()
            assertThat(statsFeature.requestFactory.customStatsEndpointUrl)
                .isEqualTo(config.customStatsEndpointUrl)
        }
    }
}
