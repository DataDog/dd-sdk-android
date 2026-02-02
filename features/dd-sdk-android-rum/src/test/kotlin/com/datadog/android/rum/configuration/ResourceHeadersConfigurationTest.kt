/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.configuration

import com.datadog.android.rum.utils.forge.Configurator
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class ResourceHeadersConfigurationTest {

    private lateinit var testedBuilder: ResourceHeadersConfiguration.Builder

    @BeforeEach
    fun `set up`() {
        testedBuilder = ResourceHeadersConfiguration.Builder()
    }

    // region Helper functions

    private fun List<HeaderMatcher>.toHeaderNames(): List<String> =
        mapNotNull { (it as? HeaderMatcher.Exact)?.name }

    // endregion

    @Test
    fun `M include default response headers W build()`() {
        // When
        val config = testedBuilder.build()

        // Then
        assertThat(config.responseHeadersToCapture.toHeaderNames())
            .containsAll(ResourceHeadersConfiguration.DEFAULT_RESPONSE_HEADERS)
    }

    @Test
    fun `M include default request headers W build()`() {
        // When
        val config = testedBuilder.build()

        // Then
        assertThat(config.requestHeadersToCapture.toHeaderNames())
            .containsAll(ResourceHeadersConfiguration.DEFAULT_REQUEST_HEADERS)
    }

    @Test
    fun `M capture custom request headers W captureRequestHeaders()`(forge: Forge) {
        // Given
        val customHeaders = forge.aList { anAlphabeticalString() }

        // When
        val config = testedBuilder
            .captureRequestHeaders(customHeaders)
            .build()

        // Then
        assertThat(config.requestHeadersToCapture.toHeaderNames())
            .containsAll(customHeaders.map { it.lowercase() })
    }

    @Test
    fun `M capture custom response headers W captureResponseHeaders()`(forge: Forge) {
        // Given
        val customHeaders = forge.aList { anAlphabeticalString() }

        // When
        val config = testedBuilder
            .captureResponseHeaders(customHeaders)
            .build()

        // Then
        assertThat(config.responseHeadersToCapture.toHeaderNames())
            .containsAll(customHeaders.map { it.lowercase() })
        // Should also contain default headers
        assertThat(config.responseHeadersToCapture.toHeaderNames())
            .containsAll(ResourceHeadersConfiguration.DEFAULT_RESPONSE_HEADERS)
    }

    @Test
    fun `M filter forbidden request headers W captureRequestHeaders()`() {
        // Given
        val forbiddenHeaders = ResourceHeadersConfiguration.FORBIDDEN_HEADERS.toList()
        val validHeader = "x-custom-header"
        val allHeaders = forbiddenHeaders + listOf(validHeader)

        // When
        val config = testedBuilder
            .captureRequestHeaders(allHeaders)
            .build()

        // Then
        assertThat(config.requestHeadersToCapture.toHeaderNames())
            .contains(validHeader)
        assertThat(config.requestHeadersToCapture.toHeaderNames())
            .containsAll(ResourceHeadersConfiguration.DEFAULT_REQUEST_HEADERS)
        assertThat(config.requestHeadersToCapture.toHeaderNames())
            .doesNotContainAnyElementsOf(forbiddenHeaders)
    }

    @Test
    fun `M filter forbidden response headers W captureResponseHeaders()`() {
        // Given
        val forbiddenHeaders = ResourceHeadersConfiguration.FORBIDDEN_HEADERS.toList()
        val validHeader = "x-custom-header"
        val allHeaders = forbiddenHeaders + listOf(validHeader)

        // When
        val config = testedBuilder
            .captureResponseHeaders(allHeaders)
            .build()

        // Then
        assertThat(config.responseHeadersToCapture.toHeaderNames())
            .contains(validHeader)
        assertThat(config.responseHeadersToCapture.toHeaderNames())
            .doesNotContainAnyElementsOf(forbiddenHeaders)
    }

    @Test
    fun `M normalize headers to lowercase W captureRequestHeaders()`() {
        // Given
        val mixedCaseHeaders = listOf("X-Custom-HEADER", "ACCEPT")

        // When
        val config = testedBuilder
            .captureRequestHeaders(mixedCaseHeaders)
            .build()

        // Then
        assertThat(config.requestHeadersToCapture.toHeaderNames())
            .contains("x-custom-header", "accept")
        // Should also contain default headers
        assertThat(config.requestHeadersToCapture.toHeaderNames())
            .containsAll(ResourceHeadersConfiguration.DEFAULT_REQUEST_HEADERS)
    }

    @Test
    fun `M normalize headers to lowercase W captureResponseHeaders()`() {
        // Given
        val mixedCaseHeaders = listOf("X-RateLimit-Remaining", "X-Custom-HEADER")

        // When
        val config = testedBuilder
            .captureResponseHeaders(mixedCaseHeaders)
            .build()

        // Then
        assertThat(config.responseHeadersToCapture.toHeaderNames())
            .contains("x-ratelimit-remaining", "x-custom-header")
    }

    @Test
    fun `M deduplicate request headers W captureRequestHeaders()`() {
        // Given
        // "content-type" is in DEFAULT_REQUEST_HEADERS
        val customHeaders = listOf("content-type", "x-custom-header")

        // When
        val config = testedBuilder
            .captureRequestHeaders(customHeaders)
            .build()

        // Then
        val contentTypeCount = config.requestHeadersToCapture.toHeaderNames().count {
            it == "content-type"
        }
        assertThat(contentTypeCount).isEqualTo(1)
    }

    @Test
    fun `M deduplicate response headers W captureResponseHeaders()`() {
        // Given
        // "content-type" is in DEFAULT_RESPONSE_HEADERS
        val customHeaders = listOf("content-type", "x-custom-header")

        // When
        val config = testedBuilder
            .captureResponseHeaders(customHeaders)
            .build()

        // Then
        val contentTypeCount = config.responseHeadersToCapture.toHeaderNames().count {
            it == "content-type"
        }
        assertThat(contentTypeCount).isEqualTo(1)
    }

    @Test
    fun `M support chained builder calls W captureRequestHeaders() and captureResponseHeaders()`(
        forge: Forge
    ) {
        // Given
        val requestHeaders = forge.aList { anAlphabeticalString() }
        val responseHeaders = forge.aList { anAlphabeticalString() }

        // When
        val config = testedBuilder
            .captureRequestHeaders(requestHeaders)
            .captureResponseHeaders(responseHeaders)
            .build()

        // Then
        assertThat(config.requestHeadersToCapture.toHeaderNames())
            .containsAll(requestHeaders.map { it.lowercase() })
        assertThat(config.requestHeadersToCapture.toHeaderNames())
            .containsAll(ResourceHeadersConfiguration.DEFAULT_REQUEST_HEADERS)
        assertThat(config.responseHeadersToCapture.toHeaderNames())
            .containsAll(responseHeaders.map { it.lowercase() })
        assertThat(config.responseHeadersToCapture.toHeaderNames())
            .containsAll(ResourceHeadersConfiguration.DEFAULT_RESPONSE_HEADERS)
    }

    @Test
    fun `M verify default request headers are sensible W DEFAULT_REQUEST_HEADERS`() {
        // Then
        assertThat(ResourceHeadersConfiguration.DEFAULT_REQUEST_HEADERS)
            .containsExactlyInAnyOrder(
                "cache-control",
                "content-type",
                "accept",
                "accept-encoding"
            )
    }

    @Test
    fun `M verify default response headers are sensible W DEFAULT_RESPONSE_HEADERS`() {
        // Then
        assertThat(ResourceHeadersConfiguration.DEFAULT_RESPONSE_HEADERS)
            .containsExactlyInAnyOrder(
                "cache-control",
                "etag",
                "age",
                "expires",
                "content-type",
                "content-encoding",
                "vary",
                "content-length",
                "server-timing",
                "x-cache"
            )
    }

    @Test
    fun `M verify all forbidden headers are included W FORBIDDEN_HEADERS`() {
        // Then
        assertThat(ResourceHeadersConfiguration.FORBIDDEN_HEADERS)
            .containsExactlyInAnyOrder(
                "authorization",
                "proxy-authorization",
                "x-api-key",
                "x-access-token",
                "x-auth-token",
                "x-session-token",
                "cookie",
                "set-cookie",
                "x-forwarded-for",
                "x-real-ip",
                "cf-connecting-ip",
                "true-client-ip",
                "x-csrf-token",
                "x-xsrf-token",
                "x-security-token"
            )
    }

    @Test
    fun `M verify size limit constant W HEADER_SIZE_LIMIT_BYTES`() {
        // Then
        assertThat(ResourceHeadersConfiguration.HEADER_SIZE_LIMIT_BYTES)
            .isEqualTo(2048)
    }

    @Test
    fun `M exclude default request headers W excludeDefaultHeaders()`(forge: Forge) {
        // Given
        val customHeaders = forge.aList { anAlphabeticalString() }

        // When
        val config = testedBuilder
            .excludeDefaultHeaders()
            .captureRequestHeaders(customHeaders)
            .build()

        // Then
        assertThat(config.requestHeadersToCapture.toHeaderNames())
            .containsExactlyInAnyOrderElementsOf(customHeaders.map { it.lowercase() })
        assertThat(config.requestHeadersToCapture.toHeaderNames())
            .doesNotContainAnyElementsOf(ResourceHeadersConfiguration.DEFAULT_REQUEST_HEADERS)
    }

    @Test
    fun `M exclude default response headers W excludeDefaultHeaders()`(forge: Forge) {
        // Given
        val customHeaders = forge.aList { anAlphabeticalString() }

        // When
        val config = testedBuilder
            .excludeDefaultHeaders()
            .captureResponseHeaders(customHeaders)
            .build()

        // Then
        assertThat(config.responseHeadersToCapture.toHeaderNames())
            .containsExactlyInAnyOrderElementsOf(customHeaders.map { it.lowercase() })
        assertThat(config.responseHeadersToCapture.toHeaderNames())
            .doesNotContainAnyElementsOf(ResourceHeadersConfiguration.DEFAULT_RESPONSE_HEADERS)
    }

    @Test
    fun `M return empty lists W excludeDefaultHeaders() { no custom headers }`() {
        // When
        val config = testedBuilder
            .excludeDefaultHeaders()
            .build()

        // Then
        assertThat(config.requestHeadersToCapture.toHeaderNames()).isEmpty()
        assertThat(config.responseHeadersToCapture.toHeaderNames()).isEmpty()
    }

    @Test
    fun `M include defaults by default W build() { excludeDefaultHeaders not called }`() {
        // When
        val config = testedBuilder.build()

        // Then
        assertThat(config.requestHeadersToCapture.toHeaderNames())
            .containsAll(ResourceHeadersConfiguration.DEFAULT_REQUEST_HEADERS)
        assertThat(config.responseHeadersToCapture.toHeaderNames())
            .containsAll(ResourceHeadersConfiguration.DEFAULT_RESPONSE_HEADERS)
    }
}
