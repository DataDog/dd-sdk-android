/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android

import com.datadog.android.core.internal.net.CombinedInterceptor
import com.datadog.android.rum.RumInterceptor
import com.datadog.android.rum.internal.net.RumRequestInterceptor
import com.datadog.android.tracing.TracingInterceptor
import com.datadog.android.tracing.internal.net.TracingRequestInterceptor
import okhttp3.Interceptor
import okhttp3.OkHttpClient

/**
 * Provides automatic integration for [OkHttpClient] by way of the [Interceptor] system.
 *
 * This interceptor will combine the effects of the [TracingInterceptor] and the
 * [RumInterceptor].
 *
 * To use:
 * ```
 *   OkHttpClient client = new OkHttpClient.Builder()
 *       .addInterceptor(new DatadogInterceptor(listOf("yourdomain.com")))
 *       .build();
 * ```
 * @param tracedHosts a list of all the hosts that you want to be automatically tracked
 * by our APM [TracingInterceptor]. If no host provided the interceptor won't trace
 * any OkHttpRequest, nor propagate tracing information to the backend.
 * Please note that the host constraint will only be applied on the TracingInterceptor and we will
 * continue to dispatch RUM Resource events for each request without applying any host filtering.
 *
 */
class DatadogInterceptor(tracedHosts: List<String>) :
    Interceptor by CombinedInterceptor(
        listOf(
            TracingRequestInterceptor(tracedHosts),
            RumRequestInterceptor()
        )
    )
