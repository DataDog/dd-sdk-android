/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.cronet.internal

import com.datadog.android.cronet.DatadogCronetEngine
import com.datadog.android.rum.internal.net.RumResourceInstrumentation
import fr.xgouchet.elmyr.annotation.BoolForgery
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.chromium.net.BidirectionalStream
import org.chromium.net.CronetEngine
import org.chromium.net.NetworkQualityRttListener
import org.chromium.net.NetworkQualityThroughputListener
import org.chromium.net.RequestFinishedInfo
import org.chromium.net.UrlRequest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.concurrent.Executor

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class DatadogCronetEngineTest {

    @Mock
    lateinit var mockDelegate: CronetEngine

    @Mock
    lateinit var mockRumResourceInstrumentation: RumResourceInstrumentation

    lateinit var testedEngine: DatadogCronetEngine

    @BeforeEach
    fun setup() {
        testedEngine = DatadogCronetEngine(
            mockDelegate,
            mockRumResourceInstrumentation
        )
    }

    @Test
    fun `M delegate to engine W newBidirectionalStreamBuilder()`(
        @StringForgery url: String
    ) {
        // Given
        val mockCallback = mock<BidirectionalStream.Callback>()
        val mockExecutor = mock<Executor>()
        val mockBuilder = mock<BidirectionalStream.Builder>()
        whenever(mockDelegate.newBidirectionalStreamBuilder(url, mockCallback, mockExecutor))
            .thenReturn(mockBuilder)

        // When
        testedEngine.newBidirectionalStreamBuilder(url, mockCallback, mockExecutor)

        // Then
        verify(mockDelegate).newBidirectionalStreamBuilder(url, mockCallback, mockExecutor)
    }

    @Test
    fun `M delegate to engine W getActiveRequestCount()`(
        @IntForgery count: Int
    ) {
        // Given
        whenever(mockDelegate.activeRequestCount).thenReturn(count)

        // When
        testedEngine.activeRequestCount

        // Then
        verify(mockDelegate).activeRequestCount
    }

    @Test
    fun `M delegate to engine W addRequestFinishedListener()`() {
        // Given
        val mockListener = mock<RequestFinishedInfo.Listener>()

        // When
        testedEngine.addRequestFinishedListener(mockListener)

        // Then
        verify(mockDelegate).addRequestFinishedListener(mockListener)
    }

    @Test
    fun `M delegate to engine W removeRequestFinishedListener()`() {
        // Given
        val mockListener = mock<RequestFinishedInfo.Listener>()

        // When
        testedEngine.removeRequestFinishedListener(mockListener)

        // Then
        verify(mockDelegate).removeRequestFinishedListener(mockListener)
    }

    @Test
    fun `M delegate to engine W getHttpRttMs()`(
        @IntForgery rtt: Int
    ) {
        // Given
        whenever(mockDelegate.httpRttMs).thenReturn(rtt)

        // When
        testedEngine.httpRttMs

        // Then
        verify(mockDelegate).httpRttMs
    }

    @Test
    fun `M delegate to engine W getTransportRttMs()`(
        @IntForgery rtt: Int
    ) {
        // Given
        whenever(mockDelegate.transportRttMs).thenReturn(rtt)

        // When
        testedEngine.transportRttMs

        // Then
        verify(mockDelegate).transportRttMs
    }

    @Test
    fun `M delegate to engine W getDownstreamThroughputKbps()`(
        @IntForgery throughput: Int
    ) {
        // Given
        whenever(mockDelegate.downstreamThroughputKbps).thenReturn(throughput)

        // When
        testedEngine.downstreamThroughputKbps

        // Then
        verify(mockDelegate).downstreamThroughputKbps
    }

    @Test
    fun `M delegate to engine W startNetLogToDisk()`(
        @StringForgery dirPath: String,
        @BoolForgery logAll: Boolean,
        @IntForgery maxSize: Int
    ) {
        // When
        testedEngine.startNetLogToDisk(dirPath, logAll, maxSize)

        // Then
        verify(mockDelegate).startNetLogToDisk(dirPath, logAll, maxSize)
    }

    @Test
    fun `M delegate to engine W bindToNetwork()`(
        @LongForgery networkHandle: Long
    ) {
        // When
        testedEngine.bindToNetwork(networkHandle)

        // Then
        verify(mockDelegate).bindToNetwork(networkHandle)
    }

    @Test
    fun `M delegate to engine W getEffectiveConnectionType()`(
        @IntForgery connectionType: Int
    ) {
        // Given
        whenever(mockDelegate.effectiveConnectionType).thenReturn(connectionType)

        // When
        testedEngine.effectiveConnectionType

        // Then
        verify(mockDelegate).effectiveConnectionType
    }

    @Test
    fun `M delegate to engine W configureNetworkQualityEstimatorForTesting()`(
        @BoolForgery useLocalHostRequests: Boolean,
        @BoolForgery useSmallerResponses: Boolean,
        @BoolForgery disableOfflineCheck: Boolean
    ) {
        // When
        testedEngine.configureNetworkQualityEstimatorForTesting(
            useLocalHostRequests,
            useSmallerResponses,
            disableOfflineCheck
        )

        // Then
        verify(mockDelegate).configureNetworkQualityEstimatorForTesting(
            useLocalHostRequests,
            useSmallerResponses,
            disableOfflineCheck
        )
    }

    @Test
    fun `M delegate to engine W addRttListener()`() {
        // Given
        val mockListener = mock<NetworkQualityRttListener>()

        // When
        testedEngine.addRttListener(mockListener)

        // Then
        verify(mockDelegate).addRttListener(mockListener)
    }

    @Test
    fun `M delegate to engine W removeRttListener()`() {
        // Given
        val mockListener = mock<NetworkQualityRttListener>()

        // When
        testedEngine.removeRttListener(mockListener)

        // Then
        verify(mockDelegate).removeRttListener(mockListener)
    }

    @Test
    fun `M delegate to engine W addThroughputListener()`() {
        // Given
        val mockListener = mock<NetworkQualityThroughputListener>()

        // When
        testedEngine.addThroughputListener(mockListener)

        // Then
        verify(mockDelegate).addThroughputListener(mockListener)
    }

    @Test
    fun `M delegate to engine W removeThroughputListener()`() {
        // Given
        val mockListener = mock<NetworkQualityThroughputListener>()

        // When
        testedEngine.removeThroughputListener(mockListener)

        // Then
        verify(mockDelegate).removeThroughputListener(mockListener)
    }

    @Test
    fun `M return DatadogUrlRequestBuilder W newUrlRequestBuilder()`(
        @StringForgery url: String
    ) {
        // Given
        val mockCallback = mock<UrlRequest.Callback>()
        val mockExecutor = mock<Executor>()
        val mockBuilder = mock<UrlRequest.Builder>()
        whenever(
            mockDelegate.newUrlRequestBuilder(
                eq(url),
                any<UrlRequest.Callback>(),
                eq(mockExecutor)
            )
        ).thenReturn(mockBuilder)

        // When
        val result = testedEngine.newUrlRequestBuilder(url, mockCallback, mockExecutor)

        // Then
        verify(mockDelegate).newUrlRequestBuilder(
            eq(url),
            any<UrlRequest.Callback>(),
            eq(mockExecutor)
        )
        check(result is DatadogUrlRequestBuilder)
    }
}
