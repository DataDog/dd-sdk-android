/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.trace.internal.domain.event

import java.math.BigInteger

internal object BigIntegerUtils {

    private const val LONG_BITS_SIZE = 64
    private const val HEX_RADIX = 16
    private const val REQUIRED_ID_HEX_LENGTH = 16

    @Suppress("UnsafeThirdPartyFunctionCall") // this can't throw in this context
    private val LONG_MASK = BigInteger("ffffffffffffffff", HEX_RADIX)

    // we are not treating these exceptions because we are sure that the input is a valid BigInteger and the
    // BigInteger overflow cannot happen in this context

    @Suppress("SwallowedException")
    fun leastSignificant64BitsAsHex(traceId: BigInteger): String {
        return try {
            traceId.and(LONG_MASK).toString(HEX_RADIX).padStart(REQUIRED_ID_HEX_LENGTH, '0')
        } catch (e: NumberFormatException) {
            ""
        } catch (e: ArithmeticException) {
            ""
        } catch (e: IllegalArgumentException) {
            ""
        }
    }

    @Suppress("SwallowedException")
    fun leastSignificant64BitsAsDecimal(traceId: BigInteger): String {
        return try {
            traceId.and(LONG_MASK).toString()
        } catch (e: NumberFormatException) {
            ""
        } catch (e: ArithmeticException) {
            ""
        }
    }

    @Suppress("SwallowedException")
    fun mostSignificant64BitsAsHex(traceId: BigInteger): String {
        return try {
            traceId.shiftRight(LONG_BITS_SIZE).toString(HEX_RADIX).padStart(REQUIRED_ID_HEX_LENGTH, '0')
        } catch (e: NumberFormatException) {
            ""
        } catch (e: ArithmeticException) {
            ""
        } catch (e: IllegalArgumentException) {
            ""
        }
    }
}
