/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.trace.impl

import com.datadog.android.trace.api.trace.DatadogTraceId
import com.datadog.android.trace.api.trace.DatadogTraceIdFactory
import com.datadog.trace.api.DDTraceId

internal object DatadogTraceIdFactoryAdapter : DatadogTraceIdFactory {
    override fun from(id: Long): DatadogTraceId = DatadogTraceIdAdapter(DDTraceId.from(id))
    override fun from(id: String): DatadogTraceId = DatadogTraceIdAdapter(DDTraceId.from(id))
    override fun fromHex(id: String): DatadogTraceId = DatadogTraceIdAdapter(DDTraceId.fromHex(id))
}
