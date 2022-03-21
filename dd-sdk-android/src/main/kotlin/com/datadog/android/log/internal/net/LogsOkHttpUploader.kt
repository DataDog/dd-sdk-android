/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.log.internal.net

import com.datadog.android.core.internal.CoreFeature
import com.datadog.android.core.internal.net.DataOkHttpUploader
import com.datadog.android.core.internal.system.AndroidInfoProvider
import java.util.Locale
import okhttp3.Call

internal class LogsOkHttpUploader(
    endpoint: String,
    token: String,
    callFactory: Call.Factory,
    androidInfoProvider: AndroidInfoProvider
) : DataOkHttpUploader(buildUrl(endpoint, token), callFactory, androidInfoProvider) {

    // region DataOkHttpUploader

    override fun buildQueryParams(): Map<String, Any> {
        return mutableMapOf(
            QP_SOURCE to CoreFeature.sourceName
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
