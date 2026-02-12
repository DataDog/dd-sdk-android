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

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@ForgeConfiguration(Configurator::class)
internal class ResourceHeadersExtractorTest {

    private lateinit var testedExtractor: ResourceHeadersExtractor

    @Mock
    private lateinit var mockInternalLogger: InternalLogger

    @BeforeEach
    fun `set up`() {
        val config = ResourceHeadersConfiguration.Builder().build()
        testedExtractor = ResourceHeadersExtractor(config)
    }

    // region Extraction

    @Test
    fun `M extract matching request headers W extractRequestHeaders()`() {
        // Given
        val headers = mapOf(
            "Content-Type" to listOf("application/json"),
            "Cache-Control" to listOf("no-cache")
        )

        // When
        val result = testedExtractor.extractRequestHeaders(headers, mockInternalLogger)

        // Then
        assertThat(result).containsExactlyInAnyOrderEntriesOf(
            mapOf(
                "content-type" to "application/json",
                "cache-control" to "no-cache"
            )
        )
    }

    @Test
    fun `M extract matching response headers W extractResponseHeaders()`() {
        // Given
        val headers = mapOf(
            "Content-Type" to listOf("text/html"),
            "ETag" to listOf("\"abc123\""),
            "Content-Length" to listOf("1024")
        )

        // When
        val result = testedExtractor.extractResponseHeaders(headers, mockInternalLogger)

        // Then
        assertThat(result).containsExactlyInAnyOrderEntriesOf(
            mapOf(
                "content-type" to "text/html",
                "etag" to "\"abc123\"",
                "content-length" to "1024"
            )
        )
    }

    @Test
    fun `M join multi-value headers W extractResponseHeaders()`() {
        // Given
        val headers = mapOf(
            "Vary" to listOf("Accept", "Accept-Encoding", "Origin")
        )

        // When
        val result = testedExtractor.extractResponseHeaders(headers, mockInternalLogger)

        // Then
        assertThat(result).containsExactlyInAnyOrderEntriesOf(
            mapOf("vary" to "Accept, Accept-Encoding, Origin")
        )
    }

    @Test
    fun `M skip non-configured headers W extractRequestHeaders()`() {
        // Given: headers not in the configured capture list are ignored
        val headers = mapOf(
            "Authorization" to listOf("Bearer token"),
            "Content-Type" to listOf("application/json"),
            "Cookie" to listOf("session=abc")
        )

        // When
        val result = testedExtractor.extractRequestHeaders(headers, mockInternalLogger)

        // Then
        assertThat(result).containsExactlyInAnyOrderEntriesOf(
            mapOf("content-type" to "application/json")
        )
    }

    @Test
    fun `M return empty map W extractRequestHeaders() { empty input }`() {
        // When
        val result = testedExtractor.extractRequestHeaders(emptyMap(), mockInternalLogger)

        // Then
        assertThat(result).isEmpty()
    }

    @Test
    fun `M return empty map W extractResponseHeaders() { no matching headers }`() {
        // Given
        val headers = mapOf(
            "x-custom-untracked" to listOf("value")
        )

        // When
        val result = testedExtractor.extractResponseHeaders(headers, mockInternalLogger)

        // Then
        assertThat(result).isEmpty()
    }

    // endregion

    // region Truncation

    @Test
    fun `M truncate value exceeding 128 bytes W extractRequestHeaders()`() {
        // Given
        val longValue = "a".repeat(200)
        val headers = mapOf(
            "Content-Type" to listOf(longValue)
        )

        // When
        val result = testedExtractor.extractRequestHeaders(headers, mockInternalLogger)

        // Then
        val value = checkNotNull(result["content-type"])
        assertThat(value.toByteArray(Charsets.UTF_8).size).isLessThanOrEqualTo(128)
    }

    @Test
    fun `M return original string W truncateToByteLimit() { under limit }`() {
        // When
        val result = ResourceHeadersExtractor.truncateToByteLimit("hello", 128)

        // Then
        assertThat(result).isEqualTo("hello")
    }

    @Test
    fun `M truncate to byte limit W truncateToByteLimit() { over limit }`() {
        // Given
        val longString = "a".repeat(200)

        // When
        val result = ResourceHeadersExtractor.truncateToByteLimit(longString, 128)

        // Then
        assertThat(result.toByteArray(Charsets.UTF_8).size).isLessThanOrEqualTo(128)
    }

    @Test
    fun `M handle multibyte chars safely W truncateToByteLimit()`() {
        // Given: string with multibyte UTF-8 characters (each emoji is 4 bytes)
        val emojiString = "\uD83D\uDE00".repeat(40) // 160 bytes
        assertThat(emojiString.toByteArray(Charsets.UTF_8).size).isGreaterThan(128)

        // When
        val result = ResourceHeadersExtractor.truncateToByteLimit(emojiString, 128)

        // Then
        assertThat(result.toByteArray(Charsets.UTF_8).size).isLessThanOrEqualTo(128)
        // Should not contain replacement characters
        assertThat(result).doesNotContain("\uFFFD")
    }

    @Test
    fun `M enforce 2KB total limit W extractResponseHeaders()`() {
        // Given
        val config = ResourceHeadersConfiguration.Builder(includeDefaults = false)
            .captureHeaders((1..50).map { "x-header-$it" })
            .build()
        testedExtractor = ResourceHeadersExtractor(config)

        // Build headers where each has a ~100 byte value (enough to exceed 2KB with ~20 headers)
        val headers = (1..50).associate { "x-header-$it" to listOf("v".repeat(100)) }

        // When
        val result = testedExtractor.extractResponseHeaders(headers, mockInternalLogger)

        // Then
        val totalSize = result.entries.sumOf { it.key.length + 1 + it.value.toByteArray(Charsets.UTF_8).size }
        assertThat(totalSize).isLessThanOrEqualTo(ResourceHeadersConfiguration.HEADER_SIZE_LIMIT_BYTES)
    }

    @Test
    fun `M stop at 100 headers max W extractRequestHeaders()`() {
        // Given
        val headerNames = (1..150).map { "x-header-$it" }
        val config = ResourceHeadersConfiguration.Builder(includeDefaults = false)
            .captureHeaders(headerNames)
            .build()
        testedExtractor = ResourceHeadersExtractor(config)

        // Each header has a tiny value to avoid hitting 2KB limit first
        val headers = headerNames.associateWith { listOf("v") }

        // When
        val result = testedExtractor.extractRequestHeaders(headers, mockInternalLogger)

        // Then
        assertThat(result.size).isLessThanOrEqualTo(100)
    }

    // endregion
}
