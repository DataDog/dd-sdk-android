/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.benchmark.sample.observability

interface ObservabilityRumMonitor {
    fun startView(
        key: Any,
        name: String,
        attributes: Map<String, Any?> = emptyMap()
    )

    fun stopView(
        key: Any,
        attributes: Map<String, Any?> = emptyMap()
    )

    fun addAction(
        type: ObservabilityActionType,
        name: String,
        attributes: Map<String, Any?> = emptyMap()
    )

    fun startResource(
        key: String,
        method: ObservabilityResourceMethod,
        url: String,
        attributes: Map<String, Any?> = emptyMap()
    )

    fun stopResource(
        key: String,
        statusCode: Int?,
        size: Long?,
        kind: ObservabilityResourceKind,
        attributes: Map<String, Any?> = emptyMap()
    )

    fun addError(
        message: String,
        source: ObservabilityErrorSource,
        throwable: Throwable?,
        attributes: Map<String, Any?> = emptyMap()
    )
}
