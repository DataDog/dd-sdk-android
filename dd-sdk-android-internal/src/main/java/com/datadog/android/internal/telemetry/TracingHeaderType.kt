/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.internal.telemetry

@Suppress("UndocumentedPublicClass")
enum class TracingHeaderType {
    /**
     * Datadog's [`x-datadog-*` header](https://docs.datadoghq.com/real_user_monitoring/connect_rum_and_traces/?tab=browserrum#how-are-rum-resources-linked-to-traces).
     */
    DATADOG,

    /**
     * Open Telemetry B3 [Single header](https://github.com/openzipkin/b3-propagation#single-header).
     */
    B3,

    /**
     * Open Telemetry B3 [Multiple headers](https://github.com/openzipkin/b3-propagation#multiple-headers).
     */
    B3MULTI,

    /**
     * W3C [Trace Context header](https://www.w3.org/TR/trace-context/#tracestate-header).
     */
    TRACECONTEXT
}
