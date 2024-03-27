/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.telemetry

import com.datadog.android.api.InternalLogger
import com.datadog.android.core.sampling.RateBasedSampler
import com.datadog.android.core.sampling.Sampler

// Base class for metric events
internal interface MetricBase {
    fun sendMetric(isSuccessful: Boolean)

    companion object {
        // Basic Metric type key.
        internal const val METRIC_TYPE = "metric_type"

        internal enum class TelemetryMetrics {
            MethodCalled
        }

        fun startMetric(
            logger: InternalLogger,
            callerClass: String,
            metric: TelemetryMetrics,
            samplingRate: Float = 100.0f
        ): MetricBase? {
            val sampler: Sampler = RateBasedSampler(samplingRate)
            if (!sampler.sample()) return null

            return when (metric) {
                TelemetryMetrics.MethodCalled -> {
                    MethodCalledTelemetry(
                        callerClass,
                        logger
                    )
                }
            }
        }
    }
}
