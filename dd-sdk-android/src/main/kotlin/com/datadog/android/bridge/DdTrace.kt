/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.bridge

/**
 * The entry point to use Datadog's Trace feature.
 */
interface DdTrace {

    /**
     * Start a span, and returns a unique identifier for the span.
     */
    fun startSpan(operation: String, timestamp: Long, context: Map<String, Any?>): String

    /**
     * Finish a started span.
     */
    fun finishSpan(spanId: String, timestamp: Long, context: Map<String, Any?>): Unit
}
