/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.net

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.context.DatadogContext
import com.datadog.android.api.net.Request
import com.datadog.android.api.net.RequestExecutionContext
import com.datadog.android.api.net.RequestFactory
import com.datadog.android.api.storage.RawBatchEvent
import okhttp3.RequestBody
import okio.Buffer
import java.io.EOFException
import java.io.IOException
import java.util.Locale
import java.util.UUID

internal class ResourcesRequestFactory(
    internal val customEndpointUrl: String?,
    private val internalLogger: InternalLogger,
    private val resourceRequestBodyFactory: ResourceRequestBodyFactory =
        ResourceRequestBodyFactory(internalLogger)
) : RequestFactory {

    @Suppress("ThrowingInternalException")
    override fun create(
        context: DatadogContext,
        executionContext: RequestExecutionContext,
        batchData: List<RawBatchEvent>,
        batchMetadata: ByteArray?
    ): Request? {
        val requestBody = resourceRequestBodyFactory
            .create(batchData) ?: return null

        return resolveRequest(context, requestBody)
    }

    private fun resolveRequest(context: DatadogContext, body: RequestBody): Request? {
        val bodyAsByteArray = convertBodyToByteArray(body) ?: return null
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

    private fun convertBodyToByteArray(body: RequestBody): ByteArray? {
        var result: ByteArray? = null
        val buffer = Buffer()

        try {
            body.writeTo(buffer)
        } catch (e: IOException) {
            internalLogger.log(
                level = InternalLogger.Level.ERROR,
                target = InternalLogger.Target.MAINTAINER,
                messageBuilder = { ERROR_CONVERTING_BODY_TO_BYTEARRAY },
                throwable = e
            )
        }

        try {
            result = buffer.readByteArray()
        } catch (e: EOFException) {
            internalLogger.log(
                level = InternalLogger.Level.ERROR,
                target = InternalLogger.Target.MAINTAINER,
                messageBuilder = { ERROR_CONVERTING_BODY_TO_BYTEARRAY },
                throwable = e
            )
        }

        return result
    }

    private fun resolveHeaders(datadogContext: DatadogContext, requestId: String): Map<String, String> {
        return mapOf(
            RequestFactory.HEADER_API_KEY to datadogContext.clientToken,
            RequestFactory.HEADER_EVP_ORIGIN to datadogContext.source,
            RequestFactory.HEADER_EVP_ORIGIN_VERSION to datadogContext.sdkVersion,
            RequestFactory.HEADER_REQUEST_ID to requestId
        )
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
        internal const val APPLICATION_ID = "application_id"
        internal const val UPLOAD_DESCRIPTION = "Session Replay Resource Upload Request"
        internal const val ERROR_CONVERTING_BODY_TO_BYTEARRAY = "Error converting request body to bytearray"
    }
}
