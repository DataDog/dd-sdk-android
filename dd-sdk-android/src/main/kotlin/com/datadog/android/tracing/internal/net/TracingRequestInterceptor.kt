/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.tracing.internal.net

import com.datadog.android.core.internal.net.RequestInterceptor
import com.datadog.android.core.internal.utils.devLogger
import com.datadog.android.tracing.AndroidTracer
import com.datadog.android.tracing.internal.TracesFeature
import datadog.trace.api.DDTags
import datadog.trace.api.interceptor.MutableSpan
import io.opentracing.Span
import io.opentracing.Tracer
import io.opentracing.propagation.Format
import io.opentracing.propagation.TextMapInject
import io.opentracing.tag.Tags
import io.opentracing.util.GlobalTracer
import java.io.PrintWriter
import java.io.StringWriter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference
import okhttp3.Request
import okhttp3.Response

internal class TracingRequestInterceptor(acceptedHosts: List<String> = emptyList()) :
    RequestInterceptor {

    private val hostRegex: Regex

    init {
        val regexFormat =
            if (acceptedHosts.isEmpty()) {
                ""
            } else {
                acceptedHosts.joinToString(separator = "|") { "^(.*\\.)*$it" }
            }
        hostRegex = Regex(regexFormat, RegexOption.IGNORE_CASE)
    }

    private val localTracerReference: AtomicReference<Tracer> = AtomicReference()

    private val startedSpans = ConcurrentHashMap<String, Span>()

    // region RequestInterceptor

    override fun transformRequest(request: Request): Request {
        if (!isWhitelisted(request)) {
            return request
        }

        val tracer = resolveTracer()
        return if (tracer != null) {
            val span = tracer.buildSpan("okhttp.request").start()
            val url = request.url().toString()
            span.setTag(Tags.HTTP_URL.key, url)
            span.setTag(Tags.HTTP_METHOD.key, request.method())
            val transformedRequest = updateRequest(request, tracer, span)
            startedSpans[url] = span
            return transformedRequest
        } else {
            request
        }
    }

    override fun handleResponse(request: Request, response: Response) {
        val url = request.url().toString()
        val statusCode = response.code()
        val span = startedSpans.remove(url)
        if (span != null) {
            span.setTag(Tags.HTTP_STATUS.key, statusCode)
            if (statusCode >= 400) {
                (span as? MutableSpan)?.isError = true
            }
            span.finish()
        }
    }

    override fun handleThrowable(request: Request, throwable: Throwable) {
        val url = request.url().toString()
        val span = startedSpans.remove(url)
        if (span != null) {
            (span as? MutableSpan)?.isError = true
            span.setTag(DDTags.ERROR_MSG, throwable.message)
            span.setTag(DDTags.ERROR_TYPE, throwable.javaClass.name)
            val sw = StringWriter()
            throwable.printStackTrace(PrintWriter(sw))
            span.setTag(DDTags.ERROR_STACK, sw.toString())
            span.finish()
        }
    }

    // endregion

    // region Internal

    @Synchronized
    private fun resolveTracer(): Tracer? {
        return if (!TracesFeature.initialized.get()) {
            devLogger.w(WARNING_NO_TRACER)
            null
        } else if (GlobalTracer.isRegistered()) {
            // clear the localTracer reference if any
            localTracerReference.set(null)
            GlobalTracer.get()
        } else {
            // we check if we already have a local tracer if not we instantiate one
            resolveLocalTracer()
        }
    }

    private fun resolveLocalTracer(): Tracer {
        if (localTracerReference.get() == null) {
            // only register once
            localTracerReference.compareAndSet(null, buildLocalTracer())
            devLogger.w(WARNING_DEFAULT_TRACER)
        }
        return localTracerReference.get()
    }

    internal fun buildLocalTracer(): Tracer {
        return AndroidTracer.Builder().build()
    }

    private fun updateRequest(
        request: Request,
        tracer: Tracer,
        span: Span
    ): Request {
        val tracedRequestBuilder = request.newBuilder()
        tracer.inject(
            span.context(),
            Format.Builtin.TEXT_MAP_INJECT,
            TextMapInject { key, value ->
                tracedRequestBuilder.addHeader(key, value)
            }
        )

        return tracedRequestBuilder.build()
    }

    private fun isWhitelisted(request: Request): Boolean {
        val host = request.url().host()
        return host.matches(hostRegex)
    }

    // endregion

    companion object {
        internal const val WARNING_NO_TRACER =
            "You added a TracingInterceptor to your OkHttpClient, " +
                "but you did not enable the TracesFeature. " +
                "Your requests won't be traced."
        internal const val WARNING_DEFAULT_TRACER =
            "You added a TracingInterceptor to your OkHttpClient, " +
                "but you didn't register any Tracer. " +
                "We automatically created a local tracer for you."
    }
}
