/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.net

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.instrumentation.network.HttpRequestInfo
import com.datadog.android.internal.network.GraphQLHeaders
import com.datadog.android.internal.utils.fromBase64
import com.datadog.android.lint.InternalApi
import com.datadog.android.rum.RumAttributes
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Extracts GraphQL-related attributes and errors from network requests and responses.
 */
@InternalApi
class GraphQLExtractor {

    /**
     * Extracts GraphQL attributes (operation name, type, variables, payload) from the request headers.
     * @param requestInfo the HTTP request info containing headers
     * @return a map of GraphQL attributes decoded from Base64 header values
     */
    fun extractGraphQLAttributes(requestInfo: HttpRequestInfo): Map<String, Any?> {
        return buildMap {
            GRAPHQL_HEADER_TO_ATTRIBUTE.forEach { (header, attribute) ->
                requestInfo.headers[header.headerValue]?.firstOrNull()?.let {
                    put(attribute, it.fromBase64())
                }
            }
        }
    }

    /**
     * Extracts and normalizes GraphQL errors from a JSON response body.
     * @param contentType the content type of the response
     * @param body the response body string
     * @param internalLogger the logger for reporting parsing failures
     * @return a JSON array string of normalized errors, or null if none found
     */
    fun extractGraphQLErrors(
        contentType: String?,
        body: String?,
        internalLogger: InternalLogger
    ): String? {
        if (body == null || contentType == null || JSON_CONTENT_TYPES.none { contentType.startsWith(it) }) return null

        return try {
            val json = JSONObject(body)
            val errorsArray = if (json.has(ERRORS_KEY)) json.getJSONArray(ERRORS_KEY) else null

            if (errorsArray == null || errorsArray.length() == 0) {
                null
            } else {
                val normalizedErrors = JSONArray()
                for (i in 0 until errorsArray.length()) {
                    @Suppress("UnsafeThirdPartyFunctionCall") // JSONException is caught and we are in array bounds
                    val error = errorsArray.getJSONObject(i)
                    normalizeErrorCode(error)
                    normalizedErrors.put(error)
                }
                normalizedErrors.toString()
            }
        } catch (e: JSONException) {
            internalLogger.log(
                InternalLogger.Level.WARN,
                InternalLogger.Target.MAINTAINER,
                { ERROR_EXTRACT_GRAPHQL_ERRORS },
                e
            )
            null
        }
    }

    private fun normalizeErrorCode(error: JSONObject) {
        if (!error.has(CODE_KEY)) {
            val code = error.optJSONObject(EXTENSIONS_KEY)?.opt(CODE_KEY)
            if (code != null) {
                @Suppress("UnsafeThirdPartyFunctionCall") // caught by caller's catch blocks
                error.put(CODE_KEY, code)
            }
        }
    }

    companion object {

        /** Maximum number of bytes to peek from a response body when extracting GraphQL errors. */
        const val MAX_GRAPHQL_BODY_PEEK: Long = 512L * 1024L

        internal const val ERROR_EXTRACT_GRAPHQL_ERRORS =
            "Failed to extract GraphQL errors from response body."

        private const val ERRORS_KEY = "errors"
        private const val EXTENSIONS_KEY = "extensions"
        private const val CODE_KEY = "code"

        private val GRAPHQL_HEADER_TO_ATTRIBUTE = mapOf(
            GraphQLHeaders.DD_GRAPHQL_NAME_HEADER to RumAttributes.GRAPHQL_OPERATION_NAME,
            GraphQLHeaders.DD_GRAPHQL_TYPE_HEADER to RumAttributes.GRAPHQL_OPERATION_TYPE,
            GraphQLHeaders.DD_GRAPHQL_VARIABLES_HEADER to RumAttributes.GRAPHQL_VARIABLES,
            GraphQLHeaders.DD_GRAPHQL_PAYLOAD_HEADER to RumAttributes.GRAPHQL_PAYLOAD
        )

        internal val JSON_CONTENT_TYPES = setOf(
            "application/json",
            "application/graphql+json",
            "application/graphql-response+json"
        )
    }
}
