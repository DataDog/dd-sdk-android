/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum

import com.datadog.android.core.internal.net.CombinedInterceptor
import com.datadog.android.rum.internal.net.RumRequestInterceptor
import com.datadog.android.tracing.TracingInterceptor
import okhttp3.Interceptor
import okhttp3.OkHttpClient

/**
 * Provides automatic RUM integration for [OkHttpClient] by way of the [Interceptor] system.
 *
 * This interceptor will log the request as a RUM resource fill the request information
 * (url, method, status code, optional error).
 *
 * If you use multiple Interceptors, make sure that this one is called first.
 * If you also use the [TracingInterceptor], make sure it is called before this one.
 *
 * To use:
 * ```
 *   OkHttpClient client = new OkHttpClient.Builder()
 *       // Optional APM Traces integration
 *       .addInterceptor(new TracingInterceptor())
 *       .addInterceptor(new RumInterceptor())
 *       .build();
 * ```
 */
class RumInterceptor :
    Interceptor by CombinedInterceptor(RumRequestInterceptor())
