/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal.protection

import com.datadog.android.flags.ResponseProtection
import com.datadog.android.flags.ResponseProtectionMode
import com.datadog.android.flags.TlsPinTarget
import com.datadog.android.flags.TlsPinningProfile
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

internal class ResponseProtectionResolverTest {

    @Test
    fun `M resolve to None W resolve() { no protection }`() {
        // When
        val result = ResponseProtectionResolver.resolve(null, null)

        // Then
        assertThat(result).isSameAs(ResolvedResponseProtection.None)
        assertThat(result.persistenceScope).isNull()
        assertThat(result.assignmentEndpoint).isNull()
    }

    @Test
    fun `M resolve the profile endpoint W resolve() { valid custom profile, no custom endpoint }`() {
        // When
        val result = ResponseProtectionResolver.resolve(protection(ORIGIN, PIN_A, PIN_B), null)

        // Then
        assertThat(result).isInstanceOf(ResolvedResponseProtection.TlsPinning::class.java)
        result as ResolvedResponseProtection.TlsPinning
        assertThat(result.assignmentEndpoint).isEqualTo("$ORIGIN/precompute-assignments")
        assertThat(result.pins).containsExactlyInAnyOrder(PIN_A, PIN_B)
        assertThat(result.origin.host).isEqualTo("flags.example.com")
        assertThat(result.isPlaceholder).isFalse()
        assertThat(result.persistenceScope).matches("tls-[0-9a-f]{16}")
    }

