/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.okhttp.internal.utils

import com.datadog.android.log.LogAttributes
import com.datadog.opentracing.DDSpanContext
import io.opentracing.Span

internal object SpanSamplingIdProvider {

    fun provideId(span: Span): ULong {
        val context = span.context()
        val sessionId = (context as? DDSpanContext)?.tags?.get(LogAttributes.RUM_SESSION_ID) as? String

        // for a UUID with value aaaaaaaa-bbbb-Mccc-Nddd-1234567890ab
        // we use as the input id the last part : 0x1234567890ab
        val sessionIdToken = sessionId?.split('-')?.lastOrNull()?.toLongOrNull(HEX_RADIX)?.toULong()

        return if (sessionIdToken != null) {
            sessionIdToken
        } else {
            context.toTraceId().toBigIntegerOrNull()?.toLong()?.toULong() ?: 0u
        }
    }
}
