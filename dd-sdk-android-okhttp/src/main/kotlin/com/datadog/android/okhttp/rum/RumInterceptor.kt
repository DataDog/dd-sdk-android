/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.okhttp.rum

import androidx.annotation.FloatRange
import com.datadog.android.core.configuration.Configuration
import com.datadog.android.okhttp.DatadogInterceptor
import com.datadog.android.rum.RumFeature
import com.datadog.android.rum.RumResourceAttributesProvider
import com.datadog.android.rum.tracking.ViewTrackingStrategy
import com.datadog.android.v2.api.SdkCore
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Provides automatic RUM integration for [OkHttpClient] by way of the [Interceptor] system.
 *
 * This interceptor will log the request as a RUM Resource, and fill the request information
 * (url, method, status code, optional error). Note that RUM Resources are only tracked when a
 * view is active. You can use one of the existing [ViewTrackingStrategy] when configuring the SDK
 * (see [RumFeature.Builder.useViewTrackingStrategy]) or start a view manually (see
 * [RumMonitor.startView]).
 *
 * If you use multiple Interceptors, make sure that this one is called first.
 * If you also want to trace network request, use the
 * [DatadogInterceptor] instead, which combines the RUM and APM integrations.
 *
 * To use:
 * ```
 *   OkHttpClient client = new OkHttpClient.Builder()
 *       .addInterceptor(new RumInterceptor(sdkCore))
 *       .build();
 * ```
 *
 * @param sdkCore SDK instance to bind to.
 * @param firstPartyHosts the list of first party hosts.
 * Requests made to a URL with any one of these hosts (or any subdomain) will:
 * - be considered a first party RUM Resource and categorised as such in your RUM dashboard;
 * - be wrapped in a Span and have trace id injected to get a full flame-graph in APM.
 * If no host provided (via this argument or global configuration [Configuration.Builder.setFirstPartyHosts])
 * the interceptor won't trace any OkHttp [Request], nor propagate tracing
 * information to the backend, but RUM Resource events will still be sent for each request.
 * @param rumResourceAttributesProvider which listens on the intercepted [okhttp3.Request]
 * and offers the possibility to add custom attributes to the RUM resource events.
 * @param traceSamplingRate the sampling rate for APM traces created for auto-instrumented
 * requests. It must be a value between `0.0` and `100.0`. A value of `0.0` means no trace will
 * be kept, `100.0` means all traces will be kept (default value is `20.0`).
 */
class RumInterceptor(
    sdkCore: SdkCore,
    firstPartyHosts: List<String> = emptyList(),
    rumResourceAttributesProvider: RumResourceAttributesProvider =
        NoOpRumResourceAttributesProvider(),
    @FloatRange(from = 0.0, to = 100.0) traceSamplingRate: Float = DEFAULT_TRACE_SAMPLING_RATE
) : DatadogInterceptor(
    sdkCore,
    firstPartyHosts,
    rumResourceAttributesProvider = rumResourceAttributesProvider,
    traceSamplingRate = traceSamplingRate
)
