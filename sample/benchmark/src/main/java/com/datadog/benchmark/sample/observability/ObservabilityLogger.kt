/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.benchmark.sample.observability

interface ObservabilityLogger {
    fun log(
        priority: Int,
        message: String,
        throwable: Throwable? = null,
        attributes: Map<String, Any?> = emptyMap()
    )
}
