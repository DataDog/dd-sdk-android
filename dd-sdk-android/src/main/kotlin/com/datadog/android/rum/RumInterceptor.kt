/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum

import com.datadog.android.DatadogConfig
import com.datadog.android.DatadogInterceptor
import com.datadog.android.rum.tracking.ViewTrackingStrategy
import okhttp3.Interceptor
import okhttp3.OkHttpClient

/**
 * Provides automatic RUM integration for [OkHttpClient] by way of the [Interceptor] system.
 *
 * This interceptor will log the request as a RUM Resource, and fill the request information
 * (url, method, status code, optional error). Note that RUM Resources are only tracked when a
 * view is active. You can use one of the existing [ViewTrackingStrategy] when configuring the SDK
 * (see [DatadogConfig.Builder.useViewTrackingStrategy]) or start a view manually (see
 * [RumMonitor.startView]).
 *
 * If you use multiple Interceptors, make sure that this one is called first.
 * If you also want to trace network request, use the
 * [DatadogInterceptor] instead, which combines the RUM and APM integrations.
 *
 * To use:
 * ```
 *   OkHttpClient client = new OkHttpClient.Builder()
 *       .addInterceptor(new RumInterceptor())
 *       .build();
 * ```
 */
class RumInterceptor : DatadogInterceptor(listOf(""))
