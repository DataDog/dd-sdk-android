package com.datadog.android.tracing.internal.net

import com.datadog.android.core.internal.net.DataOkHttpUploader
import okhttp3.OkHttpClient

internal open class TracesOkHttpUploader(
    endpoint: String,
    token: String,
    client: OkHttpClient
) : DataOkHttpUploader(endpoint, token, client, UPLOAD_URL, TAG) {

    companion object {
        internal const val UPLOAD_URL =
            "%s/v1/input/%s"
        internal const val TAG = "TracesOkHttpUploader"
    }
}
