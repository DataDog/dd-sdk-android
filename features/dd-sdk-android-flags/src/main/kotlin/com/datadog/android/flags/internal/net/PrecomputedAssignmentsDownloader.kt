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
import com.datadog.android.internal.time.TimeProvider
import okhttp3.Call
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/**
 * Downloads precomputed flag assignments from Datadog Feature Flags service.
 *
 * @param callFactory Factory for creating HTTP calls
 * @param internalLogger Logger for error and debug messages
 * @param requestFactory Factory for creating precomputed assignments requests
 * @param timeProvider Provider for parsing date-based Retry-After headers
 * @param requestTimeoutMs SDK timeout for each request, in milliseconds. Zero preserves the call's existing timeout
 * @param requestRetryCount Number of retries after the first attempt
 * @param retryDelay Blocks for the requested delay before a retry
 * @param jitterSource Generates a random delay below the supplied upper bound
 */
@Suppress("LongParameterList")
internal class PrecomputedAssignmentsDownloader(
    private val callFactory: Call.Factory,
    private val internalLogger: InternalLogger,
    private val requestFactory: PrecomputedAssignmentsRequestFactory,
    private val timeProvider: TimeProvider,
    private val requestTimeoutMs: Long,
    private val requestRetryCount: Int = 0,
    private val retryDelay: (Long) -> Unit = {
        @Suppress("UnsafeThirdPartyFunctionCall") // InterruptedException is handled by executeDownloadRequest.
        Thread.sleep(it)
    },
    private val jitterSource: (Long) -> Long = {
        @Suppress("UnsafeThirdPartyFunctionCall") // The supplied upper bound is always positive.
        Random.nextLong(it)
    }
) : PrecomputedAssignmentsReader {

    @WorkerThread
    override fun readPrecomputedFlags(context: EvaluationContext, datadogContext: DatadogContext): String? {
        val request = requestFactory.create(context, datadogContext) ?: return null

        return executeDownloadRequest(request)
    }

    @Suppress("TooGenericExceptionCaught")
    private fun executeDownloadRequest(request: Request): String? {
        var attempt = 0
        var result = executeSingleRequest(request)

        while (shouldRetry(result, attempt)) {
            val retryAfterMs = (result as? DownloadResult.HttpFailure)?.retryAfterMs
            val delayMs = (retryAfterMs ?: 0L) + getBackoffMs(attempt)

            result = try {
                retryDelay(delayMs)
                executeSingleRequest(request)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                DownloadResult.UnexpectedFailure(e, isRetryable = false)
            } catch (e: Throwable) {
                DownloadResult.UnexpectedFailure(e, isRetryable = false)
            }
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

    private fun shouldRetry(result: DownloadResult, attempt: Int): Boolean {
        if (!result.isRetryable || attempt >= requestRetryCount) return false

        val retryAfterMs = (result as? DownloadResult.HttpFailure)?.retryAfterMs
        return retryAfterMs == null || retryAfterMs <= MAX_RETRY_AFTER_MS
    }

    private fun getBackoffMs(attempt: Int): Long {
        val exponentialBackoff = INITIAL_BACKOFF_MS * (1L shl attempt)
        val maximum = if (exponentialBackoff < MAX_BACKOFF_MS) exponentialBackoff else MAX_BACKOFF_MS
        return jitterSource(maximum)
    }

    @Suppress("TooGenericExceptionCaught", "UnsafeThirdPartyFunctionCall")
    private fun executeSingleRequest(request: Request): DownloadResult = try {
        val call = callFactory.newCall(request)
        if (requestTimeoutMs > 0) {
            call.timeout().timeout(requestTimeoutMs, TimeUnit.MILLISECONDS)
        }
        call.execute().use { response ->
            if (response.isSuccessful) {
                DownloadResult.Success(response.body?.string())
            } else {
                DownloadResult.HttpFailure(
                    statusCode = response.code,
                    isRetryable = isRetryableStatus(response.code),
                    retryAfterMs = getRetryAfterDelayMs(response)
                )
            }
        }
    } catch (e: IOException) {
        DownloadResult.UnexpectedFailure(e, isRetryable = true)
    } catch (e: Throwable) {
        DownloadResult.UnexpectedFailure(e, isRetryable = false)
    }

    private fun isRetryableStatus(statusCode: Int): Boolean =
        statusCode == HttpSpec.StatusCode.REQUEST_TIMEOUT ||
            statusCode in HTTP_SERVER_ERROR_MIN..HTTP_SERVER_ERROR_MAX

    @Suppress("ReturnCount") // Early returns keep each Retry-After rejection explicit.
    private fun getRetryAfterDelayMs(response: Response): Long? {
        if (response.code != HttpSpec.StatusCode.SERVICE_UNAVAILABLE) return null

        val value = response.header(HttpSpec.Header.RETRY_AFTER)?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: return null

        if (value.all { it in '0'..'9' }) {
            val seconds = value.toLongOrNull() ?: return Long.MAX_VALUE
            return if (seconds > MAX_RETRY_AFTER_MS / MILLIS_PER_SECOND) {
                Long.MAX_VALUE
            } else {
                seconds * MILLIS_PER_SECOND
            }
        }

        val retryAtMs = HTTP_DATE_FORMATS.firstNotNullOfOrNull { parseHttpDate(value, it) }
            ?: return null
        return (retryAtMs - timeProvider.getDeviceTimestampMillis()).coerceAtLeast(0L)
    }

    @Suppress("UnsafeThirdPartyFunctionCall") // Formats and non-null arguments are controlled by this class.
    private fun parseHttpDate(value: String, format: String): Long? {
        val position = ParsePosition(0)
        val parsed = SimpleDateFormat(format, Locale.US).apply {
            isLenient = false
            timeZone = TimeZone.getTimeZone("GMT")
        }.parse(value, position)

        return parsed?.time?.takeIf { position.index == value.length }
    }

    private sealed interface DownloadResult {
        val isRetryable: Boolean

        data class Success(val body: String?) : DownloadResult {
            override val isRetryable: Boolean = false
        }

        data class HttpFailure(
            val statusCode: Int,
            override val isRetryable: Boolean,
            val retryAfterMs: Long?
        ) : DownloadResult

        data class UnexpectedFailure(
            val throwable: Throwable,
            override val isRetryable: Boolean
        ) : DownloadResult
    }

    private companion object {
        const val HTTP_SERVER_ERROR_MIN = 500
        const val HTTP_SERVER_ERROR_MAX = 599
        const val INITIAL_BACKOFF_MS = 100L
        const val MAX_BACKOFF_MS = 30_000L
        const val MAX_RETRY_AFTER_MS = 30_000L
        const val MILLIS_PER_SECOND = 1_000L

        val HTTP_DATE_FORMATS = listOf(
            "EEE, dd MMM yyyy HH:mm:ss zzz",
            "EEEE, dd-MMM-yy HH:mm:ss zzz",
            "EEE MMM d HH:mm:ss yyyy"
        )
    }
}
