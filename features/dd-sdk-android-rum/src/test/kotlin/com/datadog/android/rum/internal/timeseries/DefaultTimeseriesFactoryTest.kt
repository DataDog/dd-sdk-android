/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.timeseries

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.api.storage.DataWriter
import com.datadog.android.internal.time.TimeProvider
import com.datadog.android.rum.ExperimentalRumApi
import com.datadog.android.rum.RumSessionType
import com.datadog.android.rum.internal.RumFeature.Companion.MEMORY_TIMESERIES_DISABLED_MESSAGE
import com.datadog.android.rum.internal.domain.RumContext
import com.datadog.android.rum.internal.instrumentation.insights.InsightsCollector
import com.datadog.android.rum.timeseries.TimeseriesConfiguration
import com.datadog.android.rum.utils.forge.Configurator
import com.datadog.android.utils.verifyLog
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.LongForgery
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
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.concurrent.ScheduledExecutorService

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
@OptIn(ExperimentalRumApi::class)
internal class DefaultTimeseriesFactoryTest {

    @Mock
    lateinit var mockSdkCore: FeatureSdkCore

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @Mock
    lateinit var mockTimeProvider: TimeProvider

    @Mock
    lateinit var mockWriter: DataWriter<Any>

    @Mock
    lateinit var mockExecutor: ScheduledExecutorService

    @Mock
    lateinit var mockInsightsCollector: InsightsCollector

    @Forgery
    lateinit var fakeRumContext: RumContext

    @StringForgery
    lateinit var fakeSessionId: String

    @BeforeEach
    fun `set up`() {
        whenever(mockSdkCore.internalLogger) doReturn mockInternalLogger
        whenever(mockSdkCore.timeProvider) doReturn mockTimeProvider
    }

    private fun testedFactory(totalRamBytes: Long) = DefaultTimeseriesFactory(
        sdkCore = mockSdkCore,
        configuration = TimeseriesConfiguration.Builder().build(),
        scheduledExecutorService = mockExecutor,
        insightsCollector = mockInsightsCollector,
        totalRamBytes = totalRamBytes,
        dataWriter = mockWriter
    )

    @Test
    fun `M build RumTimeseries with cpu and memory pipelines W create() {totalRamBytes greater than zero}`(
        @LongForgery(min = 1L) fakeTotalRamBytes: Long
    ) {
        // When
        val timeseries = testedFactory(fakeTotalRamBytes)
            .create(fakeSessionId, RumSessionType.USER, fakeRumContext)

        // Then
        check(timeseries is RumTimeseries)
        assertThat(timeseries.pipelines).hasSize(2)
    }

    @Test
    fun `M build RumTimeseries with only cpu pipeline W create() {totalRamBytes is zero}`() {
        // When
        val timeseries = testedFactory(0L)
            .create(fakeSessionId, RumSessionType.USER, fakeRumContext)

        // Then
        check(timeseries is RumTimeseries)
        assertThat(timeseries.pipelines).hasSize(1)
        mockInternalLogger.verifyLog(
            InternalLogger.Level.WARN,
            InternalLogger.Target.USER,
            MEMORY_TIMESERIES_DISABLED_MESSAGE,
            onlyOnce = true
        )
    }

    @Test
    fun `M propagate scheduledExecutorService W create()`() {
        // When
        val timeseries = testedFactory(0L)
            .create(fakeSessionId, RumSessionType.USER, fakeRumContext)

        // Then
        check(timeseries is RumTimeseries)
        assertThat(timeseries.scheduledExecutorService).isSameAs(mockExecutor)
    }
}
