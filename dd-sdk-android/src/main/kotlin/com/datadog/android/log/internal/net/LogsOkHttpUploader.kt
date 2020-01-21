package com.datadog.android.log.internal.net

import com.datadog.android.core.internal.net.DataOkHttpUploader
import okhttp3.OkHttpClient

internal open class LogsOkHttpUploader(
    endpoint: String,
    token: String,
    client: OkHttpClient
) : DataOkHttpUploader(endpoint, token, client, UPLOAD_URL, TAG) {

    companion object {
        private const val QP_SOURCE = "ddsource"
        private const val DD_SOURCE_MOBILE = "mobile"
        internal const val UPLOAD_URL =
            "%s/v1/input/%s?$QP_SOURCE=$DD_SOURCE_MOBILE"
        internal const val TAG = "LogsOkHttpUploader"
    }
}
