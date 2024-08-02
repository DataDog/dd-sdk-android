/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.net

import com.datadog.android.sessionreplay.model.MobileSegment
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody

internal class SegmentRequestBodyFactory(
    private val compressor: BytesCompressor = BytesCompressor()
) {

    @Suppress("UnsafeThirdPartyFunctionCall") // Caught in the caller
    fun create(serializedSegmentsPairs: List<Pair<MobileSegment, JsonObject>>): RequestBody {
        val multipartBody = MultipartBody.Builder().setType(MultipartBody.FORM)
        val metadata = JsonArray()
        serializedSegmentsPairs.forEachIndexed { index, segment ->
            // because of the way the compressed segments are concatenated in order to be
            // decompressed when retrieved by the player,
            // we need to add a new line at the end of each segment
            val segmentAsByteArray = (segment.second.toString() + "\n").toByteArray()
            val compressedData = compressor.compressBytes(segmentAsByteArray)
            val segmentAsJson = segment.first.toJson().asJsonObject.apply {
                addProperty(COMPRESSED_SEGMENT_SIZE_FORM_KEY, compressedData.size)
                addProperty(RAW_SEGMENT_SIZE_FORM_KEY, segmentAsByteArray.size)
            }
            multipartBody.addFormDataPart(
                name = SEGMENT_DATA_FORM_KEY,
                filename = "${BINARY_FILENAME_PREFIX}$index",
                body = compressedData.toRequestBody(CONTENT_TYPE_BINARY_TYPE)
            )
            metadata.add(segmentAsJson)
        }

        multipartBody.addFormDataPart(
            name = EVENT_NAME_FORM_KEY,
            filename = BLOB_FILENAME,
            body = metadata.toString().toRequestBody(CONTENT_TYPE_JSON_TYPE)
        )

        return multipartBody.build()
    }

    companion object {
        internal const val BINARY_FILENAME_PREFIX = "file"
        internal const val BLOB_FILENAME = "blob"
        internal const val EVENT_NAME_FORM_KEY = "event"
        internal const val RAW_SEGMENT_SIZE_FORM_KEY = "raw_segment_size"
        internal const val COMPRESSED_SEGMENT_SIZE_FORM_KEY = "compressed_segment_size"
        internal const val SEGMENT_DATA_FORM_KEY = "segment"
        internal val CONTENT_TYPE_BINARY_TYPE = "application/octet-stream".toMediaTypeOrNull()
        internal val CONTENT_TYPE_JSON_TYPE = "application/json".toMediaTypeOrNull()
    }
}
