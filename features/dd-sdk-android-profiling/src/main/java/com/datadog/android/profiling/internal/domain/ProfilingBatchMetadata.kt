/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.profiling.internal.domain

import com.datadog.android.api.InternalLogger
import java.nio.BufferUnderflowException
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Structured metadata for continuous profiling batch events, bundling the raw perfetto bytes
 * and the rum-mobile-events JSON attachment.
 *
 * Binary layout: [4-byte magic "DDCP"][4-byte big-endian perfetto length][perfetto bytes][rum-mobile-events bytes]
 */
internal class ProfilingBatchMetadata(
    val perfettoBytes: ByteArray,
    val rumMobileEventsBytes: ByteArray
) {
    fun toBytes(): ByteArray {
        val perfettoLength = perfettoBytes.size
        @Suppress("UnsafeThirdPartyFunctionCall") // sizes are always non-negative
        return ByteBuffer.allocate(MAGIC.size + Int.SIZE_BYTES + perfettoLength + rumMobileEventsBytes.size)
            .apply {
                order(ByteOrder.BIG_ENDIAN)
                put(MAGIC)
                putInt(perfettoLength)
                put(perfettoBytes)
                put(rumMobileEventsBytes)
            }
            .array()
    }

    companion object {
        // 4-byte magic: "DDCP" (DataDog Continuous Profile)
        internal val MAGIC = byteArrayOf(0x44, 0x44, 0x43, 0x50)

        private val MIN_HEADER_SIZE = MAGIC.size + Int.SIZE_BYTES

        /**
         * Returns a [ProfilingBatchMetadata] parsed from [bytes], or `null` if [bytes] does not
         * start with the DDCP magic prefix or if the content is corrupt/truncated.
         */
        @Suppress("ReturnCount")
        fun fromBytesOrNull(bytes: ByteArray, internalLogger: InternalLogger): ProfilingBatchMetadata? {
            if (bytes.size < MIN_HEADER_SIZE) return null
            if (!MAGIC.indices.all { bytes[it] == MAGIC[it] }) return null
            try {
                @Suppress("UnsafeThirdPartyFunctionCall") // position is guarded by MIN_HEADER_SIZE check above
                val buffer = ByteBuffer.wrap(bytes).apply {
                    order(ByteOrder.BIG_ENDIAN)
                    position(MAGIC.size)
                }
                val perfettoLength = buffer.int
                if (perfettoLength < 0 || perfettoLength > buffer.remaining()) return null
                val perfettoBytes = ByteArray(perfettoLength)
                @Suppress("UnsafeThirdPartyFunctionCall") // length guarded by perfettoLength <= remaining() check above
                buffer.get(perfettoBytes)
                val rumMobileEventsBytes = ByteArray(buffer.remaining())
                @Suppress("UnsafeThirdPartyFunctionCall") // size == remaining(), cannot underflow
                buffer.get(rumMobileEventsBytes)
                return ProfilingBatchMetadata(perfettoBytes, rumMobileEventsBytes)
            } catch (e: BufferUnderflowException) {
                internalLogger.log(
                    InternalLogger.Level.WARN,
                    InternalLogger.Target.MAINTAINER,
                    { "Failed to parse DDCP metadata: buffer underflow" },
                    e
                )
                return null
            }
        }
    }
}
