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
import com.datadog.android.rum.resource.ResourceId
import okhttp3.Request
import okio.IOException
import java.util.UUID

@Deprecated(
    "This code will be replaced with RequestExt.kt and OkHttpHttpRequestInfo in the further releases.",
    replaceWith = ReplaceWith(
        "rumNetworkInstrumentation.buildResourceId(" +
            "OkHttpHttpRequestInfo(request).buildResourceId(generateUuid)" +
            ")"
    )
)
internal fun Request.buildResourceId(generateUuid: Boolean): ResourceId {
    val uuid = tag(UUID::class.java) ?: (if (generateUuid) UUID.randomUUID() else null)

    @Suppress("DEPRECATION")
    val key = identifyRequest(this)

    return ResourceId(key, uuid?.toString())
}

@Deprecated(
    "This code will be replaced with RequestExt.kt and OkHttpHttpRequestInfo in the further releases.",
    replaceWith = ReplaceWith(
        "rumNetworkInstrumentation.buildResourceId(" +
            "OkHttpHttpRequestInfo(request).buildResourceId(generateUuid)" +
            ")"
    )
)
internal fun identifyRequest(request: Request): String {
    val method = request.method
    val url = request.url
    val body = request.body
    return if (body == null) {
        "$method•$url"
    } else {
        val contentLength = try {
            body.contentLength()
        } catch (@Suppress("SwallowedException") ioe: java.io.IOException) {
            0
        }
        val contentType = body.contentType()
        // TODO RUM-648 It is possible that if requests are say GZIPed (as an example), or maybe
        //  streaming case (?), they all will have contentLength = -1, so if they target the same URL
        //  they are going to have same identifier, messing up reporting.
        //  -1 is valid return value for contentLength() call according to the docs and stands
        //  for "unknown" case.
        //  We need to have a more precise identification.
        if (contentType != null || contentLength != 0L) {
            "$method•$url•$contentLength•$contentType"
        } else {
            "$method•$url"
        }
    }
}

internal class OkHttpRequestInfo(internal val request: Request) :
    HttpRequestInfo,
    ExtendedRequestInfo,
    MutableHttpRequestInfo {

    override val method: String get() = request.method
    override val url: String get() = request.url.toString()
    override val headers: Map<String, List<String>> get() = request.headers.toMultimap()
    override val contentType: String? get() = request.body?.contentType()?.toString()
    override fun <T> tag(type: Class<out T>): T? = request.tag(type)
    override fun contentLength(): Long? = try {
        request.body?.contentLength()
    } catch (@Suppress("SwallowedException") _: IOException) {
        null
    }

    override fun newBuilder() = OkHttpRequestInfoBuilder(request.newBuilder())
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

    override fun setUrl(url: String) = apply { requestBuilder.url(url) }

    override fun addHeader(key: String, vararg values: String) = apply {
        values.forEach { value ->
            requestBuilder.addHeader(key, value)
        }
    }

    override fun setMethod(
        method: String,
        body: HttpRequestBody?
    ) = apply { requestBuilder.method(method, (body as? OkHttpRequestBody)?.body) }

    override fun removeHeader(key: String) = apply { requestBuilder.removeHeader(key) }

    override fun <T> addTag(type: Class<in T>, tag: T?) = apply { requestBuilder.tag(type, tag) }

    override fun build(): HttpRequestInfo = OkHttpRequestInfo(requestBuilder.build())
}
