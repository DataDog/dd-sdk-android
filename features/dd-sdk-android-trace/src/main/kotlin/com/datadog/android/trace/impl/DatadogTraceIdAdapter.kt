/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.trace.impl

import com.datadog.android.trace.api.trace.DatadogTraceId
import com.datadog.trace.api.DDTraceId

internal class DatadogTraceIdAdapter(private val delegate: DDTraceId) : DatadogTraceId, DDTraceId() {
    override fun toLong(): Long = delegate.toLong()
    override fun toString(): String = delegate.toString()
    override fun toHexString(): String = delegate.toHexString()
    override fun toHexStringPadded(size: Int): String = delegate.toHexStringPadded(size)
    override fun toHighOrderLong(): Long = delegate.toHighOrderLong()

    companion object {
        val ZERO: DatadogTraceId = DatadogTraceIdAdapter(DDTraceId.ZERO)
    }
}
