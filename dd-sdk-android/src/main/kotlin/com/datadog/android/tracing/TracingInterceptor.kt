/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.tracing

import com.datadog.android.DatadogInterceptor
import com.datadog.android.core.internal.net.CombinedInterceptor
import com.datadog.android.tracing.internal.net.TracingRequestInterceptor
import io.opentracing.Span
import okhttp3.Interceptor
import okhttp3.OkHttpClient

/**
 * Provides automatic trace integration for [OkHttpClient] by way of the [Interceptor] system.
 *
 * This interceptor will create a [Span] around the request and fill the request information
 * (url, method, status code, optional error). It will also propagate the span and trace
 * information in the request header to link it with backend spans.
 *
 * If you use multiple Interceptors, make sure that this one is called first.
 * If you also want to track network requests as RUM Resources, use the
 * [DatadogInterceptor] instead, which combines the RUM and APM integrations.
 *
 * To use:
 * ```
 *   OkHttpClient client = new OkHttpClient.Builder()
 *       .addInterceptor(new TracingInterceptor(listOf("yourdomain.com")))
 *       .build();
 * ```
 *
 * @param tracedHosts a list of all the hosts that you want to be automatically tracked
 * by this interceptor. If no host is provided the interceptor won't trace any OkHttpRequest,
 * nor propagate tracing information to the backend.
 * @param tracedRequestListener a listener for automatically created [Span]s
 *
 */
class TracingInterceptor @JvmOverloads constructor(
    tracedHosts: List<String>,
    tracedRequestListener: TracedRequestListener = NoOpTracedRequestListener()
) :
    Interceptor by CombinedInterceptor(
        TracingRequestInterceptor(
            tracedRequestListener,
            tracedHosts
        )
    )
