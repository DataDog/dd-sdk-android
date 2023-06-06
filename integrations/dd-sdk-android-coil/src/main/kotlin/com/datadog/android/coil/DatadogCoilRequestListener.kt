/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.coil

import android.net.Uri
import coil.request.ImageRequest
import com.datadog.android.rum.GlobalRum
import com.datadog.android.rum.RumErrorSource
import com.datadog.android.v2.api.SdkCore
import okhttp3.HttpUrl
import java.io.File

/**
 * Provides an implementation of [coil.request.ImageRequest.Listener] already set up to send relevant information
 * to Datadog.
 *
 * It will automatically send RUM error events whenever a Coil [ImageRequest]
 * throws any [Exception].
 */
class DatadogCoilRequestListener(
    private val sdkCore: SdkCore
) : ImageRequest.Listener {

    // region Listener

    /** @inheritDoc */
    override fun onError(request: ImageRequest, throwable: Throwable) {
        GlobalRum.get(sdkCore).addError(
            REQUEST_ERROR_MESSAGE,
            RumErrorSource.SOURCE,
            throwable,
            extractRequestAttributes(request)
        )
    }

    // endregion

    // region Internals

    private fun extractRequestAttributes(request: ImageRequest): Map<String, Any?> {
        return when (request.data) {
            is String -> {
                mapOf(
                    REQUEST_PATH_TAG to request.data as String
                )
            }
            is Uri -> {
                mapOf(
                    REQUEST_PATH_TAG to (request.data as Uri).path
                )
            }
            is HttpUrl -> {
                mapOf(
                    REQUEST_PATH_TAG to (request.data as HttpUrl).url().toString()
                )
            }
            is File -> {
                mapOf(
                    REQUEST_PATH_TAG to (request.data as File).path
                )
            }
            else -> {
                emptyMap()
            }
        }
    }

    // endregion

    internal companion object {
        internal const val REQUEST_ERROR_MESSAGE = "Coil request error"
        internal const val REQUEST_PATH_TAG = "request_path"
    }
}
