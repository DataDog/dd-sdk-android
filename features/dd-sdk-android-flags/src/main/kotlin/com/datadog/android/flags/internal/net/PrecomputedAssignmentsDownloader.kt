/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal.net

import androidx.annotation.WorkerThread
import com.datadog.android.api.InternalLogger
import com.datadog.android.api.context.DatadogContext
import com.datadog.android.flags.model.EvaluationContext
import okhttp3.Call
import okhttp3.Request
import okhttp3.Response
import okio.Buffer
import java.nio.charset.StandardCharsets
import kotlin.math.min

/**
 * Downloads precomputed flag assignments from Datadog Feature Flags service.
 *
 * @param callFactory Factory for creating HTTP calls
 * @param internalLogger Logger for error and debug messages
 * @param requestFactory Factory for creating precomputed assignments requests
 * @param payloadVerifier Verifier for protected assignment responses
 */
internal class PrecomputedAssignmentsDownloader(
    private val callFactory: Call.Factory,
    private val internalLogger: InternalLogger,
    private val requestFactory: PrecomputedAssignmentsRequestFactory,
    private val payloadVerifier: AssignmentPayloadVerifier
) : PrecomputedAssignmentsReader {

    @WorkerThread
    override fun readPrecomputedFlags(
        context: EvaluationContext,
        datadogContext: DatadogContext
    ): PrecomputedAssignmentsPayload? {
        val request = requestFactory.create(context, datadogContext) ?: return null

        return executeDownloadRequest(request)
    }

    @Suppress("TooGenericExceptionCaught")
    private fun executeDownloadRequest(request: Request): PrecomputedAssignmentsPayload? = try {
        val response = callFactory.newCall(request).execute()
        handleResponse(request, response)
    } catch (e: AssignmentPayloadVerificationException) {
        internalLogger.log(
            InternalLogger.Level.ERROR,
            InternalLogger.Target.MAINTAINER,
            { "Rejected an unverified flag assignments response" },
            e
        )
        internalLogger.log(
            level = InternalLogger.Level.ERROR,
            target = InternalLogger.Target.TELEMETRY,
            messageBuilder = { "Rejected an unverified flag assignments response" },
            throwable = e,
            onlyOnce = true
        )
        null
    } catch (e: Throwable) {
        internalLogger.log(
            InternalLogger.Level.ERROR,
            InternalLogger.Target.MAINTAINER,
            { "Unexpected error while downloading flags" },
            e
        )
        null
    }

    private fun handleResponse(request: Request, response: Response): PrecomputedAssignmentsPayload? = if (
        response.isSuccessful
    ) {
        @Suppress("UnsafeThirdPartyFunctionCall") // Safe: wrapped in outer try-catch
        response.body?.use {
            val responseBody = readBoundedResponseBody(response)
            val protectedEnvelope = payloadVerifier.verify(request, response, responseBody)
            PrecomputedAssignmentsPayload(
                body = responseBody.toString(StandardCharsets.UTF_8),
                protectedEnvelope = protectedEnvelope
            )
        }
    } else {
        internalLogger.log(
            InternalLogger.Level.ERROR,
            InternalLogger.Target.MAINTAINER,
            { "Failed to download flags: ${response.code}" }
        )

        internalLogger.log(
            level = InternalLogger.Level.ERROR,
            target = InternalLogger.Target.TELEMETRY,
            messageBuilder = { "Flag assignment server returned error (${response.code})" },
            onlyOnce = true
        )

        @Suppress("UnsafeThirdPartyFunctionCall") // Safe: wrapped in outer try-catch
        response.body?.close()

        null
    }

    @Suppress(
        "RequireInternal",
        "ThrowingInternalException",
        "UnsafeThirdPartyFunctionCall"
    ) // The outer request boundary catches these failures and rejects the response.
    private fun readBoundedResponseBody(response: Response): ByteArray {
        val body = requireNotNull(response.body)
        val declaredLength = body.contentLength()
        if (declaredLength > PrecomputedAssignmentsVerifier.MAX_RESPONSE_BODY_BYTES) {
            throw AssignmentPayloadVerificationException("Flag assignment response body is too large")
        }

        val buffer = Buffer()
        val source = body.source()
        var total = 0L
        val maximum = PrecomputedAssignmentsVerifier.MAX_RESPONSE_BODY_BYTES.toLong()
        while (true) {
            val remaining = maximum - total + 1
            val count = source.read(buffer, min(READ_CHUNK_BYTES, remaining))
            if (count == -1L) break
            total += count
            if (total > maximum) {
                throw AssignmentPayloadVerificationException("Flag assignment response body is too large")
            }
        }
        return buffer.readByteArray()
    }

    private companion object {
        const val READ_CHUNK_BYTES = 8_192L
    }
}
