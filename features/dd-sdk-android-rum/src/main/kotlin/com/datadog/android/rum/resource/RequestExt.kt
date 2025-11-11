/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.resource

import com.datadog.android.api.instrumentation.network.RequestInfo
import com.datadog.android.lint.InternalApi
import java.io.IOException
import java.util.UUID

/**
 * Generates an identifier to uniquely track requests.
 */
@InternalApi
fun RequestInfo.buildResourceId(generateUuid: Boolean): ResourceId {
    val uuid = tag(UUID::class.java) ?: (if (generateUuid) UUID.randomUUID() else null)
    val key = identifyRequest(this)

    return ResourceId(key, uuid?.toString())
}

/**
 * Generates an identifier to uniquely track requests.
 */
fun identifyRequest(requestInfo: RequestInfo): String {
    val method = requestInfo.method
    val url = requestInfo.url

    val contentLength = try {
        requestInfo.contentLength()
    } catch (@Suppress("SwallowedException") ioe: IOException) {
        0
    }
    if (contentLength == null) {
        return "$method•$url"
    }
    val contentType = requestInfo.contentType
    // TODO RUM-648 It is possible that if requests are say GZIPed (as an example), or maybe
    //  streaming case (?), they all will have contentLength = -1, so if they target the same URL
    //  they are going to have same identifier, messing up reporting.
    //  -1 is valid return value for contentLength() call according to the docs and stands
    //  for "unknown" case.
    //  We need to have a more precise identification.
    return if (contentType != null || contentLength != 0L) {
        "$method•$url•$contentLength•$contentType"
    } else {
        "$method•$url"
    }
}
