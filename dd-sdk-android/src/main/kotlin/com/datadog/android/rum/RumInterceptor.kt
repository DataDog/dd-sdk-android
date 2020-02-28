/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2020 Datadog, Inc.
 */

package com.datadog.android.rum

import com.datadog.android.tracing.TracingInterceptor
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response

/**
 * Provides automatic RUM integration for [OkHttpClient] by way of the [Interceptor] system.
 *
 * This interceptor will log the request as a RUM resource fill the request information
 * (url, method, status code, optional error).
 *
 * If you use multiple Interceptors, make sure that this one is called first.
 * If you also use the [TracingInterceptor], make it is called after this one.
 *
 * To use:
 * ```
 *   OkHttpClient client = new OkHttpClient.Builder()
 *       .addInterceptor(new RumInterceptor())
 *       // Optional APM Traces integration
 *       .addInterceptor(new TracingInterceptor())
 *       .build();
 * ```
 */
class RumInterceptor : Interceptor {

    // region Interceptor

    /** @inheritdoc */
    @Suppress("TooGenericExceptionCaught")
    override fun intercept(chain: Interceptor.Chain): Response {

        val request = chain.request()
        val url = request.url().toString()
        val method = request.method()

        GlobalRum.get().startResource(request, url, mapOf("http.method" to method))

        try {
            val response = chain.proceed(request)
            GlobalRum.get().stopResource(
                request,
                response.header(HEADER_CT),
                mapOf("http.status_code" to response.code())
            )
            return response
        } catch (t: Throwable) {
            GlobalRum.get().stopResourceWithError(request, "OkHttp error on $method", "network", t)
            throw t
        }
    }

    // endregion

    companion object {
        private const val HEADER_CT = "Content-Type"
    }
}
