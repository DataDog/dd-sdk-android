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

    override fun buildQueryParams(): Map<String, Any> {
        return mutableMapOf(
            QP_SOURCE to DD_SOURCE_MOBILE
        )
    }

    // endregion

    companion object {
        private const val QP_SOURCE = "ddsource"
        private const val DD_SOURCE_MOBILE = "mobile"
        internal const val UPLOAD_URL =
            "%s/v1/input/%s"

        private fun buildUrl(endpoint: String, token: String): String {
            return String.format(
                Locale.US,
                UPLOAD_URL, endpoint, token
            )
        }
    }
}
