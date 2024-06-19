/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.trace.internal.domain.event

import java.math.BigInteger

object BigIntegerUtils {

    private const val LONG_BITS_SIZE = 64
    private const val HEX_RADIX = 16
    private const val REQUIRED_ID_HEXA_LENGTH = 16
    private val LONG_MASK = BigInteger("ffffffffffffffff", HEX_RADIX)

    fun lessSignificantUnsignedLongAsHexa(traceId: BigInteger): String {
        @Suppress("MagicNumber")
        return traceId.and(LONG_MASK).toString(HEX_RADIX).padStart(REQUIRED_ID_HEXA_LENGTH, '0')
    }

    fun lessSignificantUnsignedLongAsDecimal(traceId: BigInteger): String {
        @Suppress("MagicNumber")
        return traceId.and(LONG_MASK).toString()
    }

    fun mostSignificantUnsignedLongAsHexa(traceId: BigInteger): String {
        @Suppress("MagicNumber")
        return traceId.shiftRight(LONG_BITS_SIZE).toString(HEX_RADIX).padStart(REQUIRED_ID_HEXA_LENGTH, '0')
    }
}
