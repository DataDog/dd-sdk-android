/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.sdk.integration.network.wrappers.okhttp

import com.datadog.android.api.instrumentation.network.HttpRequestInfo
import com.datadog.android.api.instrumentation.network.HttpResponseInfo
import com.datadog.android.trace.NetworkTracedRequestListener
import com.datadog.android.trace.api.span.DatadogSpan
import java.util.concurrent.CopyOnWriteArrayList

internal class InstrumentedOkHttpSpansCollector : NetworkTracedRequestListener {
    val spans = CopyOnWriteArrayList<DatadogSpan>()
    override fun onRequestIntercepted(
        request: HttpRequestInfo,
        span: DatadogSpan,
        response: HttpResponseInfo?,
        throwable: Throwable?
    ) {
        spans.add(span)
    }
}
