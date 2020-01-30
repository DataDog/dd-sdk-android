package com.datadog.android.tracing.internal.net

import com.datadog.android.core.internal.net.DataOkHttpUploader
import java.util.Locale
import okhttp3.OkHttpClient

internal open class TracesOkHttpUploader(
    endpoint: String,
    private val token: String,
    client: OkHttpClient
) : DataOkHttpUploader(buildUrl(endpoint, token), client) {

    // region DataOkHttpUploader

    override fun setEndpoint(endpoint: String) {
        super.setEndpoint(buildUrl(endpoint, token))
    }

    // endregion

    companion object {
        internal const val UPLOAD_URL = "%s/v1/input/%s"
        internal const val TAG = "TracesOkHttpUploader"

        private fun buildUrl(endpoint: String, token: String): String {
            return String.format(
                Locale.US,
                UPLOAD_URL, endpoint, token
            )
        }
    }
}
