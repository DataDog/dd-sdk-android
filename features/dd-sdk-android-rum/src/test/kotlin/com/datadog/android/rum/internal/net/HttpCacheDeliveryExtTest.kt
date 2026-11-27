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
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
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
internal class HttpCacheDeliveryExtTest {

    @Mock
    lateinit var mockResponseInfo: HttpResponseInfo

    @Mock
    lateinit var mockCachedResponseInfo: CachedHttpResponseInfo

    @Test
    fun `M return no attributes W createCacheDeliveryAttributes() { unknown delivery }`(forge: Forge) {
        // Given
        whenever(mockCachedResponseInfo.cacheDelivery) doReturn HttpCacheDelivery.Unknown
        val fakeResponseInfo = forge.anElementFrom(mockResponseInfo, mockCachedResponseInfo)

        // When / Then
        assertThat(fakeResponseInfo.createCacheDeliveryAttributes()).isEmpty()
    }

    @Test
    fun `M return other with network bytes W createCacheDeliveryAttributes() { network load, measured bytes }`(
        @LongForgery(min = 1) fakeNetworkBytes: Long
    ) {
        // Given
        whenever(mockCachedResponseInfo.cacheDelivery) doReturn HttpCacheDelivery.NetworkLoad(fakeNetworkBytes)

        // When / Then
        assertThat(mockCachedResponseInfo.createCacheDeliveryAttributes())
            .isEqualTo(deliveryAttributes("other", fakeNetworkBytes))
    }

    @Test
    fun `M return other with a null transfer size W createCacheDeliveryAttributes() { network load, no bytes }`(
        forge: Forge
    ) {
        // Given
        val fakeNetworkBytes = forge.aNullable { aLong(max = 1) }
        whenever(mockCachedResponseInfo.cacheDelivery) doReturn HttpCacheDelivery.NetworkLoad(fakeNetworkBytes)

        // When / Then
        assertThat(mockCachedResponseInfo.createCacheDeliveryAttributes()).isEqualTo(deliveryAttributes("other", null))
    }

    @Test
    fun `M return cache with zero transfer size W createCacheDeliveryAttributes() { local hit }`() {
        // Given
        whenever(mockCachedResponseInfo.cacheDelivery) doReturn HttpCacheDelivery.LocalHit

        // When / Then
        assertThat(mockCachedResponseInfo.createCacheDeliveryAttributes()).isEqualTo(cacheAttributes(0L))
    }

    @Test
    fun `M return cache with network bytes W createCacheDeliveryAttributes() { validated hit, measured bytes }`(
        @LongForgery(min = 1) fakeNetworkBytes: Long
    ) {
        // Given
        whenever(mockCachedResponseInfo.cacheDelivery) doReturn HttpCacheDelivery.ValidatedHit(fakeNetworkBytes)

        // When / Then
        assertThat(mockCachedResponseInfo.createCacheDeliveryAttributes()).isEqualTo(cacheAttributes(fakeNetworkBytes))
    }

    @Test
    fun `M return fallback transfer size W createCacheDeliveryAttributes() { validated hit, no positive bytes }`(
        forge: Forge
    ) {
        // Given
        val fakeNetworkBytes = forge.aNullable { aLong(max = 1) }
        whenever(mockCachedResponseInfo.cacheDelivery) doReturn HttpCacheDelivery.ValidatedHit(fakeNetworkBytes)

        // When / Then
        assertThat(mockCachedResponseInfo.createCacheDeliveryAttributes()).isEqualTo(cacheAttributes(300L))
    }

    @Test
    fun `M return cache with a null transfer size W createCacheDeliveryAttributes() { cached, unknown reason }`() {
        // Given
        whenever(mockCachedResponseInfo.cacheDelivery) doReturn HttpCacheDelivery.CachedUnknownReason

        // When / Then
        assertThat(mockCachedResponseInfo.createCacheDeliveryAttributes()).isEqualTo(cacheAttributes(null))
    }

    interface CachedHttpResponseInfo : HttpResponseInfo, HttpCacheInfo

    companion object {
        private fun cacheAttributes(transferSize: Long?) = deliveryAttributes("cache", transferSize)

        private fun deliveryAttributes(deliveryType: String, transferSize: Long?) = mapOf(
            RumAttributes.RESOURCE_DELIVERY_TYPE to deliveryType,
            RumAttributes.RESOURCE_TRANSFER_SIZE to transferSize
        )
    }
}
