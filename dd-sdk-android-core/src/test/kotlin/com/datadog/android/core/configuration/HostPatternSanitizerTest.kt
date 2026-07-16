/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.configuration

import com.datadog.android.api.InternalLogger
import com.datadog.android.utils.verifyLog
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.annotation.StringForgeryType
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.times
import java.util.Locale

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
internal class HostPatternSanitizerTest {

    lateinit var testedValidator: HostPatternSanitizer

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @StringForgery(type = StringForgeryType.ALPHABETICAL)
    lateinit var fakeFeature: String

    @BeforeEach
    fun `set up`() {
        testedValidator = HostPatternSanitizer(mockInternalLogger)
    }

    @Test
    fun `M keep valid wildcard patterns W validate()`() {
        // Given
        val patterns = listOf("*.example.com", "*.example.co.uk", "preview-*.shopist.io", "example.net")

        // When
        val result = testedValidator.validate(patterns, fakeFeature)

        // Then
        assertThat(result).isEqualTo(patterns)
    }

    @Test
    fun `M keep punycode IDN wildcard W validate { punycode domain }`() {
        // xn--l1a4a.xn--p1ai is the Punycode form of a Russian .рф registrable domain.
        val pattern = "*.xn--l1a4a.xn--p1ai"

        // When
        val result = testedValidator.validate(listOf(pattern), fakeFeature)

        // Then
        assertThat(result).containsExactly(pattern)
    }

    @Test
    fun `M drop punycode TLD wildcard W validate { punycode TLD wildcard }`() {
        // xn--p1ai is the Punycode TLD for .рф — too broad, same as *.com.
        val pattern = "*.xn--p1ai"

        // When
        val result = testedValidator.validate(listOf(pattern), fakeFeature)

        // Then
        assertThat(result).isEmpty()
        mockInternalLogger.verifyLog(
            InternalLogger.Level.ERROR,
            InternalLogger.Target.USER,
            HostPatternSanitizer.ERROR_WILDCARD_NOT_SUBDOMAIN.format(Locale.US, pattern, fakeFeature)
        )
    }

    @Test
    fun `M keep deep subdomain wildcard patterns W validate { multi-level subdomain }`() {
        // Given
        // A wildcard bounded to a registrable domain stays valid at any subdomain depth: these are
        // strictly narrower than "*.example.com", which is already accepted.
        val patterns = listOf("*.foo.example.com", "*.api.staging.example.co.uk")

        // When
        val result = testedValidator.validate(patterns, fakeFeature)

        // Then
        assertThat(result).isEqualTo(patterns)
    }

    @Test
    fun `M keep plain hosts W validate { no wildcard }`(
        @StringForgery(
            regex = "(([a-z0-9]|[a-z0-9][a-z0-9-]*[a-z0-9])\\.)+([a-z]|[a-z][a-z0-9-]*[a-z0-9])"
        ) hosts: List<String>
    ) {
        // When
        val result = testedValidator.validate(hosts, fakeFeature)

        // Then
        assertThat(result).isEqualTo(hosts)
    }

    @Test
    fun `M drop and warn W validate { bare star }`() {
        // Given
        val pattern = "*"

        // When
        val result = testedValidator.validate(listOf(pattern), fakeFeature)

        // Then
        assertThat(result).isEmpty()
        mockInternalLogger.verifyLog(
            InternalLogger.Level.ERROR,
            InternalLogger.Target.USER,
            HostPatternSanitizer.ERROR_WILDCARD_NOT_SUBDOMAIN.format(Locale.US, pattern, fakeFeature)
        )
    }

    @Test
    fun `M drop and warn W validate { TLD wildcard }`() {
        // Given
        val pattern = "*.com"

        // When
        val result = testedValidator.validate(listOf(pattern), fakeFeature)

        // Then
        assertThat(result).isEmpty()
        mockInternalLogger.verifyLog(
            InternalLogger.Level.ERROR,
            InternalLogger.Target.USER,
            HostPatternSanitizer.ERROR_WILDCARD_NOT_SUBDOMAIN.format(Locale.US, pattern, fakeFeature)
        )
    }

