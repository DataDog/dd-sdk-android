/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.resource

import com.datadog.android.api.InternalLogger
import com.datadog.android.rum.RumAttributes
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
    fun `M extract matching request headers W toResourceAttributes()`(forge: Forge) {
        // Given
        val expectedMap = DEFAULT_REQUEST_HEADERS
            .associateWith { forge.anAsciiString() }
        val rawRequestHeaders = expectedMap.mapValues { listOf(it.value) }

        // When
        val result = testedExtractor.toResourceAttributes(rawRequestHeaders, emptyMap(), mockInternalLogger)

        // Then
        assertThat(result.extractedRequestHeaders()).containsExactlyEntriesOf(expectedMap)
    }

    @Test
    fun `M extract matching response headers W toResourceAttributes()`(forge: Forge) {
        // Given
        val expectedMap = DEFAULT_RESPONSE_HEADERS
            .associateWith { forge.anAsciiString() }
        val rawResponseHeaders = expectedMap.mapValues { listOf(it.value) }

        // When
        val result = testedExtractor.toResourceAttributes(emptyMap(), rawResponseHeaders, mockInternalLogger)

        // Then
        assertThat(result.extractedResponseHeaders()).containsExactlyEntriesOf(expectedMap)
    }

    @Test
    fun `M normalize header keys to lowercase W toResourceAttributes()`(forge: Forge) {
        // Given
        val headerName = forge.anElementFrom(DEFAULT_REQUEST_HEADERS)
        val fakeValue = forge.anAsciiString()
        val rawRequestHeaders = mapOf(headerName.uppercase(Locale.US) to listOf(fakeValue))

        // When
        val result = testedExtractor.toResourceAttributes(rawRequestHeaders, emptyMap(), mockInternalLogger)

        // Then
        assertThat(result.extractedRequestHeaders()).containsExactlyEntriesOf(
            mapOf(headerName to fakeValue)
        )
    }

    @Test
    fun `M join multi-value headers W toResourceAttributes()`(forge: Forge) {
        // Given
        val headerName = forge.anElementFrom(DEFAULT_RESPONSE_HEADERS)
        val fakeValues = forge.aList(forge.anInt(2, 5)) { anAsciiString() }
        val rawResponseHeaders = mapOf(headerName to fakeValues)

        // When
        val result = testedExtractor.toResourceAttributes(emptyMap(), rawResponseHeaders, mockInternalLogger)

        // Then
        assertThat(result.extractedResponseHeaders()).containsExactlyEntriesOf(
            mapOf(headerName to fakeValues.joinToString(", "))
        )
    }

    @Test
    fun `M skip non-configured headers W toResourceAttributes()`(forge: Forge) {
        // Given
        val configuredHeader = forge.anElementFrom(DEFAULT_REQUEST_HEADERS)
        val fakeValue = forge.anAsciiString()
        val nonConfiguredHeaders = forge.anHttpHeaderMap().mapValues { listOf(it.value) }
        val rawRequestHeaders = nonConfiguredHeaders + mapOf(configuredHeader to listOf(fakeValue))

        // When
        val result = testedExtractor.toResourceAttributes(rawRequestHeaders, emptyMap(), mockInternalLogger)

        // Then
        assertThat(result.extractedRequestHeaders()).containsExactlyEntriesOf(
            mapOf(configuredHeader to fakeValue)
        )
    }

    @Test
    fun `M return empty map W toResourceAttributes() { empty input }`() {
        // When
        val result = testedExtractor.toResourceAttributes(emptyMap(), emptyMap(), mockInternalLogger)

        // Then
        assertThat(result).isEmpty()
    }

    @Test
    fun `M return empty map W toResourceAttributes() { no matching headers }`(forge: Forge) {
        // Given
        val rawRequestHeaders = mapOf(
            "x-custom-${forge.anAlphabeticalString()}" to listOf(forge.anAsciiString())
        )
        val rawResponseHeaders = mapOf(
            "x-custom-${forge.anAlphabeticalString()}" to listOf(forge.anAsciiString())
        )

        // When
        val result = testedExtractor.toResourceAttributes(rawRequestHeaders, rawResponseHeaders, mockInternalLogger)

        // Then
        assertThat(result).isEmpty()
    }

    // endregion

    // region Truncation

    @Test
    fun `M truncate value exceeding 128 bytes W toResourceAttributes()`(forge: Forge) {
        // Given
        val headerName = forge.anElementFrom(DEFAULT_REQUEST_HEADERS)
        val longValue = forge.aStringMatching("[A-Za-z0-9]{200,300}")
        val rawRequestHeaders = mapOf(headerName to listOf(longValue))

        // When
        val result = testedExtractor.toResourceAttributes(rawRequestHeaders, emptyMap(), mockInternalLogger)

        // Then
        val value = checkNotNull(result.extractedRequestHeaders()[headerName])
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
    fun `M enforce 2KB total limit W toResourceAttributes()`() {
        // Given
        val headerNames = (1..50).map { "x-header-$it" }.toTypedArray()
        testedExtractor = ResourceHeadersExtractor.Builder(includeDefaults = false)
            .captureHeaders(*headerNames)
            .build()

        // Build headers where each has a ~100 byte value (enough to exceed 2KB with ~20 headers)
        val rawResponseHeaders = (1..50).associate { "x-header-$it" to listOf("v".repeat(100)) }

        // When
        val result = testedExtractor.toResourceAttributes(emptyMap(), rawResponseHeaders, mockInternalLogger)

        // Then
        val totalSize = result.extractedResponseHeaders().entries.sumOf {
            it.key.length + 1 + it.value.toByteArray(Charsets.UTF_8).size
        }
        assertThat(totalSize).isLessThanOrEqualTo(HEADER_SIZE_LIMIT_BYTES)
    }

    @Test
    fun `M stop at 100 headers max W toResourceAttributes()`() {
        // Given
        val headerNames = (1..150).map { "x-header-$it" }.toTypedArray()
        testedExtractor = ResourceHeadersExtractor.Builder(includeDefaults = false)
            .captureHeaders(*headerNames)
            .build()

        // Each header has a tiny value to avoid hitting 2KB limit first
        val rawRequestHeaders = headerNames.associateWith { listOf("v") }

        // When
        val result = testedExtractor.toResourceAttributes(rawRequestHeaders, emptyMap(), mockInternalLogger)

        // Then
        assertThat(result.extractedRequestHeaders().size).isLessThanOrEqualTo(100)
    }

    // endregion

    // region toResourceAttributes - attribute keys

    @Test
    fun `M return both attribute keys W toResourceAttributes() { matching request and response }`(forge: Forge) {
        // Given
        val fakeReqValue = forge.anAsciiString()
        val fakeResValue = forge.anAsciiString()
        val rawRequestHeaders = mapOf("content-type" to listOf(fakeReqValue))
        val rawResponseHeaders = mapOf("content-type" to listOf(fakeResValue))

        // When
        val result = testedExtractor.toResourceAttributes(
            rawRequestHeaders,
            rawResponseHeaders,
            mockInternalLogger
        )

        // Then
        assertThat(result.extractedRequestHeaders()).containsEntry("content-type", fakeReqValue)
        assertThat(result.extractedResponseHeaders()).containsEntry("content-type", fakeResValue)
    }

    @Test
    fun `M omit request headers key W toResourceAttributes() { no matching request headers }`(forge: Forge) {
        // Given
        val rawRequestHeaders = mapOf("x-unknown-header" to listOf(forge.anAsciiString()))
        val rawResponseHeaders = mapOf("content-type" to listOf(forge.anAsciiString()))

        // When
        val result = testedExtractor.toResourceAttributes(
            rawRequestHeaders,
            rawResponseHeaders,
            mockInternalLogger
        )

        // Then
        assertThat(result).doesNotContainKey(RumAttributes.REQUEST_HEADERS)
        assertThat(result).containsKey(RumAttributes.RESPONSE_HEADERS)
    }

    @Test
    fun `M omit response headers key W toResourceAttributes() { no matching response headers }`(forge: Forge) {
        // Given
        val rawRequestHeaders = mapOf("content-type" to listOf(forge.anAsciiString()))
        val rawResponseHeaders = mapOf("x-unknown-header" to listOf(forge.anAsciiString()))

        // When
        val result = testedExtractor.toResourceAttributes(
            rawRequestHeaders,
            rawResponseHeaders,
            mockInternalLogger
        )

        // Then
        assertThat(result).containsKey(RumAttributes.REQUEST_HEADERS)
        assertThat(result).doesNotContainKey(RumAttributes.RESPONSE_HEADERS)
    }

    // endregion

    // region Helpers

    private fun Map<String, Any?>.extractedRequestHeaders(): Map<String, String> {
        @Suppress("UNCHECKED_CAST")
        return checkNotNull(this[RumAttributes.REQUEST_HEADERS] as? Map<String, String>)
    }

    private fun Map<String, Any?>.extractedResponseHeaders(): Map<String, String> {
        @Suppress("UNCHECKED_CAST")
        return checkNotNull(this[RumAttributes.RESPONSE_HEADERS] as? Map<String, String>)
    }

    // endregion
}
