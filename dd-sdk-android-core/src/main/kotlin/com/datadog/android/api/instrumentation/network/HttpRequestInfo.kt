/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.api.instrumentation.network

/**
 * Represents information about an HTTP request.
 */
interface HttpRequestInfo {
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
     */
    val contentType: String?

    /**
     * Represents the HTTP method associated with the request.
     */
    val method: String

    /**
     * Retrieves the content length of the HTTP request.
     *
     * @return the length of the content in bytes, or null if the content length is unavailable.
     */
    fun contentLength(): Long?

    /**
     * Creates a modifier to modify this request info.
     * @return a new [HttpRequestInfoModifier] initialized with this request's data.
     */
    fun modify(): HttpRequestInfoModifier
}
