/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal.protection

import com.datadog.android.api.InternalLogger

private const val TELEMETRY_REASON_KEY = "reason"
private const val INVALID_POLICY_TELEMETRY_MESSAGE = "Flags response protection policy invalid"

/**
 * Reports the resolved response protection when the feature is enabled. Telemetry carries only the reason.
 */
internal fun InternalLogger.logResponseProtection(protection: ResolvedResponseProtection) {
    when (protection) {
        is ResolvedResponseProtection.Invalid -> {
            log(
                InternalLogger.Level.ERROR,
                InternalLogger.Target.USER,
                {
                    "responseProtection is invalid (${protection.reason}). " +
                        "Flag assignments will not be fetched or loaded from cache."
                }
            )
            log(
                InternalLogger.Level.ERROR,
                InternalLogger.Target.TELEMETRY,
                { INVALID_POLICY_TELEMETRY_MESSAGE },
                additionalProperties = mapOf(TELEMETRY_REASON_KEY to protection.reason),
                onlyOnce = true
            )
        }

        is ResolvedResponseProtection.TlsPinning,
        ResolvedResponseProtection.None -> Unit
    }
}
