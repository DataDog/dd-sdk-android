/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal.model

import com.google.gson.JsonParseException
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.catchThrowable
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

@ExtendWith(ForgeExtension::class)
internal class FlagKeyObfuscationTest {
    @StringForgery(regex = "[0-9a-f]{32}")
    lateinit var fakeSalt: String

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
    fun `M cache bounded lookup hashes W encode() { descriptors keep separate caches }`(
        @StringForgery fakeKey: String
    ) {
        // Given
        val encoding = FlagKeyObfuscation.fromSalt(fakeSalt)
        val otherSalt = (if (fakeSalt[0] == '0') '1' else '0') + fakeSalt.drop(1)
        val other = FlagKeyObfuscation.fromSalt(otherSalt)

        // When
        val first = encoding.encode(fakeKey)

        // Then
        checkNotNull(first)
        assertThat(encoding.encode(fakeKey)).isSameAs(first)
        assertThat(other.encode(fakeKey)).isNotEqualTo(first)
        assertThat(encoding.encode(fakeKey)).isSameAs(first)

        // When
        repeat(1024) { encoding.encode("$fakeKey-$it") }

        // Then
        assertThat(encoding.encode(fakeKey)).isEqualTo(first).isNotSameAs(first)
    }

    @Test
    fun `M reject invalid Unicode W encode() {unpaired high surrogate}`(
        @IntForgery(min = 0xd800, max = 0xdc00) fakeSurrogate: Int,
        @StringForgery(regex = "[a-z]+") fakePrefix: String
    ) {
        // Given
        val encoding = FlagKeyObfuscation.fromSalt(fakeSalt)
        val fakeKey = fakePrefix + fakeSurrogate.toChar()

        // When
        val actual = encoding.encode(fakeKey)

        // Then
        assertThat(actual).isNull()
    }

    @Test
    fun `M reject invalid Unicode W encode() {unpaired low surrogate}`(
        @IntForgery(min = 0xdc00, max = 0xe000) fakeSurrogate: Int,
        @StringForgery(regex = "[a-z]+") fakeSuffix: String
    ) {
        // Given
        val encoding = FlagKeyObfuscation.fromSalt(fakeSalt)
        val fakeKey = fakeSurrogate.toChar() + fakeSuffix

        // When
        val actual = encoding.encode(fakeKey)

        // Then
        assertThat(actual).isNull()
    }

    @Test
    fun `M reject invalid Unicode W encode() {high surrogate followed by nonsurrogate}`(
        @IntForgery(min = 0xd800, max = 0xdc00) fakeSurrogate: Int,
        @StringForgery(regex = "[a-z]+") fakeSuffix: String
    ) {
        // Given
        val encoding = FlagKeyObfuscation.fromSalt(fakeSalt)
        val fakeKey = fakeSurrogate.toChar() + fakeSuffix

        // When
        val actual = encoding.encode(fakeKey)

        // Then
        assertThat(actual).isNull()
    }

    @Test
    fun `M consume both surrogates W encode() {adjacent pairs followed by invalid Unicode}`(
        @IntForgery(min = 0xd800, max = 0xdc00) fakeHighSurrogate: Int,
        @IntForgery(min = 0xdc00, max = 0xe000) fakeLowSurrogate: Int
    ) {
        // Given
        val encoding = FlagKeyObfuscation.fromSalt(fakeSalt)
        val fakePair = "${fakeHighSurrogate.toChar()}${fakeLowSurrogate.toChar()}"
        val fakeKey = fakePair + fakePair

        // When
        val valid = encoding.encode(fakeKey)
        val invalid = encoding.encode(fakeKey + fakeLowSurrogate.toChar())

        // Then
        assertThat(valid).matches("[0-9a-f]{64}")
        assertThat(invalid).isNull()
    }

    @Test
    fun `M reject invalid salt W fromSalt() {invalid length}`(
        @IntForgery(min = 0, max = 32) fakeLength: Int
    ) {
        // Given
        val fakeInvalidSalt = fakeSalt.take(fakeLength)

        // When
        val exception = catchThrowable { FlagKeyObfuscation.fromSalt(fakeInvalidSalt) }

        // Then
        assertThat(exception).isInstanceOf(JsonParseException::class.java)
    }

    @Test
    fun `M reject invalid salt W fromSalt() {invalid alphabet}`(
        @IntForgery(min = 0, max = 32) fakeIndex: Int,
        @StringForgery(regex = "[A-Fg-z]") fakeInvalidCharacter: String
    ) {
        // Given
        val fakeInvalidSalt = fakeSalt.replaceRange(fakeIndex, fakeIndex + 1, fakeInvalidCharacter)

        // When
        val exception = catchThrowable { FlagKeyObfuscation.fromSalt(fakeInvalidSalt) }

        // Then
        assertThat(exception).isInstanceOf(JsonParseException::class.java)
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
