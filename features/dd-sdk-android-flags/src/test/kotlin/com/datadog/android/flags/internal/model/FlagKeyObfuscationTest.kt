/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal.model

import com.google.gson.JsonParseException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource

internal class FlagKeyObfuscationTest {
    @ParameterizedTest
    @MethodSource("vectors")
    fun `M match backend hashes W encode() {shared protocol vectors}`(vector: Pair<String, String>) {
        // Given
        val (key, digest) = vector
        val encoding = FlagKeyObfuscation.fromSalt(SALT)

        // When
        val actual = encoding.encode(key)

        // Then
        assertThat(actual).isEqualTo(digest)
    }

    @Test
    fun `M cache bounded lookup hashes W encode() { descriptors keep separate caches }`() {
        // Given
        val encoding = FlagKeyObfuscation.fromSalt(SALT)
        val other = FlagKeyObfuscation.fromSalt("f".repeat(32))

        // When
        val first = checkNotNull(encoding.encode("flag"))

        // Then
        assertThat(encoding.encode("flag")).isSameAs(first)
        assertThat(other.encode("flag")).isNotEqualTo(first)
        assertThat(encoding.encode("flag")).isSameAs(first)
        assertThat(encoding.encode("café")).isNotEqualTo(encoding.encode("cafe\u0301"))

        // When
        repeat(1024) { encoding.encode("flag-$it") }

        // Then
        assertThat(encoding.encode("flag")).isEqualTo(first).isNotSameAs(first)
    }

    @ParameterizedTest
    @ValueSource(strings = ["\ud800", "\udc00", "a\ud800b"])
    fun `M reject invalid Unicode W encode() {unpaired surrogate}`(key: String) {
        // Given
        val encoding = FlagKeyObfuscation.fromSalt(SALT)

        // When
        val actual = encoding.encode(key)

        // Then
        assertThat(actual).isNull()
    }

    @ParameterizedTest
    @ValueSource(strings = ["", "000102", "000102030405060708090A0B0C0D0E0F", "000102030405060708090a0b0c0d0e0fg"])
    fun `M reject invalid salt W fromSalt() {invalid length or alphabet}`(salt: String) {
        // Given
        // Salt comes from the test case.
        // When / Then
        assertThatThrownBy { FlagKeyObfuscation.fromSalt(salt) }.isInstanceOf(JsonParseException::class.java)
    }

    companion object {
        // Fixed inputs and expected digests verify the shared backend/SDK protocol, not incidental test data.
        private const val SALT = "000102030405060708090a0b0c0d0e0f"
        private val VECTORS = listOf(
            "new-route-planner" to "a60479237ef2f69175bbe0bd581966d1583766941815dc1d414c883767795190",
            "Flag" to "adca75d2141c51b0c0f084c1058edfb8e6e763f91aaf54ca06326d9847bd9586",
            "flag" to "9817872c144b018abd77e3915bd77e2c27f4f534dccff8ffaca27361e3a5e1ee",
            " flag " to "b1a5f851cc82a3fdf03a461d72a2241584dcf455df1d384e02a937901b3bc80b",
            "café" to "3bfa8c3c17c1b61035b98ecf14007cdf5cba5ce61a48e8cfb65f8106541892d3",
            "cafe\u0301" to "1e8b7ec5e8028a1ec96b38dd37f6ccea041c0a90358904af40ac5810c23c8765",
            "🚲/旗" to "94b611e0d3b26b52f6ad66013d1c72f8a92ea109390049759e75d3c8d3aa4dab",
            "a\u0000b" to "bac134d201be5e7f28fc7019248f0809c2a013b137e866446ee71add2bc344c7",
            "" to "072d985b427f536ad0a11b2d4c5e0f7e6f0ff6d23e779e082f75599ce3fe3eba"
        )

        @JvmStatic
        fun vectors(): List<Pair<String, String>> = VECTORS
    }
}
