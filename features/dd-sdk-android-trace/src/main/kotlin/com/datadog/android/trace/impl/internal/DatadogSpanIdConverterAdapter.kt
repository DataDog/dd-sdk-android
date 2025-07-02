/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.trace.impl.internal

import com.datadog.android.trace.api.span.DatadogSpanIdConverter
import com.datadog.trace.api.DDSpanId

internal object DatadogSpanIdConverterAdapter : DatadogSpanIdConverter {
    override fun fromHex(spanId: String): Long = DDSpanId.fromHex(spanId)

    override fun toHexStringPadded(spanId: Long): String = DDSpanId.toHexStringPadded(spanId)
}
