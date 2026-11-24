/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.cronet.internal

import com.datadog.android.api.instrumentation.network.HttpCacheDelivery
import com.datadog.android.api.instrumentation.network.peekBody
import com.datadog.android.internal.network.HttpSpec
import com.datadog.android.utils.forge.Configurator
import com.datadog.tools.unit.forge.exhaustiveAttributes
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.chromium.net.UrlResponseInfo
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@ForgeConfiguration(Configurator::class)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class CronetResponseInfoTest {

    @Mock
    lateinit var mockUrlResponseInfo: UrlResponseInfo

    @Mock
    lateinit var mockRequestInfo: CronetHttpRequestInfo

    lateinit var fakeHeaders: Map<String, List<String>>

    @BeforeEach
    fun `set up`(forge: Forge) {
        fakeHeaders = forge.exhaustiveAttributes().mapValues { listOf(it.value.toString()) }
    }

    @Test
    fun `M return url W url property`(
        @StringForgery fakeUrl: String
    ) {
        // Given
        whenever(mockUrlResponseInfo.url).thenReturn(fakeUrl)
        val responseInfo = CronetHttpResponseInfo(mockUrlResponseInfo, mockRequestInfo, metricsReceivedByteCount = null)

        // When
        val result = responseInfo.url

        // Then
        assertThat(result).isEqualTo(fakeUrl)
    }

    @Test
    fun `M return status code W statusCode property`(
        @IntForgery(min = 100, max = 599) fakeStatusCode: Int
    ) {
        // Given
        whenever(mockUrlResponseInfo.httpStatusCode).thenReturn(fakeStatusCode)
        val responseInfo = CronetHttpResponseInfo(mockUrlResponseInfo, mockRequestInfo, metricsReceivedByteCount = null)

        // When
        val result = responseInfo.statusCode

        // Then
        assertThat(result).isEqualTo(fakeStatusCode)
    }

    @Test
    fun `M return headers W headers property`() {
        // Given

        whenever(mockUrlResponseInfo.allHeaders).thenReturn(fakeHeaders)
        val responseInfo = CronetHttpResponseInfo(mockUrlResponseInfo, mockRequestInfo, metricsReceivedByteCount = null)

        // When
        val result = responseInfo.headers

        // Then
        assertThat(result).isEqualTo(fakeHeaders)
    }

    @Test
    fun `M return content type W contentType property { content type header present }`(
        @StringForgery fakeContentType: String
    ) {
        // Given
        val fakeHeaders = fakeHeaders + mapOf(HttpSpec.Header.CONTENT_TYPE to listOf(fakeContentType))
        whenever(mockUrlResponseInfo.allHeaders).thenReturn(fakeHeaders)
        val responseInfo = CronetHttpResponseInfo(mockUrlResponseInfo, mockRequestInfo, metricsReceivedByteCount = null)

        // When
        val result = responseInfo.contentType

        // Then
        assertThat(result).isEqualTo(fakeContentType)
    }

    @Test
    fun `M return null W contentType property { no content type header }`() {
        // Given
        whenever(mockUrlResponseInfo.allHeaders).thenReturn(emptyMap())
        val responseInfo = CronetHttpResponseInfo(mockUrlResponseInfo, mockRequestInfo, metricsReceivedByteCount = null)

        // When
        val result = responseInfo.contentType

        // Then
        assertThat(result).isNull()
    }

    @Test
    fun `M return request W request property`() {
        // Given
        val responseInfo = CronetHttpResponseInfo(mockUrlResponseInfo, mockRequestInfo, metricsReceivedByteCount = null)

        // When
        val result = responseInfo.request

        // Then
        assertThat(result).isSameAs(mockRequestInfo)
    }

    @Test
    fun `M return null W request property { null request }`() {
        // Given
        val responseInfo = CronetHttpResponseInfo(mockUrlResponseInfo, null, metricsReceivedByteCount = null)

        // When
        val result = responseInfo.request

        // Then
        assertThat(result).isNull()
    }

    @Test
    fun `M return content length from header W computeContentLength() { header present }`(
        @LongForgery(min = 1) fakeContentLength: Long
    ) {
        // Given
        val fakeHeaders = fakeHeaders + mapOf(HttpSpec.Header.CONTENT_LENGTH to listOf(fakeContentLength.toString()))
        whenever(mockUrlResponseInfo.allHeaders).thenReturn(fakeHeaders)
        val responseInfo = CronetHttpResponseInfo(mockUrlResponseInfo, mockRequestInfo, metricsReceivedByteCount = null)

        // When
        val result = responseInfo.contentLength

        // Then
        assertThat(result).isEqualTo(fakeContentLength)
    }

    @Test
    fun `M return received byte count W computeContentLength() { no header, positive byte count }`(
        @LongForgery(min = 1) fakeByteCount: Long
    ) {
        // Given
        whenever(mockUrlResponseInfo.allHeaders).thenReturn(emptyMap())
        whenever(mockUrlResponseInfo.receivedByteCount).thenReturn(fakeByteCount)
        val responseInfo = CronetHttpResponseInfo(mockUrlResponseInfo, mockRequestInfo, metricsReceivedByteCount = null)

        // When
        val result = responseInfo.contentLength

        // Then
        assertThat(result).isEqualTo(fakeByteCount)
    }

    @Test
    fun `M return zero W computeContentLength() { no header, byte count is zero }`() {
        // Given
        whenever(mockUrlResponseInfo.allHeaders).thenReturn(emptyMap())
        whenever(mockUrlResponseInfo.receivedByteCount).thenReturn(0L)
        val responseInfo = CronetHttpResponseInfo(mockUrlResponseInfo, mockRequestInfo, metricsReceivedByteCount = null)

        // When
        val result = responseInfo.contentLength

        // Then
        assertThat(result).isEqualTo(0L)
    }

    @Test
    fun `M return null W computeContentLength() { no header, negative byte count }`() {
        // Given
        whenever(mockUrlResponseInfo.allHeaders).thenReturn(emptyMap())
        whenever(mockUrlResponseInfo.receivedByteCount).thenReturn(-1L)
        val responseInfo = CronetHttpResponseInfo(mockUrlResponseInfo, mockRequestInfo, metricsReceivedByteCount = null)

        // When
        val result = responseInfo.contentLength

        // Then
        assertThat(result).isNull()
    }

    @Test
    fun `M return null W computeContentLength() { header not a valid number }`(
        @StringForgery fakeInvalidContentLength: String
    ) {
        // Given
        val fakeHeaders = fakeHeaders + mapOf(
            HttpSpec.Header.CONTENT_LENGTH to listOf(fakeInvalidContentLength)
        )
        whenever(mockUrlResponseInfo.allHeaders).thenReturn(fakeHeaders)
        whenever(mockUrlResponseInfo.receivedByteCount).thenReturn(100L)
        val responseInfo = CronetHttpResponseInfo(mockUrlResponseInfo, mockRequestInfo, metricsReceivedByteCount = null)

        // When
        val result = responseInfo.contentLength

        // Then
        assertThat(result).isEqualTo(100L)
    }

    @Test
    fun `M prefer header over byte count W computeContentLength() { both available }`(
        @LongForgery(min = 1000) fakeHeaderLength: Long,
        @LongForgery(min = 1, max = 999) fakeByteCount: Long
    ) {
        // Given
        val fakeHeaders = fakeHeaders + mapOf(HttpSpec.Header.CONTENT_LENGTH to listOf(fakeHeaderLength.toString()))
        whenever(mockUrlResponseInfo.allHeaders).thenReturn(fakeHeaders)
        whenever(mockUrlResponseInfo.receivedByteCount).thenReturn(fakeByteCount)
        val responseInfo = CronetHttpResponseInfo(mockUrlResponseInfo, mockRequestInfo, metricsReceivedByteCount = null)

        // When
        val result = responseInfo.contentLength

        // Then
        assertThat(result).isEqualTo(fakeHeaderLength)
    }

    @Test
    fun `M return null W peekBody() { response payload never flows through the SDK }`(
        @LongForgery(min = 1) fakeMaxBytes: Long
    ) {
        // Given
        whenever(mockUrlResponseInfo.allHeaders).thenReturn(fakeHeaders)
        val responseInfo = CronetHttpResponseInfo(mockUrlResponseInfo, mockRequestInfo, metricsReceivedByteCount = null)

        // When
        val result = responseInfo.peekBody(fakeMaxBytes)

        // Then
        assertThat(result).isNull()
    }

    // Each test covers inputs that also match a later branch of the `when`, so that reordering the
    // branches fails a test

    @Test
    fun `M return NetworkLoad with the URL chain byte count W cacheDelivery { not cached }`(
        @LongForgery(min = 1) fakeReceivedByteCount: Long,
        @LongForgery(min = 1) fakeUrlChainReceivedByteCount: Long,
        @IntForgery(min = 2, max = 5) fakeUrlChainSize: Int
    ) {
        // Given
        val fakeCases = listOf(0L, fakeReceivedByteCount, null).flatMap { receivedByteCount ->
            listOf(1, fakeUrlChainSize).flatMap { urlChainSize ->
                listOf(0L, fakeUrlChainReceivedByteCount).map { urlChainReceivedByteCount ->
                    urlChainReceivedByteCount to cronetResponseInfo(
                        wasCached = false,
                        receivedByteCount = receivedByteCount,
                        urlChainSize = urlChainSize,
                        urlChainReceivedByteCount = urlChainReceivedByteCount
                    )
                }
            }
        }

        // When / Then
        fakeCases.forEach { (urlChainReceivedByteCount, testedResponseInfo) ->
            assertThat(testedResponseInfo.cacheDelivery)
                .isEqualTo(HttpCacheDelivery.NetworkLoad(networkBytes = urlChainReceivedByteCount))
        }
    }

    @Test
    fun `M return LocalHit W cacheDelivery { cached, redirected, no bytes received on the chain }`(
        @LongForgery(min = 1) fakeReceivedByteCount: Long,
        @IntForgery(min = 2, max = 5) fakeUrlChainSize: Int
    ) {
        // Given
        val testedResponseInfos = listOf(0L, fakeReceivedByteCount, null).map { receivedByteCount ->
            cronetResponseInfo(
                wasCached = true,
                receivedByteCount = receivedByteCount,
                urlChainSize = fakeUrlChainSize,
                urlChainReceivedByteCount = 0L
            )
        }

        // When / Then
        assertThat(testedResponseInfos.map { it.cacheDelivery }).containsOnly(HttpCacheDelivery.LocalHit)
    }

    @Test
    fun `M return CachedUnknownReason W cacheDelivery { cached, redirected, bytes received on the chain }`(
        @LongForgery(min = 1) fakeReceivedByteCount: Long,
        @IntForgery(min = 2, max = 5) fakeUrlChainSize: Int
    ) {
        // Given
        val testedResponseInfos = listOf(0L, fakeReceivedByteCount, null).map { receivedByteCount ->
            cronetResponseInfo(
                wasCached = true,
                receivedByteCount = receivedByteCount,
                urlChainSize = fakeUrlChainSize,
                urlChainReceivedByteCount = fakeReceivedByteCount
            )
        }

        // When / Then
        assertThat(testedResponseInfos.map { it.cacheDelivery }).containsOnly(HttpCacheDelivery.CachedUnknownReason)
    }

    @Test
    fun `M return LocalHit W cacheDelivery { cached, not redirected, no bytes received }`(
        @LongForgery(min = 1) fakeUrlChainReceivedByteCount: Long
    ) {
        // Given
        val testedResponseInfos = listOf(0L, fakeUrlChainReceivedByteCount).map { urlChainReceivedByteCount ->
            cronetResponseInfo(
                wasCached = true,
                receivedByteCount = 0L,
                urlChainReceivedByteCount = urlChainReceivedByteCount
            )
        }

        // When / Then
        assertThat(testedResponseInfos.map { it.cacheDelivery }).containsOnly(HttpCacheDelivery.LocalHit)
    }

    @Test
    fun `M return ValidatedHit W cacheDelivery { cached, not redirected, bytes received }`(
        @LongForgery(min = 1) fakeReceivedByteCount: Long
    ) {
        // Given
        val testedResponseInfo = cronetResponseInfo(
            wasCached = true,
            receivedByteCount = fakeReceivedByteCount,
            urlChainReceivedByteCount = fakeReceivedByteCount
        )

        // When / Then
        assertThat(testedResponseInfo.cacheDelivery)
            .isEqualTo(HttpCacheDelivery.ValidatedHit(networkBytes = fakeReceivedByteCount))
    }

    @Test
    fun `M return CachedUnknownReason W cacheDelivery { cached, not redirected, unknown received bytes }`(
        @LongForgery(max = 0) fakeNegativeByteCount: Long
    ) {
        // Given
        val testedResponseInfos = listOf(null, fakeNegativeByteCount).map { receivedByteCount ->
            cronetResponseInfo(wasCached = true, receivedByteCount = receivedByteCount)
        }

        // When / Then
        assertThat(testedResponseInfos.map { it.cacheDelivery }).containsOnly(HttpCacheDelivery.CachedUnknownReason)
    }

    private fun cronetResponseInfo(
        wasCached: Boolean,
        receivedByteCount: Long?,
        urlChainSize: Int = 1,
        urlChainReceivedByteCount: Long = 0L
    ): CronetHttpResponseInfo {
        val response = mock<UrlResponseInfo> {
            on { wasCached() } doReturn wasCached
            on { urlChain } doReturn List(urlChainSize) { index -> "https://example.com/hop$index" }
            on { this.receivedByteCount } doReturn urlChainReceivedByteCount
        }
        return CronetHttpResponseInfo(
            response = response,
            request = mockRequestInfo,
            metricsReceivedByteCount = receivedByteCount
        )
    }
}
