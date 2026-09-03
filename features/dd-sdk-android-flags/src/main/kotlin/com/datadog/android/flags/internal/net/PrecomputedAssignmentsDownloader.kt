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
import okhttp3.Response
import okio.Timeout
import java.io.EOFException
import java.io.IOException
import java.io.InterruptedIOException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * Downloads precomputed flag assignments from Datadog Feature Flags service.
 *
 * @param callFactory Factory for creating HTTP calls
 * @param internalLogger Logger for error and debug messages
 * @param requestFactory Factory for creating precomputed assignments requests
 * @param requestTimeoutMs SDK timeout for each request, in milliseconds. Zero preserves the call's existing timeout.
 * If both timeouts are nonzero, the shorter timeout applies
 * @param requestRetryCount Number of retries after the first attempt
 * @param retryScheduler Scheduler for the delay before each retry
 */
internal class PrecomputedAssignmentsDownloader(
    private val callFactory: Call.Factory,
    private val internalLogger: InternalLogger,
    private val requestFactory: PrecomputedAssignmentsRequestFactory,
    private val requestTimeoutMs: Long,
    private val requestRetryCount: Int = 0,
    private val retryScheduler: AssignmentRequestRetryScheduler = RandomizedAssignmentRequestRetryScheduler()
) : PrecomputedAssignmentsReader {

    @WorkerThread
    override fun readPrecomputedFlags(context: EvaluationContext, datadogContext: DatadogContext): String? {
        val request = requestFactory.create(context, datadogContext) ?: return null

        return executeDownloadRequest(request)
    }

    private fun executeDownloadRequest(request: Request): String? {
        var attempt = 0
        var result: DownloadResult

        while (true) {
            result = executeSingleRequest(request)
            if (!shouldRetry(result, attempt) || !awaitRetry(result, attempt)) break
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

    private fun awaitRetry(result: DownloadResult, attempt: Int): Boolean {
        val retryAfter = (result as? DownloadResult.HttpFailure)?.retryAfter
        return try {
            retryScheduler.awaitRetry(attempt, retryAfter)
        } catch (e: InterruptedException) {
            @Suppress("UnsafeThirdPartyFunctionCall") // Preserve the cancellation signal for the executor.
            Thread.currentThread().interrupt()
            false
        }
    }

    @Suppress("TooGenericExceptionCaught", "UnsafeThirdPartyFunctionCall")
    private fun executeSingleRequest(request: Request): DownloadResult {
        var call: Call? = null
        return try {
            val newCall = callFactory.newCall(request)
            call = newCall
            if (requestTimeoutMs > 0) {
                applyRequestTimeout(newCall)
            }
            val response = newCall.execute()
            try {
                if (response.isSuccessful) {
                    DownloadResult.Success(response.body?.string())
                } else {
                    DownloadResult.HttpFailure(
                        statusCode = response.code,
                        retryAfter = response.header(RETRY_AFTER_HEADER_NAME).takeIf {
                            response.code == HttpSpec.StatusCode.SERVICE_UNAVAILABLE
                        },
                        isRetryable = isRetryableStatus(response.code)
                    )
                }
            } finally {
                closeResponse(response)
            }
        } catch (e: IOException) {
            DownloadResult.UnexpectedFailure(
                e,
                isRetryable = isRetryableNetworkError(e, call?.isCanceled() == true)
            )
        } catch (e: Throwable) {
            DownloadResult.UnexpectedFailure(e, isRetryable = false)
        }
    }

    @Suppress(
        "CheckInternal", // Reject an invalid custom Call before an unbounded request starts.
        "UnsafeThirdPartyFunctionCall" // executeSingleRequest catches custom Call failures.
    )
    private fun applyRequestTimeout(call: Call) {
        val timeout = call.timeout()
        check(timeout !== Timeout.NONE) {
            "A custom assignment request call must provide a configurable timeout"
        }
        val requestTimeoutNanos = TimeUnit.MILLISECONDS.toNanos(requestTimeoutMs)
        val callTimeoutNanos = timeout.timeoutNanos()
        if (callTimeoutNanos == 0L || requestTimeoutNanos < callTimeoutNanos) {
            timeout.timeout(requestTimeoutMs, TimeUnit.MILLISECONDS)
        }
    }

    @Suppress("SwallowedException", "TooGenericExceptionCaught", "UnsafeThirdPartyFunctionCall")
    private fun closeResponse(response: Response) {
        // A custom Call.Factory can return a response without a body. Response.close() rejects that shape.
        if (response.body == null) return
        try {
            response.close()
        } catch (_: Exception) {
            // Cleanup must not replace the HTTP result or prevent a retry.
        }
    }

    private fun isRetryableStatus(statusCode: Int): Boolean =
        statusCode == HttpSpec.StatusCode.REQUEST_TIMEOUT ||
            statusCode in HTTP_SERVER_ERROR_MIN..HTTP_SERVER_ERROR_MAX

    private fun isRetryableNetworkError(error: IOException, wasCanceled: Boolean): Boolean {
        if (wasCanceled && !error.isOkHttpCallTimeout()) return false

        return when (error) {
            is SocketTimeoutException,
            is UnknownHostException,
            is SocketException,
            is EOFException -> true
            is InterruptedIOException -> error.isOkHttpCallTimeout()
            else -> false
        }
    }

    // OkHttp 4.12 cancels a call before it reports a call-timeout InterruptedIOException.
    private fun IOException.isOkHttpCallTimeout(): Boolean =
        this is InterruptedIOException && message == OKHTTP_CALL_TIMEOUT_MESSAGE

    private sealed interface DownloadResult {
        val isRetryable: Boolean

        data class Success(val body: String?) : DownloadResult {
            override val isRetryable: Boolean = false
        }

        data class HttpFailure(
            val statusCode: Int,
            val retryAfter: String?,
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
        const val OKHTTP_CALL_TIMEOUT_MESSAGE = "timeout"
        const val RETRY_AFTER_HEADER_NAME = "Retry-After"
    }
}

internal fun interface AssignmentRequestRetryScheduler {
    /**
     * Waits before the next retry.
     *
     * @return false when the server delay is too long and the request must not be retried
     */
    @Throws(InterruptedException::class)
    fun awaitRetry(attempt: Int, retryAfter: String?): Boolean
}

internal class RandomizedAssignmentRequestRetryScheduler(
    private val randomLong: (Long) -> Long = { upperBound ->
        @Suppress("UnsafeThirdPartyFunctionCall") // The upper bound is always positive.
        Random.nextLong(upperBound)
    },
    private val currentTimeMillis: () -> Long = System::currentTimeMillis,
    private val sleeper: (Long) -> Unit = Thread::sleep
) : AssignmentRequestRetryScheduler {

    override fun awaitRetry(attempt: Int, retryAfter: String?): Boolean {
        val retryAfterDelay = parseRetryAfter(retryAfter)
        if (retryAfterDelay is RetryAfterDelay.TooLong) return false

        val backoffUpperBound = min(INITIAL_BACKOFF_MS * (1L shl attempt), MAX_BACKOFF_MS)
        val backoffMs = randomLong(backoffUpperBound)
        val delayMs = (retryAfterDelay as? RetryAfterDelay.Accepted)?.milliseconds.orZero() + backoffMs
        if (delayMs > 0) sleeper(delayMs)
        return true
    }

    private fun parseRetryAfter(value: String?): RetryAfterDelay {
        val trimmedValue = value?.trim() ?: return RetryAfterDelay.NotProvided
        return when {
            trimmedValue.matches(DIGITS_REGEX) -> parseDeltaSeconds(trimmedValue)
            trimmedValue.isEmpty() || trimmedValue.toDoubleOrNull() != null -> RetryAfterDelay.NotProvided
            else -> parseRetryAfterDate(trimmedValue)
        }
    }

    private fun parseDeltaSeconds(value: String): RetryAfterDelay {
        val seconds = value.toLongOrNull()
        return if (seconds == null || seconds > MAX_RETRY_AFTER_MS / MILLISECONDS_PER_SECOND) {
            RetryAfterDelay.TooLong
        } else {
            RetryAfterDelay.Accepted(seconds * MILLISECONDS_PER_SECOND)
        }
    }

    private fun parseRetryAfterDate(value: String): RetryAfterDelay {
        val dateMs = parseHttpDate(value) ?: return RetryAfterDelay.NotProvided
        val delayMs = max(dateMs - currentTimeMillis(), 0)
        return if (delayMs > MAX_RETRY_AFTER_MS) {
            RetryAfterDelay.TooLong
        } else {
            RetryAfterDelay.Accepted(delayMs)
        }
    }

    @Suppress("UnsafeThirdPartyFunctionCall") // All parser inputs are non-null and parse failures return null.
    private fun parseHttpDate(value: String): Long? {
        HTTP_DATE_FORMATS.forEach { pattern ->
            val formatter = SimpleDateFormat(pattern, Locale.US).apply {
                isLenient = false
                timeZone = TimeZone.getTimeZone("GMT")
            }
            val position = ParsePosition(0)
            val date = formatter.parse(value, position)
            if (date != null && position.index == value.length) return date.time
        }
        return null
    }

    private fun Long?.orZero(): Long = this ?: 0L

    private sealed interface RetryAfterDelay {
        data object NotProvided : RetryAfterDelay
        data object TooLong : RetryAfterDelay
        data class Accepted(val milliseconds: Long) : RetryAfterDelay
    }

    private companion object {
        const val INITIAL_BACKOFF_MS = 100L
        const val MAX_BACKOFF_MS = 30_000L
        const val MAX_RETRY_AFTER_MS = 30_000L
        const val MILLISECONDS_PER_SECOND = 1_000L
        val DIGITS_REGEX = Regex("^\\d+$")
        val HTTP_DATE_FORMATS = listOf(
            "EEE, dd MMM yyyy HH:mm:ss zzz",
            "EEEE, dd-MMM-yy HH:mm:ss zzz",
            "EEE MMM d HH:mm:ss yyyy"
        )
    }
}
