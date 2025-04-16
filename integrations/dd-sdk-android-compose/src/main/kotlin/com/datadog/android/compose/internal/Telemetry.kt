/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.compose.internal

import com.datadog.android.Datadog
import com.datadog.android.api.InternalLogger
import com.datadog.android.api.SdkCore
import com.datadog.android.api.feature.FeatureSdkCore

internal fun sendTelemetry(
    autoInstrumented: Boolean = false,
    instrumentationType: InstrumentationType,
    sdkCore: SdkCore = Datadog.getInstance()
) {
    val message = "$DATADOG_SEMANTICS_TELEMETRY_LOG: ${instrumentationType.value}"
    val attributes = mapOf(
        KEY_COMPOSE_INSTRUMENTATION to mapOf(
            KEY_ENABLED to autoInstrumented,
            KEY_INSTRUMENTATION_TYPE to instrumentationType
        )
    )
    (sdkCore as? FeatureSdkCore)?.internalLogger?.log(
        level = InternalLogger.Level.INFO,
        target = InternalLogger.Target.TELEMETRY,
        messageBuilder = { message },
        onlyOnce = true,
        additionalProperties = attributes
    )
}

internal enum class InstrumentationType(val value: String) {
    Semantics("Semantics"),
    ViewTracking("ViewTracking")
}

private const val KEY_COMPOSE_INSTRUMENTATION = "compose_instrumentation"

private const val KEY_ENABLED = "enabled"

private const val KEY_INSTRUMENTATION_TYPE = "instrumentation_type"

private const val DATADOG_SEMANTICS_TELEMETRY_LOG =
    "Datadog Compose Integration Telemetry"
