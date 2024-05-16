/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.persistence.tlvformat

import java.nio.ByteBuffer

internal class TLVBlock(
    val type: TLVBlockType,
    val data: ByteArray
) {
    internal fun serialize(): ByteArray? {
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
        return ByteBuffer
            .allocate(data.size + Int.SIZE_BYTES + Short.SIZE_BYTES)
            .putShort(typeAsShort)
            .putInt(length)
            .put(data)
            .array()
    }
}
