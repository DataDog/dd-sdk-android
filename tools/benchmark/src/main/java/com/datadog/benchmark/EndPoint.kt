/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.benchmark

/**
 * Enumeration of datadog metrics, traces endpoints.
 */
enum class EndPoint(
    private val metrics: String,
    private val traces: String
) {

    US1(
        metrics = "https://api.datadoghq.com/",
        traces = "https://browser-intake-datadoghq.com/"
    ),
    US3(
        metrics = "https://api.us3.datadoghq.com/",
        traces = "https://browser-intake-us3-datadoghq.com/"
    ),
    US5(
        metrics = "https://api.us5.datadoghq.com/",
        traces = "https://browser-intake-us5-datadoghq.com/"
    ),
    EU1(
        metrics = "https://api.datadoghq.eu/",
        traces = "https://public-trace-http-intake.logs.datadoghq.eu/"
    ),
    AP1(
        metrics = "https://api.ap1.datadoghq.com/",
        traces = "https://browser-intake-ap1-datadoghq.com/"
    ),
    AP2(
        metrics = "https://api.ap2.datadoghq.com/",
        traces = "https://browser-intake-ap2-datadoghq.com/"
    );

    /**
     * Gets the url for submitting metrics.
     */
    fun metricUrl(): String = metrics + "api/v2/series"

    /**
     * Gets the url for submitting traces.
     */
    fun tracesUrl(): String = traces + "api/v2/spans"
}
