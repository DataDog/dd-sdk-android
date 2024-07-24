/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.data.upload

import com.datadog.android.api.InternalLogger

@Suppress("StringLiteralDuplication")
internal sealed class UploadStatus(val shouldRetry: Boolean = false, val code: Int = UNKNOWN_RESPONSE_CODE) {

    internal class Success(responseCode: Int) : UploadStatus(shouldRetry = false, code = responseCode)
    internal object NetworkError : UploadStatus(shouldRetry = true)
    internal object DNSError : UploadStatus(shouldRetry = true)
    internal object RequestCreationError : UploadStatus(shouldRetry = false)
    internal class InvalidTokenError(responseCode: Int) : UploadStatus(shouldRetry = false, code = responseCode)
    internal class HttpRedirection(responseCode: Int) : UploadStatus(shouldRetry = false, code = responseCode)
    internal class HttpClientError(responseCode: Int) : UploadStatus(shouldRetry = false, code = responseCode)
    internal class HttpServerError(responseCode: Int) : UploadStatus(shouldRetry = true, code = responseCode)
    internal class HttpClientRateLimiting(responseCode: Int) : UploadStatus(shouldRetry = true, code = responseCode)
    internal class UnknownError(responseCode: Int) : UploadStatus(shouldRetry = false, code = responseCode)

    internal object UnknownStatus : UploadStatus(shouldRetry = false, code = UNKNOWN_RESPONSE_CODE)

    @SuppressWarnings("LongMethod")
    fun logStatus(
        context: String,
        byteSize: Int,
        logger: InternalLogger,
        requestId: String? = null
    ) {
        val batchInfo = if (requestId == null) {
            "Batch [$byteSize bytes] ($context)"
        } else {
            "Batch $requestId [$byteSize bytes] ($context)"
        }
        when (this) {
            is NetworkError -> logger.log(
                InternalLogger.Level.WARN,
                InternalLogger.Target.USER,
                { "$batchInfo failed because of a network error; we will retry later." }
            )

            is DNSError -> logger.log(
                InternalLogger.Level.WARN,
                InternalLogger.Target.USER,
                { "$batchInfo failed because of a DNS error; we will retry later." }
            )

            is InvalidTokenError -> logger.log(
                InternalLogger.Level.ERROR,
                InternalLogger.Target.USER,
                {
                    "$batchInfo failed because your token is invalid; the batch was dropped. " +
                        "Make sure that the provided token still exists " +
                        "and you're targeting the relevant Datadog site."
                }
            )

            is HttpRedirection -> logger.log(
                InternalLogger.Level.WARN,
                InternalLogger.Target.USER,
                { "$batchInfo failed because of a network redirection; the batch was dropped." }
            )

            is HttpClientError -> {
                logger.log(
                    InternalLogger.Level.ERROR,
                    listOf(InternalLogger.Target.USER, InternalLogger.Target.TELEMETRY),
                    {
                        "$batchInfo failed because of a processing error or invalid data; " +
                            "the batch was dropped."
                    }
                )
            }

            is HttpClientRateLimiting -> {
                logger.log(
                    InternalLogger.Level.WARN,
                    listOf(InternalLogger.Target.USER, InternalLogger.Target.TELEMETRY),
                    { "$batchInfo not uploaded due to rate limitation; we will retry later." }
                )
            }

            is HttpServerError -> logger.log(
                InternalLogger.Level.ERROR,
                InternalLogger.Target.USER,
                { "$batchInfo failed because of a server processing error; we will retry later." }
            )

            is UnknownError -> logger.log(
                InternalLogger.Level.ERROR,
                InternalLogger.Target.USER,
                { "$batchInfo failed because of an unknown error (status code = $code); the batch was dropped." }
            )

            is RequestCreationError -> logger.log(
                InternalLogger.Level.ERROR,
                InternalLogger.Target.USER,
                {
                    "$batchInfo failed because of an error when creating the request; " +
                        "the batch was dropped."
                }
            )

            is Success -> logger.log(
                InternalLogger.Level.INFO,
                InternalLogger.Target.USER,
                { "$batchInfo sent successfully." }
            )

            else -> {
                // no-op
            }
        }
    }
    companion object {
        internal const val UNKNOWN_RESPONSE_CODE = 0
    }
}
