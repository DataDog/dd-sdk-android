/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.net

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.context.DatadogContext
import com.datadog.android.api.net.Request
import com.datadog.android.api.net.RequestExecutionContext
import com.datadog.android.api.net.RequestFactory
import com.datadog.android.api.storage.RawBatchEvent
import com.datadog.android.core.internal.utils.join
import com.datadog.android.internal.utils.toHexString
import com.datadog.android.rum.RumAttributes
import com.datadog.android.rum.internal.domain.event.RumViewEventFilter
import java.security.DigestException
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.util.UUID

internal class RumRequestFactory(
    internal val customEndpointUrl: String?,
    private val viewEventFilter: RumViewEventFilter,
    private val internalLogger: InternalLogger
) : RequestFactory {

    override fun create(
        context: DatadogContext,
        executionContext: RequestExecutionContext,
        batchData: List<RawBatchEvent>,
        batchMetadata: ByteArray?
    ): Request {
        val requestId = UUID.randomUUID().toString()
        val body = viewEventFilter.filterOutRedundantViewEvents(batchData)
            .map { it.data }
            .join(
                separator = PAYLOAD_SEPARATOR,
                internalLogger = internalLogger
            )
        val idempotencyKey = idempotencyKey(body)
        return Request(
            id = requestId,
            description = "RUM Request",
            url = buildUrl(context, executionContext),
            headers = buildHeaders(
                requestId,
                idempotencyKey,
                context
            ),
            body = body,
            contentType = RequestFactory.CONTENT_TYPE_TEXT_UTF8
        )
    }

    private fun buildUrl(context: DatadogContext, executionContext: RequestExecutionContext): String {
        val queryParams = mapOf(
            RequestFactory.QUERY_PARAM_SOURCE to context.source,
            RequestFactory.QUERY_PARAM_TAGS to buildTags(
                context.service,
                context.version,
                context.sdkVersion,
                context.env,
                context.variant,
                executionContext
            )

        )

        val intakeUrl = customEndpointUrl ?: (context.site.intakeEndpoint + "/api/v2/rum")
        val queryParameters = queryParams.map { "${it.key}=${it.value}" }.joinToString("&", prefix = "?")
        return intakeUrl + queryParameters
    }

    private fun buildHeaders(
        requestId: String,
        idempotencyKey: String?,
        context: DatadogContext
    ): Map<String, String> {
        val headers = mutableMapOf(
            RequestFactory.HEADER_API_KEY to context.clientToken,
            RequestFactory.HEADER_EVP_ORIGIN to context.source,
            RequestFactory.HEADER_EVP_ORIGIN_VERSION to context.sdkVersion,
            RequestFactory.HEADER_REQUEST_ID to requestId
        )
        if (idempotencyKey != null) {
            headers[RequestFactory.DD_IDEMPOTENCY_KEY] = idempotencyKey
        }
        return headers
    }

    private fun buildTags(
        serviceName: String,
        version: String,
        sdkVersion: String,
        env: String,
        variant: String,
        executionContext: RequestExecutionContext
    ) = buildString {
        append("${RumAttributes.SERVICE_NAME}:$serviceName").append(",")
            .append("${RumAttributes.APPLICATION_VERSION}:$version").append(",")
            .append("${RumAttributes.SDK_VERSION}:$sdkVersion").append(",")
            .append("${RumAttributes.ENV}:$env")

        if (variant.isNotEmpty()) {
            append(",").append("${RumAttributes.VARIANT}:$variant")
        }
        if (executionContext.previousResponseCode != null) {
            // we had a previous failure
            append(",").append("${RETRY_COUNT_KEY}:${executionContext.attemptNumber}")
            append(",").append("${LAST_FAILURE_STATUS_KEY}:${executionContext.previousResponseCode}")
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun idempotencyKey(byteArray: ByteArray): String? {
        try {
            val digest = MessageDigest.getInstance("SHA-1")
            val hashBytes = digest.digest(byteArray)
            return hashBytes.toHexString()
        } catch (e: DigestException) {
            internalLogger.log(
                InternalLogger.Level.ERROR,
                InternalLogger.Target.MAINTAINER,
                { SHA1_GENERATION_ERROR_MESSAGE },
                e
            )
        } catch (e: IllegalArgumentException) {
            internalLogger.log(
                InternalLogger.Level.ERROR,
                InternalLogger.Target.MAINTAINER,
                { SHA1_GENERATION_ERROR_MESSAGE },
                e
            )
        } catch (e: NoSuchAlgorithmException) {
            internalLogger.log(
                InternalLogger.Level.ERROR,
                InternalLogger.Target.MAINTAINER,
                { SHA1_NO_SUCH_ALGORITHM_EXCEPTION },
                e
            )
        } catch (e: NullPointerException) {
            internalLogger.log(
                InternalLogger.Level.ERROR,
                InternalLogger.Target.MAINTAINER,
                { SHA1_GENERATION_ERROR_MESSAGE },
                e
            )
        }
        return null
    }

    companion object {
        private val PAYLOAD_SEPARATOR = "\n".toByteArray(Charsets.UTF_8)
        internal const val RETRY_COUNT_KEY = "retry_count"
        internal const val LAST_FAILURE_STATUS_KEY = "last_failure_status"
        private const val SHA1_GENERATION_ERROR_MESSAGE = "Cannot generate SHA-1 hash for rum request idempotency key."
        private const val SHA1_NO_SUCH_ALGORITHM_EXCEPTION = "SHA-1 algorithm could not be found in MessageDigest."
    }
}
