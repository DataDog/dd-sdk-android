/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.internal.utils

private const val BYTE_MASK = 0xff
private const val HEX_SHIFT = 4
private const val LOWER_NIBBLE_MASK = 0x0f
private const val HEX_CHARS = "0123456789abcdef"

/**
 * Converts a ByteArray to its corresponding hexadecimal String representation.
 *
 * Each byte in the array is converted into two hexadecimal characters.
 * For example, the byte array `[0xA, 0x1F]` will be converted to the string `"0a1f"`.
 *
 * This method avoids performance overhead by using bitwise operations and
 * minimizing object allocations compared to alternatives like `joinToString`.
 *
 * @receiver ByteArray The byte array to be converted.
 * @return A hexadecimal [String] representation of the byte array.
 *
 */
fun ByteArray.toHexString(): String {
    @Suppress("UnsafeThirdPartyFunctionCall") // byte array size is always positive.
    val result = StringBuilder(size * 2)
    for (byte in this) {
        val intVal = byte.toInt() and BYTE_MASK
        result.append(HEX_CHARS[intVal ushr HEX_SHIFT]) // Append first half of byte
        result.append(HEX_CHARS[intVal and LOWER_NIBBLE_MASK]) // Append second half of byte
    }
    return result.toString()
}
