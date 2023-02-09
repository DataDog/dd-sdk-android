/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.utils

import java.math.BigInteger

internal const val HUNDRED = 100.0
internal const val HEX_RADIX = 16

internal fun Int.toHexString() = toString(HEX_RADIX)

internal fun Long.toHexString() = toString(HEX_RADIX)

internal fun BigInteger.toHexString(): String {
    return toLong().toHexString()
}

/**
 * Converts value (which should be in the range 0 to 100) to the percent format.
 */
fun Float.percent() = this / HUNDRED
