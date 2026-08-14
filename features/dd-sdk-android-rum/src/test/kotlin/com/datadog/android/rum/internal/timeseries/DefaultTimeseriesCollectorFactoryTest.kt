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
import com.datadog.android.rum.internal.domain.scope.RumViewType
import com.datadog.android.rum.internal.timeseries.provider.DataPointsReader
import com.datadog.android.rum.internal.timeseries.serializer.JsonSerializer
import com.datadog.android.rum.utils.forge.Configurator
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class DefaultTimeseriesCollectorFactoryTest {

    @Mock
    lateinit var mockSdkCore: FeatureSdkCore

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @Mock
    lateinit var mockWriter: DataWriter<Any>

    @Mock
    lateinit var mockExecutor: ScheduledExecutorService

    @Test
    fun `M build DefaultTimeseriesCollector with provided pipelines W create()`(
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
        val testedFactory = DefaultTimeseriesCollectorFactory(
            internalLogger = mockInternalLogger,
            scheduledExecutorService = mockExecutor,
            pipelinesProvider = provider
        )

        // When
        val timeseries = testedFactory.create(
            fakeSessionId,
            fakeApplicationId,
            fakeSessionType,
            RumViewType.FOREGROUND
        )

        // Then
        verify(provider).invoke(fakeSessionId, fakeApplicationId, fakeSessionType)
        assertThat(timeseries.scheduledExecutorService).isSameAs(mockExecutor)
        assertThat(timeseries.pipelines).containsExactly(pipelineA, pipelineB)
    }

    @ParameterizedTest
    @EnumSource(value = RumViewType::class, names = ["FOREGROUND", "APPLICATION_LAUNCH"])
    fun `M seed collector with provided view type W create() { foreground view }`(
        fakeViewType: RumViewType,
        @StringForgery fakeSessionId: String,
        @StringForgery fakeApplicationId: String
    ) {
        // Given
        val timeseries = createCollectorSeededWith(fakeViewType, fakeSessionId, fakeApplicationId)

        // When
        timeseries.onSessionStart()

        // Then — the seeded view type is a foreground one, so sampling starts right away
        verify(mockExecutor).schedule(any<Runnable>(), eq(FAKE_INTERVAL_MS), eq(TimeUnit.MILLISECONDS))
    }

    @ParameterizedTest
    @EnumSource(value = RumViewType::class, names = ["NONE", "BACKGROUND"])
    fun `M seed collector with provided view type W create() { non-foreground view }`(
        fakeViewType: RumViewType,
        @StringForgery fakeSessionId: String,
        @StringForgery fakeApplicationId: String
    ) {
        // Given
        val timeseries = createCollectorSeededWith(fakeViewType, fakeSessionId, fakeApplicationId)

        // When
        timeseries.onSessionStart()

        // Then — a session starting outside the foreground must not schedule any sampling
        verify(mockExecutor, never()).schedule(any<Runnable>(), any(), any())
    }

    private fun createCollectorSeededWith(
        viewType: RumViewType,
        sessionId: String,
        applicationId: String
    ): TimeseriesCollector {
        val testedFactory = DefaultTimeseriesCollectorFactory(
            internalLogger = mockInternalLogger,
            scheduledExecutorService = mockExecutor,
            pipelinesProvider = { _, _, _ -> listOf(mockPipeline()) }
        )
        return testedFactory.create(sessionId, applicationId, RumSessionType.USER, viewType)
    }

    private fun mockPipeline(): Pipeline<*> {
        val reader = mock<DataPointsReader<Double>> {
            on { intervalMs } doReturn FAKE_INTERVAL_MS
        }
        val serializer = mock<JsonSerializer<Double>>()
        return Pipeline(mockSdkCore, reader, Buffer(1), serializer, mockWriter, mockInternalLogger)
    }

    private companion object {
        const val FAKE_INTERVAL_MS = 1_000L
    }
}
