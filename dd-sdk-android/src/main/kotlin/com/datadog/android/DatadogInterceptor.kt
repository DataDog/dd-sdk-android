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
 *       .addInterceptor(new DatadogInterceptor())
 *       .build();
 * ```
 */
class DatadogInterceptor :
    Interceptor by CombinedInterceptor(
        listOf(
            TracingRequestInterceptor(),
            RumRequestInterceptor()
        )
    )
