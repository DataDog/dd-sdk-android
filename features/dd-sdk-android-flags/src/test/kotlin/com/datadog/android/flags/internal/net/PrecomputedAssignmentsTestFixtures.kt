/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal.net

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody

internal fun createPrecomputedSuccessfulResponse(body: String, url: String): Response = createPrecomputedResponse(
    code = 200,
    url = url,
    body = body.toResponseBody("application/json".toMediaType())
)

internal fun createPrecomputedSuccessfulResponseWithNullBody(url: String): Response = createPrecomputedResponse(
    code = 200,
    url = url,
    body = null
)

internal fun createPrecomputedUnsuccessfulResponse(code: Int, url: String, retryAfter: String? = null): Response =
    createPrecomputedResponse(
        code = code,
        url = url,
        body = "".toResponseBody("application/json".toMediaType()),
        retryAfter = retryAfter
    )

internal fun createPrecomputedResponse(
    code: Int,
    url: String,
    body: ResponseBody?,
    retryAfter: String? = null
): Response = Response.Builder()
    .request(Request.Builder().url(url).build())
    .protocol(Protocol.HTTP_1_1)
    .code(code)
    .message(if (code in 200..299) "OK" else "Error")
    .body(body)
    .apply {
        if (retryAfter != null) header("Retry-After", retryAfter)
    }
    .build()
