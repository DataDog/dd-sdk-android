/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.net

import com.datadog.android.core.internal.CoreFeature
import com.datadog.android.core.internal.net.DataOkHttpUploaderV2
import com.datadog.android.core.internal.net.UploadStatus
import com.datadog.android.core.internal.system.AndroidInfoProvider
import com.datadog.android.core.internal.utils.devLogger
import com.datadog.android.core.internal.utils.sdkLogger
import com.datadog.android.rum.RumAttributes
import com.datadog.android.sessionreplay.model.MobileSegment
import okhttp3.Call
import okhttp3.MediaType
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody

// TODO: RUMM-2547 Drop this class and return a list of requests
//  instead from SessionReplayRequestFactory
// This class is not test as it is meant for non - production usage. It will be dropped later.
internal class SessionReplayOkHttpUploader(
    endpoint: String,
    clientToken: String,
    source: String,
    sdkVersion: String,
    callFactory: Call.Factory,
    androidInfoProvider: AndroidInfoProvider,
    private val coreFeature: CoreFeature,
    private val compressor: BytesCompressor = BytesCompressor()
) : DataOkHttpUploaderV2(
    buildUrl(endpoint, TrackType.SESSION_REPLAY),
    clientToken,
    source,
    sdkVersion,
    callFactory,
    CONTENT_TYPE_MUTLIPART_FORM,
    androidInfoProvider,
    sdkLogger
) {

    private val tags: String
        get() {
            val elements = mutableListOf(
                "${RumAttributes.SERVICE_NAME}:${coreFeature.serviceName}",
                "${RumAttributes.APPLICATION_VERSION}:" +
                    coreFeature.packageVersionProvider.version,
                "${RumAttributes.SDK_VERSION}:$sdkVersion",
                "${RumAttributes.ENV}:${coreFeature.envName}"
            )
            if (coreFeature.variant.isNotEmpty()) {
                elements.add("${RumAttributes.VARIANT}:${coreFeature.variant}")
            }
            return elements.joinToString(",")
        }

    @Suppress("TooGenericExceptionCaught")
    fun upload(mobileSegment: MobileSegment, mobileSegmentAsBinary: ByteArray): UploadStatus {
        val uploadStatus = try {
            executeUploadRequest(mobileSegment, mobileSegmentAsBinary, requestId)
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

    @Suppress("UnsafeThirdPartyFunctionCall") // Called within a try/catch block
    private fun executeUploadRequest(
        mobileSegment: MobileSegment,
        mobileSegmentAsBinary: ByteArray,
        requestId: String
    ): UploadStatus {
        if (clientToken.isBlank()) {
            return UploadStatus.INVALID_TOKEN_ERROR
        }
        val request = buildRequest(mobileSegment, mobileSegmentAsBinary, requestId)
        val call = callFactory.newCall(request)
        val response = call.execute()
        response.close()
        return responseCodeToUploadStatus(response.code())
    }

    @Suppress("UnsafeThirdPartyFunctionCall") // Called within a try/catch block
    private fun buildRequest(
        segment: MobileSegment,
        segmentAsBinary: ByteArray,
        requestId: String
    ): Request {
        val builder = Request.Builder()
            .url(buildUrl())
            .post(buildRequestBody(segment, segmentAsBinary))

        buildHeaders(builder, requestId)

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

    override fun buildQueryParameters(): Map<String, Any> {
        return mapOf(
            QUERY_PARAM_SOURCE to source,
            QUERY_PARAM_TAGS to tags,
            QUERY_PARAM_EVP_ORIGIN_KEY to source,
            QUERY_PARAM_EVP_ORIGIN_VERSION_KEY to sdkVersion
        )
    }

    companion object {

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
        internal const val SOURCE_FORM_KEY = "end"
        internal const val SEGMENT_FORM_KEY = "segment"
        internal const val CONTENT_TYPE_BINARY = "application/octet-stream"
    }
}
