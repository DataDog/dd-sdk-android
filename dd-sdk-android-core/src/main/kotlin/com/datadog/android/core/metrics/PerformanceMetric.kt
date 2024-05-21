/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.metrics

import com.datadog.android.api.InternalLogger
import com.datadog.android.core.sampling.RateBasedSampler
import com.datadog.android.core.sampling.Sampler

/**
 * Base class for performance metric events.
 */
interface PerformanceMetric {
    /**
     * Stops measuring and sends the performance metric.
     * @param isSuccessful - was the operation being measured completed successfully.
     */
    fun stopAndSend(isSuccessful: Boolean)

    companion object {
        /**
         * Basic Metric type key.
         */
        const val METRIC_TYPE: String = "metric_type"

        /**
         * If sampled in, returns a metric object to start measuring performance.
         * @param logger - an instance of the internal logger.
         * @param callerClass - the name of the class calling this method.
         * @param metric - the performance metric that we want to measure.
         * @param samplingRate - the rate at which this metric should be sampled between 0 and 100.
         * @return a PerformanceMetric object that can later be used to send telemetry, or null if sampled out
         */
        fun startMetric(
            logger: InternalLogger,
            callerClass: String,
            metric: TelemetryMetricType,
            samplingRate: Float = 100.0f
        ): PerformanceMetric? {
            val sampler: Sampler = RateBasedSampler(samplingRate)
            if (!sampler.sample()) return null

            return when (metric) {
                TelemetryMetricType.MethodCalled -> {
                    MethodCalledTelemetry(
                        callerClass,
                        logger
                    )
                }
            }
        }
    }
}
