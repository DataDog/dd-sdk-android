/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.api.instrumentation.network

import com.datadog.android.utils.forge.Configurator
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions

@Extensions(
    ExtendWith(ForgeExtension::class)
)
@ForgeConfiguration(Configurator::class)
internal class HttpCacheInfoTest {

    @Test
    fun `M return NotCached W cacheDelivery { capability unsupported }`() {
        // Given
        val testedInfo: HttpResponseInfo = StubResponseInfo()

        // When
        val result = testedInfo.cacheDelivery

        // Then
        assertThat(result).isEqualTo(HttpCacheDelivery.NotCached)
    }

    @Test
    fun `M delegate W cacheDelivery { capability supported }`(
        forge: Forge
    ) {
        // Given
        val fakeDelivery = forge.anElementFrom(
            HttpCacheDelivery.NotCached,
            HttpCacheDelivery.LocalHit,
            HttpCacheDelivery.ValidatedHit(forge.aNullable { aPositiveLong() }),
            HttpCacheDelivery.CachedUnknownReason
        )
        val testedInfo: HttpResponseInfo = CachedResponseInfo(fakeDelivery)

        // When
        val result = testedInfo.cacheDelivery

        // Then
        assertThat(result).isSameAs(fakeDelivery)
    }

    private open class StubResponseInfo : HttpResponseInfo {
        override val url: String = ""
        override val statusCode: Int = 200
        override val headers: Map<String, List<String>> = emptyMap()
        override val contentType: String? = null
        override val contentLength: Long? = null
        override val request: HttpRequestInfo? = null
    }

    private class CachedResponseInfo(
        override val cacheDelivery: HttpCacheDelivery
    ) : StubResponseInfo(), HttpCacheInfo
}
