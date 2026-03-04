/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.resource

import com.datadog.android.api.InternalLogger
import com.datadog.android.rum.internal.utils.truncateToUtf8ByteSize
import com.datadog.android.rum.resource.ResourceHeadersExtractor.Companion.DEFAULT_REQUEST_HEADERS
import com.datadog.android.rum.resource.ResourceHeadersExtractor.Companion.DEFAULT_RESPONSE_HEADERS
import com.datadog.android.rum.resource.ResourceHeadersExtractor.Companion.HEADER_SIZE_LIMIT_BYTES
import com.datadog.tools.unit.forge.anHttpHeaderMap
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import java.util.Locale

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
internal class ResourceHeadersExtractorTest {

    private lateinit var testedExtractor: ResourceHeadersExtractor

    @Mock
    private lateinit var mockInternalLogger: InternalLogger

    @BeforeEach
    fun `set up`() {
        testedExtractor = ResourceHeadersExtractor.Builder().build()
    }

    // region Extraction

    @Test
    fun `M extract matching request headers W extractRequestHeaders()`(forge: Forge) {
        // Given
        val expectedMap = DEFAULT_REQUEST_HEADERS
            .associateWith { forge.anAsciiString() }
        val headers = expectedMap.mapValues { listOf(it.value) }

        // When
        val result = testedExtractor.extractRequestHeaders(headers, mockInternalLogger)

        // Then
        assertThat(result).containsExactlyEntriesOf(expectedMap)
    }

    @Test
    fun `M extract matching response headers W extractResponseHeaders()`(forge: Forge) {
        // Given
        val expectedMap = DEFAULT_RESPONSE_HEADERS
            .associateWith { forge.anAsciiString() }
        val headers = expectedMap.mapValues { listOf(it.value) }

        // When
        val result = testedExtractor.extractResponseHeaders(headers, mockInternalLogger)

        // Then
        assertThat(result).containsExactlyEntriesOf(expectedMap)
    }

    @Test
    fun `M normalize header keys to lowercase W extractRequestHeaders()`(forge: Forge) {
        // Given
        val headerName = forge.anElementFrom(DEFAULT_REQUEST_HEADERS)
        val fakeValue = forge.anAsciiString()
        val headers = mapOf(headerName.uppercase(Locale.US) to listOf(fakeValue))

        // When
        val result = testedExtractor.extractRequestHeaders(headers, mockInternalLogger)

        // Then
        assertThat(result).containsExactlyEntriesOf(
            mapOf(headerName to fakeValue)
        )
    }

    @Test
    fun `M join multi-value headers W extractResponseHeaders()`(forge: Forge) {
        // Given
        val headerName = forge.anElementFrom(DEFAULT_RESPONSE_HEADERS)
        val fakeValues = forge.aList(forge.anInt(2, 5)) { anAsciiString() }
        val headers = mapOf(headerName to fakeValues)

        // When
        val result = testedExtractor.extractResponseHeaders(headers, mockInternalLogger)

        // Then
        assertThat(result).containsExactlyEntriesOf(
            mapOf(headerName to fakeValues.joinToString(", "))
        )
    }