    @Test
    fun `M drop and warn W validate { public suffix wildcard }`() {
        // Given
        val patterns = listOf("*.co.uk", "*.github.io")

        // When
        val result = testedValidator.validate(patterns, fakeFeature)

        // Then
        assertThat(result).isEmpty()
        patterns.forEach { pattern ->
            mockInternalLogger.verifyLog(
                InternalLogger.Level.ERROR,
                InternalLogger.Target.USER,
                HostPatternSanitizer.ERROR_WILDCARD_NOT_SUBDOMAIN.format(Locale.US, pattern, fakeFeature),
                mode = times(patterns.size)
            )
        }
    }

    @Test
    fun `M drop and warn W validate { wildcard not at label boundary }`() {
        // Given
        // "*example.com" would also match sibling registrable domains such as "evilexample.com",
        // so the wildcard must sit in a subdomain label (immediately followed by a '.').
        val pattern = "*example.com"

        // When
        val result = testedValidator.validate(listOf(pattern), fakeFeature)

        // Then
        assertThat(result).isEmpty()
        mockInternalLogger.verifyLog(
            InternalLogger.Level.ERROR,
            InternalLogger.Target.USER,
            HostPatternSanitizer.ERROR_WILDCARD_NOT_SUBDOMAIN.format(Locale.US, pattern, fakeFeature)
        )
    }

    @Test
    fun `M keep empty string W validate { blank }`() {
        // When
        val result = testedValidator.validate(listOf(""), fakeFeature)

        // Then
        assertThat(result).containsExactly("")
    }

    @Test
    fun `M keep valid uppercase patterns in original case W validate { uppercase }`() {
        // When
        // matching is case-insensitive, so uppercase patterns are valid and returned unchanged
        val result = testedValidator.validate(
            listOf("*.SHOPIST.IO", "Preview-*.Example.COM"),
            fakeFeature
        )

        // Then
        assertThat(result).containsExactly("*.SHOPIST.IO", "Preview-*.Example.COM")
    }

    @Test
    fun `M drop and warn W validate { more than one wildcard }`() {
        // Given
        val pattern = "*.foo.*.bar"

        // When
        val result = testedValidator.validate(listOf(pattern), fakeFeature)

        // Then
        assertThat(result).isEmpty()
        mockInternalLogger.verifyLog(
            InternalLogger.Level.ERROR,
            InternalLogger.Target.USER,
            HostPatternSanitizer.ERROR_MULTIPLE_WILDCARDS.format(Locale.US, pattern, fakeFeature)
        )
    }

    @Test
    fun `M drop and warn W validate { invalid characters }`() {
        // Given
        val patterns = listOf("foo'bar.com", "back\\slash.com", "https://foo.com")

        // When
        val result = testedValidator.validate(patterns, fakeFeature)

        // Then
        assertThat(result).isEmpty()
        patterns.forEach { pattern ->
            mockInternalLogger.verifyLog(
                InternalLogger.Level.ERROR,
                InternalLogger.Target.USER,
                HostPatternSanitizer.ERROR_INVALID_CHARACTERS.format(Locale.US, pattern, fakeFeature),
                mode = times(patterns.size)
            )
        }
    }

    @Test
    fun `M warn with original pattern W validate { uppercase invalid characters }`() {
        // Given
        val pattern = "FOO'BAR.com"

        // When
        testedValidator.validate(listOf(pattern), fakeFeature)

        // Then
        mockInternalLogger.verifyLog(
            InternalLogger.Level.ERROR,
            InternalLogger.Target.USER,
            HostPatternSanitizer.ERROR_INVALID_CHARACTERS.format(Locale.US, pattern, fakeFeature)
        )
    }

    @Test
    fun `M keep valid and drop invalid W validate { mixed }`() {
        // Given
        val validWildcard = "*.shopist.io"
        val plainHost = "example.com"
        val multiWildcard = "*.foo.*.bar"
        val invalidChars = "foo'bar.com"
        val patterns = listOf(validWildcard, multiWildcard, plainHost, invalidChars)

        // When
        val result = testedValidator.validate(patterns, fakeFeature)

        // Then
        assertThat(result).containsExactly(validWildcard, plainHost)
    }
}
