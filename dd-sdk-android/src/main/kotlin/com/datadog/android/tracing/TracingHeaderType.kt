/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.tracing

/**
 * Defines the list of tracing header types that can be injected into http requests.
 * @property headerType Explicit header type property introduced in order to have a consistent value
 * in case if enum values are renamed.
 */
enum class TracingHeaderType(val headerType: String) {
    /**
     * Datadog's [`x-datadog-*` header](https://docs.datadoghq.com/real_user_monitoring/connect_rum_and_traces/?tab=browserrum#how-are-rum-resources-linked-to-traces).
     */
    DATADOG("DATADOG"),

    /**
     * Open Telemetry B3 [Single header](https://github.com/openzipkin/b3-propagation#single-header).
     */
    B3("B3"),

    /**
     * Open Telemetry B3 [Multiple headers](https://github.com/openzipkin/b3-propagation#multiple-headers).
     */
    B3MULTI("B3MULTI"),

    /**
     * W3C [Trace Context header](https://www.w3.org/TR/trace-context/#tracestate-header).
     */
    TRACECONTEXT("TRACECONTEXT")
}
