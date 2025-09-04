/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.trace.internal

/**
 * Common attributes used by spans.
 */
@Suppress("PackageNameVisibility")
object SpanAttributes {
    /**
     *  Internal attribute representing [DatadogContext] captured at the span start.
     */
    const val DATADOG_INITIAL_CONTEXT: String = "_dd.datadog_initial_context"
}
