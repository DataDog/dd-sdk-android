/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2020 Datadog, Inc.
 */

package com.datadog.android.rum

import com.datadog.android.tracing.TracingInterceptor
import datadog.opentracing.propagation.ExtractedContext
import io.opentracing.propagation.Format
import io.opentracing.propagation.TextMapExtract
import io.opentracing.util.GlobalTracer
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

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
class RumInterceptor : Interceptor {

    // region Interceptor

    /** @inheritdoc */
    @Suppress("TooGenericExceptionCaught")
    override fun intercept(chain: Interceptor.Chain): Response {

        val request = chain.request()
        val url = request.url().toString()
        val method = request.method()
        val traceId = extractTraceId(request)

        GlobalRum.get().startResource(
            request,
            method,
            url,
            if (traceId != null) mapOf(RumAttributes.TRACE_ID to traceId) else emptyMap()
        )

        try {
            val response = chain.proceed(request)
            val mimeType = response.header(HEADER_CT)
            val kind = when {
                method in xhrMethods -> RumResourceKind.XHR
                mimeType == null -> RumResourceKind.UNKNOWN
                else -> RumResourceKind.fromMimeType(mimeType)
            }
            GlobalRum.get().stopResource(
                request,
                kind,
                mapOf(
                    RumAttributes.HTTP_STATUS_CODE to response.code(),
                    RumAttributes.NETWORK_BYTES_WRITTEN to
                        (response.body()?.contentLength() ?: 0)
                )
            )
            return response
        } catch (t: Throwable) {
            GlobalRum.get().stopResourceWithError(request, "OkHttp error on $method", "network", t)
            throw t
        }
    }

    private fun extractTraceId(request: Request): String? {
        val extractedContext = GlobalTracer.get()
            .extract(
                Format.Builtin.TEXT_MAP_EXTRACT,
                TextMapExtract {
                    request.headers()
                        .toMultimap()
                        .map { it.key to it.value.joinToString(";") }
                        .toMap()
                        .toMutableMap()
                        .iterator()
                }
            )

        val traceId = (extractedContext as? ExtractedContext)?.traceId?.toString()
        return traceId
    }

    // endregion

    companion object {
        private const val HEADER_CT = "Content-Type"

        private val xhrMethods = arrayOf("POST", "PUT", "DELETE")
    }
}
