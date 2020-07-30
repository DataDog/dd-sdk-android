/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android

import com.datadog.android.core.internal.net.CombinedInterceptor
import com.datadog.android.rum.RumInterceptor
import com.datadog.android.rum.RumMonitor
import com.datadog.android.rum.internal.net.RumRequestInterceptor
import com.datadog.android.rum.tracking.ViewTrackingStrategy
import com.datadog.android.tracing.NoOpTracedRequestListener
import com.datadog.android.tracing.TracedRequestListener
import com.datadog.android.tracing.TracingInterceptor
import com.datadog.android.tracing.internal.net.TracingRequestInterceptor
import io.opentracing.Span
import okhttp3.Interceptor
import okhttp3.OkHttpClient

/**
 * Provides automatic integration for [OkHttpClient] by way of the [Interceptor] system.
 *
 * This interceptor will combine the effects of the [TracingInterceptor] and the
 * [RumInterceptor].
 *
 * From [RumInterceptor]: this interceptor will log the request as a RUM Resource, and fill the
 * request information (url, method, status code, optional error). Note that RUM Resources are only
 * tracked when a view is active. You can use one of the existing [ViewTrackingStrategy] when
 * configuring the SDK (see [DatadogConfig.Builder.useViewTrackingStrategy]) or start a view
 * manually (see [RumMonitor.startView]).
 *
 * From [TracingInterceptor]: This interceptor will create a [Span] around the request and fill the
 * request information (url, method, status code, optional error). It will also propagate the span
 * and trace information in the request header to link it with backend spans.
 *
 * To use:
 * ```
 *   OkHttpClient client = new OkHttpClient.Builder()
 *       .addInterceptor(new DatadogInterceptor(listOf("yourdomain.com")))
 *       .build();
 * ```
 *
 * @param tracedHosts a list of all the hosts that you want to be automatically tracked
 * by our APM [TracingInterceptor]. If no host provided the interceptor won't trace
 * any OkHttpRequest, nor propagate tracing information to the backend.
 * Please note that the host constraint will only be applied on the TracingInterceptor and we will
 * continue to dispatch RUM Resource events for each request without applying any host filtering.
 * @param tracedRequestListener which listens on the intercepted [okhttp3.Request] and offers
 * the possibility to modify the created [io.opentracing.Span].
 *
 */

class DatadogInterceptor @JvmOverloads constructor(
    tracedHosts: List<String>,
    tracedRequestListener: TracedRequestListener = NoOpTracedRequestListener()
) :
    Interceptor by CombinedInterceptor(
        listOf(
            TracingRequestInterceptor(tracedRequestListener, tracedHosts),
            RumRequestInterceptor()
        )
    )
