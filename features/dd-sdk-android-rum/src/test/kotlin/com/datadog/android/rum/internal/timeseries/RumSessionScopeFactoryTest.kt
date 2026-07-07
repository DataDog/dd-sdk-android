/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.timeseries

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.api.storage.DataWriter
import com.datadog.android.rum.RumSessionType
import com.datadog.android.rum.internal.timeseries.provider.DataPointsReader
import com.datadog.android.rum.internal.timeseries.serializer.JsonSerializer
import com.datadog.android.rum.utils.forge.Configurator
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.concurrent.ScheduledExecutorService

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class RumSessionScopeFactoryTest {

    @Mock
    lateinit var mockSdkCore: FeatureSdkCore

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @Mock
    lateinit var mockWriter: DataWriter<Any>

    @Mock
    lateinit var mockExecutor: ScheduledExecutorService

    @Test
    fun `M build RumSessionScopeTimeseries with provided pipelines W create()`(
        @StringForgery fakeSessionId: String,
        @StringForgery fakeApplicationId: String
    ) {
        // Given
        val fakeSessionType = RumSessionType.USER
        val pipelineA = mockPipeline()
        val pipelineB = mockPipeline()
        val provider: (String, String, RumSessionType) -> List<Pipeline<*>> =
            mock<(String, String, RumSessionType) -> List<Pipeline<*>>>().apply {
                whenever(this(fakeSessionId, fakeApplicationId, fakeSessionType))
                    .thenReturn(listOf(pipelineA, pipelineB))
            }
        val testedFactory = RumSessionScopeTimeseriesFactory(
            internalLogger = mockInternalLogger,
            collectInBackground = false,
            scheduledExecutorService = mockExecutor,
            pipelinesProvider = provider
        )

        // When
        val timeseries = testedFactory.create(fakeSessionId, fakeApplicationId, fakeSessionType)

        // Then
        verify(provider).invoke(fakeSessionId, fakeApplicationId, fakeSessionType)
        assertThat(timeseries.scheduledExecutorService).isSameAs(mockExecutor)
    }

    @Test
    fun `M propagate collectInBackground flag W create()`(
        @StringForgery fakeSessionId: String,
        @StringForgery fakeApplicationId: String
    ) {
        // Given
        val emptyProvider: (String, String, RumSessionType) -> List<Pipeline<*>> = { _, _, _ -> emptyList() }
        val testedFactory = RumSessionScopeTimeseriesFactory(
            internalLogger = mockInternalLogger,
            collectInBackground = true,
            scheduledExecutorService = mockExecutor,
            pipelinesProvider = emptyProvider
        )

        // When / Then
        assertDoesNotThrow {
            testedFactory.create(fakeSessionId, fakeApplicationId, RumSessionType.USER)
        }
    }

    private fun mockPipeline(): Pipeline<*> {
        val reader = mock<DataPointsReader<Double>>()
        val serializer = mock<JsonSerializer<Double>>()
        return Pipeline(mockSdkCore, reader, Buffer(1), serializer, mockWriter, mockInternalLogger)
    }
}
