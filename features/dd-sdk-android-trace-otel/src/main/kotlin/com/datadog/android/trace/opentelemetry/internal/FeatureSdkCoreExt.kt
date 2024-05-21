/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.trace.opentelemetry.internal

import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureSdkCore

private const val SPAN_ID_KEY = "span_id"
private const val TRACE_ID_KEY = "trace_id"

internal fun FeatureSdkCore.addActiveTraceToContext(traceId: String, spanId: String) {
    val activeTraceContextName = resolveActiveTraceContextName()
    updateFeatureContext(Feature.TRACING_FEATURE_NAME) {
        it[activeTraceContextName] = mapOf(
            SPAN_ID_KEY to spanId,
            TRACE_ID_KEY to traceId
        )
    }
}

internal fun FeatureSdkCore.removeActiveTraceFromContext() {
    val activeTraceContextName = resolveActiveTraceContextName()
    updateFeatureContext(Feature.TRACING_FEATURE_NAME) {
        it.remove(activeTraceContextName)
    }
}

private fun resolveActiveTraceContextName(): String {
    // scope is thread-local and at the given time for the particular thread it can
    // be only one active scope.
    return "context@${Thread.currentThread().name}"
}
