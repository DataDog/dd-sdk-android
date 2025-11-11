/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.api.instrumentation.network

import com.datadog.android.api.InternalLogger

/**
 * Represents information about an HTTP response.
 * Isolates specific library's details from the Datadog SDK.
 */
interface ResponseInfo {
    /**
     * Represents the URL associated with an HTTP response.
     */
    val url: String

    /**
     * Represents the HTTP status code associated with the response.
     */
    val statusCode: Int

    /**
     * Represents the HTTP headers associated with a response.
     */
    val headers: Map<String, List<String>>

    /**
     * Represents the MIME type of the payload associated with an HTTP response.
     *
     * The existence of this value depends on the specific implementation and may be null.
     */
    val contentType: String?

    /**
     * Calculates and retrieves the content length from the response information.
     *
     * @param internalLogger the logger used for reporting internal messages or errors during computation.
     * @return the content length as a Long if available, or null if it cannot be determined.
     */
    fun computeContentLength(internalLogger: InternalLogger): Long?

    /**
     * Retrieves the first value associated with the specified header key.
     *
     * @param key the name of the header to retrieve.
     * @return the first value of the header as a string, or null if the header does not exist or has no values.
     */
    fun headerValue(key: String): String? {
        return headers[key]?.firstOrNull()
    }
}
