/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.featureflags.internal.repository.net

import com.datadog.android.api.InternalLogger
import com.datadog.android.flags.featureflags.internal.model.FlagsContext
import com.datadog.android.flags.featureflags.model.EvaluationContext
import okhttp3.Headers
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONException
import org.json.JSONObject

/**
 * Default implementation of [PrecomputedAssignmentsRequestFactory].
 *
 * This factory builds HTTP POST requests for fetching precomputed flag assignments
 * from the Datadog Feature Flags service. It handles endpoint resolution, authentication
 * headers, and JSON request body construction.
 *
 * @param internalLogger Logger for error and debug messages
 */
internal class DefaultPrecomputedAssignmentsRequestFactory(private val internalLogger: InternalLogger) :
    PrecomputedAssignmentsRequestFactory {

    @Suppress("ReturnCount", "TooGenericExceptionCaught")
    override fun create(context: EvaluationContext, flagsContext: FlagsContext): Request? {
        val url = EndpointsHelper.getFlaggingEndpoint(flagsContext, internalLogger) ?: return null

        val headers = buildHeaders(flagsContext)

        val body = buildRequestBody(context, flagsContext) ?: return null

        return try {
            Request.Builder()
                .url(url)
                .headers(headers)
                .post(body)
                .build()
        } catch (e: Exception) {
            internalLogger.log(
                InternalLogger.Level.ERROR,
                InternalLogger.Target.MAINTAINER,
                { "Unable to create precomputed assignments request" },
                e
            )
            null
        }
    }

    private fun buildHeaders(flagsContext: FlagsContext): Headers {
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
        }

        return headersBuilder.build()
    }

    private fun buildRequestBody(context: EvaluationContext, flagsContext: FlagsContext): RequestBody? = try {
        val attributeObj = buildStringifiedAttributes(context)

        val subject = JSONObject()
            .put("targeting_key", context.targetingKey)
            .put("targeting_attributes", attributeObj)
        val env = buildEnvPayload(flagsContext)
        val attributes = JSONObject()
            .put("env", env)
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

    companion object {
        private const val HEADER_APPLICATION_ID = "dd-application-id"
        private const val HEADER_CLIENT_TOKEN = "dd-client-token"
        private const val HEADER_CONTENT_TYPE = "Content-Type"
        private const val CONTENT_TYPE_VND_JSON = "application/vnd.api+json"
    }
}
