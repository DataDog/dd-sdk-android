/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.okhttp

import com.datadog.android.trace.TraceContextInjection

/**
 * Defines whether the trace context should be injected into all requests or only sampled ones.
 */
@Deprecated(
    "Use com.datadog.android.trace.TraceContextInjection instead.",
    replaceWith = ReplaceWith(
        "TraceContextInjection",
        imports = ["com.datadog.android.trace.TraceContextInjection"]
    )
)
typealias TraceContextInjection = TraceContextInjection
