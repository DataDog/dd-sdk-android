/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.net

import com.datadog.android.log.Logger

@Suppress("StringLiteralDuplication")
internal enum class UploadStatus {
    SUCCESS,
    NETWORK_ERROR,
    INVALID_TOKEN_ERROR,
    HTTP_REDIRECTION,
    HTTP_CLIENT_ERROR,
    HTTP_SERVER_ERROR,
    HTTP_CLIENT_ERROR_RETRY,
    UNKNOWN_ERROR;

    fun logStatus(
        context: String,
        byteSize: Int,
        logger: Logger,
        ignoreInfo: Boolean,
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
            INVALID_TOKEN_ERROR -> logger.e(
                "$batchInfo failed because your token is invalid. " +
                    "Make sure that the provided token still exists."
            )
            HTTP_REDIRECTION -> logger.w(
                "$batchInfo failed because of a network error (redirection); we will retry later."
            )
            HTTP_CLIENT_ERROR -> logger.e(
                "$batchInfo failed because of a processing error or invalid data; " +
                    "the batch was dropped."
            )
            HTTP_CLIENT_ERROR_RETRY -> logger.e(
                "$batchInfo failed because of a request error; we will retry later."
            )
            HTTP_SERVER_ERROR -> logger.e(
                "$batchInfo failed because of a server processing error; we will retry later."
            )
            UNKNOWN_ERROR -> logger.e(
                "$batchInfo failed because of an unknown error; we will retry later."
            )
            SUCCESS -> if (!ignoreInfo) {
                logger.v("$batchInfo sent successfully.")
            }
        }
    }
}
