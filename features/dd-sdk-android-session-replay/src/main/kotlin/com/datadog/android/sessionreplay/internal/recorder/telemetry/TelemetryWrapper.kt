/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.telemetry

import com.datadog.android.api.InternalLogger
import com.datadog.android.core.sampling.RateBasedSampler
import com.datadog.android.core.sampling.Sampler
import com.datadog.android.sessionreplay.internal.recorder.telemetry.MethodCalledTelemetry.Companion.METHOD_CALL_OPERATION_NAME

internal class TelemetryWrapper(
    private val logger: InternalLogger
) {
    internal fun startMetric(
        // Platform agnostic name of the operation.
        operationName: String,

        // The name of the class that calls the method.
        callerClass: String,

        // The sampling rate of the method call. Value between `0.0` and `100.0`,
        // where `0.0` means NO event will be processed and `100.0` means ALL events will be processed.
        // Note that this value is multiplicated by telemetry sampling (by default 20%) and
        // metric events sampling (hardcoded to 15%). Making it effectively 3% sampling rate
        // for sending events, when this value is set to `100`.
        samplingRate: Float = 100.0f
    ): MethodCalledTelemetry? {
        val sampler: Sampler = RateBasedSampler(samplingRate)
        if (!sampler.sample()) return null

        return when (operationName) {
            METHOD_CALL_OPERATION_NAME -> {
                MethodCalledTelemetry(
                    callerClass,
                    logger
                )
            }

            else -> {
                null
            }
        }
    }
}
