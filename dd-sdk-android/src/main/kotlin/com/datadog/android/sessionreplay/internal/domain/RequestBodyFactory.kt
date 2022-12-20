/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.domain

import com.datadog.android.core.internal.utils.internalLogger
import com.datadog.android.sessionreplay.model.MobileSegment
import com.datadog.android.sessionreplay.net.BytesCompressor
import com.datadog.android.v2.api.InternalLogger
import com.google.gson.JsonObject
import okhttp3.MediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody

internal class RequestBodyFactory(
    private val compressor: BytesCompressor = BytesCompressor()
) {

    fun create(
        segment: MobileSegment,
        serializedSegment: JsonObject
    ): RequestBody? {
        return try {
            // we need to add a new line at the end of each segment for being able to format it
            // as an Array when read by the player
            val segmentAsBinary = (serializedSegment.toString() + "\n").toByteArray()
            buildRequestBody(segment, segmentAsBinary)
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            internalLogger.log(
                InternalLogger.Level.ERROR,
                InternalLogger.Target.MAINTAINER,
                "Unable to create session replay request body.",
                e
            )
            null
        }
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

    companion object {

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
    }
}
