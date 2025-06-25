/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.trace.api.span

import com.datadog.android.trace.api.DatadogTraceId

interface DatadogSpan {
    var isError: Boolean?
    val isRootSpan: Boolean
    val samplingPriority: Int?
    val traceId: DatadogTraceId
    val parentSpanId: Long?
    var resourceName: String?
    var serviceName: String
    var operationName: String
    val durationNano: Long
    val startTime: Long

    fun context(): DatadogSpanContext
    fun finish()
    fun drop()

    fun setTag(tag: String?, value: String?)
    fun setTag(tag: String?, value: Boolean)
    fun setTag(tag: String?, value: Number?)
}
