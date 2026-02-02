/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.net

import com.datadog.android.api.InternalLogger
import com.datadog.android.rum.configuration.ResourceHeadersConfiguration
import com.datadog.android.rum.utils.forge.Configurator
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
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class ResourceHeadersExtractorTest {

    private lateinit var testedExtractor: ResourceHeadersExtractor

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @BeforeEach
    fun `set up`() {
        // Default configuration with some request and response headers
        val config = ResourceHeadersConfiguration.Builder()
            .captureRequestHeaders(listOf("x-request-id", "x-correlation-id"))
            .captureResponseHeaders(listOf("x-ratelimit-remaining"))
            .build()
        testedExtractor = ResourceHeadersExtractor(config, mockInternalLogger)
    }

    @Test
    fun `M extract matching request headers W extractRequestHeaders()`() {
        // Given
        val headers = mapOf(
            "X-Request-ID" to listOf("abc123"),
            "X-Correlation-ID" to listOf("def456"),
            "Content-Type" to listOf("application/json"),
            "Cache-Control" to listOf("no-cache")
        )

        // When
        val result = testedExtractor.extractRequestHeaders(headers)

        // Then
        assertThat(result).containsEntry("x-request-id", "abc123")
        assertThat(result).containsEntry("x-correlation-id", "def456")
        // Default headers should also be captured
        assertThat(result).containsEntry("content-type", "application/json")
        assertThat(result).containsEntry("cache-control", "no-cache")
    }

    @Test
    fun `M extract matching response headers W extractResponseHeaders()`() {
        // Given
        val headers = mapOf(
            "X-RateLimit-Remaining" to listOf("100"),
            "Cache-Control" to listOf("max-age=3600"),
            "Content-Type" to listOf("application/json")
        )

        // When
        val result = testedExtractor.extractResponseHeaders(headers)

        // Then
        assertThat(result).containsKey("x-ratelimit-remaining")
        assertThat(result["x-ratelimit-remaining"]).isEqualTo("100")
        // Should also have default headers
        assertThat(result).containsKey("cache-control")
        assertThat(result).containsKey("content-type")
    }

    @Test
    fun `M return only default headers W extractRequestHeaders() { no custom headers configured }`() {
        // Given
        val config = ResourceHeadersConfiguration.Builder().build()
        testedExtractor = ResourceHeadersExtractor(config, mockInternalLogger)
        val headers = mapOf(
            "X-Custom" to listOf("value"),
            "Content-Type" to listOf("application/json"),
            "Cache-Control" to listOf("no-cache")
        )

        // When
        val result = testedExtractor.extractRequestHeaders(headers)

        // Then
        // Only default headers should be captured, not X-Custom
        assertThat(result).doesNotContainKey("x-custom")
        assertThat(result).containsEntry("content-type", "application/json")
        assertThat(result).containsEntry("cache-control", "no-cache")
    }

    @Test
    fun `M return empty map W extractRequestHeaders() { no matching headers in input }`() {
        // Given
        // Headers that don't match any configured or default headers
        val headers = mapOf(
            "X-Unknown-Header" to listOf("value"),
            "X-Another-Unknown" to listOf("another-value")
        )

        // When
        val result = testedExtractor.extractRequestHeaders(headers)

        // Then
        // No headers match the configured or default request headers
        assertThat(result).isEmpty()
    }

    @Test
    fun `M handle case-insensitive matching W extractRequestHeaders()`() {
        // Given
        val headers = mapOf(
            "x-request-id" to listOf("lowercase"),
            "X-CORRELATION-ID" to listOf("uppercase")
        )

        // When
        val result = testedExtractor.extractRequestHeaders(headers)

        // Then
        assertThat(result).containsEntry("x-request-id", "lowercase")
        assertThat(result).containsEntry("x-correlation-id", "uppercase")
    }

    @Test
    fun `M join multiple values W extractRequestHeaders() { header has multiple values }`() {
        // Given
        val headers = mapOf(
            "X-Request-ID" to listOf("value1", "value2", "value3")
        )

        // When
        val result = testedExtractor.extractRequestHeaders(headers)

        // Then
        assertThat(result["x-request-id"]).isEqualTo("value1, value2, value3")
    }

    @Test
    fun `M skip headers that would exceed size limit W extractRequestHeaders()`() {
        // Given
        // Create headers that will exceed the 2KB limit
        val longValue = "x".repeat(1000)
        val config = ResourceHeadersConfiguration.Builder()
            .captureRequestHeaders(listOf("header1", "header2", "header3"))
            .build()
        testedExtractor = ResourceHeadersExtractor(config, mockInternalLogger)

        val headers = mapOf(
            "Header1" to listOf(longValue),
            "Header2" to listOf(longValue),
            "Header3" to listOf(longValue)
        )

        // When
        val result = testedExtractor.extractRequestHeaders(headers)

        // Then
        // Should have first two headers but not third (would exceed 2KB)
        val totalSize = result.entries.sumOf { it.key.length + 1 + it.value.length }
        assertThat(totalSize).isLessThanOrEqualTo(ResourceHeadersConfiguration.HEADER_SIZE_LIMIT_BYTES)
    }

    @Test
    fun `M skip forbidden headers W extractRequestHeaders() { forbidden header in input }`() {
        // Given
        // Even if authorization header is present in input, the extractor should filter it
        // Note: Builder also filters forbidden headers, but extractor double-checks
        val config = ResourceHeadersConfiguration.Builder()
            .captureRequestHeaders(
                listOf("x-request-id", "authorization")
            ) // "authorization" will be filtered by Builder
            .build()
        testedExtractor = ResourceHeadersExtractor(config, mockInternalLogger)

        val headers = mapOf(
            "X-Request-ID" to listOf("abc123"),
            "Authorization" to listOf("Bearer secret-token")
        )

        // When
        val result = testedExtractor.extractRequestHeaders(headers)

        // Then
        assertThat(result).containsKey("x-request-id")
        // Forbidden headers should never be captured, even if present in input
        assertThat(result).doesNotContainKey("authorization")
    }

    @Test
    fun `M skip empty header values W extractRequestHeaders()`() {
        // Given
        val headers = mapOf(
            "X-Request-ID" to listOf("abc123"),
            "X-Correlation-ID" to emptyList(),
            // Default headers with empty values should also be skipped
            "Content-Type" to emptyList(),
            "Cache-Control" to emptyList()
        )

        // When
        val result = testedExtractor.extractRequestHeaders(headers)

        // Then
        assertThat(result).containsKey("x-request-id")
        assertThat(result["x-request-id"]).isEqualTo("abc123")
        // Empty values should be skipped
        assertThat(result).doesNotContainKey("x-correlation-id")
        assertThat(result).doesNotContainKey("content-type")
        assertThat(result).doesNotContainKey("cache-control")
    }

    @Test
    fun `M respect header priority order W extractRequestHeaders() { size limit reached }`() {
        // Given
        // Create a scenario where we hit size limit - first headers should be preferred
        val headerNames = (1..20).map { "header$it" }
        val config = ResourceHeadersConfiguration.Builder()
            .captureRequestHeaders(headerNames)
            .build()
        testedExtractor = ResourceHeadersExtractor(config, mockInternalLogger)

        // Each header value is 100 bytes, so with ~20 headers we'll exceed 2KB
        val headers = headerNames.associateWith { listOf("x".repeat(100)) }

        // When
        val result = testedExtractor.extractRequestHeaders(headers)

        // Then
        // First headers should be present, later ones should be skipped
        assertThat(result).containsKey("header1")
        assertThat(result).containsKey("header2")
        // Verify we didn't exceed size limit
        val totalSize = result.entries.sumOf { it.key.length + 1 + it.value.length }
        assertThat(totalSize).isLessThanOrEqualTo(ResourceHeadersConfiguration.HEADER_SIZE_LIMIT_BYTES)
    }

    @Test
    fun `M extract default response headers W extractResponseHeaders()`() {
        // Given
        val headers = mapOf(
            "Cache-Control" to listOf("max-age=3600"),
            "ETag" to listOf("\"abc123\""),
            "Content-Type" to listOf("application/json"),
            "Content-Length" to listOf("1234"),
            "Server-Timing" to listOf("db;dur=53, app;dur=47.2")
        )

        // When
        val result = testedExtractor.extractResponseHeaders(headers)

        // Then
        assertThat(result).containsEntry("cache-control", "max-age=3600")
        assertThat(result).containsEntry("etag", "\"abc123\"")
        assertThat(result).containsEntry("content-type", "application/json")
        assertThat(result).containsEntry("content-length", "1234")
        assertThat(result).containsEntry("server-timing", "db;dur=53, app;dur=47.2")
    }

    @Test
    fun `M handle empty input map W extractRequestHeaders()`() {
        // Given
        val headers = emptyMap<String, List<String>>()

        // When
        val result = testedExtractor.extractRequestHeaders(headers)

        // Then
        assertThat(result).isEmpty()
    }

    @Test
    fun `M handle empty input map W extractResponseHeaders()`() {
        // Given
        val headers = emptyMap<String, List<String>>()

        // When
        val result = testedExtractor.extractResponseHeaders(headers)

        // Then
        assertThat(result).isEmpty()
    }
}
