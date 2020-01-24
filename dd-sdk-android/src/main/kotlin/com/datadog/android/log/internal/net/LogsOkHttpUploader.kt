package com.datadog.android.log.internal.net

import android.os.Build
import com.datadog.android.BuildConfig
import com.datadog.android.core.internal.net.DataOkHttpUploader
import okhttp3.OkHttpClient

internal open class LogsOkHttpUploader(
    endpoint: String,
    token: String,
    client: OkHttpClient
) : DataOkHttpUploader(endpoint, token, client, UPLOAD_URL, TAG) {

    private val userAgent = System.getProperty(SYSTEM_UA).let {
        if (it.isNullOrBlank()) {
            "Datadog/${BuildConfig.VERSION_NAME} " +
                    "(Linux; U; Android ${Build.VERSION.RELEASE}; " +
                    "${Build.MODEL} Build/${Build.ID})"
        } else {
            it
        }
    }

    override fun headers(): MutableMap<String, String> {
        val headers = super.headers()
        headers[HEADER_UA] = userAgent // traces intake doesn't support this type of user agent
        // and crashes at message format
        return headers
    }

    companion object {
        private const val HEADER_UA = "User-Agent"
        const val SYSTEM_UA = "http.agent"
        private const val QP_SOURCE = "ddsource"
        private const val DD_SOURCE_MOBILE = "mobile"
        internal const val UPLOAD_URL =
            "%s/v1/input/%s?$QP_SOURCE=$DD_SOURCE_MOBILE"
        internal const val TAG = "LogsOkHttpUploader"
    }
}
