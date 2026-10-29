/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal.net

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.context.DatadogContext
import com.datadog.android.api.feature.Feature
import com.datadog.android.flags.ExperimentationOptOutMode
import com.datadog.android.flags.internal.evaluation.ExperimentationOptOut
import com.datadog.android.flags.internal.evaluation.wireValue
import com.datadog.android.flags.internal.getFlagsEndpoint
import com.datadog.android.flags.internal.model.FlagKeyObfuscation
import com.datadog.android.flags.internal.model.PrecomputedAssignmentsRequest
import com.datadog.android.flags.model.EvaluationContext
import com.datadog.android.internal.rum.rumContext
import okhttp3.Headers
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Factory for creating HTTP requests to fetch precomputed flag assignments.
 */
internal class PrecomputedAssignmentsRequestFactory(
    private val internalLogger: InternalLogger,
    private val customFlagEndpoint: String?,
    private val experimentationOptOutMode: ExperimentationOptOutMode = ExperimentationOptOutMode.DISABLED
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
     * @param diagnostics Snapshot for the logical fetch that created this request.
     * @return A fully-formed OkHttp Request ready for execution, or null if the request
     *         cannot be constructed (e.g., invalid endpoint or headers)
     */
    @Suppress("ReturnCount")
    fun create(
        context: EvaluationContext,
        datadogContext: DatadogContext,
        diagnostics: PrecomputedAssignmentsRequest.Diagnostics
    ): Request? {
        val url = customFlagEndpoint
            ?: datadogContext.site.getFlagsEndpoint(PREVIEW_CUSTOMER_DOMAIN)
            ?: return null

        val headers = buildHeaders(datadogContext) ?: return null

        val body = buildRequestBody(context, datadogContext, diagnostics)

        @Suppress("UnsafeThirdPartyFunctionCall") // Safe: inputs validated, caller handles null
        return Request.Builder()
            .url(url)
            .headers(headers)
            .post(body)
            .build()
    }

    private fun buildHeaders(datadogContext: DatadogContext): Headers? {
        val headersBuilder = Headers.Builder()

        try {
            headersBuilder
                .add(HEADER_CLIENT_TOKEN, datadogContext.clientToken)
                .add(HEADER_CONTENT_TYPE, CONTENT_TYPE_VND_JSON)

            // Bridge SDKs that consume raw snapshots cannot resolve encoded keys yet.
            if (datadogContext.source == "android") {
                headersBuilder.add(
                    "X-DD-FEATURE-FLAGS-CAPABILITIES",
                    listOf(FlagKeyObfuscation.CAPABILITY).sorted().joinToString(",")
                )
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

    private fun buildRequestBody(
        context: EvaluationContext,
        datadogContext: DatadogContext,
        diagnostics: PrecomputedAssignmentsRequest.Diagnostics
    ): RequestBody {
        // Java callers can supply null entries despite the Kotlin map type.
        val inputAttributes: Map<out String?, String?> = context.attributes
        val targetingAttributes = mutableMapOf<String, String>()
        for ((key, value) in inputAttributes) {
            if (key != null && value != null) {
                targetingAttributes[key] = value
            }
        }
        val body = PrecomputedAssignmentsRequest(
            diagnostics = diagnostics,
            data = PrecomputedAssignmentsRequest.Data(
                attributes = PrecomputedAssignmentsRequest.Attributes(
                    evaluationOptions = if (experimentationOptOutMode != ExperimentationOptOutMode.DISABLED &&
                        context.attributes.containsKey(ExperimentationOptOut.ATTRIBUTE)
                    ) {
                        PrecomputedAssignmentsRequest.EvaluationOptions(experimentationOptOutMode.wireValue)
                    } else {
                        null
                    },
                    env = PrecomputedAssignmentsRequest.Env(ddEnv = datadogContext.env),
                    source = PrecomputedAssignmentsRequest.Source(
                        sdkName = if (datadogContext.source == "android") "dd-sdk-android" else datadogContext.source,
                        sdkVersion = datadogContext.sdkVersion
                    ),
                    subject = PrecomputedAssignmentsRequest.Subject(
                        targetingKey = context.targetingKey,
                        targetingAttributes = PrecomputedAssignmentsRequest.TargetingAttributes(
                            additionalProperties = targetingAttributes
                        )
                    )
                )
            )
        )
        @Suppress("UnsafeThirdPartyFunctionCall") // Generated JSON is a valid string for the request body.
        return body.toJson().toString().toRequestBody()
    }

    private val DatadogContext.rumApplicationId: String?
        get() = featuresContext[Feature.RUM_FEATURE_NAME]?.rumContext()?.applicationId

    companion object {
        private const val HEADER_APPLICATION_ID = "dd-application-id"
        private const val HEADER_CLIENT_TOKEN = "dd-client-token"
        private const val HEADER_CONTENT_TYPE = "Content-Type"
        private const val CONTENT_TYPE_VND_JSON = "application/vnd.api+json"
        private const val PREVIEW_CUSTOMER_DOMAIN = "preview"
    }
}
