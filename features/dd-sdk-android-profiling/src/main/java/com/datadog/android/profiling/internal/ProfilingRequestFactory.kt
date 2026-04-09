/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.profiling.internal

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.context.DatadogContext
import com.datadog.android.api.net.Request
import com.datadog.android.api.net.RequestExecutionContext
import com.datadog.android.api.net.RequestFactory
import com.datadog.android.api.storage.RawBatchEvent
import com.datadog.android.profiling.internal.domain.ProfilingBatchMetadata
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.Buffer
import java.io.IOException
import java.util.Locale
import java.util.UUID

internal class ProfilingRequestFactory(
    internal val customEndpointUrl: String?,
    private val internalLogger: InternalLogger
) : RequestFactory {

    @Throws(IOException::class)
    override fun create(
        context: DatadogContext,
        executionContext: RequestExecutionContext,
        batchData: List<RawBatchEvent>,
        batchMetadata: ByteArray?
    ): Request {
        val requestId = UUID.randomUUID().toString()
        val requestBody = buildRequestBody(batchData)
        return Request(
            id = requestId,
            description = PROFILING_REQUEST_DESCRIPTION,
            url = buildUrl(context),
            headers = buildHeaders(requestId, context),
            body = extractByteArrayFromBody(requestBody),
            contentType = requestBody.contentType().toString()
        )
    }

    private fun buildHeaders(
        requestId: String,
        context: DatadogContext
    ): Map<String, String> {
        return mapOf(
            RequestFactory.HEADER_API_KEY to context.clientToken,
            RequestFactory.HEADER_EVP_ORIGIN to context.source,
            RequestFactory.HEADER_EVP_ORIGIN_VERSION to context.sdkVersion,
            RequestFactory.HEADER_REQUEST_ID to requestId
        )
    }

    private fun buildUrl(
        context: DatadogContext
    ): String {
        return customEndpointUrl ?: (context.site.intakeEndpoint + "/api/v2/profile")
    }

    @Suppress("UnsafeThirdPartyFunctionCall", "ThrowingInternalException") // Caught in the caller
    private fun buildRequestBody(batchData: List<RawBatchEvent>): RequestBody {
        if (batchData.isEmpty()) {
            throw IllegalStateException(EMPTY_BATCH_DATA_ERROR_MESSAGE)
        }
        if (batchData.size > 1) {
            internalLogger.log(
                InternalLogger.Level.WARN,
                InternalLogger.Target.MAINTAINER,
                { MULTIPLE_BATCH_EVENTS_WARNING_MESSAGE.format(Locale.US, batchData.size) }
            )
        }
        val multipartBodyBuilder = MultipartBody.Builder().setType(MultipartBody.FORM)
        val rawBatchEvent = batchData.first()
        multipartBodyBuilder.addFormDataPart(
            EVENT_NAME_FORM_KEY,
            EVENT_FILE_NAME,
            rawBatchEvent.data.toRequestBody(CONTENT_TYPE_JSON_TYPE)
        )
        val eventMetadata = ProfilingBatchMetadata.fromBytesOrNull(rawBatchEvent.metadata, internalLogger)
        if (eventMetadata != null) {
            multipartBodyBuilder.addFormDataPart(
                PERFETTO_FILE_NAME,
                PERFETTO_FILE_NAME,
                eventMetadata.perfettoBytes.toRequestBody(CONTENT_TYPE_BINARY_TYPE)
            )
            // TODO RUM-15408: Wait for profiling-backend to support RUM events labelling
            /*multipartBodyBuilder.addFormDataPart(
                RUM_MOBILE_EVENTS_FILE_NAME,
                RUM_MOBILE_EVENTS_FILE_NAME,
                batchMetadata.rumMobileEventsBytes.toRequestBody(CONTENT_TYPE_JSON_TYPE)
            )*/
        } else {
            multipartBodyBuilder.addFormDataPart(
                PERFETTO_FILE_NAME,
                PERFETTO_FILE_NAME,
                rawBatchEvent.metadata.toRequestBody(CONTENT_TYPE_BINARY_TYPE)
            )
        }
        return multipartBodyBuilder.build()
    }

    @Suppress("UnsafeThirdPartyFunctionCall") // Caught in the caller
    private fun extractByteArrayFromBody(body: RequestBody): ByteArray {
        val buffer = Buffer()
        body.writeTo(buffer)
        return buffer.readByteArray()
    }

    companion object {
        private const val PROFILING_REQUEST_DESCRIPTION = "Profiling Request"
        private const val PERFETTO_FILE_NAME = "perfetto.proto"
        private const val EVENT_NAME_FORM_KEY = "event"
        private const val EVENT_FILE_NAME = "event.json"
        private val CONTENT_TYPE_BINARY_TYPE = "application/octet-stream".toMediaTypeOrNull()
        private val CONTENT_TYPE_JSON_TYPE = "application/json".toMediaTypeOrNull()
        internal const val EMPTY_BATCH_DATA_ERROR_MESSAGE =
            "Cannot build profiling request: batchData is empty."
        internal const val MULTIPLE_BATCH_EVENTS_WARNING_MESSAGE =
            "Expected a single profiling event per batch, but got %d. Using the first event."
    }
}
