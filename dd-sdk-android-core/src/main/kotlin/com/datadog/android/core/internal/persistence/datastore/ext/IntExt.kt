/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.persistence.datastore.ext

import java.nio.ByteBuffer

internal fun Int.toByteArray(): ByteArray {
    // capacity is not a negative integer, buffer is not read only,
    // has sufficient capacity and is backed by an array
    @Suppress("UnsafeThirdPartyFunctionCall")
    return ByteBuffer.allocate(Int.SIZE_BYTES)
        .putInt(this).array()
}
