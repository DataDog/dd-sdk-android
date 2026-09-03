/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.trace.internal.net

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.context.DatadogContext
import com.datadog.android.api.net.Request
import com.datadog.android.api.net.RequestExecutionContext
import com.datadog.android.api.net.RequestFactory
import com.datadog.android.api.storage.RawBatchEvent
import java.util.UUID

internal class ClientStatsRequestFactory(
    private val internalLogger: InternalLogger,
    internal val customStatsEndpointUrl: String?
) : RequestFactory {

    override fun create(
        context: DatadogContext,
        executionContext: RequestExecutionContext,
        batchData: List<RawBatchEvent>,
        batchMetadata: ByteArray?
    ): Request? {
        val baseUrl = customStatsEndpointUrl ?: (context.site.intakeEndpoint + "/api/v0.2/stats")
        // BatchStatsWriter persists one fully-wrapped, already gzip-compressed StatsPayload
        // (envelope + splitPayload flag already applied) per batch, and maxItemsPerBatch = 1
        // guarantees exactly one item here.
        val payload = batchData.firstOrNull()?.data
        if (payload == null) {
            internalLogger.log(
                InternalLogger.Level.WARN,
                InternalLogger.Target.MAINTAINER,
                { EMPTY_BATCH_DATA_MESSAGE }
            )
            return null
        }

        val requestId = UUID.randomUUID().toString()

        return Request(
            id = requestId,
            description = "Client Stats Request",
            url = baseUrl,
            headers = buildHeaders(
                requestId,
                context.clientToken,
                context.source,
                context.sdkVersion
            ),
            body = payload,
            contentType = "application/msgpack"
        )
    }

    private fun buildHeaders(
        requestId: String,
        clientToken: String,
        source: String,
        sdkVersion: String
    ): Map<String, String> {
        return mapOf(
            RequestFactory.HEADER_API_KEY to clientToken,
            RequestFactory.HEADER_EVP_ORIGIN to source,
            RequestFactory.HEADER_EVP_ORIGIN_VERSION to sdkVersion,
            RequestFactory.HEADER_REQUEST_ID to requestId,
            // Payload is already gzip-compressed by BatchStatsWriter; this tells the shared
            // upload interceptor not to compress it again.
            HEADER_CONTENT_ENCODING to ENCODING_GZIP
        )
    }

    internal companion object {
        internal const val EMPTY_BATCH_DATA_MESSAGE =
            "ClientStatsRequestFactory received an empty batch, no request will be created."

        private const val HEADER_CONTENT_ENCODING = "Content-Encoding"
        private const val ENCODING_GZIP = "gzip"
    }
}
