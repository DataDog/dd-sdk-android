/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.api.instrumentation.network

import java.io.IOException

/**
 * Represents information about an HTTP request.
 * Isolates specific library's details from the Datadog SDK.
 */
interface RequestInfo {
    /**
     * The URL associated with the HTTP request.
     */
    val url: String

    /**
     * Represents the HTTP headers associated with the request.
     */
    val headers: Map<String, List<String>>

    /**
     * The MIME type of the payload associated with the HTTP request or response, represented
     * as a string. Can be null if the content type is unspecified.
     *
     * The existence of this value depends on the specific implementation and may be null.
     */
    val contentType: String?

    /**
     * Represents the HTTP method associated with the request.
     */
    val method: String

    /**
     * Returns the tag attached with type as a key, or null if no tag is attached with that key.
     */
    fun <T> tag(type: Class<out T>): T?

    /**
     * Retrieves the content length of the HTTP request.
     *
     * @return the length of the content in bytes, or null if the content length is unavailable.
     * @throws IOException if an error occurs while calculating the content length.
     */
    @Throws(IOException::class)
    fun contentLength(): Long?
}
