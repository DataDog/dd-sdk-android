/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.profiling.internal.domain

import java.io.IOException
import java.io.InputStream

/**
 * View over a continuous profiling batch metadata blob, bundling the raw perfetto bytes
 * and the rum-mobile-events JSON attachment. Offsets and lengths point into [bytes], so
 * that large perfetto payloads are never copied.
 *
 * Binary layout: [4-byte magic "DDCP"][4-byte big-endian perfetto length][perfetto bytes][rum-mobile-events bytes]
 */
internal class ProfilingBatchMetadata(
    val bytes: ByteArray,
    val perfettoOffset: Int,
    val perfettoLength: Int,
    val rumMobileEventsOffset: Int,
    val rumMobileEventsLength: Int
) {

    companion object {
        // 4-byte magic: "DDCP" (DataDog Continuous Profile)
        internal val MAGIC = byteArrayOf(0x44, 0x44, 0x43, 0x50)

        private val HEADER_SIZE = MAGIC.size + Int.SIZE_BYTES

        /**
         * Builds the metadata blob in a single allocation, reading exactly [perfettoLength]
         * bytes from [perfettoInput] directly into it. Returns `null` if [perfettoInput] does not
         * hold exactly [perfettoLength] bytes.
         *
         * @throws IOException if [perfettoInput] fails.
         */
        @Throws(IOException::class)
        fun encode(perfettoInput: InputStream, perfettoLength: Int, rumMobileEventsBytes: ByteArray): ByteArray? {
            val bytes = ByteArray(HEADER_SIZE + perfettoLength + rumMobileEventsBytes.size)
            @Suppress("UnsafeThirdPartyFunctionCall") // bytes is larger than MAGIC
            MAGIC.copyInto(bytes)
            for (i in 0 until Int.SIZE_BYTES) {
                bytes[MAGIC.size + i] = (perfettoLength ushr (Byte.SIZE_BITS * (Int.SIZE_BYTES - 1 - i))).toByte()
            }
            var read = 0
            var truncated = false
            while (read < perfettoLength && !truncated) {
                @Suppress("UnsafeThirdPartyFunctionCall") // offset and length are within bounds
                val count = perfettoInput.read(bytes, HEADER_SIZE + read, perfettoLength - read)
                if (count < 0) truncated = true else read += count
            }
            // Trailing data means the input changed after its length was taken: reject the snapshot.
            @Suppress("UnsafeThirdPartyFunctionCall") // IOException is part of this function's contract
            if (truncated || perfettoInput.read() >= 0) return null
            @Suppress("UnsafeThirdPartyFunctionCall") // bytes was sized to fit rumMobileEventsBytes
            rumMobileEventsBytes.copyInto(bytes, destinationOffset = HEADER_SIZE + perfettoLength)
            return bytes
        }

        /**
         * Returns a [ProfilingBatchMetadata] view over [bytes], or `null` if [bytes] does not
         * start with the DDCP magic prefix or if the content is corrupt/truncated.
         */
        @Suppress("ReturnCount")
        fun fromBytesOrNull(bytes: ByteArray): ProfilingBatchMetadata? {
            if (bytes.size < HEADER_SIZE) return null
            if (!MAGIC.indices.all { bytes[it] == MAGIC[it] }) return null
            var perfettoLength = 0
            for (i in MAGIC.size until HEADER_SIZE) {
                perfettoLength = (perfettoLength shl Byte.SIZE_BITS) or (bytes[i].toInt() and BYTE_MASK)
            }
            if (perfettoLength < 0 || perfettoLength > bytes.size - HEADER_SIZE) return null
            val rumOffset = HEADER_SIZE + perfettoLength
            return ProfilingBatchMetadata(
                bytes = bytes,
                perfettoOffset = HEADER_SIZE,
                perfettoLength = perfettoLength,
                rumMobileEventsOffset = rumOffset,
                rumMobileEventsLength = bytes.size - rumOffset
            )
        }

        private const val BYTE_MASK = 0xFF
    }
}
