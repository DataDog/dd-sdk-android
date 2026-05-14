/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.internal.sampling

private const val HEX_RADIX = 16

/**
 * Extracts a numeric identifier from a RUM session ID for use as input to a [DeterministicSampler].
 */
object SessionSamplingIdProvider {

    /**
     * Returns the last hyphen-delimited segment of [sessionId] parsed as an unsigned hexadecimal
     * long, or `0` if the input is malformed or the segment is not valid hex.
     *
     * For a UUID `aaaaaaaa-bbbb-cccc-dddd-1234567890ab` this returns `0x1234567890ab`.
     */
    fun provideId(sessionId: String): ULong {
        return sessionId.split('-')
            .lastOrNull()
            ?.toULongOrNull(HEX_RADIX)
            ?: 0uL
    }
}
