package com.datadog.android.log.net

import com.datadog.android.core.internal.net.DataOkHttpUploaderTest
import com.datadog.android.log.internal.net.LogsOkHttpUploader
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient

internal class LogsOkHttpUploaderTest : DataOkHttpUploaderTest<LogsOkHttpUploader>() {

    override fun uploader(): LogsOkHttpUploader {
        return LogsOkHttpUploader(
            fakeEndpoint,
            fakeToken,
            OkHttpClient.Builder()
                .connectTimeout(TIMEOUT_TEST_MS, TimeUnit.MILLISECONDS)
                .readTimeout(TIMEOUT_TEST_MS, TimeUnit.MILLISECONDS)
                .writeTimeout(TIMEOUT_TEST_MS, TimeUnit.MILLISECONDS)
                .build()
        )
    }

    override fun urlFormat(): String {
        return LogsOkHttpUploader.UPLOAD_URL
    }
}
