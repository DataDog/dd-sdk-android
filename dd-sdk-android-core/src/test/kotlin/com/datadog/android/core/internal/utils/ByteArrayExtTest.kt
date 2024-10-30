/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.utils

import com.datadog.android.api.InternalLogger
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.annotation.StringForgeryType
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.verifyNoInteractions
import kotlin.math.max

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
internal class ByteArrayExtTest {

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    // region split()

    @Test
    fun `M splits a byteArray W split() {0 separator}`(
        @StringForgery(StringForgeryType.ALPHABETICAL, size = 1) separator: String,
        @StringForgery(StringForgeryType.NUMERICAL) rawString: String
    ) {
        // Given
        val byteArray = rawString.toByteArray(Charsets.UTF_8)

        // When
        val subs = byteArray.split(separator.first().code.toByte(), mockInternalLogger)

        // Then
        assertThat(subs).hasSize(1)
        assertThat(subs[0]).isEqualTo(byteArray)
    }

    @Test
    fun `M splits a byteArray W split() {1 separator}`(
        @StringForgery(StringForgeryType.ALPHABETICAL, size = 1) separator: String,
        @StringForgery(StringForgeryType.NUMERICAL) part0: String,
        @StringForgery(StringForgeryType.NUMERICAL) part1: String
    ) {
        // Given
        val rawString = part0 + separator + part1
        val byteArray = rawString.toByteArray(Charsets.UTF_8)

        // When
        val subs = byteArray.split(separator.first().code.toByte(), mockInternalLogger)

        // Then
        assertThat(subs).hasSize(2)
        assertThat(String(subs[0])).isEqualTo(part0)
        assertThat(String(subs[1])).isEqualTo(part1)
    }

    @Test
    fun `M splits a byteArray W split() {trailing separator}`(
        @StringForgery(StringForgeryType.ALPHABETICAL, size = 1) separator: String,
        @StringForgery(StringForgeryType.NUMERICAL) part0: String
    ) {
        // Given
        val rawString = part0 + separator
        val byteArray = rawString.toByteArray(Charsets.UTF_8)

        // When
        val subs = byteArray.split(separator.first().code.toByte(), mockInternalLogger)

        // Then
        assertThat(subs).hasSize(1)
        assertThat(String(subs[0])).isEqualTo(part0)
    }

    @Test
    fun `M splits a byteArray W split() {leading separator}`(
        @StringForgery(StringForgeryType.ALPHABETICAL, size = 1) separator: String,
        @StringForgery(StringForgeryType.NUMERICAL) part0: String
    ) {
        // Given
        val rawString = separator + part0
        val byteArray = rawString.toByteArray(Charsets.UTF_8)

        // When
        val subs = byteArray.split(separator.first().code.toByte(), mockInternalLogger)

        // Then
        assertThat(subs).hasSize(1)
        assertThat(String(subs[0])).isEqualTo(part0)
    }

    @Test
    fun `M splits a byteArray W split() {consecutive separators}`(
        @StringForgery(StringForgeryType.ALPHABETICAL, size = 1) separator: String,
        @StringForgery(StringForgeryType.NUMERICAL) part0: String,
        @StringForgery(StringForgeryType.NUMERICAL) part1: String
    ) {
        // Given
        val rawString = part0 + separator + separator + part1
        val byteArray = rawString.toByteArray(Charsets.UTF_8)

        // When
        val subs = byteArray.split(separator.first().code.toByte(), mockInternalLogger)

        // Then
        assertThat(subs).hasSize(2)
        assertThat(String(subs[0])).isEqualTo(part0)
        assertThat(String(subs[1])).isEqualTo(part1)
    }

    // endregion

    // region indexOf()

    @Test
    fun `M returns -1 W indexOf() {invalid start}`(
        @StringForgery(StringForgeryType.ALPHABETICAL, size = 1) searchedChar: String,
        @StringForgery(StringForgeryType.ALPHABETICAL) rawString: String
    ) {
        // Given
        val byteArray = rawString.toByteArray(Charsets.UTF_8)

        // When
        val index = byteArray.indexOf(searchedChar.first().code.toByte(), -1)

        // Then
        assertThat(index).isEqualTo(-1)
    }

