/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.benchmark.internal.model

/**
 * This enumeration corresponds to Datadog submitting metrics API,
 * The type of metric. The available types are 0 (unspecified),
 * 1 (count), 2 (rate), and 3 (gauge). Allowed enum values: 0,1,2,3.
 *
 * See https://docs.datadoghq.com/api/latest/metrics/#submit-metrics
 */
@Suppress("MagicNumber")
internal enum class MetricType(val value: Int) {

    UNSPECIFIED(0),

    COUNT(1),

    RATE(2),

    GAUGE(3)
}
