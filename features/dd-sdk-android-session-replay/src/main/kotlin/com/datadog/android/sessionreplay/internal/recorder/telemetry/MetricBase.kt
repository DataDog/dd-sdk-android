/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.telemetry

// Base class for metric events
internal interface MetricBase {
    companion object {
        // Basic Metric type key.
        internal const val METRIC_TYPE = "metric_type"
    }
}
