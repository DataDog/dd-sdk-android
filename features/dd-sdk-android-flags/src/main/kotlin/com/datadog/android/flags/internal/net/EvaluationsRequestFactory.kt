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
import com.datadog.android.core.internal.utils.join
import java.util.UUID

/**
 * Factory for creating requests to the EVP intake endpoint for evaluation logging.
 *
 * Request format:
 * - Endpoint: /api/v2/flagevaluations
 * - Content-Type: text/plain
 * - Payload: NDJSON (newline-delimited JSON) of FlagEvaluation events
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
            body = batchData.map { it.data }
                .join(
                    separator = PAYLOAD_SEPARATOR,
                    internalLogger = internalLogger
                ),
            contentType = RequestFactory.CONTENT_TYPE_TEXT_UTF8
        )
    }

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

    private companion object {
        private const val EVALUATION_ENDPOINT = "/api/v2/flagevaluations"
        private val PAYLOAD_SEPARATOR = "\n".toByteArray(Charsets.UTF_8)
    }
}
