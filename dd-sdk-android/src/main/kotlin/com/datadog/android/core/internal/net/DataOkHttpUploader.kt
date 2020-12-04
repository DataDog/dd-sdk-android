/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.net

import android.os.Build
import com.datadog.android.BuildConfig
import com.datadog.android.core.internal.utils.sdkLogger
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody

internal abstract class DataOkHttpUploader(
    private var url: String,
    private val client: OkHttpClient,
    internal val contentType: String = CONTENT_TYPE_JSON
) : DataUploader {

    // region DataUploader

    @Suppress("TooGenericExceptionCaught")
    override fun upload(data: ByteArray): UploadStatus {

        return try {
            val request = buildRequest(data)
            val call = client.newCall(request)
            val response = call.execute()
            sdkLogger.i(
                "Response " +
                    "from ${url.substring(0, 32)}… " +
                    "code:${response.code()} " +
                    "body:${response.body()?.string()} " +
                    "headers:${response.headers()}"
            )
            responseCodeToUploadStatus(response.code())
        } catch (e: Throwable) {
            sdkLogger.e("unable to upload data", e)
            UploadStatus.NETWORK_ERROR
        }
    }

    // endregion

    // region DataOkHttpUploader

    abstract fun buildQueryParams(): Map<String, Any>

    // endregion

    // region Internal

    private fun headers(): MutableMap<String, String> {
        return mutableMapOf(
            HEADER_UA to userAgent,
            HEADER_CT to contentType
        )
    }

    private val userAgent by lazy {
        System.getProperty(SYSTEM_UA).let {
            if (it.isNullOrBlank()) {
                "Datadog/${BuildConfig.VERSION_NAME} " +
                    "(Linux; U; Android ${Build.VERSION.RELEASE}; " +
                    "${Build.MODEL} Build/${Build.ID})"
            } else {
                it
            }
        }
    }

    private fun buildRequest(data: ByteArray): Request {
        // add query params
        val urlWithQueryParams = urlWithQueryParams()
        sdkLogger.d("Sending data to POST $urlWithQueryParams")
        val builder = Request.Builder()
            .url(urlWithQueryParams)
            .post(RequestBody.create(null, data))
        headers().forEach {
            builder.addHeader(it.key, it.value)
            sdkLogger.d("$TAG: ${it.key}: ${it.value}")
        }
        return builder.build()
    }

    private fun urlWithQueryParams(): String {
        val baseUrl = url
        var firstAdded = false
        return buildQueryParams()
            .asSequence()
            .fold(
                baseUrl,
                { url, entry ->
                    if (firstAdded) {
                        "$url&${entry.key}=${entry.value}"
                    } else {
                        firstAdded = true
                        "$url?${entry.key}=${entry.value}"
                    }
                }
            )
    }

    private fun responseCodeToUploadStatus(code: Int): UploadStatus {
        return when (code) {
            403 -> UploadStatus.INVALID_TOKEN_ERROR
            in 200..299 -> UploadStatus.SUCCESS
            in 300..399 -> UploadStatus.HTTP_REDIRECTION
            in 400..499 -> UploadStatus.HTTP_CLIENT_ERROR
            in 500..599 -> UploadStatus.HTTP_SERVER_ERROR
            else -> UploadStatus.UNKNOWN_ERROR
        }
    }

    // endregion

    companion object {
        internal const val DD_SOURCE_ANDROID = "android"

        internal const val CONTENT_TYPE_JSON = "application/json"
        internal const val CONTENT_TYPE_TEXT_UTF8 = "text/plain;charset=UTF-8"

        internal const val QP_BATCH_TIME = "batch_time"
        internal const val QP_SOURCE = "ddsource"

        private const val HEADER_CT = "Content-Type"
        private const val HEADER_UA = "User-Agent"

        const val SYSTEM_UA = "http.agent"

        private const val TAG = "DataOkHttpUploader"
    }
}
