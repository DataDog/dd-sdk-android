/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.cronet.internal

import org.chromium.net.UrlRequest

internal fun UrlRequest.Builder.addHeaders(headers: Map<String, List<String>>) = apply {
    headers.forEach { (key, values) ->
        values.forEach { addHeader(key, it) }
    }
}
