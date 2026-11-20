/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.profiling.internal.utils

import com.datadog.android.api.InternalLogger
import com.github.luben.zstd.ZstdException
import com.github.luben.zstd.ZstdOutputStream
import java.io.File
import java.io.IOException
import java.io.OutputStream

private const val COMPRESSION_LEVEL = 6

internal const val COMPRESSION_FAILED_MESSAGE =
    "Failed to zstd-compress the Perfetto trace, saving it uncompressed."

/**
 * Compresses [file] as a zstd frame of at most [maxBytes], or reports the trace as raw when
 * compression fails, as long as the raw trace fits [maxBytes].
 */
internal fun compressFileSafe(file: File, maxBytes: Int, internalLogger: InternalLogger): CompressionResult {
    val sizeBytes = fileSizeSafe(file.path, internalLogger)
    if (sizeBytes <= 0L) return CompressionResult.Unreadable
    return try {
        CompressionResult.Compressed(compressFile(file, maxBytes))
    } catch (e: CompressedSizeLimitException) {
        CompressionResult.TooLarge(e.compressedSizeBytes)
    } catch (e: IOException) {
        rawFallback(maxBytes, sizeBytes, internalLogger, e)
    } catch (e: SecurityException) {
        rawFallback(maxBytes, sizeBytes, internalLogger, e)
    } catch (e: ZstdException) {
        rawFallback(maxBytes, sizeBytes, internalLogger, e)
    } catch (e: IllegalStateException) {
        // Native compression context allocation failed
        rawFallback(maxBytes, sizeBytes, internalLogger, e)
    } catch (e: LinkageError) {
        // Native library missing or unsupported ABI
        rawFallback(maxBytes, sizeBytes, internalLogger, e)
    }
}

// Streams the file through zstd so peak memory stays around the compressed output, and aborts
// with CompressedSizeLimitException as soon as the output exceeds maxBytes.
@Throws(IOException::class)
private fun compressFile(file: File, maxBytes: Int): ByteArray {
    val sink = BoundedByteArrayOutputStream(maxBytes)
    @Suppress("UnsafeThirdPartyFunctionCall") // Handled in compressFileSafe
    file.inputStream().use { input ->
        ZstdOutputStream(sink, COMPRESSION_LEVEL).use { input.copyTo(it) }
    }
    return sink.toByteArray()
}

// A partial frame is never saved: the raw trace is handed over by path, and the caller streams
// it into the metadata blob, so no raw copy is held here. The size measured before compressing is
// reused: the writer streams the raw file through encode(stream, size, rum), which rejects a trace
// truncated or grown since it was measured.
private fun rawFallback(
    maxBytes: Int,
    sizeBytes: Long,
    internalLogger: InternalLogger,
    error: Throwable
): CompressionResult {
    logCompressionFailure(internalLogger, error)
    return rawFallbackResult(sizeBytes, maxBytes)
}

internal fun logCompressionFailure(internalLogger: InternalLogger, throwable: Throwable) {
    internalLogger.log(
        InternalLogger.Level.ERROR,
        InternalLogger.Target.MAINTAINER,
        { COMPRESSION_FAILED_MESSAGE },
        throwable,
        onlyOnce = true
    )
}

// The measured size decides the state on its own, so the decision is testable without a failure.
internal fun rawFallbackResult(sizeBytes: Long, maxBytes: Int): CompressionResult = when {
    sizeBytes <= 0L -> CompressionResult.Unreadable
    sizeBytes > maxBytes -> CompressionResult.TooLarge(sizeBytes)
    else -> CompressionResult.Raw(sizeBytes)
}

/**
 * What to save for the Perfetto trace.
 */
internal sealed class CompressionResult {

    /** A standard zstd frame of the trace. */
    internal class Compressed(val bytes: ByteArray) : CompressionResult()

    /** The trace is uncompressed, [sizeBytes] long. */
    internal class Raw(val sizeBytes: Long) : CompressionResult()

    /** The trace reached [sizeBytes], over the limit. */
    internal class TooLarge(val sizeBytes: Long) : CompressionResult()

    /** The trace could not be read. */
    internal object Unreadable : CompressionResult()
}

internal class CompressedSizeLimitException(val compressedSizeBytes: Long) :
    IOException("Compressed profile exceeds the size limit")

// Owns its buffer so it starts small and its capacity never grows past maxBytes: peak memory is at
// most two buffers of maxBytes, during a growth copy or the final exact-size copy.
internal class BoundedByteArrayOutputStream(private val maxBytes: Int) : OutputStream() {

    var size = 0
        private set

    private var buffer = ByteArray(minOf(INITIAL_BUFFER_BYTES, maxBytes))

    override fun write(b: Int) {
        ensureCapacity(1)
        buffer[size] = b.toByte()
        size++
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        ensureCapacity(len)
        @Suppress("UnsafeThirdPartyFunctionCall") // Callers pass valid ranges
        System.arraycopy(b, off, buffer, size, len)
        size += len
    }

    @Suppress("UnsafeThirdPartyFunctionCall") // size is never negative
    internal fun toByteArray(): ByteArray = if (size == buffer.size) buffer else buffer.copyOf(size)

    private fun ensureCapacity(len: Int) {
        val newSize = size + len
        @Suppress("ThrowingInternalException") // Caught in compressFileSafe to drop the profile
        if (newSize > maxBytes) throw CompressedSizeLimitException(newSize.toLong())
        if (newSize > buffer.size) {
            val newCapacity = minOf(maxOf(buffer.size * 2L, newSize.toLong()), maxBytes.toLong())
            @Suppress("UnsafeThirdPartyFunctionCall") // newCapacity is positive and at most maxBytes
            buffer = buffer.copyOf(newCapacity.toInt())
        }
    }

    companion object {
        private const val INITIAL_BUFFER_BYTES = 8 * 1024
    }
}
