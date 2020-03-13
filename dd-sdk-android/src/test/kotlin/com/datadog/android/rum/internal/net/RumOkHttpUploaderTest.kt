package com.datadog.android.rum.internal.net

import com.datadog.android.core.internal.net.DataOkHttpUploaderTest
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient

internal class RumOkHttpUploaderTest : DataOkHttpUploaderTest<RumOkHttpUploader>() {

    override fun uploader(): RumOkHttpUploader {
        return RumOkHttpUploader(
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
        return RumOkHttpUploader.UPLOAD_URL
    }

    override fun expectedPathRegex(): String {
        return "\\/v1\\/input/${fakeToken}\\?batch_time=\\d+&ddsource=mobile$"
    }
}
