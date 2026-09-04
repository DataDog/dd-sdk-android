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
import okio.Timeout
import java.util.concurrent.TimeUnit

/**
 * Downloads precomputed flag assignments from Datadog Feature Flags service.
 *
 * @param callFactory Factory for creating HTTP calls
 * @param internalLogger Logger for error and debug messages
 * @param requestFactory Factory for creating precomputed assignments requests
 * @param requestTimeoutMs SDK timeout for the request, in milliseconds. Zero preserves the call timeout
 */
internal class PrecomputedAssignmentsDownloader(
    private val callFactory: Call.Factory,
    private val internalLogger: InternalLogger,
    private val requestFactory: PrecomputedAssignmentsRequestFactory,
    private val requestTimeoutMs: Long = 0
) : PrecomputedAssignmentsReader {

    @WorkerThread
    override fun readPrecomputedFlags(context: EvaluationContext, datadogContext: DatadogContext): String? {
        val request = requestFactory.create(context, datadogContext) ?: return null

        return executeDownloadRequest(request)
    }

    @Suppress("TooGenericExceptionCaught")
    private fun executeDownloadRequest(request: Request): String? = try {
        val call = callFactory.newCall(request)
        if (requestTimeoutMs > 0) {
            applyRequestTimeout(call)
        }
        val response = call.execute()
        handleResponse(response)
    } catch (e: Throwable) {
        internalLogger.log(
            InternalLogger.Level.ERROR,
            InternalLogger.Target.MAINTAINER,
            { "Unexpected error while downloading flags" },
            e
        )
        null
    }

    @Suppress(
        "CheckInternal", // Reject an invalid custom Call before an unbounded request starts.
        "UnsafeThirdPartyFunctionCall" // The caller catches custom Call failures.
    )
    private fun applyRequestTimeout(call: Call) {
        val timeout = call.timeout()
        check(timeout !== Timeout.NONE) {
            "The assignment request Call.Factory must provide a configurable Call.timeout()"
        }

        val requestTimeoutNanos = TimeUnit.MILLISECONDS.toNanos(requestTimeoutMs)
        val callTimeoutNanos = timeout.timeoutNanos()
        if (callTimeoutNanos == 0L || requestTimeoutNanos < callTimeoutNanos) {
            timeout.timeout(requestTimeoutMs, TimeUnit.MILLISECONDS)
        }
    }

    private fun handleResponse(response: Response): String? = if (response.isSuccessful) {
        @Suppress("UnsafeThirdPartyFunctionCall") // Safe: wrapped in outer try-catch
        response.body?.use { it.string() }
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
}
