/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.wrappers

import android.util.Base64
import java.lang.AssertionError

internal class Base64Wrapper {
    internal fun encodeToString(byteArray: ByteArray, flags: Int): String {
        @Suppress("SwallowedException", "TooGenericExceptionCaught")
        return try {
            Base64.encodeToString(byteArray, flags)
        } catch (e: AssertionError) {
            // This should never happen since we are using the default encoding
            // TODO: REPLAY-1364 Add logs here once the sdkLogger is added
            ""
        }
    }
}
