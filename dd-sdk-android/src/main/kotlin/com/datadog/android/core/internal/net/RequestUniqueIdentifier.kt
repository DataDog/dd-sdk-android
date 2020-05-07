/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.net

import okhttp3.Request

/**
 * Generates an identifier to uniquely track requests.
 */
internal fun identifyRequest(request: Request): String {
    val method = request.method()
    val url = request.url()
    val body = request.body()
    return "$method•$url•${body?.contentLength()}•${body?.contentType()}"
}
