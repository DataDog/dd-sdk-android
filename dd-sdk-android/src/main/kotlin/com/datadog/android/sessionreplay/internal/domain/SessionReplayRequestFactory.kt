/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.domain

import com.datadog.android.core.internal.utils.internalLogger
import com.datadog.android.rum.RumAttributes
import com.datadog.android.sessionreplay.net.BatchesToSegmentsMapper
import com.datadog.android.v2.api.InternalLogger
import com.datadog.android.v2.api.Request
import com.datadog.android.v2.api.RequestFactory
import com.datadog.android.v2.api.context.DatadogContext
import okhttp3.RequestBody
import okio.Buffer
import java.util.Locale
import java.util.UUID

internal class SessionReplayRequestFactory(
    private val endpoint: String,
    private val batchToSegmentsMapper: BatchesToSegmentsMapper = BatchesToSegmentsMapper(),
    private val requestBodyFactory: RequestBodyFactory = RequestBodyFactory(),
    private val bodyToByteArrayFactory: (RequestBody) -> ByteArray = {
        val buffer = Buffer()
        // this is handled below in the wrapper function
        @Suppress("UnsafeThirdPartyFunctionCall")
        it.writeTo(buffer)
        @Suppress("UnsafeThirdPartyFunctionCall")
        buffer.readByteArray()
    }
) : RequestFactory {

    private val intakeUrl by lazy {
        String.format(
            Locale.US,
            UPLOAD_URL,
            endpoint,
            "replay"
        )
    }

    override fun create(
        context: DatadogContext,
        batchData: List<ByteArray>,
        batchMetadata: ByteArray?
    ): Request {
        val serializedSegmentPair = batchToSegmentsMapper.map(batchData)
        if (serializedSegmentPair == null) {
            // TODO: RUMM-2397 Add the proper logs here once the sdkLogger will be added
            // the data in the batch is corrupted. We return an empty request just to make
            // sure the batch will be deleted by the core.
            return getEmptyRequest()
        }
        val body = requestBodyFactory.create(
            serializedSegmentPair.first,
            serializedSegmentPair.second
        )
        return resolveRequest(context, body)
    }

    private fun getEmptyRequest(): Request {
        val id = UUID.randomUUID().toString()
        return Request(
            id,
            "",
            "",
            emptyMap(),
            body = ByteArray(0)
        )
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
    private fun resolveRequest(context: DatadogContext, body: RequestBody?): Request {
        if (body != null) {
            val bodyAsByteArray = safeBodyToByteArray(body) ?: return getEmptyRequest()
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
        } else {
            return getEmptyRequest()
        }
    }

    private fun safeBodyToByteArray(body: RequestBody): ByteArray? {
        return try {
            bodyToByteArrayFactory(body)
        } catch (@Suppress("TooGenericExceptionCaught") e: Throwable) {
            internalLogger.log(
                InternalLogger.Level.ERROR,
                InternalLogger.Target.MAINTAINER,
                EXTRACT_BYTE_ARRAY_FROM_BODY_ERROR_MESSAGE,
                e
            )
            null
        }
    }

    // endregion

    companion object {
        internal const val EXTRACT_BYTE_ARRAY_FROM_BODY_ERROR_MESSAGE =
            "Unable to extract the ByteArray from session replay request body."
        private const val UPLOAD_URL = "%s/api/v2/%s"
    }
}
