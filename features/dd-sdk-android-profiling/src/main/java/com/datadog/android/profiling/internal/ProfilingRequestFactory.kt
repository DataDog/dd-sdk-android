/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.profiling.internal

import com.datadog.android.api.context.DatadogContext
import com.datadog.android.api.net.Request
import com.datadog.android.api.net.RequestExecutionContext
import com.datadog.android.api.net.RequestFactory
import com.datadog.android.api.storage.RawBatchEvent
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.Buffer
import java.io.IOException
import java.util.UUID

internal class ProfilingRequestFactory(
    internal val customEndpointUrl: String?
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

    @Suppress("UnsafeThirdPartyFunctionCall") // Caught in the caller
    private fun buildRequestBody(batchData: List<RawBatchEvent>): RequestBody {
        val multipartBodyBuilder = MultipartBody.Builder().setType(MultipartBody.FORM)
        batchData.forEach { rawEvent ->
            multipartBodyBuilder.addFormDataPart(
                PERFETTO_FILE_NAME,
                PERFETTO_FILE_NAME,
                rawEvent.metadata.toRequestBody(CONTENT_TYPE_BINARY_TYPE)
            )
            multipartBodyBuilder.addFormDataPart(
                EVENT_NAME_FORM_KEY,
                EVENT_FILE_NAME,
                rawEvent.data.toRequestBody(CONTENT_TYPE_JSON_TYPE)
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
    }
}
