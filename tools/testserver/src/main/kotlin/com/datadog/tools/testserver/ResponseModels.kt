/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.tools.testserver

internal data class MethodResponse(
    val method: String,
    val path: String,
    val body: String? = null,
    val headers: Map<String, String> = emptyMap()
)

internal data class ErrorResponse(
    val error: Boolean = true,
    val statusCode: Int,
    val method: String,
    val message: String
)
