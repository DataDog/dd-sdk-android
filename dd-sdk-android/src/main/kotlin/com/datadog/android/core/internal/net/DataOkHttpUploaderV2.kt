/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.net

import android.os.Build
import com.datadog.android.BuildConfig
import com.datadog.android.core.internal.utils.devLogger
import com.datadog.android.log.Logger
import java.util.Locale
import java.util.UUID
import okhttp3.Call
import okhttp3.Request
import okhttp3.RequestBody

internal abstract class DataOkHttpUploaderV2(
    internal var intakeUrl: String,
    internal var clientToken: String,
    internal var source: String,
    internal val callFactory: Call.Factory,
    internal val contentType: String,
    internal val internalLogger: Logger
) : DataUploader {

    internal enum class TrackType(val trackName: String) {
        LOGS("logs"),
        RUM("rum"),
        SPANS("spans")
    }

    private val uploaderName = javaClass.simpleName

    private val userAgent by lazy {
        System.getProperty(DataOkHttpUploader.SYSTEM_UA).let {
            if (it.isNullOrBlank()) {
                "Datadog/${BuildConfig.SDK_VERSION_NAME} " +
                    "(Linux; U; Android ${Build.VERSION.RELEASE}; " +
                    "${Build.MODEL} Build/${Build.ID})"
            } else {
                it
            }
        }
    }

    // region DataUploader

    @Suppress("TooGenericExceptionCaught")
    override fun upload(data: ByteArray): UploadStatus {
        val requestId = UUID.randomUUID().toString()
        val uploadStatus = try {
            executeUploadRequest(data, requestId)
        } catch (e: Throwable) {
            internalLogger.e("Unable to upload batch data.", e)
            UploadStatus.NETWORK_ERROR
        }

        uploadStatus.logStatus(uploaderName, data.size, devLogger, false, requestId)
        uploadStatus.logStatus(uploaderName, data.size, internalLogger, true, requestId)

        return uploadStatus
    }

    // endregion

    // region Internal

    private fun executeUploadRequest(
        data: ByteArray,
        requestId: String
    ): UploadStatus {
        val request = buildRequest(data, requestId)
        val call = callFactory.newCall(request)
        val response = call.execute()
        return responseCodeToUploadStatus(response.code())
    }

    private fun buildRequest(data: ByteArray, requestId: String): Request {
        val builder = Request.Builder()
            .url(buildUrl())
            .post(RequestBody.create(null, data))

        buildHeaders(builder, requestId)

        return builder.build()
    }

    private fun buildUrl(): String {
        val queryParams = buildQueryParameters()
        return if (queryParams.isEmpty()) {
            intakeUrl
        } else {
            intakeUrl + queryParams.map { "${it.key}=${it.value}" }.joinToString("&", prefix = "?")
        }
    }

    private fun buildHeaders(builder: Request.Builder, requestId: String) {
        builder.addHeader(HEADER_API_KEY, clientToken)
        builder.addHeader(HEADER_EVP_ORIGIN, source)
        builder.addHeader(HEADER_EVP_ORIGIN_VERSION, BuildConfig.SDK_VERSION_NAME)
        builder.addHeader(HEADER_USER_AGENT, userAgent)
        builder.addHeader(HEADER_CONTENT_TYPE, contentType)
        builder.addHeader(HEADER_REQUEST_ID, requestId)
    }

    protected open fun buildQueryParameters(): Map<String, Any> {
        return emptyMap()
    }

    private fun responseCodeToUploadStatus(code: Int): UploadStatus {
        return when (code) {
            HTTP_ACCEPTED -> UploadStatus.SUCCESS
            HTTP_BAD_REQUEST -> UploadStatus.HTTP_CLIENT_ERROR
            HTTP_UNAUTHORIZED -> UploadStatus.INVALID_TOKEN_ERROR
            HTTP_FORBIDDEN -> UploadStatus.HTTP_CLIENT_ERROR
            HTTP_CLIENT_TIMEOUT -> UploadStatus.HTTP_CLIENT_RATE_LIMITING
            HTTP_ENTITY_TOO_LARGE -> UploadStatus.HTTP_CLIENT_ERROR
            HTTP_TOO_MANY_REQUESTS -> UploadStatus.HTTP_CLIENT_RATE_LIMITING
            HTTP_INTERNAL_ERROR -> UploadStatus.HTTP_SERVER_ERROR
            HTTP_UNAVAILABLE -> UploadStatus.HTTP_SERVER_ERROR
            else -> UploadStatus.UNKNOWN_ERROR
        }
    }

    companion object {

        const val HTTP_ACCEPTED = 202

        const val HTTP_BAD_REQUEST = 400
        const val HTTP_UNAUTHORIZED = 401
        const val HTTP_FORBIDDEN = 403
        const val HTTP_CLIENT_TIMEOUT = 408
        const val HTTP_ENTITY_TOO_LARGE = 413
        const val HTTP_TOO_MANY_REQUESTS = 429

        const val HTTP_INTERNAL_ERROR = 500
        const val HTTP_UNAVAILABLE = 503

        internal const val HEADER_API_KEY = "DD-API-KEY"
        internal const val HEADER_EVP_ORIGIN = "DD-EVP-ORIGIN"
        internal const val HEADER_EVP_ORIGIN_VERSION = "DD-EVP-ORIGIN-VERSION"
        internal const val HEADER_REQUEST_ID = "DD-REQUEST-ID"
        internal const val HEADER_CONTENT_TYPE = "Content-Type"
        internal const val HEADER_USER_AGENT = "User-Agent"

        internal const val QUERY_PARAM_SOURCE = "ddsource"
        internal const val QUERY_PARAM_TAGS = "ddtags"

        internal const val CONTENT_TYPE_JSON = "application/json"
        internal const val CONTENT_TYPE_TEXT_UTF8 = "text/plain;charset=UTF-8"

        private const val UPLOAD_URL = "%s/api/v2/%s"

        internal fun buildUrl(endpoint: String, trackType: TrackType): String {
            return String.format(
                Locale.US,
                UPLOAD_URL,
                endpoint,
                trackType.trackName
            )
        }
    }
}
