/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2019 Datadog, Inc.
 */

package com.datadog.android.log.internal.net

import android.util.Log
import com.datadog.android.BuildConfig
import java.io.IOException
import java.util.Locale
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody

internal class LogOkHttpUploader(
    private val endpoint: String,
    private val token: String,
    private val client: OkHttpClient = OkHttpClient.Builder().build()
) : LogUploader {

    // region LogUploader

    @Suppress("TooGenericExceptionCaught")
    override fun uploadLogs(logs: List<String>): LogUploadStatus {

        return try {
            val request = buildRequest(logs)
            val response = client.newCall(request).execute()
            Log.i("T", "Got response : ${response.code()}")
            responseCodeToLogUploadStatus(response.code())
        } catch (e: IOException) {
            Log.e("T", "Network error", e)
            LogUploadStatus.NETWORK_ERROR
        }
    }

    // endregion

    // region Internal

    private fun buildUrl(endpoint: String, token: String): String {
        return String.format(Locale.US, UPLOAD_URL, endpoint, token)
    }

    private fun buildBody(logs: List<String>): RequestBody {
        return RequestBody.create(
            null,
            logs.joinToString(separator = ",", prefix = "[", postfix = "]")
        )
    }

    private fun buildRequest(logs: List<String>): Request {
        return Request.Builder()
            .url(buildUrl(endpoint, token))
            .post(buildBody(logs))
            .addHeader(HEADER_UA, USER_AGENT)
            .addHeader(HEADER_CT, CONTENT_TYPE)
            .build()
    }

    private fun responseCodeToLogUploadStatus(code: Int): LogUploadStatus {
        return when (code) {
            in 200..299 -> LogUploadStatus.SUCCESS
            in 300..399 -> LogUploadStatus.HTTP_REDIRECTION
            in 400..499 -> LogUploadStatus.HTTP_CLIENT_ERROR
            in 500..599 -> LogUploadStatus.HTTP_SERVER_ERROR
            else -> LogUploadStatus.UNKNOWN_ERROR
        }
    }

    // endregion

    companion object {

        private const val HEADER_UA = "User-Agent"
        private const val HEADER_CT = "Content-Type"

        private const val CONTENT_TYPE = "application/json"
        private const val USER_AGENT = "datadog-android/log:" + BuildConfig.VERSION_NAME

        private const val QP_SOURCE = "ddsource"
        private const val DD_SOURCE_MOBILE = "mobile"

        const val UPLOAD_URL = "%s/v1/input/%s?$QP_SOURCE=$DD_SOURCE_MOBILE"
    }
}
