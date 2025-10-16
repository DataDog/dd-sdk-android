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

internal class ExposuresRequestFactory(
    private val internalLogger: InternalLogger,
    private val customExposureEndpoint: String?
) : RequestFactory {

    override fun create(
        context: DatadogContext,
        executionContext: RequestExecutionContext,
        batchData: List<RawBatchEvent>,
        batchMetadata: ByteArray?
    ): Request {
        val requestId = UUID.randomUUID().toString()
        val url = customExposureEndpoint ?: (context.site.intakeEndpoint + "/api/v2/exposures")

        return Request(
            id = requestId,
            description = "Exposure Request",
            url = url,
            headers = buildHeaders(
                requestId,
                context.clientToken, // replace when testing
                context.source,
                context.sdkVersion
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
        private val PAYLOAD_SEPARATOR = "\n".toByteArray(Charsets.UTF_8)
    }
}
