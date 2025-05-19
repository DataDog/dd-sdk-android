/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.okhttp.trace

import com.datadog.tools.annotation.NoOpImplementation
import okhttp3.Request
import okhttp3.Response

/**
 * Listener for automatically created [Span] around OkHttp [Request].
 */

@NoOpImplementation
interface TracedRequestListener {

    /**
     * Notifies that a span was automatically created around an OkHttp [Request].
     * You can update the given [Span] (e.g.: add custom tags / baggage items) before it
     * is persisted. Won't be called if [Request] wasn't sampled.
     * @param request the intercepted [Request]
     * @param span the [Span] created around the intercepted [Request]
     * @param response the [Request] response in case of any
     * @param throwable in case an error occurred during the [Request]
     */
    fun onRequestIntercepted(
        request: Request,
        span: Span,
        response: Response?,
        throwable: Throwable?
    )
}
