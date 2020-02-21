/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2020 Datadog, Inc.
 */

package com.datadog.android.rum.internal.net

import com.datadog.android.core.internal.net.DataOkHttpUploader
import java.util.Locale
import okhttp3.OkHttpClient

internal open class RumOkHttpUploader(
    endpoint: String,
    private val token: String,
    client: OkHttpClient
) : DataOkHttpUploader(buildUrl(endpoint, token), client, CONTENT_TYPE_TEXT_UTF8) {

    // region DataOkHttpUploader

    override fun setEndpoint(endpoint: String) {
        super.setEndpoint(buildUrl(endpoint, token))
    }

    // endregion

    companion object {
        private const val QP_SOURCE = "ddsource"
        private const val DD_SOURCE_MOBILE = "mobile"
        internal const val UPLOAD_URL =
            "%s/v1/input/%s?$QP_SOURCE=$DD_SOURCE_MOBILE"

        private fun buildUrl(endpoint: String, token: String): String {
            return String.format(
                Locale.US,
                UPLOAD_URL, endpoint, token
            )
        }
    }
}
