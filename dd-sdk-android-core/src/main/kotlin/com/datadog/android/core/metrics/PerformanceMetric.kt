/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.metrics

import com.datadog.android.lint.InternalApi

/**
 * Base class for performance metric events.
 */
interface PerformanceMetric {
    /**
     * Stops measuring and sends the performance metric.
     * @param isSuccessful - was the operation being measured completed successfully.
     */
    @InternalApi
    fun stopAndSend(isSuccessful: Boolean)

    companion object {
        /**
         * Basic Metric type key.
         */
        const val METRIC_TYPE: String = "metric_type"
    }
}
