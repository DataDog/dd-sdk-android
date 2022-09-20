/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.v2.rum.internal.net

import com.datadog.android.core.internal.utils.join
import com.datadog.android.rum.RumAttributes
import com.datadog.android.v2.api.Request
import com.datadog.android.v2.api.RequestFactory
import com.datadog.android.v2.api.context.DatadogContext
import java.util.Locale
import java.util.UUID

internal class RumRequestFactory(
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
            description = "RUM Request",
            url = buildUrl(context),
            headers = buildHeaders(
                requestId,
                context.clientToken,
                context.source,
                context.sdkVersion
            ),
            body = batchData.join(
                separator = PAYLOAD_SEPARATOR
            ),
            contentType = RequestFactory.CONTENT_TYPE_TEXT_UTF8
        )
    }

    private fun buildUrl(context: DatadogContext): String {
        val queryParams = mapOf(
            RequestFactory.QUERY_PARAM_SOURCE to context.source,
            RequestFactory.QUERY_PARAM_TAGS to buildTags(
                context.service,
                context.version,
                context.sdkVersion,
                context.env,
                context.variant
            )
        )

        val intakeUrl = "%s/api/v2/rum".format(Locale.US, endpointUrl)

        return intakeUrl + queryParams.map { "${it.key}=${it.value}" }
            .joinToString("&", prefix = "?")
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

    private fun buildTags(
        serviceName: String,
        version: String,
        sdkVersion: String,
        env: String,
        variant: String
    ): String {
        val elements = mutableListOf(
            "${RumAttributes.SERVICE_NAME}:$serviceName",
            "${RumAttributes.APPLICATION_VERSION}:$version",
            "${RumAttributes.SDK_VERSION}:$sdkVersion",
            "${RumAttributes.ENV}:$env"
        )

        if (variant.isNotEmpty()) {
            elements.add("${RumAttributes.VARIANT}:$variant")
        }

        return elements.joinToString(",")
    }

    companion object {
        private val PAYLOAD_SEPARATOR = "\n".toByteArray(Charsets.UTF_8)
    }
}
