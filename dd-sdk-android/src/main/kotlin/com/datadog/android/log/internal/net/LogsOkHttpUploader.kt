package com.datadog.android.log.internal.net

import com.datadog.android.core.internal.net.DataOkHttpUploader
import java.util.Locale
import okhttp3.OkHttpClient

internal open class LogsOkHttpUploader(
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
        private const val QP_SOURCE = "ddsource"
        private const val DD_SOURCE_ANDROID = "android"
        internal const val UPLOAD_URL =
            "%s/v1/input/%s?$QP_SOURCE=$DD_SOURCE_ANDROID"

        private fun buildUrl(endpoint: String, token: String): String {
            return String.format(
                Locale.US,
                UPLOAD_URL, endpoint, token
            )
        }
    }
}
