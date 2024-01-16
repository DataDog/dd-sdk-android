/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.domain

import com.datadog.android.api.context.DatadogContext
import com.datadog.android.api.net.Request
import com.datadog.android.api.net.RequestFactory
import com.datadog.android.api.storage.RawBatchEvent
import com.datadog.android.sessionreplay.internal.exception.InvalidPayloadFormatException
import com.datadog.android.sessionreplay.internal.net.BatchesToSegmentsMapper
import okhttp3.RequestBody
import okio.Buffer
import java.util.Locale
import java.util.UUID

internal class SegmentRequestFactory(
    internal val customEndpointUrl: String?,
    private val batchToSegmentsMapper: BatchesToSegmentsMapper,
    private val segmentRequestBodyFactory: SegmentRequestBodyFactory = SegmentRequestBodyFactory()
) : RequestFactory {

    override fun create(
        context: DatadogContext,
        batchData: List<RawBatchEvent>,
        batchMetadata: ByteArray?
    ): Request {
        val serializedSegmentPair = batchToSegmentsMapper.map(batchData.map { it.data })
        if (serializedSegmentPair == null) {
            @Suppress("ThrowingInternalException")
            throw InvalidPayloadFormatException(
                "The payload format was broken and an upload" +
                    " request could not be created"
            )
        }
        val body = segmentRequestBodyFactory.create(
            serializedSegmentPair.first,
            serializedSegmentPair.second
        )
        return resolveRequest(context, body)
    }

    private fun buildUrl(datadogContext: DatadogContext): String {
        return String.format(
            Locale.US,
            UPLOAD_URL,
            customEndpointUrl ?: datadogContext.site.intakeEndpoint,
            "replay"
        )
    }

    private fun resolveHeaders(datadogContext: DatadogContext, requestId: String): Map<String, String> {
        return mapOf(
            RequestFactory.HEADER_API_KEY to datadogContext.clientToken,
            RequestFactory.HEADER_EVP_ORIGIN to datadogContext.source,
            RequestFactory.HEADER_EVP_ORIGIN_VERSION to datadogContext.sdkVersion,
            RequestFactory.HEADER_REQUEST_ID to requestId
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
