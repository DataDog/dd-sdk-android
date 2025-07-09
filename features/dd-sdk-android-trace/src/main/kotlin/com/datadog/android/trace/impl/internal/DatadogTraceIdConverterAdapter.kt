/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.trace.impl.internal

import com.datadog.android.trace.api.trace.DatadogTraceId
import com.datadog.android.trace.api.trace.DatadogTraceIdConverter
import com.datadog.trace.api.DDTraceId

internal object DatadogTraceIdConverterAdapter : DatadogTraceIdConverter {
    override fun zero(): DatadogTraceId = DatadogTraceIdAdapter.ZERO
    override fun from(id: Long): DatadogTraceId = DatadogTraceIdAdapter(DDTraceId.from(id))
    override fun from(id: String): DatadogTraceId = DatadogTraceIdAdapter(DDTraceId.from(id))
    override fun fromHex(id: String): DatadogTraceId = DatadogTraceIdAdapter(DDTraceId.fromHex(id))
    override fun toLong(traceId: DatadogTraceId): Long = (traceId as DatadogTraceIdAdapter).toLong()
    override fun toHexString(traceId: DatadogTraceId): String = (traceId as DatadogTraceIdAdapter).toHexString()
}
