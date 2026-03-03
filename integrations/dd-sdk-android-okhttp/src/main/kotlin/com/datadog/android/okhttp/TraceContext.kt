/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.okhttp

import com.datadog.android.lint.InternalApi

/**
 * Deprecated. Use com.datadog.android.trace.internal.net.TraceContext instead."
 * The context of a trace to be propagated through the OkHttp requests for Datadog tracing.
 */
@InternalApi
@Deprecated(
    "Use com.datadog.android.trace.internal.net.TraceContext instead.",
    replaceWith = ReplaceWith(
        "TraceContextInjection",
        imports = ["com.datadog.android.trace.internal.net.TraceContext"]
    )
)
// TODO RUM-13454 Remove with SDK v4 release.
data class TraceContext(
    /**
     * The trace id.
     */
    val traceId: String,
    /**
     * The span id.
     */
    val spanId: String,
    /**
     * The sampling priority.
     */
    val samplingPriority: Int
)
