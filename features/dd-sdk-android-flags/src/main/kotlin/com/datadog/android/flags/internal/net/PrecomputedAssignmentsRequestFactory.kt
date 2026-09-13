/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal.net

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.context.DatadogContext
import com.datadog.android.api.feature.Feature
import com.datadog.android.flags.AssignmentProtection
import com.datadog.android.flags.internal.getFlagsEndpoint
import com.datadog.android.flags.model.EvaluationContext
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONException
import org.json.JSONObject
import java.security.SecureRandom

/**
 * Factory for creating HTTP requests to fetch precomputed flag assignments.
 */
internal class PrecomputedAssignmentsRequestFactory(
    private val internalLogger: InternalLogger,
    private val customFlagEndpoint: String?,
    private val authorizationStore: AssignmentAuthorizationStore = AssignmentAuthorizationStore(null),
    private val assignmentProtection: AssignmentProtection = if (authorizationStore.snapshot().isEnabled) {
        AssignmentProtection.SIGNED_AND_AUTHORIZED
    } else {
        AssignmentProtection.DISABLED
    },
    private val hasValidProtectionConfiguration: Boolean = true
) {

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
     * @param datadogContext The [DatadogContext] holding common information about SDK
     * @param nonceOverride A validated nonce from a persisted signed response. This value is for cache verification.
     * @return A fully-formed OkHttp Request ready for execution, or null if the request
     *         cannot be constructed (e.g., invalid endpoint, JSON serialization error)
     */
    @Suppress("CyclomaticComplexMethod", "ReturnCount")
    fun create(
        context: EvaluationContext,
        datadogContext: DatadogContext,
        nonceOverride: String? = null
    ): Request? {
        if (!hasValidProtectionConfiguration) {
            logInvalidProtectionConfiguration()
            return null
        }
        if (nonceOverride != null && !isValidNonce(nonceOverride)) return null
        val url = (
            customFlagEndpoint
                ?: datadogContext.site.getFlagsEndpoint(PREVIEW_CUSTOMER_DOMAIN)
                ?: return null
            ).toHttpUrlOrNull()
            ?: return null

        if (assignmentProtection != AssignmentProtection.DISABLED && !url.isHttps) {
            logProtectionError("Protected flag assignment delivery requires an HTTPS endpoint")
            return null
        }

        val authorization = authorizationStore.snapshot()
        if (
            assignmentProtection == AssignmentProtection.SIGNED_AND_AUTHORIZED &&
            authorization.authorization == null
        ) {
            return null
        }
        val body = buildRequestBody(context, datadogContext) ?: return null
        if (
            assignmentProtection != AssignmentProtection.DISABLED &&
            body.contentLength() > PrecomputedAssignmentsVerifier.MAX_REQUEST_BODY_BYTES
        ) {
            logProtectionError("Protected flag assignment request body is too large")
            return null
        }
        val headers = buildHeaders(datadogContext, authorization, nonceOverride) ?: return null

        @Suppress("UnsafeThirdPartyFunctionCall") // Safe: inputs validated, caller handles null
        return Request.Builder()
            .url(url)
            .headers(headers)
            .post(body)
            .build()
    }

    @Suppress("RequireInternal", "UnsafeThirdPartyFunctionCall") // Failures are caught and return null.
    private fun buildHeaders(
        datadogContext: DatadogContext,
        authorization: AssignmentAuthorizationSnapshot,
        nonceOverride: String?
    ): Headers? {
        val headersBuilder = Headers.Builder()

        try {
            headersBuilder
                .add(HEADER_CLIENT_TOKEN, datadogContext.clientToken)
                .add(HEADER_CONTENT_TYPE, CONTENT_TYPE_VND_JSON)
            if (assignmentProtection != AssignmentProtection.DISABLED) {
                headersBuilder
                    .add(
                        PrecomputedAssignmentsVerifier.SIGNATURE_VERSION_HEADER,
                        PrecomputedAssignmentsVerifier.SIGNATURE_VERSION
                    )
                    .add(
                        PrecomputedAssignmentsVerifier.REQUEST_NONCE_HEADER,
                        nonceOverride ?: createNonce()
                    )
            }
            if (assignmentProtection == AssignmentProtection.SIGNED_AND_AUTHORIZED) {
                val token = requireNotNull(authorization.authorization).bearerToken
                require(token.length <= MAX_AUTHORIZATION_TOKEN_LENGTH && token.none(Char::isWhitespace)) {
                    "Assignment authorization token is invalid"
                }
                headersBuilder.add(HEADER_AUTHORIZATION, "Bearer $token")
            }

            datadogContext.rumApplicationId?.let {
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

    private fun buildRequestBody(context: EvaluationContext, datadogContext: DatadogContext): RequestBody? = try {
        val attributeObj = buildStringifiedAttributes(context)

        val subject = JSONObject()
            .put("targeting_key", context.targetingKey)
            .put("targeting_attributes", attributeObj)
        val env = buildEnvPayload(datadogContext)
        val source = buildSourcePayload(datadogContext)
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
    private fun buildEnvPayload(datadogContext: DatadogContext): JSONObject =
        JSONObject()
            .put("dd_env", datadogContext.env)

    @Suppress("UnsafeThirdPartyFunctionCall") // call wrapped in try/catch
    private fun buildSourcePayload(datadogContext: DatadogContext): JSONObject =
        JSONObject()
            .put("sdk_name", SDK_NAME)
            .put("sdk_version", datadogContext.sdkVersion)

    private val DatadogContext.rumApplicationId: String?
        get() = featuresContext.get(Feature.RUM_FEATURE_NAME)
            ?.get("application_id") as? String

    @Suppress("UnsafeThirdPartyFunctionCall") // SecureRandom failures make request construction fail closed.
    private fun createNonce(): String = ByteArray(NONCE_SIZE_BYTES)
        .also { SecureRandom().nextBytes(it) }
        .joinToString(separator = "") {
            (it.toInt() and HEX_BYTE_MASK).toString(HEX_RADIX).padStart(HEX_BYTE_WIDTH, '0')
        }

    @Suppress("UnsafeThirdPartyFunctionCall") // The length check keeps digit conversion within a two-character byte.
    private fun isValidNonce(value: String): Boolean =
        value.length == NONCE_SIZE_BYTES * HEX_BYTE_WIDTH && value.all { it.digitToIntOrNull(HEX_RADIX) != null }

    private fun logInvalidProtectionConfiguration() {
        internalLogger.log(
            level = InternalLogger.Level.ERROR,
            target = InternalLogger.Target.USER,
            messageBuilder = {
                "Assignment authorization requires SIGNED_AND_AUTHORIZED assignment protection"
            },
            onlyOnce = true
        )
    }

    private fun logProtectionError(message: String) {
        internalLogger.log(
            level = InternalLogger.Level.ERROR,
            target = InternalLogger.Target.USER,
            messageBuilder = { message },
            onlyOnce = true
        )
    }

    companion object {
        private const val HEADER_APPLICATION_ID = "dd-application-id"
        private const val HEADER_CLIENT_TOKEN = "dd-client-token"
        private const val HEADER_CONTENT_TYPE = "Content-Type"
        private const val HEADER_AUTHORIZATION = "Authorization"
        private const val CONTENT_TYPE_VND_JSON = "application/vnd.api+json"
        private const val PREVIEW_CUSTOMER_DOMAIN = "preview"
        private const val SDK_NAME = "dd-sdk-android"
        private const val NONCE_SIZE_BYTES = 16
        private const val MAX_AUTHORIZATION_TOKEN_LENGTH = 4_096
        private const val HEX_BYTE_MASK = 0xff
        private const val HEX_RADIX = 16
        private const val HEX_BYTE_WIDTH = 2
    }
}
