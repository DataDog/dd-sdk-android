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
import org.mockito.kotlin.inOrder
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
internal class ClientStatsFeatureTest {

    private lateinit var testedFeature: ClientStatsFeature

    @Mock
    lateinit var mockSdkCore: FeatureSdkCore

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @Mock
    lateinit var mockSpanEventMapper: SpanEventMapper

    @Mock
    lateinit var mockStatsExecutor: ScheduledExecutorService

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
        whenever(mockSdkCore.createScheduledExecutorService(any())) doReturn mockStatsExecutor

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
    fun `M return 512 KB max item size W storageConfiguration()`() {
        // When + Then
        assertThat(testedFeature.storageConfiguration.maxItemSize).isEqualTo(512L * 1024)
    }

    @Test
    fun `M return 4000 max items per batch W storageConfiguration()`() {
        // When + Then
        assertThat(testedFeature.storageConfiguration.maxItemsPerBatch).isEqualTo(4000)
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

    // region onStop

    @Test
    fun `M submit flush all task W onStop()`() {
        // When
        testedFeature.onStop()

        // Then
        verify(mockStatsExecutor).execute(any())
    }

    @Test
    fun `M shutdown stats executor W onStop()`() {
        // When
        testedFeature.onStop()

        // Then
        verify(mockStatsExecutor).shutdown()
    }

    @Test
    fun `M submit flush all task before shutting down stats executor W onStop()`() {
        // When
        testedFeature.onStop()

        // Then
        inOrder(mockStatsExecutor) {
            verify(mockStatsExecutor).execute(any())
            verify(mockStatsExecutor).shutdown()
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
