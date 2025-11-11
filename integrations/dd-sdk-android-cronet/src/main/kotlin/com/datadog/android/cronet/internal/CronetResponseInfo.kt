/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.cronet.internal

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.instrumentation.network.ResponseInfo
import com.datadog.android.core.internal.net.HttpSpec

internal data class CronetResponseInfo(
    private val response: org.chromium.net.UrlResponseInfo
) : ResponseInfo {

    override val url: String get() = response.url
    override val statusCode: Int get() = response.httpStatusCode
    override val headers: Map<String, List<String>> get() = response.allHeaders
    override val contentType: String? get() = headerValue(HttpSpec.Headers.CONTENT_TYPE)

    override fun computeContentLength(internalLogger: InternalLogger) =
        headerValue(HttpSpec.Headers.CONTENT_LENGTH)?.toLongOrNull()
            ?: response.receivedByteCount.takeIf { it >= 0 }
}
