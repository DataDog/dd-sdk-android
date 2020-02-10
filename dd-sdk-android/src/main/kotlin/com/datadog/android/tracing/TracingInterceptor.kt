/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2020 Datadog, Inc.
 */

package com.datadog.android.tracing

import datadog.trace.api.DDTags
import datadog.trace.api.interceptor.MutableSpan
import io.opentracing.Span
import io.opentracing.Tracer
import io.opentracing.propagation.Format.Builtin.TEXT_MAP_INJECT
import io.opentracing.propagation.TextMapInject
import io.opentracing.tag.Tags
import io.opentracing.util.GlobalTracer
import java.io.PrintWriter
import java.io.StringWriter
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/**
 * Provides automatic trace integration for [OkHttpClient] by way of the [Interceptor] system.
 *
 * This interceptor will create a [Span] around the request and fill the request information
 * (url, method, status code, optional error). It will also propagate the span and trace
 * information in the request header to link it with backend spans.
 *
 * If you use multiple Interceptors, make sure that this one is called first.
 *
 * To use:
 * ```
 *   OkHttpClient client = new OkHttpClient.Builder()
 *       .addInterceptor(new TracingInterceptor())
 *       .build();
 * ```
 */
class TracingInterceptor : Interceptor {

    // region Interceptor

    /** @inheritdoc */
    override fun intercept(chain: Interceptor.Chain): Response {
        val tracer = GlobalTracer.get()
        val span = tracer.buildSpan("okhttp.request")
            .start()

        return updateAndProceedSafely(chain, tracer, span)
    }

    // endregion

    // region Internal

    @Suppress("TooGenericExceptionCaught", "ThrowingInternalException")
    private fun updateAndProceedSafely(
        chain: Interceptor.Chain,
        tracer: Tracer,
        span: Span
    ): Response {
        try {
            val updatedRequest = updateRequest(chain.request(), tracer, span)
            val response: Response = try {
                chain.proceed(updatedRequest)
            } catch (e: Throwable) {
                handleThrowable(span, e)
                throw e
            }

            handleResponse(span, response)

            return response
        } finally {
            span.finish()
        }
    }

    private fun handleResponse(span: Span, response: Response) {
        val statusCode = response.code()
        span.setTag(Tags.HTTP_STATUS.key, statusCode)
        if (statusCode >= 400) {
            (span as? MutableSpan)?.isError = true
        }
    }

    private fun handleThrowable(span: Span, error: Throwable) {
        (span as? MutableSpan)?.isError = true
        span.setTag(DDTags.ERROR_MSG, error.message)
        span.setTag(DDTags.ERROR_TYPE, error.javaClass.name)
        val sw = StringWriter()
        error.printStackTrace(PrintWriter(sw))
        span.setTag(DDTags.ERROR_STACK, sw.toString())
    }

    private fun updateRequest(
        request: Request,
        tracer: Tracer,
        span: Span
    ): Request {
        span.setTag(Tags.HTTP_URL.key, request.url().toString())
        span.setTag(Tags.HTTP_METHOD.key, request.method())

        val tracedRequestBuilder = request.newBuilder()
        tracer.inject(
            span.context(),
            TEXT_MAP_INJECT,
            TextMapInject { key, value ->
                tracedRequestBuilder.addHeader(key, value)
            }
        )
        tracedRequestBuilder.addHeader("x-datadog-sampled", "1")

        return tracedRequestBuilder.build()
    }

    // endregion
}
