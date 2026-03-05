/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.okhttp.internal

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.instrumentation.network.HttpRequestInfo
import com.datadog.android.api.instrumentation.network.HttpResponseInfo
import okhttp3.Request
import okhttp3.Response

internal fun Request.toHttpRequestInfo() = OkHttpRequestInfo(this)
internal fun HttpRequestInfo.toOkHttpRequest(): Request? = (this as? OkHttpRequestInfo)?.originalRequest
internal fun HttpResponseInfo.toOkHttpResponse(): Response? = (this as? OkHttpHttpResponseInfo)?.originalResponse
internal fun Response.toHttpResponseInfo(internalLogger: InternalLogger) = OkHttpHttpResponseInfo(this, internalLogger)
