/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.domain

import androidx.annotation.VisibleForTesting
import com.datadog.android.api.context.DatadogContext
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.net.Request
import com.datadog.android.api.net.RequestFactory
import com.datadog.android.api.storage.RawBatchEvent
import com.datadog.android.sessionreplay.internal.exception.InvalidPayloadFormatException
import okhttp3.RequestBody
import okio.Buffer
import java.util.Locale
import java.util.UUID

internal class ResourceRequestFactory(
    internal val customEndpointUrl: String?,
    private val resourceRequestBodyFactory: ResourceRequestBodyFactory = ResourceRequestBodyFactory()
) : RequestFactory {

    @Suppress("ThrowingInternalException")
    override fun create(
        context: DatadogContext,
        batchData: List<RawBatchEvent>,
        batchMetadata: ByteArray?
    ): Request {
        val applicationId = getApplicationId(context)
        ?: throw InvalidPayloadFormatException(COULD_NOT_GET_APPLICATION_ID_ERROR)

        val requestBody = resourceRequestBodyFactory
            .create(applicationId, batchData)

        return resolveRequest(context, requestBody)
    }

    private fun getApplicationId(datadogContext: DatadogContext): String? {
        val rumContext = datadogContext.featuresContext[Feature.RUM_FEATURE_NAME]
        return rumContext?.get(APPLICATION_ID) as? String?
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
        private const val APPLICATION_ID = "application_id"
        private const val UPLOAD_DESCRIPTION = "Session Replay Resource Upload Request"

        @VisibleForTesting
        internal const val COULD_NOT_GET_APPLICATION_ID_ERROR =
            "A payload could not be generated because we could not get the applicationId"
    }
}
