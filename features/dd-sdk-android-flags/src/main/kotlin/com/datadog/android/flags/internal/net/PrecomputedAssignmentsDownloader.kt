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
import com.datadog.android.internal.network.HttpSpec
import okhttp3.Call
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Downloads precomputed flag assignments from Datadog Feature Flags service.
 *
 * @param callFactory Factory for creating HTTP calls
 * @param internalLogger Logger for error and debug messages
 * @param requestFactory Factory for creating precomputed assignments requests
 * @param requestTimeoutMs SDK timeout for each request, in milliseconds. Zero preserves the call's existing timeout
 * @param requestRetryCount Number of retries after the first attempt
 */
internal class PrecomputedAssignmentsDownloader(
    private val callFactory: Call.Factory,
    private val internalLogger: InternalLogger,
    private val requestFactory: PrecomputedAssignmentsRequestFactory,
    private val requestTimeoutMs: Long,
    private val requestRetryCount: Int = 0
) : PrecomputedAssignmentsReader {

    @WorkerThread
    override fun readPrecomputedFlags(context: EvaluationContext, datadogContext: DatadogContext): String? {
        val request = requestFactory.create(context, datadogContext) ?: return null

        return executeDownloadRequest(request)
    }

    private fun executeDownloadRequest(request: Request): String? {
        var attempt = 0
        var result = executeSingleRequest(request)

        while (shouldRetry(result, attempt)) {
            result = executeSingleRequest(request)
            attempt++
        }

        return when (result) {
            is DownloadResult.Success -> result.body
            is DownloadResult.HttpFailure -> {
                internalLogger.log(
                    InternalLogger.Level.ERROR,
                    InternalLogger.Target.MAINTAINER,
                    { "Failed to download flags: ${result.statusCode}" }
                )
                internalLogger.log(
                    level = InternalLogger.Level.ERROR,
                    target = InternalLogger.Target.TELEMETRY,
                    messageBuilder = { "Flag assignment server returned error (${result.statusCode})" },
                    onlyOnce = true
                )
                null
            }
            is DownloadResult.UnexpectedFailure -> {
                internalLogger.log(
                    InternalLogger.Level.ERROR,
                    InternalLogger.Target.MAINTAINER,
                    { "Unexpected error while downloading flags" },
                    result.throwable
                )
                null
            }
        }
    }

    private fun shouldRetry(result: DownloadResult, attempt: Int): Boolean =
        result.isRetryable && attempt < requestRetryCount

    @Suppress("TooGenericExceptionCaught", "UnsafeThirdPartyFunctionCall")
    private fun executeSingleRequest(request: Request): DownloadResult {
        var call: Call? = null
        return try {
            val newCall = callFactory.newCall(request)
            call = newCall
            if (requestTimeoutMs > 0) {
                newCall.timeout().timeout(requestTimeoutMs, TimeUnit.MILLISECONDS)
            }
            newCall.execute().use { response ->
                if (response.isSuccessful) {
                    DownloadResult.Success(response.body?.string())
                } else {
                    DownloadResult.HttpFailure(
                        statusCode = response.code,
                        isRetryable = isRetryableStatus(response.code)
                    )
                }
            }
        } catch (e: IOException) {
            DownloadResult.UnexpectedFailure(e, isRetryable = call?.isCanceled() != true)
        } catch (e: Throwable) {
            DownloadResult.UnexpectedFailure(e, isRetryable = false)
        }
    }

    private fun isRetryableStatus(statusCode: Int): Boolean =
        statusCode == HttpSpec.StatusCode.REQUEST_TIMEOUT ||
            statusCode in HTTP_SERVER_ERROR_MIN..HTTP_SERVER_ERROR_MAX

    private sealed interface DownloadResult {
        val isRetryable: Boolean

        data class Success(val body: String?) : DownloadResult {
            override val isRetryable: Boolean = false
        }

        data class HttpFailure(
            val statusCode: Int,
            override val isRetryable: Boolean
        ) : DownloadResult

        data class UnexpectedFailure(
            val throwable: Throwable,
            override val isRetryable: Boolean
        ) : DownloadResult
    }

    private companion object {
        const val HTTP_SERVER_ERROR_MIN = 500
        const val HTTP_SERVER_ERROR_MAX = 599
    }
}
