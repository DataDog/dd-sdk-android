/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.cronet.internal

import com.datadog.android.api.instrumentation.network.ExtendedRequestInfo
import com.datadog.android.api.instrumentation.network.HttpRequestInfo
import com.datadog.android.api.instrumentation.network.HttpRequestInfoModifier
import com.datadog.android.core.internal.net.HttpSpec
import com.datadog.android.trace.internal.net.RequestTraceState
import org.chromium.net.UploadDataProvider
import org.chromium.net.UrlRequest
import java.io.IOException

internal data class CronetHttpRequestInfo(
    private val requestContext: DatadogCronetRequestContext
) : HttpRequestInfo, ExtendedRequestInfo {

    override val url: String
        get() = requestContext.url

    override val method: String
        get() = requestContext.method

    private val annotations: List<Any>
        get() = requestContext.annotations

    override val headers: Map<String, List<String>>
        get() = requestContext.headers

    override val contentType: String?
        get() = headers[HttpSpec.Headers.CONTENT_TYPE]?.firstOrNull()

    @Suppress("UNCHECKED_CAST")
    override fun <T> tag(type: Class<out T>): T? = annotations.firstOrNull { type.isInstanceOf(it) } as? T

    override fun contentLength(): Long? = headers[HttpSpec.Headers.CONTENT_LENGTH]
        ?.firstOrNull()?.toLongOrNull()
        ?: requestContext.uploadDataProvider?.contentLength()

    override fun modify(): CronetHttpRequestInfoModifier =
        CronetHttpRequestInfoModifier(requestContext.copy())

    /**
     * Builds the underlying delegate UrlRequest.
     * This applies all accumulated headers to the delegate and calls delegate.build().
     * Should be called from DatadogUrlRequest.start() after tracing headers have been added.
     */
    internal fun buildCronetRequest(
        tracingState: RequestTraceState?
    ): UrlRequest = requestContext.buildCronetRequest(this, tracingState)

    // We have to override toString in order to prevent StackOverflowException,
    // as annotations could hold a link to this CronetHttpRequestInfo instance itself.
    override fun toString() = "CronetHttpRequestInfo(" +
        "url='$url', " +
        "method='$method', " +
        "headers=$headers, " +
        "provider=${requestContext.uploadDataProvider}, " +
        "annotations=${annotations.size}" +
        ")"

    private fun UploadDataProvider.contentLength(): Long? = try {
        length.takeIf { it >= 0 }
    } catch (@Suppress("SwallowedException") _: IOException) {
        null
    }

    private fun Class<*>.isInstanceOf(value: Any) = when (this) {
        Boolean::class.javaPrimitiveType -> value is Boolean
        Byte::class.javaPrimitiveType -> value is Byte
        Char::class.javaPrimitiveType -> value is Char
        Short::class.javaPrimitiveType -> value is Short
        Int::class.javaPrimitiveType -> value is Int
        Long::class.javaPrimitiveType -> value is Long
        Float::class.javaPrimitiveType -> value is Float
        Double::class.javaPrimitiveType -> value is Double
        else -> this.isInstance(value)
    }
}

internal class CronetHttpRequestInfoModifier(
    private val requestContext: DatadogCronetRequestContext
) : HttpRequestInfoModifier {

    override fun setUrl(url: String): HttpRequestInfoModifier = apply {
        requestContext.url = url
    }

    override fun addHeader(key: String, vararg values: String) = apply {
        requestContext.addHeader(key, *values)
    }

    override fun removeHeader(key: String) = apply {
        // Cronet doesn't support removing headers, but we track it in requestContext
        requestContext.removeHeader(key)
    }

    override fun <T> addTag(type: Class<in T>, tag: T?) = apply {
        requestContext.setTag(type, tag)
    }

    override fun result() = requestContext.buildRequestInfo()
}
