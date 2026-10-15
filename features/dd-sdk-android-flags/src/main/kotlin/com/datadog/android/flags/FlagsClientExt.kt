/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags

import com.datadog.android.flags.internal.DatadogFlagsClient
import com.datadog.android.flags.model.EvaluationContext

/**
 * Sets whether this client suppresses new evaluation telemetry and automatic RUM flag annotations.
 *
 * This synchronous, local change does not fetch assignments or change flag values and exposures.
 * Existing telemetry remains queued. This function has no effect unless
 * [FlagsClient.Builder.enableEvaluationTelemetryOptOutParameter] enabled the parameter for this client.
 * Custom [FlagsClient] implementations and no-op clients ignore this function.
 *
 * Use this typed parameter instead of adding a string to [EvaluationContext.attributes].
 * OpenFeature integrations read the Boolean `dd_evaluation_telemetry_opt_out` context attribute.
 * Java callers can use `FlagsClientExtKt.setEvaluationTelemetryOptOut(client, true)`.
 *
 * @param optOut whether to suppress new evaluation telemetry.
 */
fun FlagsClient.setEvaluationTelemetryOptOut(optOut: Boolean) {
    if (this is DatadogFlagsClient) {
        setEvaluationTelemetryOptOut(optOut)
    }
}
