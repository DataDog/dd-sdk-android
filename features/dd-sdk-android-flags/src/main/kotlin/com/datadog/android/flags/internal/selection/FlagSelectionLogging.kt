/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal.selection

import com.datadog.android.api.InternalLogger

// Telemetry carries only the reason, never selection values, and is sent once per process.
private const val TELEMETRY_REASON_KEY = "reason"

private const val FLAG_SELECTION_DROPPED_TELEMETRY_MESSAGE = "Flag selection dropped"
private const val FLAG_SELECTION_IGNORED_TELEMETRY_MESSAGE = "Flag selection ignored"

internal fun InternalLogger.logDroppedFlagSelection(error: FlagSelectionError) {
    log(
        InternalLogger.Level.ERROR,
        InternalLogger.Target.USER,
        {
            "flagSelection was not sent (${error.reason}: ${error.limit}). " +
                "Loading the full flag configuration."
        }
    )
    log(
        InternalLogger.Level.DEBUG,
        InternalLogger.Target.TELEMETRY,
        { FLAG_SELECTION_DROPPED_TELEMETRY_MESSAGE },
        additionalProperties = mapOf(TELEMETRY_REASON_KEY to error.reason),
        onlyOnce = true
    )
}

internal fun InternalLogger.logIgnoredFlagSelection(reason: String) {
    log(
        InternalLogger.Level.ERROR,
        InternalLogger.Target.USER,
        { "flagSelection was ignored by the server ($reason). Loaded the full flag configuration." }
    )
    log(
        InternalLogger.Level.DEBUG,
        InternalLogger.Target.TELEMETRY,
        { FLAG_SELECTION_IGNORED_TELEMETRY_MESSAGE },
        additionalProperties = mapOf(TELEMETRY_REASON_KEY to reason),
        onlyOnce = true
    )
}
