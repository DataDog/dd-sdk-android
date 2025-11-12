/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sdk.integration.sessionreplay

import com.datadog.android.sdk.rules.HandledRequest
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import okio.Buffer
import java.io.ByteArrayOutputStream
import java.util.Locale
import java.util.zip.Inflater

/**
 * Utility functions for parsing and decompressing Session Replay segments in integration tests.
 */
internal object SessionReplaySegmentUtils {

    private val SEGMENT_FORM_DATA_REGEX =
        Regex("content-disposition: form-data; name=\"segment\"; filename=\"(.+)\"")
    private val CONTENT_LENGTH_REGEX =
        Regex("content-length: (\\d+)")

    /**
     * Extracts and parses a Session Replay segment from a handled request.
     * The segment is decompressed and parsed as JSON.
     *
     * @return The parsed JSON element, or null if no segment found or parsing failed
     */
    fun HandledRequest.extractSrSegmentAsJson(): JsonElement? {
        val compressedSegmentBody = resolveSrSegmentBodyFromRequest(requestBuffer.clone())
        if (compressedSegmentBody.isNotEmpty()) {
            // decompress the segment binary
            return JsonParser.parseString(String(decompressBytes(compressedSegmentBody)))
        }

        return null
    }

    /**
     * Resolves the Session Replay segment body from a multipart form request buffer.
     *
     * Example of a multipart form segment body:
     * ```
     * Content-Disposition: form-data; name="segment"; filename="db081a08-96ab-4931-a98e-b2dd2d9c1b34"
     * Content-Type: application/octet-stream
     * Content-Length: 1060
     *
     * <compressed segment as byte array of 1060 length>
     * ```
     *
     * @param buffer The request buffer to parse
     * @return The compressed segment bytes, or empty array if not found
     */
    private fun resolveSrSegmentBodyFromRequest(buffer: Buffer): ByteArray {
        var line = buffer.readUtf8Line()
        while (line != null) {
            if (line.lowercase(Locale.ENGLISH).matches(SEGMENT_FORM_DATA_REGEX)) {
                return extractSegmentBody(buffer) ?: ByteArray(0)
            }
            line = buffer.readUtf8Line()
        }
        return ByteArray(0)
    }

    /**
     * Extracts the segment body bytes from the buffer after finding the Content-Disposition header.
     *
     * @param buffer The request buffer positioned after the Content-Disposition line
     * @return The compressed segment bytes, or null if extraction failed
     */
    private fun extractSegmentBody(buffer: Buffer): ByteArray? {
        // Skip Content-Type line
        buffer.readUtf8Line()

        // Read and parse Content-Length
        val contentLengthLine = buffer.readUtf8Line()?.lowercase(Locale.ENGLISH)
        val matcher = contentLengthLine?.let { CONTENT_LENGTH_REGEX.find(it, 0) }
        val contentLength = matcher?.groupValues?.getOrNull(1)?.toLongOrNull() ?: return null

        // Skip empty line before content
        buffer.readUtf8Line()

        return buffer.readByteArray(contentLength)
    }

    /**
     * Decompresses a byte array using the Inflater (DEFLATE algorithm).
     *
     * @param input The compressed byte array
     * @return The decompressed byte array
     */
    private fun decompressBytes(input: ByteArray): ByteArray {
        val bos = ByteArrayOutputStream()
        val buf = ByteArray(DECOMPRESSION_BUFFER_SIZE)
        val decompressor = Inflater()
        decompressor.setInput(input, 0, input.size)
        var uncompressedBytes: Int
        do {
            uncompressedBytes = decompressor.inflate(buf)
            if (uncompressedBytes > 0) {
                bos.write(buf, 0, uncompressedBytes)
            }
        } while (uncompressedBytes > 0)
        decompressor.end()
        return bos.toByteArray()
    }
}
