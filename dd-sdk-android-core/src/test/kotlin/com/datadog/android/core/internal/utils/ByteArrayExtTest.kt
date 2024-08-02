/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.utils

import com.datadog.android.api.InternalLogger
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
internal class ByteArrayExtTest {

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    // region split
    @Test
    fun `splits a byteArray with 0 separator`(forge: Forge) {
        val separationChar = forge.anAlphabeticalChar()
        val rawString = forge.aNumericalString()
        val byteArray = rawString.toByteArray(Charsets.UTF_8)

        val subs = byteArray.split(separationChar.code.toByte(), mockInternalLogger)

        assertThat(subs).hasSize(1)
        assertThat(subs[0]).isEqualTo(byteArray)
    }

    @Test
    fun `splits a byteArray with 1 separator`(forge: Forge) {
        val separationChar = forge.anAlphabeticalChar()
        val part0 = forge.aNumericalString()
        val part1 = forge.aNumericalString()
        val rawString = part0 + separationChar + part1
        val byteArray = rawString.toByteArray(Charsets.UTF_8)

        val subs = byteArray.split(separationChar.code.toByte(), mockInternalLogger)

        assertThat(subs).hasSize(2)
        assertThat(String(subs[0])).isEqualTo(part0)
        assertThat(String(subs[1])).isEqualTo(part1)
    }

    @Test
    fun `splits a byteArray with trailing separator`(forge: Forge) {
        val separationChar = forge.anAlphabeticalChar()
        val part0 = forge.aNumericalString()
        val rawString = part0 + separationChar
        val byteArray = rawString.toByteArray(Charsets.UTF_8)

        val subs = byteArray.split(separationChar.code.toByte(), mockInternalLogger)

        assertThat(subs).hasSize(1)
        assertThat(String(subs[0])).isEqualTo(part0)
    }

    @Test
    fun `splits a byteArray with leading separator`(forge: Forge) {
        val separationChar = forge.anAlphabeticalChar()
        val part0 = forge.aNumericalString()
        val rawString = separationChar + part0
        val byteArray = rawString.toByteArray(Charsets.UTF_8)

        val subs = byteArray.split(separationChar.code.toByte(), mockInternalLogger)

        assertThat(subs).hasSize(1)
        assertThat(String(subs[0])).isEqualTo(part0)
    }

    @Test
    fun `splits a byteArray with consecutive separators`(forge: Forge) {
        val separationChar = forge.anAlphabeticalChar()
        val part0 = forge.aNumericalString()
        val part1 = forge.aNumericalString()
        val rawString = part0 + separationChar + separationChar + part1
        val byteArray = rawString.toByteArray(Charsets.UTF_8)

        val subs = byteArray.split(separationChar.code.toByte(), mockInternalLogger)

        assertThat(subs).hasSize(2)
        assertThat(String(subs[0])).isEqualTo(part0)
        assertThat(String(subs[1])).isEqualTo(part1)
    }

    // endregion

    // region indexOf

    @Test
    fun `returns -1 when byte not found`(forge: Forge) {
        val rawString = forge.aNumericalString()
        val byteArray = rawString.toByteArray(Charsets.UTF_8)

        val index = byteArray.indexOf(forge.anAlphabeticalChar().code.toByte(), 0)

        assertThat(index).isEqualTo(-1)
    }

    @Test
    fun `finds index of byte`(forge: Forge) {
        val rawString = forge.anAsciiString()
        val char = rawString[forge.anInt(0, rawString.length)]
        val expectedIndex = rawString.indexOf(char)

        val byteArray = rawString.toByteArray(Charsets.UTF_8)
        val index = byteArray.indexOf(char.code.toByte(), 0)

        assertThat(index).isEqualTo(expectedIndex)
    }

    @Test
    fun `finds all indexes of byte`(forge: Forge) {
        val rawString = forge.aNumericalString(64)
        val char = rawString[forge.anInt(0, rawString.length)]
        val expectedIndexes = mutableListOf<Int>()
        var nextExpectedIndex = rawString.indexOf(char, 0)
        while (nextExpectedIndex != -1) {
            expectedIndexes.add(nextExpectedIndex)
            nextExpectedIndex = rawString.indexOf(char, nextExpectedIndex + 1)
        }

        val byteArray = rawString.toByteArray(Charsets.UTF_8)
        val foundIndexes = mutableListOf<Int>()
        var nextIndex = byteArray.indexOf(char.code.toByte(), 0)
        while (nextIndex != -1) {
            foundIndexes.add(nextIndex)
            nextIndex = byteArray.indexOf(char.code.toByte(), nextIndex + 1)
        }

        assertThat(foundIndexes)
            .containsAll(expectedIndexes)
    }

    // endregion

    // region join

