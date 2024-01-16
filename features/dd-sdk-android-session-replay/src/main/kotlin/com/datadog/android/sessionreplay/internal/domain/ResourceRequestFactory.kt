/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.domain

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.context.DatadogContext
import com.datadog.android.api.net.Request
import com.datadog.android.api.net.RequestFactory
import com.datadog.android.api.storage.RawBatchEvent
import com.datadog.android.sessionreplay.internal.exception.InvalidPayloadFormatException
import com.datadog.android.sessionreplay.internal.net.RawEventsToResourcesMapper
import com.datadog.android.sessionreplay.internal.recorder.SessionReplayResource
import okhttp3.RequestBody
import okio.Buffer
import java.io.IOException
import java.io.ObjectInputStream
import java.io.StreamCorruptedException
import java.lang.NullPointerException
import java.util.Locale
import java.util.UUID

internal class ResourceRequestFactory(
    internal val customEndpointUrl: String?,
    private val rawEventsToResourcesMapper: RawEventsToResourcesMapper,
    private val internalLogger: InternalLogger,
    private val resourceRequestBodyFactory: ResourceRequestBodyFactory = ResourceRequestBodyFactory()
) : RequestFactory {

    override fun create(
        context: DatadogContext,
        batchData: List<RawBatchEvent>,
        batchMetadata: ByteArray?
    ): Request {
        val resources = batchData.mapNotNull { rawBatchEvent ->
            extractSessionReplayResource(rawBatchEvent)
        }

        if (resources.isEmpty()) {
            @Suppress("ThrowingInternalException")
            throw InvalidPayloadFormatException(INVALID_PAYLOAD_FORMAT_ERROR)
        }

        val requestBody = resourceRequestBodyFactory.create(resources, internalLogger)
        return resolveRequest(context, requestBody)
    }

    private fun resolveRequest(context: DatadogContext, body: RequestBody): Request {
        val bodyAsByteArray = extractByteArrayFromBody(body)
        val requestId = UUID.randomUUID().toString()
        val description = UPLOAD_DESCRIPTION
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

    private fun resolveHeaders(datadogContext: DatadogContext, requestId: String): Map<String, String> {
        return mapOf(
            RequestFactory.HEADER_API_KEY to datadogContext.clientToken,
            RequestFactory.HEADER_EVP_ORIGIN to datadogContext.source,
            RequestFactory.HEADER_EVP_ORIGIN_VERSION to datadogContext.sdkVersion,
            RequestFactory.HEADER_REQUEST_ID to requestId
        )
    }

    @SuppressWarnings("TooGenericExceptionCaught", "ThrowingInternalException")
    private fun extractSessionReplayResource(rawBatchEvent: RawBatchEvent): SessionReplayResource? {
        @Suppress("UnsafeThirdPartyFunctionCall")
        return runCatching {
            val ois = ObjectInputStream(rawBatchEvent.data.inputStream())
            rawEventsToResourcesMapper
                .map(ois)
        }.getOrElse {
            when (it) {
                is StreamCorruptedException,
                is NullPointerException,
                is SecurityException,
                is IOException -> {
                    throw InvalidPayloadFormatException(
                        INVALID_PAYLOAD_FORMAT_ERROR
                    )
                }
                else -> throw it
            }
        }
    }

    private fun buildUrl(datadogContext: DatadogContext): String {
        return String.format(
            Locale.US,
            UPLOAD_URL,
            customEndpointUrl ?: datadogContext.site.intakeEndpoint,
            "replay"
        )
    }

    companion object {
        private const val UPLOAD_URL = "%s/api/v2/%s"
        private const val UPLOAD_DESCRIPTION = "Session Replay Resource Upload Request"
        private const val INVALID_PAYLOAD_FORMAT_ERROR =
            "The payload format was broken and an upload request could not be created"
    }
}
