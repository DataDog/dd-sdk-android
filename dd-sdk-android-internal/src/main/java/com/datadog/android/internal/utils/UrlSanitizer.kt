/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.internal.utils

import java.util.Locale

private val SENSITIVE_QUERY_PARAM_NAMES = setOf(
    "x-amz-security-token",
    "x-amz-signature",
    "x-amz-credential",
    "authorization",
    "access_token",
    "id_token",
    "refresh_token",
    "token",
    "session_token",
    "api_key",
    "apikey",
    "client_secret",
    "secret",
    "password",
    "signature",
    "credential"
)

private const val REDACTED_VALUE = "<redacted>"

/**
 * Returns this URL string with the values of known-sensitive query parameters
 * (auth tokens, signatures, credentials, secrets) replaced with a redacted
 * placeholder. Parameter names are matched case-insensitively; parameter names
 * that don't match, and everything else in the URL (scheme, host, path,
 * fragment), are left unchanged.
 *
 * This never throws: a URL with no query string, or one whose query string
 * doesn't look like `name=value&name=value`, is returned unchanged.
 *
 * @return the URL with sensitive query parameter values redacted.
 */
fun String.redactSensitiveQueryParams(): String {
    val queryStart = indexOf('?')
    if (queryStart == -1 || queryStart == length - 1) return this

    val base = substring(0, queryStart + 1)
    val afterQuestionMark = substring(queryStart + 1)
    val fragmentStart = afterQuestionMark.indexOf('#')
    val query = if (fragmentStart >= 0) afterQuestionMark.substring(0, fragmentStart) else afterQuestionMark
    val fragment = if (fragmentStart >= 0) afterQuestionMark.substring(fragmentStart) else ""

    val sanitizedQuery = query.split('&').joinToString("&") { param ->
        val eqIndex = param.indexOf('=')
        if (eqIndex == -1) {
            param
        } else {
            val name = param.substring(0, eqIndex)
            if (SENSITIVE_QUERY_PARAM_NAMES.contains(name.lowercase(Locale.US))) {
                "$name=$REDACTED_VALUE"
            } else {
                param
            }
        }
    }

    return "$base$sanitizedQuery$fragment"
}
