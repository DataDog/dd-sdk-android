/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.trace.impl

import com.datadog.android.trace.api.DatadogTraceId
import com.datadog.trace.api.DDTraceId

class DatadogTraceIdAdapter(private val delegate: DDTraceId) : DatadogTraceId {
    override fun toHexString(): String = delegate.toHexString()
    override fun toLong(): Long = delegate.toLong()

    companion object {
        val ZERO: DatadogTraceId = DatadogTraceIdAdapter(DDTraceId.ZERO)
        fun from(id: Long): DatadogTraceId = DatadogTraceIdAdapter(DDTraceId.from(id))
        fun from(id: String): DatadogTraceId = DatadogTraceIdAdapter(DDTraceId.from(id))
        fun fromHex(id: String): DatadogTraceId = DatadogTraceIdAdapter(DDTraceId.fromHex(id))
    }
}