    @Test
    fun `M skip non-configured headers W extractRequestHeaders()`(forge: Forge) {
        // Given
        val configuredHeader = forge.anElementFrom(DEFAULT_REQUEST_HEADERS)
        val fakeValue = forge.anAsciiString()
        val nonConfiguredHeaders = forge.anHttpHeaderMap().mapValues { listOf(it.value) }
        val headers = nonConfiguredHeaders + mapOf(configuredHeader to listOf(fakeValue))

        // When
        val result = testedExtractor.extractRequestHeaders(headers, mockInternalLogger)

        // Then
        assertThat(result).containsExactlyEntriesOf(
            mapOf(configuredHeader to fakeValue)
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
    fun `M return empty map W extractResponseHeaders() { no matching headers }`(forge: Forge) {
        // Given
        val headers = mapOf(
            "x-custom-${forge.anAlphabeticalString()}" to
                listOf(forge.anAsciiString())
        )

        // When
        val result = testedExtractor.extractResponseHeaders(headers, mockInternalLogger)

        // Then
        assertThat(result).isEmpty()
    }

    // endregion

    // region Truncation

    @Test
    fun `M truncate value exceeding 128 bytes W extractRequestHeaders()`(forge: Forge) {
        // Given
        val headerName = forge.anElementFrom(DEFAULT_REQUEST_HEADERS)
        val longValue = forge.aStringMatching("[A-Za-z0-9]{200,300}")
        val headers = mapOf(
            headerName to listOf(longValue)
        )

        // When
        val result = testedExtractor.extractRequestHeaders(headers, mockInternalLogger)

        // Then
        val value = checkNotNull(result[headerName])
        assertThat(value.toByteArray(Charsets.UTF_8).size).isLessThanOrEqualTo(128)
    }

    @Test
    fun `M return original string and byte size W truncateToUtf8ByteSize() { under limit }`(forge: Forge) {
        // Given
        val shortString = forge.aStringMatching("[A-Za-z0-9 /;=.-]{1,50}")

        // When
        val (result, byteSize) = shortString.truncateToUtf8ByteSize(128)

        // Then
        assertThat(result).isEqualTo(shortString)
        assertThat(byteSize).isEqualTo(shortString.toByteArray(Charsets.UTF_8).size)
    }

    @Test
    fun `M truncate to byte limit W truncateToUtf8ByteSize() { over limit }`(forge: Forge) {
        // Given
        val longString = forge.aStringMatching("[A-Za-z0-9 /;=.-]{200,500}")

        // When
        val (result, byteSize) = longString.truncateToUtf8ByteSize(128)

        // Then
        assertThat(result.toByteArray(Charsets.UTF_8).size).isLessThanOrEqualTo(128)
        assertThat(byteSize).isEqualTo(result.toByteArray(Charsets.UTF_8).size)
    }

    @Test
    fun `M handle multibyte chars safely W truncateToUtf8ByteSize()`() {
        // Given: string with multibyte UTF-8 characters (each emoji is 4 bytes)
        val emojiString = "\uD83D\uDE00".repeat(40) // 160 bytes
        assertThat(emojiString.toByteArray(Charsets.UTF_8).size).isGreaterThan(128)

        // When
        val (result, byteSize) = emojiString.truncateToUtf8ByteSize(128)

        // Then
        assertThat(byteSize).isLessThanOrEqualTo(128)
        assertThat(result.toByteArray(Charsets.UTF_8).size).isEqualTo(byteSize)
        // Should not contain replacement characters
        assertThat(result).doesNotContain("\uFFFD")
    }

    @Test
    fun `M enforce 2KB total limit W extractResponseHeaders()`() {
        // Given
        val headerNames = (1..50).map { "x-header-$it" }.toTypedArray()
        testedExtractor = ResourceHeadersExtractor.Builder(includeDefaults = false)
            .captureHeaders(*headerNames)
            .build()

        // Build headers where each has a ~100 byte value (enough to exceed 2KB with ~20 headers)
        val headers = (1..50).associate { "x-header-$it" to listOf("v".repeat(100)) }

        // When
        val result = testedExtractor.extractResponseHeaders(headers, mockInternalLogger)

        // Then
        val totalSize = result.entries.sumOf { it.key.length + 1 + it.value.toByteArray(Charsets.UTF_8).size }
        assertThat(totalSize).isLessThanOrEqualTo(HEADER_SIZE_LIMIT_BYTES)
    }

    @Test
    fun `M stop at 100 headers max W extractRequestHeaders()`() {
        // Given
        val headerNames = (1..150).map { "x-header-$it" }.toTypedArray()
        testedExtractor = ResourceHeadersExtractor.Builder(includeDefaults = false)
            .captureHeaders(*headerNames)
            .build()

        // Each header has a tiny value to avoid hitting 2KB limit first
        val headers = headerNames.associateWith { listOf("v") }

        // When
        val result = testedExtractor.extractRequestHeaders(headers, mockInternalLogger)

        // Then
        assertThat(result.size).isLessThanOrEqualTo(100)
    }

    // endregion
}
