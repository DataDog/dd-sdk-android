package com.datadog.android.log.internal.net

import android.os.Build
import com.datadog.android.BuildConfig
import com.datadog.android.core.internal.net.DataOkHttpUploader
import java.util.Locale
import okhttp3.OkHttpClient

internal open class LogsOkHttpUploader(
    endpoint: String,
    private val token: String,
    client: OkHttpClient
) : DataOkHttpUploader(buildUrl(endpoint, token), client) {

    // region DataOkHttpUploader

    override fun headers(): MutableMap<String, String> {
        val headers = super.headers()
        headers[HEADER_UA] = userAgent
        return headers
    }

    override fun setEndpoint(endpoint: String) {
        super.setEndpoint(buildUrl(endpoint, token))
    }

    // endregion

    // region Internal

    private val userAgent = System.getProperty(SYSTEM_UA).let {
        if (it.isNullOrBlank()) {
            "Datadog/${BuildConfig.VERSION_NAME} " +
                "(Linux; U; Android ${Build.VERSION.RELEASE}; " +
                "${Build.MODEL} Build/${Build.ID})"
        } else {
            it
        }
    }

    // endregion

    companion object {
        private const val HEADER_UA = "User-Agent"
        const val SYSTEM_UA = "http.agent"
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
