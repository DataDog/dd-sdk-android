/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.utils

import com.datadog.android.lint.InternalApi
import java.math.BigInteger

internal const val HEX_RADIX = 16

/**
 * Converts [Int] into hexadecimal representation.
 */
@InternalApi
fun Int.toHexString(): String = toString(HEX_RADIX)

/**
 * Converts [Long] into hexadecimal representation.
 */
@InternalApi
fun Long.toHexString(): String = toString(HEX_RADIX)

/**
 * Converts [BigInteger] into hexadecimal representation.
 */
@InternalApi
fun BigInteger.toHexString(): String {
    return toLong().toHexString()
}