    @Test
    fun `M keep the custom endpoint W resolve() { custom endpoint with the profile origin }`() {
        // Given
        val customEndpoint = "$ORIGIN/custom/path"

        // When
        val result = ResponseProtectionResolver.resolve(protection("$ORIGIN/", PIN_A, PIN_B), customEndpoint)

        // Then
        assertThat(result.assignmentEndpoint).isEqualTo(customEndpoint)
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "https://other.example.com/precompute-assignments",
            "http://flags.example.com/precompute-assignments",
            "https://flags.example.com:8443/precompute-assignments",
            "not a url"
        ]
    )
    fun `M resolve to Invalid W resolve() { custom endpoint outside the profile origin }`(endpoint: String) {
        // When
        val result = ResponseProtectionResolver.resolve(protection(ORIGIN, PIN_A, PIN_B), endpoint)

        // Then
        assertThat(result).isInstanceOf(ResolvedResponseProtection.Invalid::class.java)
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "http://flags.example.com",
            "https://*.example.com",
            "https://user:secret@flags.example.com",
            "https://flags.example.com/path",
            "https://flags.example.com/?query=1",
            "https://flags.example.com/#fragment",
            "flags.example.com",
            ""
        ]
    )
    fun `M resolve to Invalid W resolve() { origin is not an exact HTTPS origin }`(origin: String) {
        // When
        val result = ResponseProtectionResolver.resolve(protection(origin, PIN_A, PIN_B), null)

        // Then
        assertThat(result).isInstanceOf(ResolvedResponseProtection.Invalid::class.java)
        assertThat((result as ResolvedResponseProtection.Invalid).reason).contains("origin")
        assertThat(result.persistenceScope).isEqualTo(ResolvedResponseProtection.INVALID_PERSISTENCE_SCOPE)
    }

    @Test
    fun `M resolve to Invalid W resolve() { single pin }`() {
        // When
        val result = ResponseProtectionResolver.resolve(protection(ORIGIN, PIN_A), null)

        // Then
        assertThat(result).isInstanceOf(ResolvedResponseProtection.Invalid::class.java)
    }

    @Test
    fun `M resolve to Invalid W resolve() { duplicated pin }`() {
        // When
        val result = ResponseProtectionResolver.resolve(protection(ORIGIN, PIN_A, PIN_A), null)

        // Then
        assertThat(result).isInstanceOf(ResolvedResponseProtection.Invalid::class.java)
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
            "sha1/AAAAAAAAAAAAAAAAAAAAAAAAAAA=",
            "sha256/AAAA",
            "sha256/not-base64!",
            "sha256/"
        ]
    )
    fun `M resolve to Invalid W resolve() { malformed pin }`(pin: String) {
        // When
        val result = ResponseProtectionResolver.resolve(protection(ORIGIN, PIN_A, PIN_B, pin), null)

        // Then
        assertThat(result).isInstanceOf(ResolvedResponseProtection.Invalid::class.java)
        assertThat((result as ResolvedResponseProtection.Invalid).reason).contains("pin")
    }

    @Test
    fun `M resolve to Invalid W resolve() { null pin from Java }`() {
        // Given
        @Suppress("UNCHECKED_CAST")
        val pins = listOf(PIN_A, PIN_B, null) as List<String>

        // When
        val result = ResponseProtectionResolver.resolve(
            ResponseProtection.tlsPinning(
                ResponseProtectionMode.REQUIRED,
                TlsPinningProfile.custom(ORIGIN, TlsPinTarget.LEAF, pins)
            ),
            null
        )

        // Then
        assertThat(result).isInstanceOf(ResolvedResponseProtection.Invalid::class.java)
    }

    @Test
    fun `M canonicalize the pins W resolve() { base64 without padding }`() {
        // When
        val result = ResponseProtectionResolver.resolve(protection(ORIGIN, PIN_A.trimEnd('='), PIN_B), null)

        // Then
        assertThat((result as ResolvedResponseProtection.TlsPinning).pins).containsExactlyInAnyOrder(PIN_A, PIN_B)
    }

    @Test
    fun `M produce a stable scope W resolve() { same policy, different pin order }`() {
        // When
        val first = ResponseProtectionResolver.resolve(protection(ORIGIN, PIN_A, PIN_B), null)
        val second = ResponseProtectionResolver.resolve(protection("$ORIGIN/", PIN_B, PIN_A), null)

        // Then
        assertThat(first.persistenceScope).isEqualTo(second.persistenceScope)
    }

    @Test
    fun `M produce a different scope W resolve() { different pins or origin }`() {
        // When
        val reference = ResponseProtectionResolver.resolve(protection(ORIGIN, PIN_A, PIN_B), null)
        val otherPins = ResponseProtectionResolver.resolve(protection(ORIGIN, PIN_A, PIN_C), null)
        val otherOrigin = ResponseProtectionResolver.resolve(
            protection("https://other.example.com", PIN_A, PIN_B),
            null
        )

        // Then
        assertThat(reference.persistenceScope)
            .isNotEqualTo(otherPins.persistenceScope)
            .isNotEqualTo(otherOrigin.persistenceScope)
    }

    @Test
    fun `M resolve a placeholder policy W resolve() { DATADOG_STAGING_V1 }`() {
        // When
        val result = ResponseProtectionResolver.resolve(
            ResponseProtection.tlsPinning(ResponseProtectionMode.REQUIRED, TlsPinningProfile.DATADOG_STAGING_V1),
            null
        )

        // Then
        assertThat(result).isInstanceOf(ResolvedResponseProtection.TlsPinning::class.java)
        assertThat((result as ResolvedResponseProtection.TlsPinning).isPlaceholder).isTrue()
        assertThat(result.pins).hasSizeGreaterThanOrEqualTo(ResponseProtectionResolver.MIN_PINS)
    }

    private fun protection(origin: String, vararg pins: String): ResponseProtection =
        ResponseProtection.tlsPinning(
            ResponseProtectionMode.REQUIRED,
            TlsPinningProfile.custom(origin, TlsPinTarget.LEAF, pins.toList())
        )

    companion object {
        private const val ORIGIN = "https://flags.example.com"
        private const val PIN_A = "sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
        private const val PIN_B = "sha256/AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE="
        private const val PIN_C = "sha256/AgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgI="
    }
}
