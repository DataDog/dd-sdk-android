/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.sampling

private const val HEX_RADIX = 16

internal object SessionSamplingIdProvider {

    // for a UUID with value aaaaaaaa-bbbb-cccc-dddd-1234567890ab
    // we use the last part as the deterministic input: 0x1234567890ab
    fun provideId(sessionId: String): ULong {
        return sessionId.split('-')
            .lastOrNull()
            ?.toULongOrNull(HEX_RADIX)
            ?: 0uL
    }
}
