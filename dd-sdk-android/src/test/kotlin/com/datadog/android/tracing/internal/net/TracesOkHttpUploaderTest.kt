package com.datadog.android.tracing.internal.net

import com.datadog.android.core.internal.net.DataOkHttpUploaderTest
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient

internal class TracesOkHttpUploaderTest : DataOkHttpUploaderTest<TracesOkHttpUploader>() {

    override fun uploader(): TracesOkHttpUploader {
        return TracesOkHttpUploader(
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
        return TracesOkHttpUploader.UPLOAD_URL
    }

    override fun expectedPathRegex(): String {
        return "\\/v1\\/input\\/$fakeToken$"
    }
}
