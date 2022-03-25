/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.net

import com.datadog.android.log.Logger
import com.datadog.android.log.internal.utils.errorWithTelemetry

@Suppress("StringLiteralDuplication")
internal enum class UploadStatus(val shouldRetry: Boolean) {
    SUCCESS(shouldRetry = false),
    NETWORK_ERROR(shouldRetry = true),
    INVALID_TOKEN_ERROR(shouldRetry = false),
    HTTP_REDIRECTION(shouldRetry = false),
    HTTP_CLIENT_ERROR(shouldRetry = false),
    HTTP_SERVER_ERROR(shouldRetry = true),
    HTTP_CLIENT_RATE_LIMITING(shouldRetry = true),
    UNKNOWN_ERROR(shouldRetry = false);

    fun logStatus(
        context: String,
        byteSize: Int,
        logger: Logger,
        ignoreInfo: Boolean,
        sendToTelemetry: Boolean,
        requestId: String? = null
    ) {
        val batchInfo = if (requestId == null) {
            "Batch [$byteSize bytes] ($context)"
        } else {
            "Batch $requestId [$byteSize bytes] ($context)"
        }
        when (this) {
            NETWORK_ERROR -> logger.e(
                "$batchInfo failed because of a network error; we will retry later."
            )
            INVALID_TOKEN_ERROR -> if (!ignoreInfo) {
                logger.e(
                    "$batchInfo failed because your token is invalid. " +
                        "Make sure that the provided token still exists."
                )
            }
            HTTP_REDIRECTION -> logger.w(
                "$batchInfo failed because of a network redirection; the batch was dropped."
            )
            HTTP_CLIENT_ERROR -> {
                val message = "$batchInfo failed because of a processing error or invalid data; " +
                    "the batch was dropped."
                if (sendToTelemetry) {
                    logger.errorWithTelemetry(message)
                } else {
                    logger.e(message)
                }
            }
            HTTP_CLIENT_RATE_LIMITING -> {
                val message = "$batchInfo failed because of a request error; we will retry later."
                if (sendToTelemetry) {
                    logger.errorWithTelemetry(message)
                } else {
                    logger.e(message)
                }
            }
            HTTP_SERVER_ERROR -> logger.e(
                "$batchInfo failed because of a server processing error; we will retry later."
            )
            UNKNOWN_ERROR -> logger.e(
                "$batchInfo failed because of an unknown error; the batch was dropped."
            )
            SUCCESS -> if (!ignoreInfo) {
                logger.v("$batchInfo sent successfully.")
            }
        }
    }
}
