/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.net

import okhttp3.Request
import java.io.IOException

/**
 * Generates an identifier to uniquely track requests.
 */
internal fun identifyRequest(request: Request): String {
    val method = request.method()
    val url = request.url()
    val body = request.body()
    return if (body == null) {
        "$method•$url"
    } else {
        val contentLength = try {
            body.contentLength()
        } catch (@Suppress("SwallowedException") ioe: IOException) {
            0
        }
        val contentType = body.contentType()
        // TODO RUMM-3062 It is possible that if requests are say GZIPed (as an example), or maybe
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
