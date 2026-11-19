/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.net

import com.datadog.android.api.instrumentation.network.HttpCacheDelivery
import com.datadog.android.api.instrumentation.network.HttpCacheInfo
import com.datadog.android.api.instrumentation.network.HttpResponseInfo
import com.datadog.android.rum.RumAttributes
import com.datadog.android.rum.utils.forge.Configurator
import fr.xgouchet.elmyr.annotation.LongForgery
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

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class HttpCacheDeliveryResolverTest {

    private lateinit var testedResolver: HttpCacheDeliveryResolver

    @Mock
    lateinit var mockResponseInfo: HttpResponseInfo

    @Mock
    lateinit var mockCachedResponseInfo: CachedHttpResponseInfo

    @BeforeEach
    fun `set up`() {
        testedResolver = HttpCacheDeliveryResolver()
    }

    @Test
    fun `M return no attributes W resolve() { response info without cache info }`() {
        // When
        val result = testedResolver.resolve(mockResponseInfo)

        // Then
        assertThat(result).isEmpty()
    }

    @Test
    fun `M return no attributes W resolve() { not cached }`() {
        // Given
        whenever(mockCachedResponseInfo.cacheDelivery) doReturn HttpCacheDelivery.NotCached

        // When
        val result = testedResolver.resolve(mockCachedResponseInfo)

        // Then
        assertThat(result).isEmpty()
    }

    @Test
    fun `M return cache with zero transfer size W resolve() { local hit }`() {
        // Given
        whenever(mockCachedResponseInfo.cacheDelivery) doReturn HttpCacheDelivery.LocalHit

        // When
        val result = testedResolver.resolve(mockCachedResponseInfo)

        // Then
        assertThat(result).isEqualTo(
            mapOf(
                RumAttributes.INTERNAL_RESOURCE_DELIVERY_TYPE to "cache",
                RumAttributes.INTERNAL_RESOURCE_TRANSFER_SIZE to 0L
            )
        )
    }

    @Test
    fun `M return cache with network bytes W resolve() { validated hit, measured bytes }`(
        @LongForgery(min = 1) fakeNetworkBytes: Long
    ) {
        // Given
        whenever(mockCachedResponseInfo.cacheDelivery) doReturn HttpCacheDelivery.ValidatedHit(fakeNetworkBytes)

        // When
        val result = testedResolver.resolve(mockCachedResponseInfo)

        // Then
        assertThat(result).isEqualTo(
            mapOf(
                RumAttributes.INTERNAL_RESOURCE_DELIVERY_TYPE to "cache",
                RumAttributes.INTERNAL_RESOURCE_TRANSFER_SIZE to fakeNetworkBytes
            )
        )
    }

    @Test
    fun `M return cache with fallback transfer size W resolve() { validated hit, unmeasured bytes }`() {
        // Given
        whenever(mockCachedResponseInfo.cacheDelivery) doReturn HttpCacheDelivery.ValidatedHit(null)

        // When
        val result = testedResolver.resolve(mockCachedResponseInfo)

        // Then
        assertThat(result).isEqualTo(
            mapOf(
                RumAttributes.INTERNAL_RESOURCE_DELIVERY_TYPE to "cache",
                RumAttributes.INTERNAL_RESOURCE_TRANSFER_SIZE to 300L
            )
        )
    }

    @Test
    fun `M return cache with fallback transfer size W resolve() { validated hit, non positive bytes }`(
        @LongForgery(max = 1) fakeNetworkBytes: Long
    ) {
        // Given
        whenever(mockCachedResponseInfo.cacheDelivery) doReturn HttpCacheDelivery.ValidatedHit(fakeNetworkBytes)

        // When
        val result = testedResolver.resolve(mockCachedResponseInfo)

        // Then
        assertThat(result).isEqualTo(
            mapOf(
                RumAttributes.INTERNAL_RESOURCE_DELIVERY_TYPE to "cache",
                RumAttributes.INTERNAL_RESOURCE_TRANSFER_SIZE to 300L
            )
        )
    }

    @Test
    fun `M return cache with a null transfer size W resolve() { cached, unknown reason }`() {
        // Given
        whenever(mockCachedResponseInfo.cacheDelivery) doReturn HttpCacheDelivery.CachedUnknownReason

        // When
        val result = testedResolver.resolve(mockCachedResponseInfo)

        // Then
        assertThat(result).isEqualTo(
            mapOf(
                RumAttributes.INTERNAL_RESOURCE_DELIVERY_TYPE to "cache",
                RumAttributes.INTERNAL_RESOURCE_TRANSFER_SIZE to null
            )
        )
    }

    interface CachedHttpResponseInfo : HttpResponseInfo, HttpCacheInfo
}
