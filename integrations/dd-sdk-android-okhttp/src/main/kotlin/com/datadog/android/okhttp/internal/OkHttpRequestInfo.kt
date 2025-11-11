/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.okhttp.internal

import com.datadog.android.api.instrumentation.network.RequestInfo
import com.datadog.android.rum.resource.buildResourceId
import okhttp3.Request

internal fun Request.buildResourceId(generateUuid: Boolean) =
    OkHttpRequestInfo(this).buildResourceId(generateUuid)

internal class OkHttpRequestInfo(internal val request: Request) : RequestInfo {

    override val url: String get() = request.url.toString()
    override val headers: Map<String, List<String>> get() = request.headers.toMultimap()
    override val contentType: String? get() = request.body?.contentType()?.toString()
    override val method: String get() = request.method

    override fun <T> tag(type: Class<out T>): T? = request.tag(type)
    override fun contentLength(): Long = request.body?.contentLength() ?: 0L
}
