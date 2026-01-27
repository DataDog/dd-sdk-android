/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal.net

import com.datadog.android.api.InternalLogger
import com.datadog.android.flags.internal.getFlagsEndpoint
import com.datadog.android.flags.internal.model.FlagsContext
import com.datadog.android.flags.model.EvaluationContext
import okhttp3.Headers
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONException
import org.json.JSONObject

/**
 * Factory for creating HTTP requests to fetch precomputed flag assignments.
 */
internal class PrecomputedAssignmentsRequestFactory(private val internalLogger: InternalLogger) {

    /**
     * Creates an OkHttp Request for fetching precomputed flag assignments.
     *
     * This method constructs a complete HTTP POST request including:
     * - URL (endpoint) determination based on site or custom configuration
     * - Headers (authentication, content-type, etc.)
     * - Request body (evaluation context data)
     *
     * @param context The evaluation context containing targeting key and custom attributes
     *                for flag evaluation
     * @param flagsContext The flags context containing SDK configuration, authentication,
     *                     site information, and custom endpoint settings
     * @return A fully-formed OkHttp Request ready for execution, or null if the request
     *         cannot be constructed (e.g., invalid endpoint, JSON serialization error)
     */
    @Suppress("ReturnCount")
    fun create(context: EvaluationContext, flagsContext: FlagsContext): Request? {
        val url = flagsContext.customFlagEndpoint
            ?: flagsContext.site.getFlagsEndpoint(PREVIEW_CUSTOMER_DOMAIN)
            ?: return null

        val headers = buildHeaders(flagsContext) ?: return null

        val body = buildRequestBody(context, flagsContext) ?: return null

        @Suppress("UnsafeThirdPartyFunctionCall") // Safe: inputs validated, caller handles null
        return Request.Builder()
            .url(url)
            .headers(headers)
            .post(body)
            .build()
    }

    private fun buildHeaders(flagsContext: FlagsContext): Headers? {
        val headersBuilder = Headers.Builder()

        try {
            headersBuilder
                .add(HEADER_CLIENT_TOKEN, flagsContext.clientToken)
                .add(HEADER_CONTENT_TYPE, CONTENT_TYPE_VND_JSON)

            flagsContext.applicationId?.let {
                headersBuilder.add(HEADER_APPLICATION_ID, it)
            }
        } catch (e: IllegalArgumentException) {
            internalLogger.log(
                InternalLogger.Level.ERROR,
                InternalLogger.Target.MAINTAINER,
                { "Failed to build HTTP headers: invalid header values" },
                e
            )
            return null
        }

        return headersBuilder.build()
    }

    private fun buildRequestBody(context: EvaluationContext, flagsContext: FlagsContext): RequestBody? = try {
        val attributeObj = buildStringifiedAttributes(context)

        val subject = JSONObject()
            .put("targeting_key", context.targetingKey)
            .put("targeting_attributes", attributeObj)
        val env = buildEnvPayload(flagsContext)
        val source = buildSourcePayload(flagsContext)
        val attributes = JSONObject()
            .put("env", env)
            .put("source", source)
            .put("subject", subject)
        val data = JSONObject()
            .put("type", "precompute-assignments-request")
            .put("attributes", attributes)
        val body = JSONObject()
            .put("data", data)

        // String.toRequestBody() can internally throw IOException/ArrayIndexOutOfBoundsException,
        // but not in this context with a valid JSON string from JSONObject.toString()
        @Suppress("UnsafeThirdPartyFunctionCall")
        body.toString().toRequestBody()
    } catch (e: JSONException) {
        internalLogger.log(
            InternalLogger.Level.ERROR,
            InternalLogger.Target.MAINTAINER,
            { "Failed to create request body: JSON error" },
            e
        )
        null
    }

    @Suppress("UnsafeThirdPartyFunctionCall") // call wrapped in try/catch
    private fun buildStringifiedAttributes(context: EvaluationContext): JSONObject {
        val contextJson = JSONObject()
        context.attributes.forEach { (key, value) ->
            contextJson.put(key, value)
        }
        return contextJson
    }

    @Suppress("UnsafeThirdPartyFunctionCall") // call wrapped in try/catch
    private fun buildEnvPayload(flagsContext: FlagsContext): JSONObject =
        JSONObject()
            .put("dd_env", flagsContext.env)

    @Suppress("UnsafeThirdPartyFunctionCall") // call wrapped in try/catch
    private fun buildSourcePayload(flagsContext: FlagsContext): JSONObject =
        JSONObject()
            .put("sdk_name", SDK_NAME)
            .put("sdk_version", flagsContext.sdkVersion)

    companion object {
        private const val HEADER_APPLICATION_ID = "dd-application-id"
        private const val HEADER_CLIENT_TOKEN = "dd-client-token"
        private const val HEADER_CONTENT_TYPE = "Content-Type"
        private const val CONTENT_TYPE_VND_JSON = "application/vnd.api+json"
        private const val PREVIEW_CUSTOMER_DOMAIN = "preview"
        private const val SDK_NAME = "dd-sdk-android"
    }
}
