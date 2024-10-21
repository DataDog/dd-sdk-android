/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal

import com.datadog.android.api.InternalLogger

internal class PrivacyHelper(private val internalLogger: InternalLogger) {
    internal fun logInvalidPrivacyLevelError(e: Exception) {
        internalLogger.log(
            InternalLogger.Level.ERROR,
            listOf(InternalLogger.Target.USER, InternalLogger.Target.TELEMETRY),
            { INVALID_PRIVACY_LEVEL_ERROR },
            e
        )
    }

    internal companion object {
        internal const val INVALID_PRIVACY_LEVEL_ERROR = "Invalid privacy level"
    }
}
