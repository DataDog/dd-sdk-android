/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.trace.internal

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.internal.time.TimeProvider
import com.datadog.android.trace.event.SpanEventMapper
import com.datadog.android.trace.internal.domain.metrics.ClientStatsAdapter
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.utils.verifyLog
import fr.xgouchet.elmyr.annotation.BoolForgery
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
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.concurrent.ExecutorService
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class ClientStatsFeatureTest {

    private lateinit var testedFeature: ClientStatsFeature

    @Mock
    lateinit var mockSdkCore: FeatureSdkCore

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @Mock
    lateinit var mockSpanEventMapper: SpanEventMapper

    @Mock
    lateinit var mockStatAggregatorExecutor: ExecutorService

    @Mock
    lateinit var mockFlushSchedulerExecutor: ScheduledExecutorService

    @Mock
    lateinit var mockTimeProvider: TimeProvider

    @StringForgery(regex = "https://[a-z]+\\.com")
    lateinit var fakeEndpointUrl: String

    @BoolForgery
    var fakeNetworkInfoEnabled: Boolean = false

    @LongForgery(min = 1L)
    var fakeTimestampMillis: Long = 0L

    @BeforeEach
    fun `set up`() {
        whenever(mockSdkCore.internalLogger) doReturn mockInternalLogger
        whenever(mockSdkCore.timeProvider) doReturn mockTimeProvider
        whenever(mockTimeProvider.getDeviceTimestampMillis()) doReturn fakeTimestampMillis
        whenever(mockSdkCore.createSingleThreadExecutorService(any())) doReturn mockStatAggregatorExecutor
        whenever(mockSdkCore.createScheduledExecutorService(any())) doReturn mockFlushSchedulerExecutor

        testedFeature = ClientStatsFeature(
            sdkCore = mockSdkCore,
            customEndpointUrl = fakeEndpointUrl,
            spanEventMapper = mockSpanEventMapper,
            networkInfoEnabled = fakeNetworkInfoEnabled
        )
    }

    // region name

    @Test
    fun `M return client stats feature name W name()`() {
        // When + Then
        assertThat(testedFeature.name).isEqualTo(Feature.TRACING_CLIENT_STATS_FEATURE_NAME)
    }

    // endregion

    // region storageConfiguration

    @Test
    fun `M return 15 MB max item size W storageConfiguration()`() {
        // When + Then
        assertThat(testedFeature.storageConfiguration.maxItemSize).isEqualTo(15L * 1024 * 1024)
    }

    @Test
    fun `M return 1 max items per batch W storageConfiguration()`() {
        // When + Then
        assertThat(testedFeature.storageConfiguration.maxItemsPerBatch).isEqualTo(1)
    }

    @Test
    fun `M return 15 MB max batch size W storageConfiguration()`() {
        // When + Then
        assertThat(testedFeature.storageConfiguration.maxBatchSize).isEqualTo(15L * 1024 * 1024)
    }

    @Test
    fun `M return 18 hour old batch threshold W storageConfiguration()`() {
        // When + Then
        assertThat(testedFeature.storageConfiguration.oldBatchThreshold)
            .isEqualTo(18L * 60L * 60L * 1000L)
    }

    // endregion

    // region requestFactory

    @Test
    fun `M return ClientStatsRequestFactory with custom endpoint W requestFactory()`() {
        // When + Then
        assertThat(testedFeature.requestFactory.customStatsEndpointUrl).isEqualTo(fakeEndpointUrl)
    }

    @Test
    fun `M return ClientStatsRequestFactory with null endpoint W requestFactory() { no custom url }`() {
        // Given
        val feature = ClientStatsFeature(
            sdkCore = mockSdkCore,
            customEndpointUrl = null,
            spanEventMapper = mockSpanEventMapper,
            networkInfoEnabled = fakeNetworkInfoEnabled
        )

        // When + Then
        assertThat(feature.requestFactory.customStatsEndpointUrl).isNull()
    }

    // endregion

    // region onInitialize

    @Test
    fun `M schedule periodic flush with 10 second interval W onInitialize()`() {
        // When
        testedFeature.onInitialize(mock())

        // Then
        verify(mockFlushSchedulerExecutor).scheduleWithFixedDelay(
            any(),
            eq(10_000L),
            eq(10_000L),
            eq(TimeUnit.MILLISECONDS)
        )
    }

    @Test
    fun `M log warning W onInitialize() { flush scheduling rejected }`() {
        // Given
        whenever(mockFlushSchedulerExecutor.scheduleWithFixedDelay(any(), any(), any(), any()))
            .doThrow(RejectedExecutionException("Rejected"))

        // When
        testedFeature.onInitialize(mock())

        // Then
        mockInternalLogger.verifyLog(
            level = InternalLogger.Level.WARN,
            targets = listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
            message = "Failed to schedule stats flush",
            throwableClass = RejectedExecutionException::class.java
        )
    }

    // endregion

    // region onStop

    @Test
    fun `M shutdown flush scheduler W onStop()`() {
        // Given
        testedFeature.onInitialize(mock())

        // When
        testedFeature.onStop()

        // Then
        verify(mockFlushSchedulerExecutor).shutdownNow()
    }

    @Test
    fun `M submit flush all task to stat aggregator W onStop()`() {
        // Given
        testedFeature.onInitialize(mock())

        // When
        testedFeature.onStop()

        // Then
        verify(mockStatAggregatorExecutor).execute(any())
    }

    @Test
    fun `M shutdown stat aggregator W onStop()`() {
        // Given
        testedFeature.onInitialize(mock())

        // When
        testedFeature.onStop()

        // Then
        verify(mockStatAggregatorExecutor).shutdown()
    }

    @Test
    fun `M shutdown flush scheduler before shutting down stat aggregator W onStop()`() {
        // Given
        testedFeature.onInitialize(mock())

        // When
        testedFeature.onStop()

        // Then
        inOrder(mockFlushSchedulerExecutor, mockStatAggregatorExecutor) {
            verify(mockFlushSchedulerExecutor).shutdownNow()
            verify(mockStatAggregatorExecutor).shutdown()
        }
    }

    // endregion

    // region aggregator

    @Test
    fun `M expose ClientStatsAdapter W aggregator`() {
        // When + Then
        assertThat(testedFeature.aggregator).isInstanceOf(ClientStatsAdapter::class.java)
    }

    // endregion
}
