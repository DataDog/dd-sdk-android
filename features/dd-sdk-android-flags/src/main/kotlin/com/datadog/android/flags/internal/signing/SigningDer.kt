/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal.signing

/** Bounded, canonical DER envelope reader for the restricted signing certificate profile. */
@Suppress("MagicNumber") // DER tags, length forms and bit masks are wire constants.
internal class SigningDer private constructor(
    val tag: Int,
    val encoded: ByteArray,
    val value: ByteArray,
    val children: List<SigningDer>
) {
    fun sequence(): List<SigningDer> {
        signingCheck(tag == 0x30)
        return children
    }

    companion object {
        fun read(bytes: ByteArray): SigningDer {
            val budget = intArrayOf(512)
            val (node, end) = parse(bytes, 0, bytes.size, 0, budget)
            signingCheck(end == bytes.size)
            return node
        }

        private fun parse(
            bytes: ByteArray,
            start: Int,
            limit: Int,
            depth: Int,
            budget: IntArray
        ): Pair<SigningDer, Int> {
            signingCheck(depth <= 24 && --budget[0] >= 0 && start + 2 <= limit)
            var offset = start
            val tag = bytes[offset++].toInt() and 255
            signingCheck(tag != 0 && tag and 31 != 31)
            var length = bytes[offset++].toInt() and 255
            if (length >= 128) {
                val count = length and 127
                signingCheck(count in 1..2 && offset + count <= limit && bytes[offset] != 0.toByte())
                length = 0
                repeat(count) { length = (length shl 8) or (bytes[offset++].toInt() and 255) }
                signingCheck(length >= 128 && (count == 1 || length >= 256))
            }
            signingCheck(length <= limit - offset)
            val end = offset + length
            val value = signingSlice(bytes, offset, end)
            val children = mutableListOf<SigningDer>()
            if (tag and 32 != 0) {
                while (offset < end) {
                    val child = parse(bytes, offset, end, depth + 1, budget)
                    children.add(child.first)
                    offset = child.second
                }
            }
            canonical(tag, value, children)
            return SigningDer(tag, signingSlice(bytes, start, end), value, children) to end
        }

        private fun canonical(tag: Int, value: ByteArray, children: List<SigningDer>) {
            when (tag) {
                1 -> signingCheck(value.size == 1 && (value[0] == 0.toByte() || value[0] == (-1).toByte()))
                2 -> {
                    signingCheck(value.isNotEmpty())
                    if (value.size > 1) {
                        signingCheck(!(value[0] == 0.toByte() && value[1].toInt() and 128 == 0))
                        signingCheck(!(value[0] == (-1).toByte() && value[1].toInt() and 128 != 0))
                    }
                }
                3 -> {
                    signingCheck(value.isNotEmpty())
                    val unused = value[0].toInt()
                    signingCheck(unused in 0..7 && (value.size > 1 || unused == 0))
                    signingCheck(value[value.size - 1].toInt() and ((1 shl unused) - 1) == 0)
                }
                5 -> signingCheck(value.isEmpty())
                6 -> {
                    signingCheck(value.isNotEmpty() && value[value.size - 1].toInt() and 128 == 0)
                    value.forEachIndexed { i, b ->
                        signingCheck(!(b == 128.toByte() && (i == 0 || value[i - 1].toInt() and 128 == 0)))
                    }
                }
                23 -> signingCheck(value.toString(Charsets.US_ASCII).matches(Regex("[0-9]{12}Z")))
                24 -> signingCheck(value.toString(Charsets.US_ASCII).matches(Regex("[0-9]{14}Z")))
                0x31 -> children.zipWithNext().forEach { (a, b) -> signingCheck(compare(a.encoded, b.encoded) <= 0) }
            }
        }

        private fun compare(a: ByteArray, b: ByteArray): Int {
            for (i in 0 until minOf(a.size, b.size)) {
                val difference = (a[i].toInt() and 255) - (b[i].toInt() and 255)
                if (difference != 0) return difference
            }
            return a.size - b.size
        }
    }
}
