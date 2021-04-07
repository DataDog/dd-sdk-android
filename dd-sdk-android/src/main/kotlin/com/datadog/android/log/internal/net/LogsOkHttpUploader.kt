/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.log.internal.net

import com.datadog.android.core.internal.net.DataOkHttpUploader
import java.util.Locale
import okhttp3.Call

internal open class LogsOkHttpUploader(
    endpoint: String,
    token: String,
    callFactory: Call.Factory
) : DataOkHttpUploader(buildUrl(endpoint, token), callFactory) {

    // region DataOkHttpUploader

    override fun buildQueryParams(): Map<String, Any> {
        return mutableMapOf(
            QP_BATCH_TIME to System.currentTimeMillis(),
            QP_SOURCE to DD_SOURCE_ANDROID
        )
    }

    // endregion

    companion object {
        internal const val UPLOAD_URL = "%s/v1/input/%s"

        private fun buildUrl(endpoint: String, token: String): String {
            return String.format(
                Locale.US,
                UPLOAD_URL,
                endpoint,
                token
            )
        }
    }
}
