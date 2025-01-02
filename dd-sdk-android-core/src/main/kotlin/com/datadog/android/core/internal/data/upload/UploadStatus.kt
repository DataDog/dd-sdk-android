/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.data.upload

import com.datadog.android.api.InternalLogger
import java.util.Locale

internal sealed class UploadStatus(
    val shouldRetry: Boolean = false,
    val code: Int = UNKNOWN_RESPONSE_CODE,
    val throwable: Throwable? = null
) {

    internal class Success(responseCode: Int) : UploadStatus(shouldRetry = false, code = responseCode)

    internal class NetworkError(throwable: Throwable) :
        UploadStatus(shouldRetry = true, throwable = throwable)

    internal class DNSError(throwable: Throwable) :
        UploadStatus(shouldRetry = true, throwable = throwable)

    internal class RequestCreationError(throwable: Throwable?) :
        UploadStatus(shouldRetry = false, throwable = throwable)

    internal class InvalidTokenError(responseCode: Int) : UploadStatus(shouldRetry = false, code = responseCode)
    internal class HttpRedirection(responseCode: Int) : UploadStatus(shouldRetry = false, code = responseCode)
    internal class HttpClientError(responseCode: Int) : UploadStatus(shouldRetry = false, code = responseCode)
    internal class HttpServerError(responseCode: Int) : UploadStatus(shouldRetry = true, code = responseCode)
    internal class HttpClientRateLimiting(responseCode: Int) : UploadStatus(shouldRetry = true, code = responseCode)
    internal class UnknownHttpError(responseCode: Int) : UploadStatus(shouldRetry = false, code = responseCode)
    internal class UnknownException(throwable: Throwable) : UploadStatus(shouldRetry = true, throwable = throwable)
    internal object UnknownStatus : UploadStatus(shouldRetry = false, code = UNKNOWN_RESPONSE_CODE)

    fun logStatus(
        context: String,
        byteSize: Int,
        logger: InternalLogger,
        attempts: Int,
        requestId: String? = null
    ) {
        val level = resolveInternalLogLevel()
        val targets = resolveInternalLogTarget()
        logger.log(
            level,
            targets,
            {
                buildStatusMessage(requestId, byteSize, context, throwable, attempts)
            }
        )
    }

    private fun resolveInternalLogTarget() = when (this) {
        is HttpClientError,
        is HttpClientRateLimiting,
        is UnknownStatus -> listOf(InternalLogger.Target.USER, InternalLogger.Target.TELEMETRY)

        is DNSError,
        is HttpRedirection,
        is HttpServerError,
        is InvalidTokenError,
        is NetworkError,
        is RequestCreationError,
        is Success,
        is UnknownException,
        is UnknownHttpError -> listOf(InternalLogger.Target.USER)
    }

    private fun resolveInternalLogLevel() = when (this) {
        is HttpClientError,
        is HttpServerError,
        is InvalidTokenError,
        is RequestCreationError,
        is UnknownException,
        is UnknownHttpError -> InternalLogger.Level.ERROR

        is DNSError,
        is HttpClientRateLimiting,
        is HttpRedirection,
        is UnknownStatus,
        is NetworkError -> InternalLogger.Level.WARN

        is Success -> InternalLogger.Level.INFO
    }

    private fun buildStatusMessage(
        requestId: String?,
        byteSize: Int,
        context: String,
        throwable: Throwable?,
        requestAttempts: Int
    ): String {
        val buildString = buildString {
            if (requestId == null) {
                append("Batch [$byteSize bytes] ($context)")
            } else {
                append("Batch $requestId [$byteSize bytes] ($context)")
            }

            when (this@UploadStatus) {
                is DNSError -> append(" failed because of a DNS error")
                is HttpClientError -> append(" failed because of a processing error or invalid data")
                is HttpClientRateLimiting -> append(" failed because of an intake rate limitation")
                is HttpRedirection -> append(" failed because of a network redirection")
                is HttpServerError -> append(" failed because of a server processing error")
                is InvalidTokenError -> append(" failed because your token is invalid")
                is NetworkError -> append(" failed because of a network error")
                is RequestCreationError -> append(" failed because of an error when creating the request")
                is UnknownException -> append(" failed because of an unknown error")
                is UnknownHttpError -> append(" failed because of an unexpected HTTP error (status code = $code)")
                is UnknownStatus -> append(" status is unknown")
                is Success -> append(" sent successfully.")
            }

            if (throwable != null) {
                append(" (")
                append(throwable.javaClass.name)
                append(": ")
                append(throwable.message)
                append(")")
            }

            if (shouldRetry) {
                append("; we will retry later.")
            } else if (this@UploadStatus !is Success) {
                append("; the batch was dropped.")
            }

            if (this@UploadStatus is InvalidTokenError) {
                append(
                    " Make sure that the provided token still exists " +
                        "and you're targeting the relevant Datadog site."
                )
            }
            append(
                ATTEMPTS_LOG_MESSAGE_FORMAT.format(
                    Locale.US,
                    requestAttempts,
                    code
                )
            )
        }
        return buildString
    }

    companion object {
        internal const val UNKNOWN_RESPONSE_CODE = 0
        internal const val ATTEMPTS_LOG_MESSAGE_FORMAT = " This request was attempted %d time(s)."
    }
}
