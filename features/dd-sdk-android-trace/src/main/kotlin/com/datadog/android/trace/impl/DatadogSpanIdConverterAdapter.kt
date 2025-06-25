/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.trace.impl

import com.datadog.android.trace.api.DatadogSpanIdConverter
import com.datadog.trace.api.DDSpanId

object DatadogSpanIdConverterAdapter : DatadogSpanIdConverter {
    const val ZERO = DDSpanId.ZERO

    override fun from(s: String?): Long = DDSpanId.from(s)
    override fun fromHex(s: String?): Long = DDSpanId.fromHex(s)
    override fun fromHex(s: String?, start: Int, len: Int, lowerCaseOnly: Boolean) =
        DDSpanId.fromHex(s, start, len, lowerCaseOnly)

    override fun toString(id: Long): String = DDSpanId.toString(id)
    override fun toHexString(id: Long): String = DDSpanId.toHexString(id)
    override fun toHexStringPadded(id: Long): String = DDSpanId.toHexStringPadded(id)
}