    @Test
    fun `M join items W join() { no prefix }`(
        @StringForgery separator: String,
        @StringForgery suffix: String,
        forge: Forge
    ) {
        // Given
        val dataBytes = forge.aList {
            forge.aString().toByteArray()
        }

        val separatorBytes = separator.toByteArray()
        val suffixBytes = suffix.toByteArray()

        val expected = dataBytes.reduce { acc, item ->
            acc + separatorBytes + item
        } + suffixBytes

        // When
        val joined = dataBytes.join(
            separatorBytes,
            suffix = suffixBytes,
            internalLogger = mockInternalLogger
        )

        // Then
        assertThat(joined).isEqualTo(expected)
    }

    @Test
    fun `M join items W join() { no suffix }`(
        @StringForgery separator: String,
        @StringForgery prefix: String,
        forge: Forge
    ) {
        // Given
        val dataBytes = forge.aList {
            forge.aString().toByteArray()
        }

        val separatorBytes = separator.toByteArray()
        val prefixBytes = prefix.toByteArray()

        val expected = prefixBytes + dataBytes.reduce { acc, item ->
            acc + separatorBytes + item
        }

        // When
        val joined = dataBytes.join(
            separatorBytes,
            prefix = prefixBytes,
            internalLogger = mockInternalLogger
        )

        // Then
        assertThat(joined).isEqualTo(expected)
    }

    @Test
    fun `M join items W join() { no suffix and prefix }`(
        @StringForgery separator: String,
        forge: Forge
    ) {
        // Given
        val dataBytes = forge.aList {
            forge.aString().toByteArray()
        }

        val separatorBytes = separator.toByteArray()

        val expected = dataBytes.reduce { acc, item ->
            acc + separatorBytes + item
        }

        // When
        val joined = dataBytes.join(separatorBytes, internalLogger = mockInternalLogger)

        // Then
        assertThat(joined).isEqualTo(expected)
    }

    @Test
    fun `M join items W join() { empty separator }`(
        @StringForgery prefix: String,
        @StringForgery suffix: String,
        forge: Forge
    ) {
        // Given
        val dataBytes = forge.aList {
            forge.aString().toByteArray()
        }

        val prefixBytes = prefix.toByteArray()
        val suffixBytes = suffix.toByteArray()

        val expected = prefixBytes + dataBytes.reduce { acc, bytes ->
            acc + bytes
        } + suffixBytes

        // When
        val joined = dataBytes.join(
            separator = ByteArray(0),
            prefix = prefixBytes,
            suffix = suffixBytes,
            internalLogger = mockInternalLogger
        )

        // Then
        assertThat(joined).isEqualTo(expected)
    }

    @Test
    fun `M join items W join()`(
        @StringForgery separator: String,
        @StringForgery prefix: String,
        @StringForgery suffix: String,
        forge: Forge
    ) {
        // Given
        val dataBytes = forge.aList {
            forge.aString().toByteArray()
        }

        val separatorBytes = separator.toByteArray()
        val prefixBytes = prefix.toByteArray()
        val suffixBytes = suffix.toByteArray()

        val expected = prefixBytes + dataBytes.reduce { acc, item ->
            acc + separatorBytes + item
        } + suffixBytes

        // When
        val joined =
            dataBytes.join(
                separator = separatorBytes,
                prefix = prefixBytes,
                suffix = suffixBytes,
                internalLogger = mockInternalLogger
            )

        // Then
        assertThat(joined).isEqualTo(expected)
    }

    @Test
    fun `M join items W join() { no items }`(
        @StringForgery separator: String,
        @StringForgery prefix: String,
        @StringForgery suffix: String
    ) {
        // Given
        val dataBytes = emptyList<ByteArray>()

        val separatorBytes = separator.toByteArray()
        val prefixBytes = prefix.toByteArray()
        val suffixBytes = suffix.toByteArray()

        val expected = prefixBytes + suffixBytes

        // When
        val joined =
            dataBytes.join(
                separator = separatorBytes,
                prefix = prefixBytes,
                suffix = suffixBytes,
                internalLogger = mockInternalLogger
            )

        // Then
        assertThat(joined).isEqualTo(expected)
    }

    @Test
    fun `M join items W join() { single item }`(
        @StringForgery separator: String,
        @StringForgery prefix: String,
        @StringForgery suffix: String,
        forge: Forge
    ) {
        // Given
        val dataBytes = listOf(forge.aString().toByteArray())

        val separatorBytes = separator.toByteArray()
        val prefixBytes = prefix.toByteArray()
        val suffixBytes = suffix.toByteArray()

        val expected = prefixBytes + dataBytes[0] + suffixBytes

        // When
        val joined =
            dataBytes.join(
                separator = separatorBytes,
                prefix = prefixBytes,
                suffix = suffixBytes,
                internalLogger = mockInternalLogger
            )

        // Then
        assertThat(joined).isEqualTo(expected)
    }

    @Test
    fun `M join items W join() { no prefix, no suffix, no data }`(
        @StringForgery separator: String
    ) {
        // Given
        val dataBytes = emptyList<ByteArray>()

        // When
        val joined =
            dataBytes.join(separator = separator.toByteArray(), internalLogger = mockInternalLogger)

        // Then
        assertThat(joined).isEmpty()
    }

    // endregion
}
