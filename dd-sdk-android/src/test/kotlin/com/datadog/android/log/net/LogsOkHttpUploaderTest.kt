package com.datadog.android.log.net

import android.os.Build
import com.datadog.android.BuildConfig
import com.datadog.android.core.internal.net.DataOkHttpUploaderTest
import com.datadog.android.log.internal.net.LogsOkHttpUploader
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.RecordedRequest
import org.assertj.core.api.Assertions.assertThat

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

    override fun assertHeaders(request: RecordedRequest) {
        super.assertHeaders(request)
        val expectedUserAgent = if (fakeUserAgent.isBlank()) {
            "Datadog/${BuildConfig.VERSION_NAME} " +
                    "(Linux; U; Android ${Build.VERSION.RELEASE}; " +
                    "${Build.MODEL} Build/${Build.ID})"
        } else {
            fakeUserAgent
        }
        assertThat(request.getHeader("User-Agent"))
            .isEqualTo(expectedUserAgent)
    }

    override fun tag(): String {
        return LogsOkHttpUploader.TAG
    }

    override fun urlFormat(): String {
        return LogsOkHttpUploader.UPLOAD_URL
    }
}
