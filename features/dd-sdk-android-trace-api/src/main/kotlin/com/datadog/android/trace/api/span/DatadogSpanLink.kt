/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.trace.api.span

import com.datadog.android.trace.api.trace.DatadogTraceId
import com.datadog.tools.annotation.NoOpImplementation

/**
 * Represents a link to a Datadog span, which contains metadata and identifiers for associating a span
 * with its trace and related details.
 */
@NoOpImplementation
interface DatadogSpanLink {
    /**
     * The unique identifier for a Datadog span.
     */
    val spanId: Long

    /**
     * Indicates whether the Datadog span has been sampled.
     */
    val sampled: Boolean

    /**
     * The unique identifier for a Datadog trace.
     */
    val traceId: DatadogTraceId

    /**
     * Represents a string representation of a specific trace related to the Datadog span.
     */
    val traceStrace: String

    /**
     * A map containing key-value pairs of additional attributes associated with a Datadog span.
     */
    val attributes: Map<String, String>?
}
