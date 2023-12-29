/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.domain

import com.datadog.android.sessionreplay.internal.net.BytesCompressor
import com.datadog.android.sessionreplay.model.MobileSegment
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody

internal class RequestBodyFactory(
    private val compressor: BytesCompressor = BytesCompressor()
) {

    fun create(serializedSegmentsPairs: List<Pair<MobileSegment, JsonObject>>): RequestBody {
        return buildRequestBody(serializedSegmentsPairs)
    }

    @Suppress("UnsafeThirdPartyFunctionCall") // Handled up in the caller chain
    private fun buildRequestBody(segments: List<Pair<MobileSegment, JsonObject>>):
            RequestBody {
        val multipartBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
        val metadata = JsonArray()
        segments.forEachIndexed{ index, segment ->
            val segmentAsByteArray = (segment.second.toString() + "\n").toByteArray()
            val compressedData = compressor.compressBytes(segmentAsByteArray)
            val segmentAsJson = segment.first.toJson().asJsonObject.apply {
                addProperty("compressed_segment_size", compressedData.size)
                addProperty(RAW_SEGMENT_SIZE_FORM_KEY, segmentAsByteArray.size)
            }
            multipartBody.addFormDataPart(
                    SEGMENT_FORM_KEY,
                    "file$index",
                    compressedData.toRequestBody(CONTENT_TYPE_BINARY.toMediaTypeOrNull())
            )
            metadata.add(segmentAsJson)
        }
        multipartBody.addFormDataPart(
                "event",
                filename = "blob",
                metadata.toString()
                        .toRequestBody(CONTENT_TYPE_JSON.toMediaTypeOrNull())
        )
        return multipartBody.build()
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
        internal const val CONTENT_TYPE_JSON = "application/json"
    }
}