    @Test
    fun `M returns -1 W indexOf() {byte not found}`(
        @StringForgery(StringForgeryType.ALPHABETICAL, size = 1) searchedChar: String,
        @StringForgery(StringForgeryType.NUMERICAL) rawString: String
    ) {
        // Given
        val byteArray = rawString.toByteArray(Charsets.UTF_8)

        // When
        val index = byteArray.indexOf(searchedChar.first().code.toByte(), 0)

        // Then
        assertThat(index).isEqualTo(-1)
    }

    @Test
    fun `M return index W indexOf() {byte found}`(
        @StringForgery(StringForgeryType.ALPHABETICAL, size = 1) searchedChar: String,
        @StringForgery(StringForgeryType.NUMERICAL) part0: String,
        @StringForgery(StringForgeryType.NUMERICAL) part1: String
    ) {
        // Given
        val rawString = part0 + searchedChar + part1
        val byteArray = rawString.toByteArray(Charsets.UTF_8)
        val expectedIndex = part0.toByteArray(Charsets.UTF_8).size

        // When
        val index = byteArray.indexOf(searchedChar.first().code.toByte(), 0)

        // Then
        assertThat(index).isEqualTo(expectedIndex)
    }

    @Test
    fun `M find all indexes W indexOf()`(
        @StringForgery(StringForgeryType.ALPHABETICAL, size = 1) searchedChar: String,
        @StringForgery(StringForgeryType.NUMERICAL) parts: List<String>
    ) {
        // Given
        val rawString = parts.joinToString(searchedChar)
        val expectedIndexes = mutableListOf<Int>()
        var prevExpectedIndex = 0
        parts.forEachIndexed { index, s ->
            if (index > 0) {
                expectedIndexes.add(prevExpectedIndex)
                prevExpectedIndex++
            }
            prevExpectedIndex += s.toByteArray().size
        }
        val byteArray = rawString.toByteArray(Charsets.UTF_8)

        // When
        val foundIndexes = mutableListOf<Int>()
        var prevFoundIndex = 0
        do {
            val index = byteArray.indexOf(searchedChar.first().code.toByte(), prevFoundIndex)
            if (index >= 0) {
                foundIndexes.add(index)
                prevFoundIndex = index + 1
            } else {
                prevFoundIndex = -1
            }
        } while (prevFoundIndex >= 0)

        // Then
        assertThat(foundIndexes).containsAll(expectedIndexes)
    }

    // endregion

