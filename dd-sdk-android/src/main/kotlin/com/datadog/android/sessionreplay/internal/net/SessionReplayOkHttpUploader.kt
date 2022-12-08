/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.net

import com.datadog.android.core.internal.net.UploadStatus
import com.datadog.android.core.internal.utils.devLogger
import com.datadog.android.core.internal.utils.sdkLogger
import com.datadog.android.log.Logger
import com.datadog.android.rum.RumAttributes
import com.datadog.android.sessionreplay.model.MobileSegment
import com.datadog.android.sessionreplay.net.BytesCompressor
import com.datadog.android.v2.api.context.DatadogContext
import okhttp3.Call
import okhttp3.MediaType
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody
import java.util.Locale
import java.util.UUID

// TODO: RUMM-2547 Drop this class and return a list of requests
//  instead from SessionReplayRequestFactory
// This class is not test as it is meant for non - production usage. It will be dropped later.
@Suppress("TooManyFunctions")
internal class SessionReplayOkHttpUploader(
    private val endpoint: String,
    internal val callFactory: Call.Factory,
    internal val internalLogger: Logger = sdkLogger,
    private val compressor: BytesCompressor = BytesCompressor()
) {

    private val uploaderName = javaClass.simpleName

    private fun userAgent(datadogContext: DatadogContext): String {
        return sanitizeHeaderValue(System.getProperty(SYSTEM_UA))
            .ifBlank {
                "Datadog/${sanitizeHeaderValue(datadogContext.sdkVersion)} " +
                    "(Linux; U; Android ${datadogContext.deviceInfo.osVersion}; " +
                    "${datadogContext.deviceInfo.deviceModel} " +
                    "Build/${datadogContext.deviceInfo.deviceBuildId})"
            }
    }

    private val intakeUrl by lazy {
        String.format(
            Locale.US,
            UPLOAD_URL,
            endpoint,
            "replay"
        )
    }

    private fun tags(datadogContext: DatadogContext): String {
        val elements = mutableListOf(
            "${RumAttributes.SERVICE_NAME}:${datadogContext.service}",
            "${RumAttributes.APPLICATION_VERSION}:" +
                datadogContext.version,
            "${RumAttributes.SDK_VERSION}:${datadogContext.sdkVersion}",
            "${RumAttributes.ENV}:${datadogContext.env}"
        )
        if (datadogContext.variant.isNotEmpty()) {
            elements.add("${RumAttributes.VARIANT}:${datadogContext.variant}")
        }
        return elements.joinToString(",")
    }

    @Suppress("TooGenericExceptionCaught")
    fun upload(
        datadogContext: DatadogContext,
        mobileSegment: MobileSegment,
        mobileSegmentAsBinary: ByteArray
    ): UploadStatus {
        val requestId = UUID.randomUUID().toString()
        val uploadStatus = try {
            executeUploadRequest(
                datadogContext,
                mobileSegment,
                mobileSegmentAsBinary,
                requestId
            )
        } catch (e: Throwable) {
            internalLogger.e("Unable to upload batch data.", e)
            UploadStatus.NETWORK_ERROR
        }

        uploadStatus.logStatus(
            uploaderName,
            mobileSegmentAsBinary.size,
            devLogger,
            ignoreInfo = false,
            sendToTelemetry = false,
            requestId = requestId
        )
        uploadStatus.logStatus(
            uploaderName,
            mobileSegmentAsBinary.size,
            internalLogger,
            ignoreInfo = true,
            sendToTelemetry = true,
            requestId = requestId
        )

        return uploadStatus
    }

    // region Internal

    @Suppress("UnsafeThirdPartyFunctionCall") // Called within a try/catch block
    private fun executeUploadRequest(
        datadogContext: DatadogContext,
        mobileSegment: MobileSegment,
        mobileSegmentAsBinary: ByteArray,
        requestId: String
    ): UploadStatus {
        if (datadogContext.clientToken.isBlank()) {
            return UploadStatus.INVALID_TOKEN_ERROR
        }
        val request = buildRequest(datadogContext, mobileSegment, mobileSegmentAsBinary, requestId)
        val call = callFactory.newCall(request)
        val response = call.execute()
        response.close()
        return responseCodeToUploadStatus(response.code())
    }

    @Suppress("UnsafeThirdPartyFunctionCall") // Called within a try/catch block
    private fun buildRequest(
        datadogContext: DatadogContext,
        segment: MobileSegment,
        segmentAsBinary: ByteArray,
        requestId: String
    ): Request {
        val builder = Request.Builder()
            .url(intakeUrl)
            .post(buildRequestBody(segment, segmentAsBinary))

        buildHeaders(datadogContext, builder, requestId)

        return builder.build()
    }

    @Suppress("UnsafeThirdPartyFunctionCall") // Called within a try/catch block
    private fun buildRequestBody(segment: MobileSegment, segmentAsBinary: ByteArray): RequestBody {
        val compressedData = compressor.compressBytes(segmentAsBinary)
        return MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                SEGMENT_FORM_KEY,
                segment.session.id,
                RequestBody.create(
                    MediaType.parse(CONTENT_TYPE_BINARY),
                    compressedData
                )
            )
            .addFormDataPart(
                SEGMENT_FORM_KEY,
                segment.session.id

            )
            .addFormDataPart(
                APPLICATION_ID_FORM_KEY,
                segment.application.id
            )
            .addFormDataPart(
                SESSION_ID_FORM_KEY,
                segment.session.id
            )
            .addFormDataPart(
                VIEW_ID_FORM_KEY,
                segment.view.id
            )
            .addFormDataPart(
                HAS_FULL_SNAPSHOT_FORM_KEY,
                segment.hasFullSnapshot.toString()
            )
            .addFormDataPart(
                RECORDS_COUNT_FORM_KEY,
                segment.recordsCount.toString()
            )
            .addFormDataPart(
                RAW_SEGMENT_SIZE_FORM_KEY,
                compressedData.size.toString()
            )
            .addFormDataPart(
                START_TIMESTAMP_FORM_KEY,
                segment.start.toString()
            )
            .addFormDataPart(
                END_TIMESTAMP_FORM_KEY,
                segment.end.toString()
            )
            .addFormDataPart(
                SOURCE_FORM_KEY,
                segment.source.toJson().asString
            )
            .build()
    }

    internal fun buildUrl(datadogContext: DatadogContext): String {
        val queryParams = buildQueryParameters(datadogContext)
        return if (queryParams.isEmpty()) {
            intakeUrl
        } else {
            intakeUrl + queryParams.map { "${it.key}=${it.value}" }
                .joinToString("&", prefix = "?")
        }
    }

    private fun buildHeaders(
        datadogContext: DatadogContext,
        builder: Request.Builder,
        requestId: String
    ) {
        builder.addHeader(HEADER_API_KEY, datadogContext.clientToken)
        builder.addHeader(HEADER_EVP_ORIGIN, datadogContext.source)
        builder.addHeader(HEADER_EVP_ORIGIN_VERSION, datadogContext.sdkVersion)
        builder.addHeader(HEADER_USER_AGENT, userAgent(datadogContext))
        builder.addHeader(HEADER_CONTENT_TYPE, CONTENT_TYPE_MULTIPART_FORM)
        builder.addHeader(HEADER_REQUEST_ID, requestId)
    }

    private fun responseCodeToUploadStatus(code: Int): UploadStatus {
        return when (code) {
            HTTP_ACCEPTED -> UploadStatus.SUCCESS
            HTTP_BAD_REQUEST -> UploadStatus.HTTP_CLIENT_ERROR
            HTTP_UNAUTHORIZED -> UploadStatus.INVALID_TOKEN_ERROR
            HTTP_FORBIDDEN -> UploadStatus.INVALID_TOKEN_ERROR
            HTTP_CLIENT_TIMEOUT -> UploadStatus.HTTP_CLIENT_RATE_LIMITING
            HTTP_ENTITY_TOO_LARGE -> UploadStatus.HTTP_CLIENT_ERROR
            HTTP_TOO_MANY_REQUESTS -> UploadStatus.HTTP_CLIENT_RATE_LIMITING
            HTTP_INTERNAL_ERROR -> UploadStatus.HTTP_SERVER_ERROR
            HTTP_UNAVAILABLE -> UploadStatus.HTTP_SERVER_ERROR
            else -> UploadStatus.UNKNOWN_ERROR
        }
    }

    private fun sanitizeHeaderValue(value: String?): String {
        return value?.filter { isValidHeaderValueChar(it) }.orEmpty()
    }

    private fun isValidHeaderValueChar(c: Char): Boolean {
        return c == '\t' || c in '\u0020' until '\u007F'
    }

    private fun buildQueryParameters(datadogContext: DatadogContext): Map<String, Any> {
        return mapOf(
            QUERY_PARAM_SOURCE to datadogContext.source,
            QUERY_PARAM_TAGS to tags(datadogContext),
            QUERY_PARAM_EVP_ORIGIN_KEY to datadogContext.source,
            QUERY_PARAM_EVP_ORIGIN_VERSION_KEY to datadogContext.sdkVersion
        )
    }

    // endregion

    companion object {

        const val SYSTEM_UA = "http.agent"

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

        private const val UPLOAD_URL = "%s/api/v2/%s"

        internal const val QUERY_PARAM_EVP_ORIGIN_VERSION_KEY = "dd-evp-origin-version"
        internal const val QUERY_PARAM_EVP_ORIGIN_KEY = "dd-evp-origin"
        internal const val APPLICATION_ID_FORM_KEY = "application.id"
        internal const val SESSION_ID_FORM_KEY = "session.id"
        internal const val VIEW_ID_FORM_KEY = "view.id"
        internal const val HAS_FULL_SNAPSHOT_FORM_KEY = "has_full_snapshot"
        internal const val RECORDS_COUNT_FORM_KEY = "records_count"
        internal const val RAW_SEGMENT_SIZE_FORM_KEY = "raw_segment_size"
        internal const val START_TIMESTAMP_FORM_KEY = "start"
        internal const val END_TIMESTAMP_FORM_KEY = "end"
        internal const val SOURCE_FORM_KEY = "source"
        internal const val SEGMENT_FORM_KEY = "segment"
        internal const val CONTENT_TYPE_BINARY = "application/octet-stream"
        internal const val CONTENT_TYPE_MULTIPART_FORM = "multipart/form-data"
    }
}
