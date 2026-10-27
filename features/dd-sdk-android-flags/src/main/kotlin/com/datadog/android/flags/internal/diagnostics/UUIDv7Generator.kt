/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal.diagnostics

import java.util.UUID

/** UUIDv7 Generator. */
internal object UUIDv7Generator {
    fun generate(timestampMillis: Long): UUID {
        // RFC 9562: 48-bit Unix milliseconds, version 7, RFC variant, and 74 random bits.
        val timestamp = timestampMillis.coerceIn(0, MAX_TIMESTAMP)
        val random = UUID.randomUUID()
        return UUID(
            (timestamp shl TIMESTAMP_SHIFT) or VERSION_BITS or (random.mostSignificantBits and RANDOM_BITS_MASK),
            random.leastSignificantBits
        )
    }

    private const val MAX_TIMESTAMP = 0xffffffffffffL
    private const val TIMESTAMP_SHIFT = 0x10
    private const val VERSION_BITS = 0x7000L
    private const val RANDOM_BITS_MASK = 0x0fffL
}
