/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.profiling.internal.utils

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.catchThrowableOfType
import org.junit.jupiter.api.Test

internal class BoundedByteArrayOutputStreamTest {

    @Test
    fun `M hold exactly the written bytes W write() {output equal to the limit}`() {
        // Given
        val testedStream =
            BoundedByteArrayOutputStream(MAX_BYTES)
        val bytes = ByteArray(MAX_BYTES) { it.toByte() }

        // When
        testedStream.write(bytes, 0, 60)
        testedStream.write(bytes, 60, 39)
        testedStream.write(bytes[99].toInt())

        // Then
        assertThat(testedStream.size).isEqualTo(MAX_BYTES)
        assertThat(testedStream.buffer).hasSize(MAX_BYTES)
        assertThat(testedStream.buffer.copyOf(testedStream.size)).isEqualTo(bytes)
    }

    @Test
    fun `M hold the written bytes W write() {output below the limit}`() {
        // Given
        val testedStream =
            BoundedByteArrayOutputStream(
                LARGE_MAX_BYTES
            )
        val chunk = ByteArray(9_000) { it.toByte() }

        // When
        testedStream.write(chunk, 0, chunk.size)

        // Then
        assertThat(testedStream.size).isEqualTo(chunk.size)
        // The buffer is longer than the data, so callers must honour size
        assertThat(testedStream.buffer.size).isGreaterThan(chunk.size)
        assertThat(testedStream.buffer.copyOf(testedStream.size)).isEqualTo(chunk)
    }

    @Test
    fun `M abort W write() {one byte over the limit}`() {
        // Given
        val testedStream =
            BoundedByteArrayOutputStream(MAX_BYTES)
        testedStream.write(ByteArray(MAX_BYTES), 0, MAX_BYTES)

        // When
        val error =
            catchThrowableOfType(
                CompressedSizeLimitException::class.java
            ) {
                testedStream.write(0)
            }

        // Then
        assertThat(error.compressedSizeBytes).isEqualTo(MAX_BYTES + 1L)
    }

    @Test
    fun `M abort W write() {chunk crosses the limit}`() {
        // Given
        val testedStream =
            BoundedByteArrayOutputStream(MAX_BYTES)

        // When
        val error =
            catchThrowableOfType(
                CompressedSizeLimitException::class.java
            ) {
                testedStream.write(ByteArray(MAX_BYTES + 10), 0, MAX_BYTES + 10)
            }

        // Then
        assertThat(error.compressedSizeBytes).isEqualTo(MAX_BYTES + 10L)
    }

    companion object {
        private const val MAX_BYTES = 100
        private const val LARGE_MAX_BYTES = 32 * 1024
    }
}
