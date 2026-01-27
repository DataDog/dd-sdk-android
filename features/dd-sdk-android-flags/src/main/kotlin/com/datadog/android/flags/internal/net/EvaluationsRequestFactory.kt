/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal.net

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.context.DatadogContext
import com.datadog.android.api.net.Request
import com.datadog.android.api.net.RequestExecutionContext
import com.datadog.android.api.net.RequestFactory
import com.datadog.android.api.storage.RawBatchEvent
import com.datadog.android.flags.model.BatchedFlagEvaluations
import java.util.UUID

/**
 * Factory for creating requests to the EVP intake endpoint for evaluation logging.
 *
 * Request format:
 * - Endpoint: /api/v2/flagevaluations
 * - Content-Type: application/json
 * - Payload: BatchedFlagEvaluations with context wrapper
 *
 * @param internalLogger logger for errors
 * @param customEvaluationEndpoint optional custom endpoint override
 */
internal class EvaluationsRequestFactory(
    private val internalLogger: InternalLogger,
    private val customEvaluationEndpoint: String?
) : RequestFactory {

    override fun create(
        context: DatadogContext,
        executionContext: RequestExecutionContext,
        batchData: List<RawBatchEvent>,
        batchMetadata: ByteArray?
    ): Request {
        val requestId = UUID.randomUUID().toString()
        val url = customEvaluationEndpoint ?: (context.site.intakeEndpoint + EVALUATION_ENDPOINT)

        return Request(
            id = requestId,
            description = "Evaluation Request",
            url = url,
            headers = buildHeaders(
                requestId = requestId,
                clientToken = context.clientToken,
                source = context.source,
                sdkVersion = context.sdkVersion
            ),
            body = buildBatchedPayload(context, batchData),
            contentType = RequestFactory.CONTENT_TYPE_JSON
        )
    }

    /**
     * Builds the request headers.
     *
     * @param requestId unique request ID
     * @param clientToken Datadog client token
     * @param source SDK source identifier
     * @param sdkVersion SDK version
     * @return map of headers
     */
    private fun buildHeaders(
        requestId: String,
        clientToken: String,
        source: String,
        sdkVersion: String
    ): Map<String, String> = mapOf(
        RequestFactory.HEADER_API_KEY to clientToken,
        RequestFactory.HEADER_EVP_ORIGIN to source,
        RequestFactory.HEADER_EVP_ORIGIN_VERSION to sdkVersion,
        RequestFactory.HEADER_REQUEST_ID to requestId
    )

    /**
     * Builds the batched payload with context wrapper.
     *
     * The payload must be a BatchedFlagEvaluations object:
     * {
     *   "context": { service, version, env, device, os, rum, geo },
     *   "flagEvaluations": [ ... ]
     * }
     *
     * @param context Datadog context for extracting metadata
     * @param batchData list of raw batch events (individual FlagEvaluation objects)
     * @return batched payload as UTF-8 bytes
     */
    private fun buildBatchedPayload(context: DatadogContext, batchData: List<RawBatchEvent>): ByteArray {
        // Parse individual FlagEvaluation events
        val flagEvaluations = mutableListOf<BatchedFlagEvaluations.FlagEvaluation>()
        batchData.forEach { event ->
            try {
                val jsonString = String(event.data, Charsets.UTF_8)
                val flagEvaluation = BatchedFlagEvaluations.FlagEvaluation.fromJson(jsonString)
                @Suppress("UnsafeThirdPartyFunctionCall") // add() is safe - wrapped in try-catch
                flagEvaluations.add(flagEvaluation)
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                // Catch all JSON parsing exceptions to prevent batch failure
                internalLogger.log(
                    InternalLogger.Level.ERROR,
                    InternalLogger.Target.MAINTAINER,
                    { "Failed to parse flag evaluation for batching" },
                    e
                )
                // Skip malformed events, continue with valid ones
            }
        }

        // Build batched payload with context wrapper
        val batchedPayload = BatchedFlagEvaluations(
            context = buildTopLevelContext(context),
            flagEvaluations = flagEvaluations
        )

        return batchedPayload.toJson().toString().toByteArray(Charsets.UTF_8)
    }

    /**
     * Builds the top-level context from DatadogContext.
     *
     * Extracts service, version, env, device, os, and RUM metadata.
     *
     * @param context Datadog context
     * @return top-level context object
     */
    private fun buildTopLevelContext(context: DatadogContext): BatchedFlagEvaluations.Context =
        BatchedFlagEvaluations.Context(
            service = context.service,
            version = context.version,
            env = context.env,
            device = buildDeviceContext(context),
            os = buildOsContext(context),
            geo = null, // Geo info not currently tracked
            rum = buildRumContext(context)
        )

    private fun buildDeviceContext(context: DatadogContext): BatchedFlagEvaluations.Device {
        val deviceInfo = context.deviceInfo
        return BatchedFlagEvaluations.Device(
            name = deviceInfo.deviceName,
            type = deviceInfo.deviceType.toString().lowercase(),
            brand = deviceInfo.deviceBrand,
            model = deviceInfo.deviceModel
        )
    }

    private fun buildOsContext(context: DatadogContext): BatchedFlagEvaluations.Os {
        val deviceInfo = context.deviceInfo
        return BatchedFlagEvaluations.Os(
            name = deviceInfo.osName,
            version = deviceInfo.osVersion
        )
    }

    private fun buildRumContext(context: DatadogContext): BatchedFlagEvaluations.Rum? {
        // Extract RUM context from features context if available
        val rumContext = context.featuresContext["rum"]
        val applicationId = rumContext?.get(RUM_APPLICATION_ID) as? String
        val viewName = rumContext?.get(RUM_VIEW_NAME) as? String

        return if (applicationId != null || viewName != null) {
            BatchedFlagEvaluations.Rum(
                application = applicationId?.let { BatchedFlagEvaluations.Application(id = it) },
                view = viewName?.let { BatchedFlagEvaluations.View(url = it) }
            )
        } else {
            null
        }
    }

    private companion object {
        private const val EVALUATION_ENDPOINT = "/api/v2/flagevaluations"

        // RUM Context keys - must match com.datadog.android.rum.internal.domain.RumContext constants
        private const val RUM_APPLICATION_ID = "application_id"
        private const val RUM_VIEW_NAME = "view_name"
    }
}
