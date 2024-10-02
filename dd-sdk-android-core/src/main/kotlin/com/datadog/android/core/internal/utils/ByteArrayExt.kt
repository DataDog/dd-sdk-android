/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.utils

import com.datadog.android.api.InternalLogger
import com.datadog.android.lint.InternalApi
import java.nio.ByteBuffer

/**
 * Splits this [ByteArray] to a list of [ByteArray] around occurrences of the specified [delimiter].
 *
 * @param delimiter a byte to be used as delimiter.
 * @param internalLogger logger to use.
 */
internal fun ByteArray.split(delimiter: Byte, internalLogger: InternalLogger): List<ByteArray> {
    val result = mutableListOf<ByteArray>()

    var offset = 0
    var nextIndex: Int

    do {
        nextIndex = indexOf(delimiter, offset)
        val length = if (nextIndex >= 0) nextIndex - offset else size - offset
        if (length > 0) {
            val subArray = ByteArray(length)
            this.copyTo(offset, subArray, 0, length, internalLogger)
            result.add(subArray)
        }
        offset = nextIndex + 1
    } while (nextIndex != -1)

    return result
}

/**
 * Joins a collection of [ByteArray] elements into a single [ByteArray], taking into account
 * separator between elements and prefix and suffix decoration of the final array.
 *
 * @param separator Separator to use between the parts joined.
 * @param prefix Optional prefix to add to the result.
 * @param suffix Optional suffix to add to the result.
 * @param internalLogger logger to use.
 */
@InternalApi
fun Collection<ByteArray>.join(
    separator: ByteArray,
    prefix: ByteArray = ByteArray(0),
    suffix: ByteArray = ByteArray(0),
    internalLogger: InternalLogger
): ByteArray {
    val dataSize = this.sumOf { it.size }
    val separatorsSize = if (this.isNotEmpty()) separator.size * (this.size - 1) else 0
    val resultSize = prefix.size + dataSize + separatorsSize + suffix.size

    val result = ByteArray(resultSize)

    var offset = 0

    prefix.copyTo(0, result, 0, prefix.size, internalLogger)
    offset += prefix.size

    for (item in this.withIndex()) {
        item.value.copyTo(0, result, offset, item.value.size, internalLogger)
        offset += item.value.size
        if (item.index != this.size - 1) {
            separator.copyTo(0, result, offset, separator.size, internalLogger)
            offset += separator.size
        }
    }

    suffix.copyTo(0, result, offset, suffix.size, internalLogger)

    return result
}

/**
 * Returns the index within this [ByteArray] of the first occurrence of the specified [b],
 * starting from the specified [startIndex].
 *
 * @return An index of the first occurrence of [b] or `-1` if none is found.
 */
@Suppress("ReturnCount")
internal fun ByteArray.indexOf(b: Byte, startIndex: Int = 0): Int {
    if (startIndex < 0) return -1

    for (i in startIndex until size) {
        @Suppress("UnsafeThirdPartyFunctionCall") // iteration over indexes which exist
        if (get(i) == b) {
            return i
        }
    }
    return -1
}

/**
 * Performs a safe version of [System.arraycopy] by performing the necessary checks and try-catch.
 *
 * @return true if the copy was successful.
 */
internal fun ByteArray.copyTo(
    srcPos: Int,
    dest: ByteArray,
    destPos: Int,
    length: Int,
    internalLogger: InternalLogger
): Boolean {
    return if (destPos + length > dest.size) {
        internalLogger.log(
            InternalLogger.Level.WARN,
            InternalLogger.Target.MAINTAINER,
            { "Cannot copy ByteArray, dest doesn't have enough space" }
        )
        false
    } else if (srcPos + length > size) {
        internalLogger.log(
            InternalLogger.Level.WARN,
            InternalLogger.Target.MAINTAINER,
            { "Cannot copy ByteArray, src doesn't have enough data" }
        )
        false
    } else {
        // this and dest can't be null, NPE cannot happen here
        // both are ByteArrays, ArrayStoreException cannot happen here
        @Suppress("UnsafeThirdPartyFunctionCall")
        System.arraycopy(this, srcPos, dest, destPos, length)
        true
    }
}

/**
 * Reads a long from this byte array.
 * Note that the ByteArray needs to be at least of size 8.
 */
internal fun ByteArray.toLong(): Long {
    // wrap provides valid backing array
    @Suppress("UnsafeThirdPartyFunctionCall")
    return ByteBuffer.wrap(this).getLong()
}

/**
 * Reads an int from this byte array.
 * Note that the ByteArray needs to be at least of size 4.
 */
internal fun ByteArray.toInt(): Int {
    // wrap provides valid backing array
    @Suppress("UnsafeThirdPartyFunctionCall")
    return ByteBuffer.wrap(this).getInt()
}

/**
 * Reads a short from this byte array.
 * Note that the ByteArray needs to be at least of size 2.
 */
internal fun ByteArray.toShort(): Short {
    // wrap provides valid backing array
    @Suppress("UnsafeThirdPartyFunctionCall")
    return ByteBuffer.wrap(this).getShort()
}

/**
 * Creates a copy of a range within this ByteArray into a new Byte Array.
 * If the copy would have thrown an exception, an empty byte array is returned instead.
 * @param fromIndex the start of the range (inclusive) to copy.
 * @param toIndex the end of the range (exclusive) to copy.
 */
@Suppress("TooGenericExceptionCaught", "SwallowedException")
internal fun ByteArray.copyOfRangeSafe(fromIndex: Int, toIndex: Int): ByteArray {
    return try {
        this.copyOfRange(fromIndex, toIndex)
    } catch (e: IndexOutOfBoundsException) {
        byteArrayOf()
    } catch (e: IllegalArgumentException) {
        byteArrayOf()
    }
}
