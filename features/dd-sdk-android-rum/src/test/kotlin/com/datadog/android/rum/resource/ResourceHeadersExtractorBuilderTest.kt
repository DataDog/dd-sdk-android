/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.resource

import com.datadog.android.rum.resource.ResourceHeadersExtractor.Companion.DEFAULT_REQUEST_HEADERS
import com.datadog.android.rum.resource.ResourceHeadersExtractor.Companion.DEFAULT_RESPONSE_HEADERS
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class ResourceHeadersExtractorBuilderTest {

    // region Defaults

    @Test
    fun `M include defaults by default W build()`() {
        // When
        val extractor = ResourceHeadersExtractor.Builder().build()

        // Then
        assertThat(extractor.allowedRequestHeaders).containsExactlyElementsOf(
            DEFAULT_REQUEST_HEADERS
        )
        assertThat(extractor.allowedResponseHeaders).containsExactlyElementsOf(
            DEFAULT_RESPONSE_HEADERS
        )
    }

    @Test
    fun `M produce empty lists W build() { includeDefaults = false, no custom headers }`() {
        // When
        val extractor = ResourceHeadersExtractor.Builder(includeDefaults = false).build()

        // Then
        assertThat(extractor.allowedRequestHeaders).isEmpty()
        assertThat(extractor.allowedResponseHeaders).isEmpty()
    }

    @Test
    fun `M include only custom headers W build() { includeDefaults = false }`() {
        // Given
        val customHeaders = arrayOf("x-request-id", "x-ratelimit-remaining")

        // When
        val extractor = ResourceHeadersExtractor.Builder(includeDefaults = false)
            .captureHeaders(*customHeaders)
            .build()

        // Then
        assertThat(extractor.allowedRequestHeaders).containsExactlyElementsOf(customHeaders.toList())
        assertThat(extractor.allowedResponseHeaders).containsExactlyElementsOf(customHeaders.toList())
    }

    // endregion

    // region Security Pattern Filtering

    @Test
    fun `M filter all well-known sensitive headers W build()`() {
        // Given - well-known sensitive headers that all match the security regex
        val sensitiveHeaders = arrayOf(
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

        // When
        val extractor = ResourceHeadersExtractor.Builder(includeDefaults = false)
            .captureHeaders(*sensitiveHeaders)
            .build()

        // Then
        assertThat(extractor.allowedRequestHeaders).isEmpty()
        assertThat(extractor.allowedResponseHeaders).isEmpty()
    }

    // endregion

    // region Normalization

    @Test
    fun `M normalize to lowercase W build() { mixed case custom headers }`() {
        // Given
        val customHeaders = arrayOf("X-Request-ID", "X-Correlation-Id")

        // When
        val extractor = ResourceHeadersExtractor.Builder(includeDefaults = false)
            .captureHeaders(*customHeaders)
            .build()

        // Then
        assertThat(extractor.allowedRequestHeaders).containsExactly("x-request-id", "x-correlation-id")
        assertThat(extractor.allowedResponseHeaders).containsExactly("x-request-id", "x-correlation-id")
    }

    @Test
    fun `M remove duplicates W build() { duplicate custom headers }`() {
        // Given
        val customHeaders = arrayOf("x-request-id", "X-REQUEST-ID", "x-request-id")

        // When
        val extractor = ResourceHeadersExtractor.Builder(includeDefaults = false)
            .captureHeaders(*customHeaders)
            .build()

        // Then
        assertThat(extractor.allowedRequestHeaders).containsExactly("x-request-id")
        assertThat(extractor.allowedResponseHeaders).containsExactly("x-request-id")
    }

    @Test
    fun `M deduplicate with defaults W build() { custom header overlaps default }`() {
        // Given
        val customHeaders = arrayOf("content-type", "x-request-id")

        // When
        val extractor = ResourceHeadersExtractor.Builder(includeDefaults = true)
            .captureHeaders(*customHeaders)
            .build()

        // Then
        // content-type is a default, so it shouldn't appear twice
        val contentTypeCount = extractor.allowedRequestHeaders.count { it == "content-type" }
        assertThat(contentTypeCount).isEqualTo(1)
        assertThat(extractor.allowedRequestHeaders).contains("x-request-id")
    }

    // endregion
}
