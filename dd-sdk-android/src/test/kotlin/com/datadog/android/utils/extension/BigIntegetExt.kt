/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2020 Datadog, Inc.
 */

package com.datadog.android.utils.extension

import java.math.BigInteger

fun BigInteger.toHexString(): String {
    return toLong().toString(16)
}

fun String.hexToBigInteger(): BigInteger {
    return toLong(16).toBigInteger()
}
