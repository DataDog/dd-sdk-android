/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.profiling.internal.utils

import com.datadog.android.api.InternalLogger
import com.datadog.android.profiling.forge.Configurator
import com.datadog.android.utils.verifyLog
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import okio.GzipSink
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.catchThrowableOfType
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.io.File
import java.io.IOException
import java.util.Random
import java.util.zip.GZIPInputStream

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class GzipCompressionTest {

    @Mock
    private lateinit var mockInternalLogger: InternalLogger

    @TempDir
    lateinit var tmp: File

    @Test
    fun `M return a gzip stream W compressFileSafe()`(forge: Forge) {
        // Given
        val rawBytes = forge.aString(size = 64 * 1024).toByteArray()
        val file = tmp.resolve("trace").apply { writeBytes(rawBytes) }

        // When
        val result = compressFileSafe(file, LIMIT_BYTES, mockInternalLogger)

        // Then
        assertThat(result).isInstanceOf(CompressionResult.Compressed::class.java)
        val compressed = result as CompressionResult.Compressed
        assertThat(compressed.bytes).startsWith(*GZIP_MAGIC)
        assertThat(compressed.bytes.gzipDecompress()).isEqualTo(rawBytes)
        verifyNoInteractions(mockInternalLogger)
    }

    @Test
    fun `M return exactly the stream W compressFileSafe() {at the limit}`(forge: Forge) {
        // Given the size the compressed stream of a trace takes
        val rawBytes = forge.aString(size = 4 * 1024).toByteArray()
        val file = tmp.resolve("trace").apply { writeBytes(rawBytes) }
        val streamSize = (compressFileSafe(file, LIMIT_BYTES, mockInternalLogger) as CompressionResult.Compressed)
            .bytes.size

        // When the limit is exactly that size
        val result = compressFileSafe(file, streamSize, mockInternalLogger)

        // Then the stream is accepted, is exactly that long, and is a standard gzip stream
        assertThat(result).isInstanceOf(CompressionResult.Compressed::class.java)
        val compressed = result as CompressionResult.Compressed
        assertThat(compressed.bytes).hasSize(streamSize)
        assertThat(compressed.bytes.gzipDecompress()).isEqualTo(rawBytes)
    }

    @Test
    fun `M report the size reached W compressFileSafe() {one byte under the stream size}`(forge: Forge) {
        // Given
        val rawBytes = forge.aString(size = 4 * 1024).toByteArray()
        val file = tmp.resolve("trace").apply { writeBytes(rawBytes) }
        val streamSize = (compressFileSafe(file, LIMIT_BYTES, mockInternalLogger) as CompressionResult.Compressed)
            .bytes.size

        // When the limit is one byte below the size of the stream
        val result = compressFileSafe(file, streamSize - 1, mockInternalLogger)

        // Then the abort reports the compressed size reached, which is the full stream size
        assertThat(result).isInstanceOf(CompressionResult.TooLarge::class.java)
        assertThat((result as CompressionResult.TooLarge).sizeBytes).isEqualTo(streamSize.toLong())
        assertThat(result.compressed).isTrue()
        verifyNoInteractions(mockInternalLogger)
    }

    @Test
    fun `M return the size reached W compressFileSafe() {compressed output over the limit}`(forge: Forge) {
        // Given
        val incompressible = ByteArray(8 * LIMIT_BYTES).also { Random(forge.aLong()).nextBytes(it) }
        val file = tmp.resolve("trace").apply { writeBytes(incompressible) }

        // When
        val result = compressFileSafe(file, LIMIT_BYTES, mockInternalLogger)

        // Then
        assertThat(result).isInstanceOf(CompressionResult.TooLarge::class.java)
        assertThat((result as CompressionResult.TooLarge).sizeBytes).isGreaterThan(LIMIT_BYTES.toLong())
        assertThat(result.sizeBytes).isLessThan(2L * LIMIT_BYTES)
        // The size is the compressed size reached when the stream aborted
        assertThat(result.compressed).isTrue()
        verifyNoInteractions(mockInternalLogger)
    }

    @Test
    fun `M report the trace as unreadable W compressFileSafe() {the trace is empty}`() {
        // Given an empty trace, which would otherwise become a valid empty gzip stream
        val empty = tmp.resolve("trace").apply { writeBytes(ByteArray(0)) }

        // When
        val result = compressFileSafe(empty, LIMIT_BYTES, mockInternalLogger)

        // Then
        assertThat(result).isSameAs(CompressionResult.Unreadable)
        verifyNoInteractions(mockInternalLogger)
    }

    @Test
    fun `M report the trace as unreadable W compressFileSafe() {the trace is missing}`() {
        // Given
        val missing = tmp.resolve("missing")

        // When
        val result = compressFileSafe(missing, LIMIT_BYTES, mockInternalLogger)

        // Then
        assertThat(result).isSameAs(CompressionResult.Unreadable)
        verifyNoInteractions(mockInternalLogger)
    }

    @Test
    fun `M report the raw trace W compressFileSafe() {writing the compressed output fails}`(forge: Forge) {
        verifyCompressionFailure(IOException(forge.anAlphabeticalString()), forge)
    }

    @Test
    fun `M report the raw trace W compressFileSafe() {access denied}`(forge: Forge) {
        verifyCompressionFailure(SecurityException(forge.anAlphabeticalString()), forge)
    }

    @Test
    fun `M propagate the error W compressFileSafe() {the VM runs out of memory}`(forge: Forge) {
        // Given
        val fakeError = OutOfMemoryError(forge.anAlphabeticalString())
        mockGzipWrite(fakeError) {
            val file = tmp.resolve("trace").apply { writeBytes(ByteArray(TRACE_SIZE)) }

            // When
            val thrown = catchThrowableOfType(OutOfMemoryError::class.java) {
                compressFileSafe(file, LIMIT_BYTES, mockInternalLogger)
            }

            // Then a fatal VM error is never swallowed
            assertThat(thrown).isNotNull()
            verifyNoInteractions(mockInternalLogger)
        }
    }

    @Test
    fun `M report the raw trace W compressFileSafe() {raw trace under the limit}`(forge: Forge) {
        // Given a raw trace within the limit whose compression fails
        val fakeError = IOException(forge.anAlphabeticalString())
        mockGzipWrite(fakeError) {
            val file = tmp.resolve("trace").apply { writeBytes(ByteArray(SMALL_LIMIT / 2)) }

            // When
            val result = compressFileSafe(file, SMALL_LIMIT, mockInternalLogger)

            // Then
            assertThat(result).isInstanceOf(CompressionResult.Raw::class.java)
            assertThat((result as CompressionResult.Raw).sizeBytes).isEqualTo(SMALL_LIMIT / 2L)
            verifyCompressionFailureLogged(fakeError)
        }
    }

    @Test
    fun `M report the raw trace W compressFileSafe() {raw trace exactly at the limit}`(forge: Forge) {
        // Given
        val fakeError = IOException(forge.anAlphabeticalString())
        mockGzipWrite(fakeError) {
            val file = tmp.resolve("trace").apply { writeBytes(ByteArray(SMALL_LIMIT)) }

            // When
            val result = compressFileSafe(file, SMALL_LIMIT, mockInternalLogger)

            // Then
            assertThat(result).isInstanceOf(CompressionResult.Raw::class.java)
            assertThat((result as CompressionResult.Raw).sizeBytes).isEqualTo(SMALL_LIMIT.toLong())
        }
    }

    @Test
    fun `M report the trace as too large W compressFileSafe() {raw trace over the limit}`(forge: Forge) {
        // Given a raw trace larger than the limit whose compression fails
        val fakeError = IOException(forge.anAlphabeticalString())
        mockGzipWrite(fakeError) {
            val file = tmp.resolve("trace").apply { writeBytes(ByteArray(SMALL_LIMIT + 1)) }

            // When
            val result = compressFileSafe(file, SMALL_LIMIT, mockInternalLogger)

            // Then the measured raw size is reported, not a compressed size
            assertThat(result).isInstanceOf(CompressionResult.TooLarge::class.java)
            assertThat((result as CompressionResult.TooLarge).sizeBytes).isEqualTo(SMALL_LIMIT + 1L)
            assertThat(result.compressed).isFalse()
            verifyCompressionFailureLogged(fakeError)
        }
    }

    private fun verifyCompressionFailureLogged(fakeError: Throwable) {
        mockInternalLogger.verifyLog(
            InternalLogger.Level.ERROR,
            InternalLogger.Target.MAINTAINER,
            COMPRESSION_FAILED_MESSAGE,
            fakeError,
            onlyOnce = true
        )
    }

    private fun verifyCompressionFailure(fakeError: Throwable, forge: Forge) {
        // Given a trace with a size, whose compression fails
        val rawBytes = ByteArray(TRACE_SIZE).also { Random(forge.aLong()).nextBytes(it) }
        mockGzipWrite(fakeError) {
            val file = tmp.resolve("trace").apply { writeBytes(rawBytes) }

            // When
            val result = compressFileSafe(file, LIMIT_BYTES, mockInternalLogger)

            // Then
            assertThat(result).isInstanceOf(CompressionResult.Raw::class.java)
            assertThat((result as CompressionResult.Raw).sizeBytes).isEqualTo(TRACE_SIZE.toLong())
            mockInternalLogger.verifyLog(
                InternalLogger.Level.ERROR,
                InternalLogger.Target.MAINTAINER,
                COMPRESSION_FAILED_MESSAGE,
                fakeError,
                onlyOnce = true
            )
        }
    }

    // Fails the writes to the gzip sink, which is what the compression hits.
    private fun mockGzipWrite(fakeError: Throwable, block: () -> Unit) {
        Mockito.mockConstruction(GzipSink::class.java) { mock, _ ->
            whenever(mock.write(any(), any())) doThrow fakeError
        }.use {
            block()
        }
    }

    private fun ByteArray.gzipDecompress(): ByteArray = GZIPInputStream(inputStream()).use { it.readBytes() }

    companion object {
        private const val LIMIT_BYTES = 1024 * 1024
        private const val TRACE_SIZE = 4096
        private const val SMALL_LIMIT = 4096
        private val GZIP_MAGIC = byteArrayOf(0x1F, 0x8B.toByte())
    }
}
