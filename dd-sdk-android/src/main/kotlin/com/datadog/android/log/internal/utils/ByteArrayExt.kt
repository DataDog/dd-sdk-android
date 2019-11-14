/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2019 Datadog, Inc.
 */

package com.datadog.android.log.internal.utils

/**
 * Splits this [ByteArray] to a list of [ByteArray] around occurrences of the specified [delimiter].
 *
 * @param delimiter a byte to be used as delimiter.
 * TODO @param limit The maximum number of substrings to return. Zero by default means no limit is set.
*/
internal fun ByteArray.split(delimiter: Byte): List<ByteArray> {
    val result = mutableListOf<ByteArray>()

    var offset = 0
    var nextIndex: Int

    do {
        nextIndex = indexOf(delimiter, offset)
        val length = if (nextIndex >= 0) nextIndex - offset else size - offset
        if (length > 0) {
            val subArray = ByteArray(length)
            System.arraycopy(this, offset, subArray, 0, length)
            result.add(subArray)
        }
        offset = nextIndex + 1
    } while (nextIndex != -1)

    return result
}

/**
 * Returns the index within this [ByteArray] of the first occurrence of the specified [b],
 * starting from the specified [startIndex].
 *
 * @return An index of the first occurrence of [b] or `-1` if none is found.
 */
internal fun ByteArray.indexOf(b: Byte, startIndex: Int = 0): Int {
    for (i in startIndex until size) {
        if (get(i) == b) {
            return i
        }
    }
    return -1
}
