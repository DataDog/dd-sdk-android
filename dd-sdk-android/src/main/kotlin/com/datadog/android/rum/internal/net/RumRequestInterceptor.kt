/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.net

import com.datadog.android.core.internal.net.RequestInterceptor
import com.datadog.android.core.internal.net.identifyRequest
import com.datadog.android.rum.GlobalRum
import com.datadog.android.rum.RumAttributes
import com.datadog.android.rum.RumErrorSource
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
        val requestId = identifyRequest(request)
        val requestAttributes = getRequestAttributes(request)
        GlobalRum.get().startResource(
            requestId,
            method,
            url,
            requestAttributes
        )
        return request
    }

    override fun handleResponse(request: Request, response: Response) {
        val requestId = identifyRequest(request)
        val method = request.method()
        val mimeType = response.header(HEADER_CT)

        val url = request.url().toString()
        val kind = when {
            method in xhrMethods -> RumResourceKind.XHR
            mimeType == null -> RumResourceKind.UNKNOWN
            else -> RumResourceKind.fromMimeType(mimeType)
        }

        GlobalRum.get().stopResource(
            requestId,
            response.code(),
            getBodyLength(response),
            kind,
            emptyMap()
        )
        handleErrorStatus(method, url, response.code())
    }

    override fun handleThrowable(request: Request, throwable: Throwable) {
        val requestId = identifyRequest(request)
        val method = request.method()
        val url = request.url().toString()
        GlobalRum.get().stopResourceWithError(
            requestId,
            null,
            ERROR_MSG_FORMAT.format(method, url),
            RumErrorSource.NETWORK,
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

    private fun handleErrorStatus(method: String, url: String, statusCode: Int) {
        if (statusCode >= 400) {
            GlobalRum.get().addError(
                ERROR_MSG_FORMAT.format(method, url),
                RumErrorSource.NETWORK,
                null,
                mapOf(
                    RumAttributes.ERROR_RESOURCE_URL to url,
                    RumAttributes.ERROR_RESOURCE_METHOD to method,
                    RumAttributes.ERROR_RESOURCE_STATUS_CODE to statusCode
                )
            )
        }
    }

    private fun getRequestAttributes(request: Request): Map<String, Any?> {
        val traceId = extractTraceId(request)
        val requestBodyLength = request.body()?.contentLength()
        return if (traceId == null && requestBodyLength == null) {
            emptyMap()
        } else {
            val attributes = mutableMapOf<String, Any>()
            traceId?.let { attributes[RumAttributes.TRACE_ID] = it }
            requestBodyLength?.let { attributes[RumAttributes.NETWORK_BYTES_READ] = it }
            attributes
        }
    }

    private fun getBodyLength(response: Response): Long? {
        val body = response.peekBody(MAX_BODY_PEEK)
        val contentLength = body.contentLength()
        return if (contentLength == 0L) null else contentLength
    }

    // endregion

    companion object {
        internal const val HEADER_CT = "Content-Type"

        internal const val ERROR_MSG_FORMAT = "OkHttp request error %s %s"

        private val xhrMethods = arrayOf("POST", "PUT", "DELETE")

        // We need to limit this value as the body will be loaded in memory
        private const val MAX_BODY_PEEK: Long = 32 * 1024L * 1024L
    }
}