    // region join()

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
        @StringForgery data: List<String>
    ) {
        // Given
        val dataBytes = data.map { it.toByteArray() }
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
        @StringForgery data: List<String>
    ) {
        // Given
        val dataBytes = data.map { it.toByteArray() }
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
        @StringForgery data: String
    ) {
        // Given
        val dataBytes = listOf(data.toByteArray())
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

    // region copyTo()

    @Test
    fun `M copy data W copyTo() {copy entire content}`(
        @StringForgery(StringForgeryType.NUMERICAL) rawString: String
    ) {
        // Given
        val byteArray = rawString.toByteArray(Charsets.UTF_8)
        val destination = ByteArray(byteArray.size)

        // When
        val result = byteArray.copyTo(0, destination, 0, byteArray.size, mockInternalLogger)

        // Then
        assertThat(result).isTrue()
        assertThat(byteArray).isEqualTo(destination)
        verifyNoInteractions(mockInternalLogger)
    }

    @Test
    fun `M copy data W copyTo() {copy partial content}`(
        @StringForgery(StringForgeryType.NUMERICAL, size = 64) rawString: String,
        @IntForgery(min = 0, max = 32) startIndex: Int,
        @IntForgery(min = 33, max = 64) endIndex: Int
    ) {
        // Given
        val byteArray = rawString.toByteArray(Charsets.UTF_8)
        val copySize = endIndex - startIndex
        val destination = ByteArray(copySize)

        // When
        val result = byteArray.copyTo(startIndex, destination, 0, copySize, mockInternalLogger)

        // Then
        assertThat(result).isTrue()
        for (i in 0 until copySize) {
            assertThat(byteArray[startIndex + i]).isEqualTo(destination[i])
        }
        verifyNoInteractions(mockInternalLogger)
    }

    @Test
    fun `M return false W copyTo() {invalid source size}`(
        @StringForgery(StringForgeryType.NUMERICAL) rawString: String,
        @IntForgery(min = 1, max = 128) overflow: Int
    ) {
        // Given
        val byteArray = rawString.toByteArray(Charsets.UTF_8)
        val destination = ByteArray(byteArray.size + overflow + 1)

        // When
        val result = byteArray.copyTo(0, destination, 0, byteArray.size + overflow, mockInternalLogger)

        // Then
        assertThat(result).isFalse()
    }

    @Test
    fun `M return false W copyTo() {invalid destination size}`(
        @StringForgery(StringForgeryType.NUMERICAL) rawString: String,
        @IntForgery(min = 1, max = 128) underflow: Int
    ) {
        // Given
        val byteArray = rawString.toByteArray(Charsets.UTF_8)
        val destination = ByteArray(max(byteArray.size - underflow, 0))

        // When
        val result = byteArray.copyTo(0, destination, 0, byteArray.size, mockInternalLogger)

        // Then
        assertThat(result).isFalse()
    }

    // endregion

    // region data I/O

    @Test
    fun `M retrieve a short stored in a byte array W toByteArray() + toShort()`(
        @IntForgery(min = 0, max = Short.MAX_VALUE.toInt()) i: Int
    ) {
        // Given
        val s = i.toShort()
        val byteArray = s.toByteArray()

        // When
        val result = byteArray.toShort()

        // Then
        assertThat(result).isEqualTo(s)
    }

    @Test
    fun `M retrieve an int stored in a byte array W toByteArray() + toInt()`(
        @IntForgery i: Int
    ) {
        // Given
        val byteArray = i.toByteArray()

        // When
        val result = byteArray.toInt()

        // Then
        assertThat(result).isEqualTo(i)
    }

    @Test
    fun `M retrieve a long stored in a byte array W toByteArray() + toLong()`(
        @LongForgery l: Long
    ) {
        // Given
        val byteArray = l.toByteArray()

        // When
        val result = byteArray.toLong()

        // Then
        assertThat(result).isEqualTo(l)
    }

    @Test
    fun `M return whole byte array W copyOfRangeSafe()`(
        @StringForgery(size = 32) data: String
    ) {
        // Given
        val byteArray = data.toByteArray()

        // When
        val result = byteArray.copyOfRangeSafe(0, byteArray.size)

        // Then
        assertThat(result).isEqualTo(byteArray)
    }

    @Test
    fun `M return subset byte array W copyOfRangeSafe()`(
        @StringForgery(size = 32) prefix: String,
        @StringForgery(size = 32) data: String,
        @StringForgery(size = 32) postfix: String
    ) {
        // Given
        val prefixByteArray = prefix.toByteArray()
        val dataByteArray = data.toByteArray()
        val postfixByteArray = postfix.toByteArray()
        val byteArray = prefixByteArray + dataByteArray + postfixByteArray

        // When
        val result = byteArray.copyOfRangeSafe(prefixByteArray.size, prefixByteArray.size + dataByteArray.size)

        // Then
        assertThat(result).isEqualTo(dataByteArray)
    }

    @Test
    fun `M return an empty byte array W copyOfRangeSafe() { negative index }`(
        @StringForgery(size = 32) data: String,
        @IntForgery(min = -512, max = 0) negativeIndex: Int
    ) {
        // Given
        val byteArray = data.toByteArray()

        // When
        val result = byteArray.copyOfRangeSafe(negativeIndex, 1)

        // Then
        assertThat(result).isEmpty()
    }

    @Test
    fun `M return an empty byte array W copyOfRangeSafe() { out of bounds index }`(
        @StringForgery(size = 32) data: String,
        @IntForgery(min = 1, max = 512) positiveIndex: Int
    ) {
        // Given
        val byteArray = data.toByteArray()

        // When
        val result = byteArray.copyOfRangeSafe(0, byteArray.size + positiveIndex)

        // Then
        assertThat(result).isEmpty()
    }

    @Test
    fun `M return an empty byte array W copyOfRangeSafe() { illegal index }`(
        @StringForgery(size = 32) data: String
    ) {
        // Given
        val byteArray = data.toByteArray()

        // When
        val result = byteArray.copyOfRangeSafe(byteArray.lastIndex, 0)

        // Then
        assertThat(result).isEmpty()
    }

    // endregion
}
