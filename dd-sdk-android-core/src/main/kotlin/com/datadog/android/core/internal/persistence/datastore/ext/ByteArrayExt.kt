/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.persistence.datastore.ext

import java.nio.ByteBuffer

internal fun ByteArray.toLong(): Long {
    // wrap provides valid backing array
    @Suppress("UnsafeThirdPartyFunctionCall")
    return ByteBuffer.wrap(this).getLong()
}

internal fun ByteArray.toInt(): Int {
    // wrap provides valid backing array
    @Suppress("UnsafeThirdPartyFunctionCall")
    return ByteBuffer.wrap(this).getInt()
}

internal fun ByteArray.toShort(): Short {
    // wrap provides valid backing array
    @Suppress("UnsafeThirdPartyFunctionCall")
    return ByteBuffer.wrap(this).getShort()
}

@Suppress("TooGenericExceptionCaught", "SwallowedException")
internal fun ByteArray.copyOfRangeSafe(start: Int, end: Int): ByteArray {
    return try {
        this.copyOfRange(start, end)
    } catch (e: IndexOutOfBoundsException) {
        byteArrayOf()
    } catch (e: IllegalArgumentException) {
        byteArrayOf()
    }
}
