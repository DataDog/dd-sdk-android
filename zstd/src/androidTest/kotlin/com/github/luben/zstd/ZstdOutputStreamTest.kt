/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.github.luben.zstd

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.IOException

/**
 * Instrumented tests for [ZstdOutputStream].
 *
 * These tests verify the compression behavior of the stripped-down :zstd module
 * which only supports compression (no decompression).
 */
@RunWith(AndroidJUnit4::class)
class ZstdOutputStreamTest {

    // region Lifecycle Tests

    @Test
    fun closeCalledTwice_doesNotThrow() {
        // Given
        val outputStream = ByteArrayOutputStream()
        val zstdOutputStream = ZstdOutputStream(outputStream)

        // When
        zstdOutputStream.close()
        zstdOutputStream.close()

        // Then - no exception thrown
    }

    @Test(expected = IOException::class)
    fun writeAfterClose_throwsIOException() {
        // Given
        val outputStream = ByteArrayOutputStream()
        val zstdOutputStream = ZstdOutputStream(outputStream)
        zstdOutputStream.close()

        // When
        zstdOutputStream.write("test".toByteArray())

        // Then - IOException expected
    }

    @Test(expected = IOException::class)
    fun writeSingleByteAfterClose_throwsIOException() {
        // Given
        val outputStream = ByteArrayOutputStream()
        val zstdOutputStream = ZstdOutputStream(outputStream)
        zstdOutputStream.close()

        // When
        zstdOutputStream.write(42)

        // Then - IOException expected
    }

    @Test(expected = IOException::class)
    fun flushAfterClose_throwsIOException() {
        // Given
        val outputStream = ByteArrayOutputStream()
        val zstdOutputStream = ZstdOutputStream(outputStream)
        zstdOutputStream.close()

        // When
        zstdOutputStream.flush()

        // Then - IOException expected
    }

    // endregion

    // region Compression Output Tests

    @Test
    fun closeWithoutWriting_producesValidZstdFrame() {
        // Given
        val outputStream = ByteArrayOutputStream()
        val zstdOutputStream = ZstdOutputStream(outputStream)

        // When
        zstdOutputStream.close()
        val compressed = outputStream.toByteArray()

        // Then
        // A valid empty zstd frame should have at least the magic number and frame header
        assertThat(compressed).isNotEmpty()
        // Zstd magic number is 0xFD2FB528 (little-endian: 28 B5 2F FD)
        assertThat(compressed.size).isGreaterThanOrEqualTo(4)
        assertThat(compressed[0]).isEqualTo(0x28.toByte())
        assertThat(compressed[1]).isEqualTo(0xB5.toByte())
        assertThat(compressed[2]).isEqualTo(0x2F.toByte())
        assertThat(compressed[3]).isEqualTo(0xFD.toByte())
    }

    @Test
    fun compressingData_producesNonEmptyOutput() {
        // Given
        val inputData = "Hello, Zstd compression test!".toByteArray()
        val outputStream = ByteArrayOutputStream()
        val zstdOutputStream = ZstdOutputStream(outputStream)

        // When
        zstdOutputStream.write(inputData)
        zstdOutputStream.close()
        val compressed = outputStream.toByteArray()

        // Then
        assertThat(compressed).isNotEmpty()
        // Verify zstd magic number
        assertThat(compressed[0]).isEqualTo(0x28.toByte())
        assertThat(compressed[1]).isEqualTo(0xB5.toByte())
        assertThat(compressed[2]).isEqualTo(0x2F.toByte())
        assertThat(compressed[3]).isEqualTo(0xFD.toByte())
    }

    @Test
    fun writingSingleByteAtATime_producesValidOutput() {
        // Given
        val inputData = "Test data for single byte writes".toByteArray()
        val outputStream = ByteArrayOutputStream()
        val zstdOutputStream = ZstdOutputStream(outputStream)

        // When
        for (byte in inputData) {
            zstdOutputStream.write(byte.toInt())
        }
        zstdOutputStream.close()
        val compressed = outputStream.toByteArray()

        // Then
        assertThat(compressed).isNotEmpty()
        // Verify zstd magic number
        assertThat(compressed[0]).isEqualTo(0x28.toByte())
        assertThat(compressed[1]).isEqualTo(0xB5.toByte())
        assertThat(compressed[2]).isEqualTo(0x2F.toByte())
        assertThat(compressed[3]).isEqualTo(0xFD.toByte())
    }

