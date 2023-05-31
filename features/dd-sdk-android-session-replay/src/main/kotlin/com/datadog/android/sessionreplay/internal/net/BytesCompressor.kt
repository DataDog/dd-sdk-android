/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.net

import java.io.ByteArrayOutputStream
import java.util.zip.Deflater

/**
 *  Compresses the payload data using the ZIP compression algorithm.
 *  This class is meant for internal usage.
 */
internal class BytesCompressor {

    fun compressBytes(uncompressedData: ByteArray): ByteArray {
        // Create the compressor with highest level of compression.
        val deflater = Deflater(COMPRESSION_LEVEL)
        // We will start with an OutputStream double the size of the data
        val outputStream = ByteArrayOutputStream(uncompressedData.size * 2)
        // Compress the data
        // in order to align with dogweb way of decompressing the segments we need to compress
        // using the SYNC_FLUSH flag which adds the 0000FFFF flag at the end of the
        // compressed data
        val compressedData = outputStream.use {
            compress(deflater, uncompressedData, it, Deflater.SYNC_FLUSH)
            // in order to align with dogweb way of decompressing the segments we need to add
            // a fake checksum at the end
            compress(deflater, ByteArray(0), it, Deflater.FULL_FLUSH)
            deflater.end()
            it.toByteArray()
        }
        // Get the compressed data
        return compressedData
    }

    private fun compress(
        deflater: Deflater,
        data: ByteArray,
        output: ByteArrayOutputStream,
        flag: Int
    ) {
        var buffer = getBufferByDataAndFlag(data, flag)
        var retriesCounter = 1
        var bytesRead: Int
        do {
            buffer = ByteArray(buffer.size * retriesCounter)
            if (flag == Deflater.SYNC_FLUSH) {
                deflater.reset()
            }
            deflater.setInput(data)
            if (flag == Deflater.FULL_FLUSH) {
                deflater.finish()
            }
            @Suppress("UnsafeThirdPartyFunctionCall")
            // we are only calling this with valid flags
            bytesRead = deflater.deflate(buffer, 0, buffer.size, flag)
            // according with the API if bytesRead >= buffer.size we need
            // to retry with a bigger buffer
            retriesCounter += 1
        } while (bytesRead >= buffer.size)
        @Suppress("UnsafeThirdPartyFunctionCall")
        // we are calling this function always with a valid buffer (bytesRead < buffer.size)
        output.write(buffer, 0, bytesRead)
    }

    private fun getBufferByDataAndFlag(data: ByteArray, flag: Int): ByteArray {
        return when (flag) {
            Deflater.FULL_FLUSH -> ByteArray(
                HEADER_SIZE_IN_BYTES +
                    data.size + CHECKSUM_FLAG_SIZE_IN_BYTES
            )
            Deflater.SYNC_FLUSH -> ByteArray(
                HEADER_SIZE_IN_BYTES +
                    data.size + SYNC_FLAG_SIZE_IN_BYTES
            )
            else -> ByteArray(HEADER_SIZE_IN_BYTES + data.size)
        }
    }

    companion object {
        private const val HEADER_SIZE_IN_BYTES = 2
        private const val SYNC_FLAG_SIZE_IN_BYTES = 4
        internal const val CHECKSUM_FLAG_SIZE_IN_BYTES = 6

        // We are using compression level 6 in order to align with the same compression type used
        // in the browser sdk.
        private const val COMPRESSION_LEVEL = 6
    }
}
