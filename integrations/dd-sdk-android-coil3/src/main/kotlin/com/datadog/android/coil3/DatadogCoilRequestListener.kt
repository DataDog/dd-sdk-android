/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.coil3

import coil3.request.ErrorResult
import coil3.request.ImageRequest
import com.datadog.android.Datadog
import com.datadog.android.api.SdkCore
import com.datadog.android.rum.GlobalRumMonitor
import com.datadog.android.rum.RumErrorSource
import okhttp3.HttpUrl
import java.io.File

/**
 * Provides an implementation of [coil3.request.ImageRequest.Listener] already set up to send relevant information
 * to Datadog.
 *
 * It will automatically send RUM error events whenever a Coil [ImageRequest]
 * throws any [Exception].
 *
 * @param sdkCore SDK instance to use for the reporting. If not provided, default instance
 * will be used.
 */
class DatadogCoilRequestListener
@JvmOverloads
constructor(
    private val sdkCore: SdkCore = Datadog.getInstance()
) : ImageRequest.Listener {

    // region Listener

    /** @inheritDoc */
    override fun onError(request: ImageRequest, result: ErrorResult) {
        GlobalRumMonitor.get(sdkCore).addError(
            REQUEST_ERROR_MESSAGE,
            RumErrorSource.SOURCE,
            result.throwable,
            extractRequestAttributes(request)
        )
    }

    // endregion

    // region Internals

    private fun extractRequestAttributes(request: ImageRequest): Map<String, Any?> {
        return when (val data = request.data) {
            is String -> {
                mapOf(REQUEST_PATH_TAG to data)
            }
            is android.net.Uri -> {
                mapOf(REQUEST_PATH_TAG to data.path)
            }
            is HttpUrl -> {
                @Suppress("UnsafeThirdPartyFunctionCall") // toUrl() is safe - HttpUrl guarantees valid http/https URL
                val urlValue = data.toUrl().toString()
                mapOf(REQUEST_PATH_TAG to urlValue)
            }
            is File -> {
                mapOf(REQUEST_PATH_TAG to data.path)
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
