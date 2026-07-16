/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.internal.utils

import com.datadog.android.internal.forge.Configurator
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.junit.jupiter.MockitoExtension

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@ForgeConfiguration(Configurator::class)
internal class UrlSanitizerTest {

    @Test
    fun `M return url unchanged W redactSensitiveQueryParams() {no query string}`(
        @StringForgery fakePath: String
    ) {
        val url = "https://example.com/$fakePath"

        assertThat(url.redactSensitiveQueryParams()).isEqualTo(url)
    }

    @Test
    fun `M return url unchanged W redactSensitiveQueryParams() {empty query string}`() {
        val url = "https://example.com/path?"

        assertThat(url.redactSensitiveQueryParams()).isEqualTo(url)
    }

    @Test
    fun `M redact value W redactSensitiveQueryParams() {sensitive param}`() {
        val url = "https://bucket.s3.amazonaws.com/key?X-Amz-Security-Token=verysecret"

        assertThat(url.redactSensitiveQueryParams())
            .isEqualTo("https://bucket.s3.amazonaws.com/key?X-Amz-Security-Token=<redacted>")
    }

    @ParameterizedTest
    @ValueSource(strings = ["X-Amz-Security-Token", "x-amz-security-token", "X-AMZ-SECURITY-TOKEN"])
    fun `M redact value case-insensitively W redactSensitiveQueryParams()`(paramName: String) {
        val url = "https://example.com/path?$paramName=verysecret"

        assertThat(url.redactSensitiveQueryParams())
            .isEqualTo("https://example.com/path?$paramName=<redacted>")
    }

    @Test
    fun `M redact only sensitive params W redactSensitiveQueryParams() {mixed params}`() {
        val url = "https://example.com/path?foo=bar&X-Amz-Signature=abc123&baz=qux"

        assertThat(url.redactSensitiveQueryParams())
            .isEqualTo("https://example.com/path?foo=bar&X-Amz-Signature=<redacted>&baz=qux")
    }

    @Test
    fun `M not redact param whose name only contains a sensitive substring W redactSensitiveQueryParams()`() {
        val url = "https://example.com/path?nottoken=abc123"

        assertThat(url.redactSensitiveQueryParams()).isEqualTo(url)
    }

    @Test
    fun `M redact empty value W redactSensitiveQueryParams() {empty sensitive value}`() {
        val url = "https://example.com/path?token="

        assertThat(url.redactSensitiveQueryParams())
            .isEqualTo("https://example.com/path?token=<redacted>")
    }

    @Test
    fun `M keep fragment untouched W redactSensitiveQueryParams() {url with fragment}`() {
        val url = "https://example.com/path?token=abc123#section"

        assertThat(url.redactSensitiveQueryParams())
            .isEqualTo("https://example.com/path?token=<redacted>#section")
    }

    @Test
    fun `M leave value-less param untouched W redactSensitiveQueryParams() {flag param alongside sensitive one}`() {
        val url = "https://example.com/path?flag&token=abc123"

        assertThat(url.redactSensitiveQueryParams())
            .isEqualTo("https://example.com/path?flag&token=<redacted>")
    }
}
