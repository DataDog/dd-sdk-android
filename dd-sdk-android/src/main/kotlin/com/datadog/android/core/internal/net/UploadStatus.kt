/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.net

import com.datadog.android.core.internal.utils.devLogger

@Suppress("StringLiteralDuplication")
internal enum class UploadStatus {
    SUCCESS,
    NETWORK_ERROR,
    INVALID_TOKEN_ERROR,
    HTTP_REDIRECTION,
    HTTP_CLIENT_ERROR,
    HTTP_SERVER_ERROR,
    UNKNOWN_ERROR;

    fun logStatus(context: String, byteSize: Int) {
        when (this) {
            NETWORK_ERROR -> devLogger.e(
                "Unable to send batch [$byteSize bytes] ($context)" +
                    " because of a network error; we will retry later."
            )
            INVALID_TOKEN_ERROR -> devLogger.e(
                "Unable to send batch [$byteSize bytes] ($context)" +
                    " because your token is invalid. Make sure that the" +
                    " provided token still exists."
            )
            HTTP_REDIRECTION -> devLogger.w(
                "Unable to send batch [$byteSize bytes] ($context)" +
                    " because of a network error; we will retry later."
            )
            HTTP_CLIENT_ERROR -> devLogger.e(
                "Unable to send batch [$byteSize bytes] ($context)" +
                    " because of a processing error (possibly because of invalid data); " +
                    "the batch was dropped."
            )
            HTTP_SERVER_ERROR -> devLogger.e(
                "Unable to send batch [$byteSize bytes] ($context)" +
                    " because of a server processing error; we will retry later."
            )
            UNKNOWN_ERROR -> devLogger.e(
                "Unable to send batch [$byteSize bytes] ($context)" +
                    " because of an unknown error; we will retry later."
            )
            SUCCESS -> devLogger.v(
                "Batch [$byteSize bytes] sent successfully ($context)."
            )
        }
    }
}
