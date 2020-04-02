/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.net

import com.datadog.android.core.internal.net.RequestInterceptor
import com.datadog.android.rum.GlobalRum
import com.datadog.android.rum.RumAttributes
import com.datadog.android.rum.RumResourceKind
import datadog.opentracing.propagation.ExtractedContext
import io.opentracing.propagation.Format
import io.opentracing.propagation.TextMapExtract
import io.opentracing.util.GlobalTracer
import okhttp3.Request
import okhttp3.Response

internal class RumRequestInterceptor : RequestInterceptor {

    // region RequestInterceptor

    override fun transformRequest(request: Request): Request {
        val url = request.url().toString()
        val method = request.method()
        val traceId = extractTraceId(request)

        GlobalRum.get().startResource(
            request,
            method,
            url,
            if (traceId != null) mapOf(RumAttributes.TRACE_ID to traceId) else emptyMap()
        )
        return request
    }

    override fun handleResponse(request: Request, response: Response) {
        val method = request.method()
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
                    (response.body()?.contentLength() ?: 0L)
            )
        )
    }

    override fun handleThrowable(request: Request, throwable: Throwable) {
        val method = request.method()
        GlobalRum.get().stopResourceWithError(
            request,
            "OkHttp error on $method",
            "network",
            throwable
        )
    }

    // endregion

    // region Internal

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
        internal const val HEADER_CT = "Content-Type"

        private val xhrMethods = arrayOf("POST", "PUT", "DELETE")
    }
}
