/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.domain

import com.datadog.android.rum.RumAttributes
import com.datadog.android.sessionreplay.internal.exception.InvalidPayloadFormatException
import com.datadog.android.sessionreplay.internal.net.BatchesToSegmentsMapper
import com.datadog.android.v2.api.Request
import com.datadog.android.v2.api.RequestFactory
import com.datadog.android.v2.api.context.DatadogContext
import okhttp3.RequestBody
import okio.Buffer
import java.util.Locale
import java.util.UUID

internal class SessionReplayRequestFactory(
    internal val customEndpointUrl: String?,
    private val batchToSegmentsMapper: BatchesToSegmentsMapper = BatchesToSegmentsMapper(),
    private val requestBodyFactory: RequestBodyFactory = RequestBodyFactory()
) : RequestFactory {

    override fun create(
        context: DatadogContext,
        batchData: List<ByteArray>,
        batchMetadata: ByteArray?
    ): Request {
        val serializedSegmentPair = batchToSegmentsMapper.map(batchData)
        if (serializedSegmentPair == null) {
            @Suppress("ThrowingInternalException")
            throw InvalidPayloadFormatException(
                "The payload format was broken and an upload" +
                    " request could not be created"
            )
        }
        val body = requestBodyFactory.create(
            serializedSegmentPair.first,
            serializedSegmentPair.second
        )
        return resolveRequest(context, body)
    }

    private fun tags(datadogContext: DatadogContext): String {
        val elements = mutableListOf(
            "${RumAttributes.SERVICE_NAME}:${datadogContext.service}",
            "${RumAttributes.APPLICATION_VERSION}:" +
                datadogContext.version,
            "${RumAttributes.SDK_VERSION}:${datadogContext.sdkVersion}",
            "${RumAttributes.ENV}:${datadogContext.env}"
        )
        if (datadogContext.variant.isNotEmpty()) {
            elements.add("${RumAttributes.VARIANT}:${datadogContext.variant}")
        }
        return elements.joinToString(",")
    }

    private fun buildUrl(datadogContext: DatadogContext): String {
        val queryParams = buildQueryParameters(datadogContext)
        val intakeUrl = String.format(
            Locale.US,
            UPLOAD_URL,
            customEndpointUrl ?: datadogContext.site.intakeEndpoint,
            "replay"
        )
        return intakeUrl + queryParams.map { "${it.key}=${it.value}" }
            .joinToString("&", prefix = "?")
    }

    private fun resolveHeaders(datadogContext: DatadogContext, requestId: String):
        Map<String, String> {
        return mapOf(
            RequestFactory.HEADER_API_KEY to datadogContext.clientToken,
            RequestFactory.HEADER_EVP_ORIGIN to datadogContext.source,
            RequestFactory.HEADER_EVP_ORIGIN_VERSION to datadogContext.sdkVersion,
            RequestFactory.HEADER_REQUEST_ID to requestId
        )
    }

    private fun buildQueryParameters(datadogContext: DatadogContext): Map<String, Any> {
        return mapOf(
            RequestFactory.QUERY_PARAM_SOURCE to datadogContext.source,
            RequestFactory.QUERY_PARAM_TAGS to tags(datadogContext)
        )
    }

    @Suppress("ReturnCount")
    private fun resolveRequest(context: DatadogContext, body: RequestBody): Request {
        val bodyAsByteArray = extractByteArrayFromBody(body)
        val requestId = UUID.randomUUID().toString()
        val description = "Session Replay Segment Upload Request"
        val headers = resolveHeaders(context, requestId)
        val requestUrl = buildUrl(context)
        return Request(
            requestId,
            description,
            requestUrl,
            headers,
            body = bodyAsByteArray,
            contentType = body.contentType().toString()
        )
    }

    private fun extractByteArrayFromBody(body: RequestBody): ByteArray {
        val buffer = Buffer()
        @Suppress("UnsafeThirdPartyFunctionCall")
        body.writeTo(buffer)
        @Suppress("UnsafeThirdPartyFunctionCall")
        return buffer.readByteArray()
    }

    // endregion

    companion object {
        private const val UPLOAD_URL = "%s/api/v2/%s"
    }
}
