/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.configuration

import com.datadog.android.rum.utils.forge.Configurator
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.junit.jupiter.MockitoExtension

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@ForgeConfiguration(Configurator::class)
internal class ResourceHeadersConfigurationTest {

    // region Defaults

    @Test
    fun `M include defaults by default W build()`() {
        // When
        val config = ResourceHeadersConfiguration.Builder().build()

        // Then
        assertThat(config.requestHeaders).containsExactlyElementsOf(
            ResourceHeadersConfiguration.DEFAULT_REQUEST_HEADERS
        )
        assertThat(config.responseHeaders).containsExactlyElementsOf(
            ResourceHeadersConfiguration.DEFAULT_RESPONSE_HEADERS
        )
    }

    @Test
    fun `M produce empty lists W build() { includeDefaults = false, no custom headers }`() {
        // When
        val config = ResourceHeadersConfiguration.Builder(includeDefaults = false).build()

        // Then
        assertThat(config.requestHeaders).isEmpty()
        assertThat(config.responseHeaders).isEmpty()
    }

    @Test
    fun `M include only custom headers W build() { includeDefaults = false }`() {
        // Given
        val customHeaders = listOf("x-request-id", "x-ratelimit-remaining")

        // When
        val config = ResourceHeadersConfiguration.Builder(includeDefaults = false)
            .captureHeaders(customHeaders)
            .build()

        // Then
        assertThat(config.requestHeaders).containsExactlyElementsOf(customHeaders)
        assertThat(config.responseHeaders).containsExactlyElementsOf(customHeaders)
    }

    // endregion

    // region Security Pattern Filtering

    @Test
    fun `M filter all well-known sensitive headers W build()`() {
        // Given - well-known sensitive headers that all match the security regex
        val sensitiveHeaders = listOf(
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
        val config = ResourceHeadersConfiguration.Builder(includeDefaults = false)
            .captureHeaders(sensitiveHeaders)
            .build()

        // Then
        assertThat(config.requestHeaders).isEmpty()
        assertThat(config.responseHeaders).isEmpty()
    }

    // endregion

    // region Normalization

    @Test
    fun `M normalize to lowercase W build() { mixed case custom headers }`() {
        // Given
        val customHeaders = listOf("X-Request-ID", "X-Correlation-Id")

        // When
        val config = ResourceHeadersConfiguration.Builder(includeDefaults = false)
            .captureHeaders(customHeaders)
            .build()

        // Then
        assertThat(config.requestHeaders).containsExactly("x-request-id", "x-correlation-id")
        assertThat(config.responseHeaders).containsExactly("x-request-id", "x-correlation-id")
    }

    @Test
    fun `M remove duplicates W build() { duplicate custom headers }`() {
        // Given
        val customHeaders = listOf("x-request-id", "X-REQUEST-ID", "x-request-id")

        // When
        val config = ResourceHeadersConfiguration.Builder(includeDefaults = false)
            .captureHeaders(customHeaders)
            .build()

        // Then
        assertThat(config.requestHeaders).containsExactly("x-request-id")
        assertThat(config.responseHeaders).containsExactly("x-request-id")
    }

    @Test
    fun `M deduplicate with defaults W build() { custom header overlaps default }`() {
        // Given
        val customHeaders = listOf("content-type", "x-request-id")

        // When
        val config = ResourceHeadersConfiguration.Builder(includeDefaults = true)
            .captureHeaders(customHeaders)
            .build()

        // Then
        // content-type is a default, so it shouldn't appear twice
        val contentTypeCount = config.requestHeaders.count { it == "content-type" }
        assertThat(contentTypeCount).isEqualTo(1)
        assertThat(config.requestHeaders).contains("x-request-id")
    }

    // endregion
}
