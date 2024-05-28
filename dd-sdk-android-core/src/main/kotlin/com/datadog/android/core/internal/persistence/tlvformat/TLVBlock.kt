/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.persistence.tlvformat

import com.datadog.android.api.InternalLogger
import java.nio.ByteBuffer
import java.util.Locale

internal class TLVBlock(
    val type: TLVBlockType,
    val data: ByteArray,
    val internalLogger: InternalLogger
) {
    internal fun serialize(maxLength: Int = MAXIMUM_DATA_SIZE_MB): ByteArray? {
        if (data.isEmpty()) return null

        val typeAsShort = type.rawValue.toShort()
        val length = data.size

        // allocate IllegalArgumentException - cannot happen because
        // capacity is always positive
        //
        // put BufferOverflowException - cannot happen because we calculate the capacity
        // of the buffer to take into account the size of the TLV headers
        //
        // put/array ReadOnlyBufferException - cannot happen because ByteBuffer
        // gives a mutable buffer
        //
        // array UnsupportedOperationException - ByteBuffer buffer is backed by array
        @Suppress("UnsafeThirdPartyFunctionCall")
        val byteBuffer = ByteBuffer
            .allocate(data.size + Int.SIZE_BYTES + Short.SIZE_BYTES)
            .putShort(typeAsShort)
            .putInt(length)
            .put(data)
            .array()

        val bufferLength = byteBuffer.size

        return if (bufferLength > maxLength) {
            internalLogger.log(
                target = InternalLogger.Target.MAINTAINER,
                level = InternalLogger.Level.WARN,
                messageBuilder = { BYTE_LENGTH_EXCEEDED_ERROR.format(Locale.US, maxLength) }
            )
            null
        } else {
            byteBuffer
        }
    }

    internal companion object {
        // The maximum length of data (Value) in TLV block defining key data.
        private const val MAXIMUM_DATA_SIZE_MB = 10 * 1024 * 1024 // 10 mb
        internal const val BYTE_LENGTH_EXCEEDED_ERROR =
            "DataBlock length exceeds limit of %s bytes"
    }
}
