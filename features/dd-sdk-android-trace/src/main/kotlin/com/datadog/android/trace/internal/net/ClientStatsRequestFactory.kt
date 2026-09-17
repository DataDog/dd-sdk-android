/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.trace.internal.net

import com.datadog.android.api.context.DatadogContext
import com.datadog.android.api.net.Request
import com.datadog.android.api.net.RequestExecutionContext
import com.datadog.android.api.net.RequestFactory
import com.datadog.android.api.storage.RawBatchEvent
import com.datadog.android.trace.internal.domain.metrics.StatsPayload
import java.util.UUID

internal class ClientStatsRequestFactory(
    internal val customStatsEndpointUrl: String?
) : RequestFactory {

    override fun create(
        context: DatadogContext,
        executionContext: RequestExecutionContext,
        batchData: List<RawBatchEvent>,
        batchMetadata: ByteArray?
    ): Request {
        val requestId = UUID.randomUUID().toString()

        val baseUrl = customStatsEndpointUrl ?: (context.site.intakeEndpoint + "/api/v0.2/stats")
        // Batches can't be split currently due to we can't create more than 1 Request in this class,
        // nor can the batch building system report back when a batch will be filled before writing it,
        // so we cant set a splitPayload flag in the batch's metadata.
        val payload = StatsPayload(batchData.map { it.data }, splitPayload = false)

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
            body = payload.toMsgPackPayload(),
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
            RequestFactory.HEADER_REQUEST_ID to requestId
        )
    }
}
