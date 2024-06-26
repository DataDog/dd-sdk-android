/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.okhttp

/**
 * Defines whether the trace context should be injected into all requests or only sampled ones.
 */
enum class TraceContextInjection {
    /**
     * Injects trace context into all requests irrespective of the sampling decision.
     */
    All,

    /**
     * Injects trace context only into sampled requests.
     */
    Sampled
}
