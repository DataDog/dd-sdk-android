/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.okhttp.internal

import com.datadog.android.api.instrumentation.network.ExtendedRequestInfo
import com.datadog.android.api.instrumentation.network.HttpRequestBody
import com.datadog.android.api.instrumentation.network.HttpRequestInfo
import com.datadog.android.api.instrumentation.network.HttpRequestInfoBuilder
import com.datadog.android.api.instrumentation.network.MutableHttpRequestInfo
import com.datadog.android.lint.InternalApi
import com.datadog.android.rum.internal.net.RumNetworkInstrumentation
import okhttp3.Request
import okio.IOException

@Deprecated(
    "This code will be replaced with RequestExt.kt and OkHttpHttpRequestInfo in the further releases.",
    replaceWith = ReplaceWith(
        "rumNetworkInstrumentation.buildResourceId(" +
            "OkHttpHttpRequestInfo(request).buildResourceId(generateUuid)" +
            ")"
    )
)
internal fun Request.buildResourceId(generateUuid: Boolean) =
    RumNetworkInstrumentation.buildResourceId(
        OkHttpRequestInfo(this),
        generateUuid = generateUuid
    )

/**
 * [HttpRequestInfo] implementation backed by an OkHttp [Request].
 */
internal class OkHttpRequestInfo(internal val originalRequest: Request) :
    HttpRequestInfo,
    ExtendedRequestInfo,
    MutableHttpRequestInfo {

    override val method: String get() = originalRequest.method
    override val url: String get() = originalRequest.url.toString()
    override val headers: Map<String, List<String>> get() = originalRequest.headers.toMultimap()
    override val contentType: String? get() = originalRequest.body?.contentType()?.toString()
    override fun <T> tag(type: Class<out T>): T? = originalRequest.tag(type)
    override fun contentLength(): Long? = try {
        originalRequest.body?.contentLength()
    } catch (@Suppress("SwallowedException") _: IOException) {
        null
    }

    override fun newBuilder() = OkHttpRequestInfoBuilder(originalRequest.newBuilder())
}

/**
 * For internal usage only.
 *
 * [HttpRequestInfoBuilder] implementation for OkHttp requests.
 * Allows modifying request properties such as URL, headers, and tags.
 *
 * @param requestBuilder the OkHttp request builder to modify.
 */
@InternalApi
@Suppress("UnsafeThirdPartyFunctionCall") // OkHttp builder methods are safe
class OkHttpRequestInfoBuilder(private val requestBuilder: Request.Builder) : HttpRequestInfoBuilder {

    /** @inheritdoc */
    override fun setUrl(url: String) = apply { requestBuilder.url(url) }

    /** @inheritdoc */
    override fun addHeader(key: String, vararg values: String) = apply {
        values.forEach { value ->
            requestBuilder.addHeader(key, value)
        }
    }

    /** @inheritdoc */
    override fun setMethod(
        method: String,
        body: HttpRequestBody?
    ) = apply { requestBuilder.method(method, (body as? OkHttpRequestBody)?.body) }

    /** @inheritdoc */
    override fun removeHeader(key: String) = apply { requestBuilder.removeHeader(key) }

    /** @inheritdoc */
    override fun <T> addTag(type: Class<in T>, tag: T?) = apply { requestBuilder.tag(type, tag) }

    /** @inheritdoc */
    override fun build(): HttpRequestInfo = OkHttpRequestInfo(requestBuilder.build())
}
