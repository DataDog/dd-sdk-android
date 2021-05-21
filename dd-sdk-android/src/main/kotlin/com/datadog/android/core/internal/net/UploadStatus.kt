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
    UNKNOWN_ERROR;

    fun logStatus(
        context: String,
        byteSize: Int,
        logger: Logger,
        ignoreInfo: Boolean
    ) {
        when (this) {
            NETWORK_ERROR -> logger.e(
                "Unable to send batch [$byteSize bytes] ($context)" +
                    " because of a network error; we will retry later."
            )
            INVALID_TOKEN_ERROR -> logger.e(
                "Unable to send batch [$byteSize bytes] ($context)" +
                    " because your token is invalid. Make sure that the" +
                    " provided token still exists."
            )
            HTTP_REDIRECTION -> logger.w(
                "Unable to send batch [$byteSize bytes] ($context)" +
                    " because of a network error; we will retry later."
            )
            HTTP_CLIENT_ERROR -> logger.e(
                "Unable to send batch [$byteSize bytes] ($context)" +
                    " because of a processing error (possibly because of invalid data); " +
                    "the batch was dropped."
            )
            HTTP_SERVER_ERROR -> logger.e(
                "Unable to send batch [$byteSize bytes] ($context)" +
                    " because of a server processing error; we will retry later."
            )
            UNKNOWN_ERROR -> logger.e(
                "Unable to send batch [$byteSize bytes] ($context)" +
                    " because of an unknown error; we will retry later."
            )
            SUCCESS -> if (!ignoreInfo) {
                logger.v("Batch [$byteSize bytes] sent successfully ($context).")
            }
        }
    }
}
