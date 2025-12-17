/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.okhttp.internal

import com.datadog.android.api.instrumentation.network.ExtendedRequestInfo
import com.datadog.android.api.instrumentation.network.HttpRequestInfo
import com.datadog.android.rum.internal.net.RumResourceInstrumentation
import okhttp3.Request
import okio.IOException

@Deprecated(
    "This code will be replaced with RequestExt.kt and OkHttpHttpRequestInfo in the further releases.",
    replaceWith = ReplaceWith(
        "rumResourceInstrumentation.buildResourceId(" +
            "OkHttpHttpRequestInfo(request).buildResourceId(generateUuid)" +
            ")"
    )
)
internal fun Request.buildResourceId(generateUuid: Boolean) =
    RumResourceInstrumentation.buildResourceId(
        OkHttpHttpRequestInfo(this),
        generateUuid = generateUuid
    )

internal class OkHttpHttpRequestInfo(internal val request: Request) : HttpRequestInfo, ExtendedRequestInfo {

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
}
