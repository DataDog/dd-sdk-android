/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.v2.api

/**
 * Request object holding the data to be sent.
 *
 * @property id Unique identifier of the request.
 * @property description Description of the request (ex. "RUM request", "Logs request", etc.).
 * @property url URL to call.
 * @property headers Request headers. Note that User Agent header will be ignored.
 * @property body Request payload.
 * @property contentType Content type of the request, if needed.
 */
data class Request(
    val id: String,
    val description: String,
    val url: String,
    val headers: Map<String, String>,
    // won't generate custom equals/hashcode, because ID field is enough to identify the request
    // and we don't want to have array content comparison
    @Suppress("ArrayInDataClass") val body: ByteArray,
    val contentType: String? = null
)
