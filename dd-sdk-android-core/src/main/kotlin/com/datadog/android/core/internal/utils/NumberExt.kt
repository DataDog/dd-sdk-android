/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.utils

import java.nio.ByteBuffer

/**
 * Converts this [Short] into a [ByteArray] representation.
 */
internal fun Short.toByteArray(): ByteArray {
    // capacity is not a negative integer, buffer is not read only,
    // has sufficient capacity and is backed by an array
    @Suppress("UnsafeThirdPartyFunctionCall")
    return ByteBuffer.allocate(Short.SIZE_BYTES).putShort(this).array()
}

/**
 * Converts this [Int] into a [ByteArray] representation.
 */
internal fun Int.toByteArray(): ByteArray {
    // capacity is not a negative integer, buffer is not read only,
    // has sufficient capacity and is backed by an array
    @Suppress("UnsafeThirdPartyFunctionCall")
    return ByteBuffer.allocate(Int.SIZE_BYTES).putInt(this).array()
}

/**
 * Converts this [Long] into a [ByteArray] representation.
 */
internal fun Long.toByteArray(): ByteArray {
    // capacity is not a negative integer, buffer is not read only,
    // has sufficient capacity and is backed by an array
    @Suppress("UnsafeThirdPartyFunctionCall")
    return ByteBuffer.allocate(Long.SIZE_BYTES).putLong(this).array()
}
