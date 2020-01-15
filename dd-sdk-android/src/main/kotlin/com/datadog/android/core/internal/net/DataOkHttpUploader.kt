/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2019 Datadog, Inc.
 */

package com.datadog.android.core.internal.net

import android.os.Build
import com.datadog.android.BuildConfig
import com.datadog.android.log.internal.utils.sdkLogger
import java.io.IOException
import java.util.Locale
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

internal class DataOkHttpUploader(
    endpoint: String,
    private val token: String,
    private val client: OkHttpClient
) : DataUploader {

    private var url: String = buildUrl(endpoint, token)

    private val userAgent = System.getProperty(SYSTEM_UA).let {
        if (it.isNullOrBlank()) {
            "Datadog/${BuildConfig.VERSION_NAME} " +
                "(Linux; U; Android ${Build.VERSION.RELEASE}; " +
                "${Build.MODEL} Build/${Build.ID})"
        } else {
            it
        }
    }

    // region LogUploader

    override fun setEndpoint(endpoint: String) {
        url = buildUrl(endpoint, token)
    }

    @Suppress("TooGenericExceptionCaught")
    override fun upload(data: ByteArray): UploadStatus {

        return try {
            val request = buildRequest(data)
            val response = client.newCall(request).execute()
            sdkLogger.i(
                    "$TAG: Response code:${response.code} " +
                            "body:${response.body?.string()} " +
                            "headers:${response.headers}"
            )
            responseCodeToUploadStatus(response.code)
        } catch (e: IOException) {
            sdkLogger.e("$TAG: unable to upload data", e)
            UploadStatus.NETWORK_ERROR
        }
    }

    // endregion

    // region Internal

    private fun buildUrl(endpoint: String, token: String): String {
        sdkLogger.i("$TAG: using endpoint $endpoint")
        return String.format(Locale.US,
            UPLOAD_URL, endpoint, token)
    }

    private fun buildRequest(data: ByteArray): Request {
        sdkLogger.d("$TAG: Sending data to $url")
        return Request.Builder()
            .url(url)
            .post(data.toRequestBody())
            .addHeader(HEADER_UA, userAgent)
            .addHeader(
                HEADER_CT,
                CONTENT_TYPE
            )
            .build()
    }

    private fun responseCodeToUploadStatus(code: Int): UploadStatus {
        return when (code) {
            in 200..299 -> UploadStatus.SUCCESS
            in 300..399 -> UploadStatus.HTTP_REDIRECTION
            in 400..499 -> UploadStatus.HTTP_CLIENT_ERROR
            in 500..599 -> UploadStatus.HTTP_SERVER_ERROR
            else -> UploadStatus.UNKNOWN_ERROR
        }
    }

    // endregion

    companion object {

        private const val HEADER_UA = "User-Agent"
        private const val HEADER_CT = "Content-Type"

        private const val CONTENT_TYPE = "application/json"

        const val SYSTEM_UA = "http.agent"

        private const val QP_SOURCE = "ddsource"
        private const val DD_SOURCE_MOBILE = "mobile"

        const val UPLOAD_URL = "%s/v1/input/%s?$QP_SOURCE=$DD_SOURCE_MOBILE"
        private const val TAG = "DataOkHttpUploader"
    }
}
