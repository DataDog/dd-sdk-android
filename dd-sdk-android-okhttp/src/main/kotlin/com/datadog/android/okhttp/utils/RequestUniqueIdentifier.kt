/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.okhttp.utils

import okhttp3.Request
import okhttp3.internal.Util

/**
 * Generates an identifier to uniquely track requests.
 */
internal fun identifyRequest(request: Request): String {
    val method = request.method()
    val url = request.url()
    val body = request.body()
    return if (body == null || body == Util.EMPTY_REQUEST) {
        "$method•$url"
    } else {
        val contentLength = body.contentLength()
        val contentType = body.contentType()
        "$method•$url•$contentLength•$contentType"
    }
}