    @Test
    fun writingWithOffsetAndLength_producesValidOutput() {
        // Given
        val fullData = "PREFIX_actual_data_to_compress_SUFFIX".toByteArray()
        val offset = 7 // Skip "PREFIX_"
        val length = 24 // "actual_data_to_compress"
        val outputStream = ByteArrayOutputStream()
        val zstdOutputStream = ZstdOutputStream(outputStream)

        // When
        zstdOutputStream.write(fullData, offset, length)
        zstdOutputStream.close()
        val compressed = outputStream.toByteArray()

        // Then
        assertThat(compressed).isNotEmpty()
        // Verify zstd magic number
        assertThat(compressed[0]).isEqualTo(0x28.toByte())
        assertThat(compressed[1]).isEqualTo(0xB5.toByte())
        assertThat(compressed[2]).isEqualTo(0x2F.toByte())
        assertThat(compressed[3]).isEqualTo(0xFD.toByte())
    }

    @Test
    fun writingLargeDataInChunks_producesValidOutput() {
        // Given
        val chunkSize = 128 * 1024 // 128 KB chunks
        val totalSize = 512 * 1024 // 512 KB total
        val inputData = ByteArray(totalSize) { (it % 256).toByte() }
        val outputStream = ByteArrayOutputStream()
        val zstdOutputStream = ZstdOutputStream(outputStream)

        // When
        var offset = 0
        while (offset < totalSize) {
            val remaining = totalSize - offset
            val writeSize = minOf(chunkSize, remaining)
            zstdOutputStream.write(inputData, offset, writeSize)
            offset += writeSize
        }
        zstdOutputStream.close()
        val compressed = outputStream.toByteArray()

        // Then
        assertThat(compressed).isNotEmpty()
        // Compressed size should be smaller than original for repetitive data
        assertThat(compressed.size).isLessThan(totalSize)
        // Verify zstd magic number
        assertThat(compressed[0]).isEqualTo(0x28.toByte())
        assertThat(compressed[1]).isEqualTo(0xB5.toByte())
        assertThat(compressed[2]).isEqualTo(0x2F.toByte())
        assertThat(compressed[3]).isEqualTo(0xFD.toByte())
    }

    @Test
    fun flushCalled_flushesData() {
        // Given
        val inputData = "Data before flush".toByteArray()
        val outputStream = ByteArrayOutputStream()
        val zstdOutputStream = ZstdOutputStream(outputStream)

        // When
        zstdOutputStream.write(inputData)
        zstdOutputStream.flush()
        val sizeAfterFlush = outputStream.size()

        zstdOutputStream.write("More data after flush".toByteArray())
        zstdOutputStream.close()
        val finalSize = outputStream.size()

        // Then
        assertThat(sizeAfterFlush).isGreaterThan(0)
        assertThat(finalSize).isGreaterThan(sizeAfterFlush)
    }

    // endregion

    // region BufferPool Integration Tests

    @Test
    fun usingCustomBufferPool_worksCorrectly() {
        // Given
        val customPool = object : BufferPool {
            var getCount = 0
            var releaseCount = 0

            override fun get(capacity: Int): java.nio.ByteBuffer {
                getCount++
                return java.nio.ByteBuffer.allocate(capacity)
            }

            override fun release(buffer: java.nio.ByteBuffer) {
                releaseCount++
            }
        }
        val outputStream = ByteArrayOutputStream()
        val zstdOutputStream = ZstdOutputStream(outputStream, customPool)

        // When
        zstdOutputStream.write("Test with custom buffer pool".toByteArray())
        zstdOutputStream.close()

        // Then
        assertThat(customPool.getCount).isGreaterThan(0)
        assertThat(customPool.releaseCount).isGreaterThan(0)
        assertThat(outputStream.toByteArray()).isNotEmpty()
    }

    @Test
    fun usingNoPool_worksCorrectly() {
        // Given
        val outputStream = ByteArrayOutputStream()
        val zstdOutputStream = ZstdOutputStream(outputStream, NoPool.INSTANCE)

        // When
        zstdOutputStream.write("Test with NoPool".toByteArray())
        zstdOutputStream.close()
        val compressed = outputStream.toByteArray()

        // Then
        assertThat(compressed).isNotEmpty()
        // Verify zstd magic number
        assertThat(compressed[0]).isEqualTo(0x28.toByte())
        assertThat(compressed[1]).isEqualTo(0xB5.toByte())
        assertThat(compressed[2]).isEqualTo(0x2F.toByte())
        assertThat(compressed[3]).isEqualTo(0xFD.toByte())
    }

    // endregion
}
