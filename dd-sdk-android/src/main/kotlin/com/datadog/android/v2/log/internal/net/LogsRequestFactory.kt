/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.v2.log.internal.net

import com.datadog.android.core.internal.utils.join
import com.datadog.android.v2.api.Request
import com.datadog.android.v2.api.RequestFactory
import com.datadog.android.v2.api.context.DatadogContext
import java.util.Locale
import java.util.UUID

internal class LogsRequestFactory(
    private val endpointUrl: String
) : RequestFactory {

    override fun create(
        context: DatadogContext,
        batchData: List<ByteArray>,
        batchMetadata: ByteArray?
    ): Request {
        val requestId = UUID.randomUUID().toString()

        return Request(
            id = requestId,
            description = "Logs Request",
            url = buildUrl(context.source),
            headers = buildHeaders(
                requestId,
                context.clientToken,
                context.source,
                context.sdkVersion
            ),
            body = batchData.join(
                separator = PAYLOAD_SEPARATOR,
                prefix = PAYLOAD_PREFIX,
                suffix = PAYLOAD_SUFFIX
            ),
            contentType = RequestFactory.CONTENT_TYPE_JSON
        )
    }

    private fun buildUrl(source: String): String {
        return "%s/api/v2/logs?%s=%s"
            .format(Locale.US, endpointUrl, RequestFactory.QUERY_PARAM_SOURCE, source)
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

    companion object {
        private val PAYLOAD_SEPARATOR = ",".toByteArray(Charsets.UTF_8)
        private val PAYLOAD_PREFIX = "[".toByteArray(Charsets.UTF_8)
        private val PAYLOAD_SUFFIX = "]".toByteArray(Charsets.UTF_8)
    }
}
