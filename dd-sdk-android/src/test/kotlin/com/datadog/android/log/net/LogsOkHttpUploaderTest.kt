/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

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

    override fun expectedPathRegex(): String {
        return "\\/v1\\/input/${fakeToken}\\?ddsource=mobile$"
    }
}
